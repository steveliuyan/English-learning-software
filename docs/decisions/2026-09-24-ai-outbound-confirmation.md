# AI 出站确认与请求参数白名单

## 背景

AGENTS.md 要求「首次调用第三方 Endpoint 前展示域名及『文本/图片和 Key 将发送给该服务』的确认；
图片发送每次都需明确确认」，并要求只允许 `temperature` / `top_p` / `max_tokens` /
`timeout_seconds` / 受控系统提示模板，拒绝任意 Header、任意请求体覆盖与任意工具调用。

F2-02 的实现计划（`docs/superpowers/plans/2026-09-22-f2-02-ai-profile-security.md`）把这两件事
交给 `requiredConfirmation(...)` 与 `validateRequestParameters(...)` 两个纯函数，接口草案写成：

```kotlin
fun requiredConfirmation(
    profile: AiProfile,
    payloadKind: AiPayloadKind,
    hasConfirmedTextHost: Boolean,
): ConfirmationRequirement

fun validateRequestParameters(parameters: Map<String, Any?>): Result<AiAdvancedParameters>
```

实施时发现草案有两处会在真实场景下失守，于是收紧。

## 候选方案与决定

### 一、确认的粒度：布尔量还是域名

- **草案 `hasConfirmedTextHost: Boolean`**：调用方自己维护「确认过文本」这个事实。
  问题是它说不出**确认的是哪个域名**。用户先配置 `api.a.com` 并确认过，之后把 Endpoint 改成
  `api.b.com`，同一份 `true` 会继续被复用——结果是向一个从未被确认过的域名发出了请求，
  正好绕开 AGENTS.md 那一句「首次调用第三方 Endpoint 前展示域名」。
- **决定：改成 `confirmedTextHost: String?`**，用域名本身做比较。换域名一定重新问，也不需要调用方
  额外记住「旧域名是谁」。

顺带补一层一致性检查：`ConfirmationRequirement.resolvedBy(answer)` 只在 `answer` 的域名**与**
载荷类型都与当前目标一致、且 `confirmed` 为真时才转成 `Satisfied`。少了这层，先弹 A 的确认框、
再改 Endpoint 去调 B，就能拿旧确认当新授权用（典型的确认—使用时间差问题）。

### 二、Endpoint 不合法时的返回值

- **草案返回裸的 `ConfirmationRequirement`**：Endpoint 不合法时算不出目标域名，只能再加一个
  「无效」状态。这会把「配置坏了」伪装成「正常流程里的一个待办」，调用方很容易照常弹确认框。
- **决定：返回 `Result<ConfirmationRequirement>`**，失败时沿用 `AppError.InvalidAiConfiguration`。
  与既有 `validateEndpoint(...): Result<URI>` 的写法一致，也让「配置有问题」在类型上就必须被处理。

### 三、参数白名单的失败方式

`AiAdvancedParameters` 用 `require(...)` 守范围，会抛 `IllegalArgumentException`。若把它当作校验
手段，一条越界的 `temperature` 会带着异常冒到 UI 层。因此 `validateRequestParameters` 在**构造前**
逐项判定类型与有限性，并且：

- 未知参数名一律拒绝（`extra_headers`、`authorization`、`tools`、`body`、`stream` 都是测试用例），
  这样「任意 Header / 请求体覆盖 / 工具调用」在类型上就无处可放——返回的是
  `AiAdvancedParameters`，它没有承载这些字段的位置。
- 显式传 `null` 视为类型错误，**不**退回默认值。否则「想改这项但传错了」会被静默变成
  「用默认值跑」，用户永远查不出为什么设置没生效。
- 浮点字段拒绝 `NaN` / `±Infinity`；整数字段拒绝 `1024.5` 这类非整值。
- 最后仍用 `runCatching` 兜一层 `require`，把可能漏掉的越界转成失败而不是异常。

### 四、失败分类放在哪里

计划原本要改 `core/error/AppError.kt`。**决定不改。** `AppError` 是存储与本机基础设施的错误模型
（`StorageUnavailable`、`KeyStoreUnavailable`、`IntegrityMismatch`…），而 AI 的九类失败都属于传输层。
混在一起后，调用方无法从类型上判断「这个错误该不该重试网络」。因此新增自成一体的 `AiFailure`：

- 每个成员都是**不带字段的 `data object`**，用户可见文案只能来自 `AiFailureUiText` 枚举常量。
  这不是风格偏好，而是泄露防线：只要没有任何成员能携带自由文本，原始响应体、Endpoint 与 Key
  就无法顺着错误对象流到界面、日志或截图里。`AiFailureTest` 会扫描全部文案，一旦有人把它改成
  带字段的类，测试立刻失败。
- `toUserAction()` 用 `when` 对 sealed 接口做穷尽检查，将来新增失败类型会**编译失败**，
  不会悄悄落进某个 `else` 给出错误动作。
- `Cancelled` 与网络类失败映射到不同动作（`Dismiss` vs `CheckNetwork` / `RetryLater`），
  避免把用户自己取消说成「网络不可用」，把人引去排查没坏的东西。
- `Unauthorized` 映射到 `ConfigureProfile` 而不是「重试」：重试同一份被拒绝的 Key 永远不会成功。

## 影响

- F2-03 的文章生成必须经 `requiredConfirmation` 取确认、经 `validateRequestParameters` 收敛参数，
  不得自行拼接请求体；图片类调用每次都要重新确认，文本类调用按域名复用。
- 用户确认过的文本域名由调用方（ViewModel）在会话内保存，不落库：**重启后一定会重新问一次**，
  这是有意为之——把确认持久化就等于用户再也看不到那次确认。
- 确认状态只含域名与载荷类型，永远不含 Key。
