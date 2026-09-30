# 默认 AI Profile 选择设计

## 目标

为文章生成落地设备级、可解释的默认文本 AI Profile 选择，替换当前“按保存顺序取第一套有 Key 的配置”的临时策略。用户必须明确选择用于文章生成的文本配置；选择结果只保存 Profile ID，不保存密钥或配置副本。

本批只处理默认文本 AI Profile，不实现词上下文问答、GET `/models`、测试连接或语音供应商绑定改造。语音 Profile 的选择继续由现有 `speech_preferences` 独立管理。

## 已确认的产品边界

1. **设备级归属**：AI Profile 是设备级配置；默认文本 Profile 与学习 `profileId` 无关。
2. **显式选择**：用户在“AI 服务”列表中主动将一套配置设为默认。升级到本版本时不自动迁移第一套有 Key 的配置；没有默认项时，文章生成返回未配置状态并引导用户选择。
3. **可选资格**：只有声明 `AiCapability.Text` 且当前 KeyStore 中存在可读取 Key 的 Profile 才能被设为默认。Profile 的 Endpoint、模型、能力等仍由该 Profile 自身携带，不能跨 Profile 复用 Key。
4. **失效清除**：默认 Profile 被删除，或其 Key 被成功清除后，默认绑定必须一并清除。编辑 Profile 不改变其 ID 时默认绑定保持不变；保存失败不得改变默认绑定。
5. **安全边界**：Room 只保存默认 Profile ID；默认状态、Key 是否存在由 Profile 列表和 `AiProfileSecretUseCase.hasKey()` 实时派生。任何 UI 状态、日志、文章元数据和错误对象都不得包含 Key、Authorization、Endpoint 完整值或密钥别名。
6. **历史复用优先**：已有本地文章按现有复用键直接返回，不要求默认 Profile。只有真正发起新的 AI 文章生成时才检查默认绑定。
7. **跨能力隔离**：默认文本 Profile 与语音设置中的 `openAiProfileId` / `miMoProfileId` 完全独立，不能互相推导或覆盖。

## 方案选择

### 推荐：设备级单行偏好表

新增 `ai_preferences` 设备级单行表，固定主键 `device`，仅保存 `defaultTextProfileId`：

```kotlin
data class AiPreference(
    val preferenceId: String = "device",
    val defaultTextProfileId: String? = null,
)
```

优点：

- 与现有 `speech_preferences` 的设备级单行模式一致；
- 迁移简单，默认值明确为 `null`，不会把历史用户静默绑定到某个服务；
- 默认选择的生命周期和 AI 配置独立，不把默认字段误加到 `ai_profiles`，避免每次切换都更新 Profile 元数据；
- 未来可扩展其它设备级 AI 偏好而不改变 Profile 身份。

不采用的方案：

- **把 `isDefault` 加到 `ai_profiles`**：需要保证全表单一默认值，删除和并发更新更容易出现双默认或无默认；也把配置实体和设备偏好耦合。
- **复用 `speech_preferences`**：文本生成和语音绑定有不同的资格与失效语义，合并会让语音设置误影响文章生成。
- **继续运行时选第一套有 Key**：违反显式选择要求，无法解释真实出站配置。

## 数据与接口

### Domain

创建 `AiPreference` 和 `AiPreferenceRepository`：

```kotlin
interface AiPreferenceRepository {
    suspend fun get(): Result<AiPreference>
    suspend fun save(preference: AiPreference): Result<Unit>
}
```

空表读取返回 `AiPreference()`，不把“无行”当存储错误。保存使用固定主键 upsert。

### Room

新增 `AiPreferenceEntity` 与 `InternalAiPreferenceDao`，表结构只包含：

- `preferenceId TEXT NOT NULL PRIMARY KEY`
- `defaultTextProfileId TEXT`

数据库从 v15 升到 v16，提供 `MIGRATION_15_16`：创建表，不插入默认行。Room v16 首次读取时由 Repository 返回内存默认值；用户保存选择后再写入 `device` 行。不得使用 destructive migration。

迁移必须保留 `ai_profiles`、`speech_preferences` 及已有文章数据；schema JSON 必须重新导出并纳入本批变更，但不覆盖工作树中其他未提交文件。

### 默认选择服务

为避免 ViewModel 和文章用例各自复制资格判断，增加小型领域服务 `DefaultTextProfileSelector`，职责仅为：

- 读取设备 AI 偏好；
- 读取 Profile；
- 确认 Profile 含 `AiCapability.Text`；
- 通过 `hasKey()` 确认 Key 存在；
- 返回可用于文章生成的 Profile，或返回明确的不可用原因；
- 将失效绑定清除并保存空偏好。

服务不得读取 Key 明文；仅使用 `hasKey()`。默认绑定存在但 Profile 不存在、缺少文本能力或缺少 Key 时，均视为失效绑定并清除。文章生成下一次调用返回 `NoDefaultProfile`，不回退到其它 Profile。

建议结果类型：

```kotlin
sealed interface DefaultTextProfileResult {
    data class Selected(val profile: AiProfile) : DefaultTextProfileResult
    data object NoSelection : DefaultTextProfileResult
    data object Unavailable : DefaultTextProfileResult
    data object StorageUnavailable : DefaultTextProfileResult
}
```

其中 `Unavailable` 表示绑定存在但 Profile 或 Key/能力已经失效；服务清除绑定后，后续调用会得到 `NoSelection`。仓储读取失败返回 `StorageUnavailable`，不得把存储故障误报成未配置。

## 文章生成数据流

`GenerateArticleUseCase.generate()` 保持当前顺序：

1. 非换一篇时先查本地可复用文章；命中直接返回；
2. 通过 `DefaultTextProfileSelector` 取得显式默认 Profile；
3. 无选择、选择失效或存储不可用时返回 `NotConfigured` 的对应原因，不读取其它 Profile 的 Key；
4. 检查 Endpoint、出站确认、构造请求、发送、解析、质量校验和保存；
5. 仍在 `finally` 中清零真正读取出的 Key；参数摘要继续只含模型和白名单参数，不含 Endpoint 与 Key。

`NotConfiguredReason` 新增 `NoDefaultProfile`；原 `NoProfile`、`NoKey` 保留用于兼容已有测试/其它调用语义，但文章生成的默认选择路径不再使用“第一套有 Key”策略。失败不会改写文章或学习状态。

## 设置页交互

在现有 `AiProfileSettingsScreen` 的 Profile 列表中：

- 当前默认且有效的 Profile 显示“文章默认”标记；
- 有文本能力但无 Key 的 Profile 显示未设置密钥，不能显示“设为文章默认”可用动作；
- 不含文本能力的 Profile 不提供默认文本选择动作；
- 有文本能力且有 Key 的非默认 Profile 显示“设为文章默认”动作；
- 点击后先调用 ViewModel，再刷新列表；保存失败时保留原默认标记并显示存储错误；
- 当前默认 Profile 的动作显示为“已是文章默认”，不可重复写入；
- 删除默认 Profile 或清除其 Key 后，列表不再显示默认标记；
- 现有整行编辑行为、返回层级、密钥不回显、`testTag` 与无障碍语义保持。

列表项状态扩展为 `isDefaultTextProfile` 和 `canBeDefaultTextProfile`，这两个状态只由非敏感元数据与 `hasKey` 派生。

## ViewModel 行为

`AiProfileSettingsViewModel` 注入 `AiPreferenceRepository`：

- `load()` 同时读取 Profile 列表和 AI 偏好；读取任一失败时显示现有不可用状态，不虚构默认状态；
- 读取到绑定后，只有绑定 ID 与具备文本能力且有 Key 的 Profile 匹配时才标记默认；失效绑定在加载时清除；
- 增加 `setDefaultTextProfile(profileId: String)`：重新读取目标 Profile，验证文本能力与 Key，再保存 `AiPreference(defaultTextProfileId = profileId)`；任何失败都不改变 UI 中的旧默认状态；
- 删除 Profile 的安全顺序保持“先删 Key，再删元数据”，并在删除成功后清除默认绑定；清除 Key 成功后若目标是默认绑定，也清除默认绑定；默认绑定清除失败时保留配置删除结果，并显示可操作的本地存储错误；
- 新建或编辑 Profile 不自动成为默认，避免保存配置产生隐式出站行为。

## 错误与兼容语义

- **无默认选择**：文章入口显示“请先选择文章默认 AI 服务”，可进入 AI 服务设置；不尝试其它有 Key 的 Profile。
- **默认已失效**：服务自动清除绑定；当前调用显示同样的配置引导，不发起网络请求。
- **存储不可用**：显示本机配置暂时读不出来/稍后再试；不清除状态，不发起网络请求。
- **Endpoint 无效、出站未确认、网络/HTTP/解析失败**：沿用现有错误映射。
- **KeyStore `hasKey()` 失败**：视为资格不可确认，不能设为默认；不暴露密钥库底层错误细节。

## 测试策略

必须遵循 RED → GREEN → REFACTOR，每个新行为先看到预期失败。

### JVM 单元测试

- `AiPreferenceRepository` 的空表默认、固定主键 upsert、读写往返；
- `DefaultTextProfileSelector`：无选择、有效选择、Profile 不存在、缺少文本能力、缺少 Key、Profile 列表/偏好读取失败、失效绑定只清除一次且不读取 Key 明文；
- `GenerateArticleUseCase`：默认 Profile 成功生成；多套有 Key 时只使用显式默认；无默认时不发请求；失效默认不回退；本地复用不要求默认配置；
- `AiProfileSettingsViewModel`：默认状态派生、设默认资格、拒绝无文本能力/无 Key、保存失败保持旧选择、删默认 Profile/Key 清除绑定、非默认删除不影响绑定。

### Android/Compose 与迁移测试

- Room v15→v16 迁移保留既有 AI Profile、语音偏好与文章，并验证新表不包含 key/endpoint/secret 字段；
- AI Profile 列表显示唯一默认标记、资格动作与失败提示；无 Key/非文本 Profile 不可设默认；
- UI 状态和语义树中不存在 API Key、Authorization、Endpoint 完整值或 secret alias；
- 保持既有 AI Profile 编辑、删除、密钥清除、MiMo 预设和语音设置测试通过。

### 真机验收

沿用项目安全路线：重新构建应用 APK 与测试 APK，`adb install -r -t` 就地安装，使用 `am instrument -w` 定向及全量测试；禁止 `connectedDebugAndroidTest`、卸载应用和清除数据。测试前后分别拉取 `english-learning.db`、`-wal`、`-shm` 并逐文件核对 MD5。未经用户另行确认不提交、不推送。

## 不在本批范围

- 词上下文 AI 问答与本地笔记表；
- GET `/models` 与测试连接；
- 语音 Provider 选择、回退策略或音频缓存改造；
- 自动把旧用户绑定到第一套有 Key 的 Profile；
- 账号、云同步、任意请求头/请求体覆盖。
