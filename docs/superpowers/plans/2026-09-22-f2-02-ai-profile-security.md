# F2-02 AI Profile 与安全调用实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 建立多个 OpenAI-compatible AI Profile 的本地配置、Endpoint 安全校验、Key 引用绑定和可操作错误模型，为后续文章生成提供不泄露机密的安全调用边界。

**Architecture:** 将 Profile 的非敏感元数据保存在 Room，将 API Key 仅交给现有 `SecretStore`，Profile 只保存不可逆的 `SecretReference` alias。Endpoint 校验保持纯函数，不发起网络请求；AI 调用确认由独立的出站确认策略表达，传输层与 F2-03 文章生成隔离。

**Tech Stack:** Kotlin、Room、Jetpack Compose/Android test、现有 `SecretStore`/`AndroidKeyStoreSecretStore`、`AppError`、Coroutines。

**Spec:** `docs/specs/02-reading-and-ai-content.md` F2-02、AGENTS.md「AI 与隐私安全」章节。

## Global Constraints

- 仅支持 HTTPS 公网 Endpoint；拒绝 HTTP、`file:`、`content:`、环回地址、私网地址及重定向至这些地址的请求。
- API Key 不得进入 Room 业务表、日志、崩溃报告、剪贴板、导出包、文章元数据或截图诊断。
- Endpoint、模型、能力声明与 Key 必须绑定在同一 AI Profile；切换 Endpoint 不得复用其他 Profile 的 Key。
- 首次调用第三方 Endpoint 前必须展示域名及“文本/图片和 Key 将发送给该服务”的确认；图片发送每次都需明确确认。
- 只允许 `temperature`（0–2）、`top_p`（0–1）、`max_tokens`（1–4096）、`timeout_seconds`（5–120）和受控系统提示模板；拒绝未知参数。
- F2-02 不实现文章生成、TTS/OCR、真实网络请求或任意请求体覆盖。
- 所有新行为先写失败测试，再写最小生产实现；每个任务独立提交。

---

### Task 1: 锁定 Profile 领域模型与 Endpoint 校验

**Files:**
- Create: `app/src/main/java/com/example/englishlearning/ai/domain/AiProfile.kt`
- Create: `app/src/main/java/com/example/englishlearning/ai/domain/AiEndpointPolicy.kt`
- Test: `app/src/test/java/com/example/englishlearning/ai/domain/AiEndpointPolicyTest.kt`
- Test: `app/src/test/java/com/example/englishlearning/ai/domain/AiProfileTest.kt`
- Create: `docs/decisions/2026-09-22-f2-02-ai-profile-security.md`

**Interfaces:**
- `data class AiProfile(profileId: String, displayName: String, websiteUrl: String, endpoint: String, model: String, capabilities: Set<AiCapability>, secretReference: SecretReference, advancedParameters: AiAdvancedParameters)`
- `data class AiAdvancedParameters(temperature: Double = 0.7, topP: Double = 1.0, maxTokens: Int = 1024, timeoutSeconds: Int = 30, systemPromptTemplateId: String = "default-reading-v1")`
- `fun validateEndpoint(endpoint: String): Result<URI>`; invalid values return `AppErrorException` using a stable endpoint/configuration error, never the raw URL in user-facing text.

- [ ] Write tests for HTTPS-only, rejected HTTP/file/content schemes, localhost/loopback, private IPv4 ranges, IPv6 loopback/private ranges, missing host, credentials in URL, and valid public host.
- [ ] Write tests for advanced parameter boundaries and unknown parameter rejection through a typed model.
- [ ] Run the focused tests and confirm RED because the model and policy do not exist.
- [ ] Implement pure validation with `java.net.URI`/`InetAddress` parsing; do not perform DNS or network calls in the pure policy.
- [ ] Add stable error mapping without including endpoint or secret values in messages.
- [ ] Document the public-host limitation: DNS resolution and redirect validation are transport-layer responsibilities and remain outside this pure task.
- [ ] Run focused JVM tests and commit `feat(ai): define profile and endpoint security policy`.

### Task 2: Persist non-sensitive AI Profile metadata

**Files:**
- Create: `app/src/main/java/com/example/englishlearning/core/storage/entity/AiProfileEntity.kt`
- Create: `app/src/main/java/com/example/englishlearning/core/storage/dao/InternalAiProfileDao.kt`
- Modify: `app/src/main/java/com/example/englishlearning/core/storage/AppDatabase.kt`
- Create: `app/src/main/java/com/example/englishlearning/ai/AiProfileRepository.kt`
- Create: `app/src/main/java/com/example/englishlearning/ai/RoomAiProfileRepository.kt`
- Test: `app/src/androidTest/java/com/example/englishlearning/ai/RoomAiProfileRepositoryTest.kt`
- Create: `app/schemas/com.example.englishlearning.core.storage.AppDatabase/8.json`

**Interfaces:**
- `interface AiProfileRepository { suspend fun list(): Result<List<AiProfile>>; suspend fun find(profileId: String): Result<AiProfile?>; suspend fun save(profile: AiProfile): Result<Unit>; suspend fun delete(profileId: String): Result<Unit> }`
- Entity stores display name, website, endpoint, model, capability names, secret alias, typed advanced parameter values; never stores plaintext Key.

- [ ] Write a migration test from Room version 7 asserting existing tables/rows survive and the new profile table is usable.
- [ ] Write repository tests asserting CRUD round-trip, profile isolation, capability/parameter mapping, and stored field inspection has no key value field.
- [ ] Run tests before entities/repository implementation and verify RED.
- [ ] Upgrade Room `7 → 8`, add migration and exported schema; avoid destructive fallback.
- [ ] Implement repository using the established `CancellationException` discriminator and `AppErrorException(AppError.StorageUnavailable)` mapping.
- [ ] Run migration and repository instrumentation tests and commit `feat(ai): persist safe AI profile metadata`.

### Task 3: Bind Profile key references to SecretStore

**Files:**
- Create: `app/src/main/java/com/example/englishlearning/ai/AiProfileSecretUseCase.kt`
- Modify: `app/src/main/java/com/example/englishlearning/di/AppModule.kt`
- Test: `app/src/test/java/com/example/englishlearning/ai/AiProfileSecretUseCaseTest.kt`

**Interfaces:**
- `interface AiProfileSecretUseCase { fun saveKey(profile: AiProfile, key: CharArray): Result<Unit>; fun deleteKey(profile: AiProfile): Result<Unit>; fun hasKey(profile: AiProfile): Result<Boolean> }`
- Alias must be deterministically derived from profile ID with a fixed prefix, not endpoint/model/name; changing endpoint never looks up another profile’s alias.

- [ ] Write tests proving save delegates only to the profile’s alias, deletes clear that alias, hasKey reports missing/present, and the input `CharArray` is cleared by SecretStore.
- [ ] Write a test proving two profiles with different IDs cannot share a SecretReference even when endpoint/model are equal.
- [ ] Run RED, then implement the use case with no plaintext retention or logging.
- [ ] Add the Android SecretStore binding in Hilt and run focused tests; commit `feat(ai): isolate profile secrets by reference`.

### Task 4: Define confirmation and safe request policy

**Files:**
- Create: `app/src/main/java/com/example/englishlearning/ai/AiOutboundConfirmation.kt`
- Create: `app/src/main/java/com/example/englishlearning/ai/AiRequestPolicy.kt`
- Test: `app/src/test/java/com/example/englishlearning/ai/AiRequestPolicyTest.kt`

**Interfaces:**
- `enum class AiPayloadKind { Text, Image }`
- `data class AiOutboundConfirmation(val host: String, val payloadKind: AiPayloadKind, val confirmed: Boolean)`
- `fun requiredConfirmation(profile: AiProfile, payloadKind: AiPayloadKind, hasConfirmedTextHost: Boolean): ConfirmationRequirement`
- `fun validateRequestParameters(parameters: Map<String, Any?>): Result<AiAdvancedParameters>`

**实施时对上面两个签名做了收紧（理由见文末偏差 3、4）：**
- `requiredConfirmation(...)` 的第三个参数由 `hasConfirmedTextHost: Boolean` 改为 `confirmedTextHost: String?`；
  返回类型由 `ConfirmationRequirement` 改为 `Result<ConfirmationRequirement>`。
- 新增 `fun ConfirmationRequirement.resolvedBy(answer: AiOutboundConfirmation?): ConfirmationRequirement`。

- [x] Write tests for first text call requiring one host confirmation, later text calls reusing a recorded host confirmation, every image call requiring confirmation, and unconfirmed requests being rejected.
- [x] Write tests rejecting unknown parameter names, wrong value types, and values outside exact ranges.
- [x] Implement typed allow-list validation; never accept arbitrary headers, body fragments, tools, or authorization values.
- [x] Ensure confirmation state contains host and payload kind only, never the Key.
- [x] Run focused tests and commit `feat(ai): require explicit outbound confirmation`.

### Task 5: Map AI configuration errors to actionable UI state

**Files:**
- Modify: `app/src/main/java/com/example/englishlearning/core/error/AppError.kt`
- Create: `app/src/main/java/com/example/englishlearning/ai/AiFailure.kt`
- Test: `app/src/test/java/com/example/englishlearning/ai/AiFailureTest.kt`

**Interfaces:**
- `sealed interface AiFailure { data object NotConfigured; data object CapabilityUnsupported; data object NetworkUnavailable; data object Unauthorized; data object RateLimited; data object ServerUnavailable; data object Timeout; data object Cancelled; data object InvalidResponse }`
- `fun AiFailure.toUserAction(): UserAction` with actions such as configure profile, check network, retry later, or inspect generated content.

**实施时没有改 `AppError.kt`（理由见文末偏差 5）：** 新增的失败类型只属于 AI 传输层，
塞进 `AppError` 会让一个存储层错误枚举同时承载两套语义。文案改成 `AiFailureUiText` 枚举常量。

- [x] Write tests for every F2-02 error class and action mapping, ensuring raw HTTP response bodies, endpoint URLs, and Key values are excluded.
- [x] Implement stable sealed error types and UI actions; cancellation remains distinguishable from network failure.
- [x] Run focused error tests and commit `feat(ai): expose actionable AI failures`。

### Task 7: AI Profile 配置界面（本轮新增）

计划里漏了这一项：Task 1~5 全是领域层与数据层，但 spec F2-02 要求「支持多个 OpenAI-compatible
AI Profile：名称、官网、Endpoint、模型、能力、高级参数及 SecretStore 的 Key 引用」——没有界面
就无法满足。范围变化先更新计划再实施（AGENTS.md）。

**Files:**
- Create: `app/src/main/java/com/example/englishlearning/ai/AiProfileIdFactory.kt`
- Create: `app/src/main/java/com/example/englishlearning/ui/AiProfileSettingsViewModel.kt`
- Create: `app/src/main/java/com/example/englishlearning/ui/AiProfileSettingsScreen.kt`
- Test: `app/src/test/java/com/example/englishlearning/ui/AiProfileSettingsViewModelTest.kt`
- Test: `app/src/androidTest/java/com/example/englishlearning/ui/AiProfileSettingsScreenTest.kt`
- Modify: `ui/SettingsScreen.kt`（AI 分组由占位改为可点）、`ui/AppScreen.kt`（接入全屏层并把
  `aiConfigured` 改为读真实配置）、`di/AppModule.kt`（补 3 个 provider）、`MainActivity.kt`

- [x] 先写 ViewModel 测试：加载/不可读、新建默认值、编辑预填且**不回显密钥**、四类校验拒绝、
  元数据先落密钥后落、元数据失败不留孤儿密钥、编辑不改密钥槽、替换密钥只动本槽、
  删除时先清密钥再删元数据。
- [x] 写 Compose 测试：列表渲染与密钥徽章、空态、不可读态、新增/编辑/返回回调、编辑页预填、
  密钥输入框为空且不出现密钥原文、能力开关增删、字段错误、保存中禁用、新建无删除入口。
- [x] 实现 ViewModel 与界面，密钥走 `UiProfileSecretUseCase`（`CharArray`）而不是持久化字符串。
- [x] 把「设置 · AI」由占位改为可点，并在副标题里报真实的配置数量。
- [x] 把「AI 学」页头卡徽章由写死的 `false` 改为「至少一套配置已设置密钥」。
- [x] 提交 `feat(ai): add an AI profile settings screen`。

### Task 6: F2-02 integration verification and documentation

**Files:**
- Create: `docs/verification/f2-02/README.md` —— **已产出**

- [x] Run all F2-02 JVM tests, storage/error regression, and Room migration instrumentation.
      → JVM 231 tests / 0 failed / 0 skipped（44 suites，基线 190，+41）；真机 `OK (153 tests)` / `Error in` 计数 0
      （基线 132，+21）；迁移由 `AppDatabaseMigrationTest` 9 例覆盖。日志 `verification-logs/f2-02-instrumented-final.txt`。
- [x] Run a device test proving profile metadata can be read after process restart while Key material is not present in Room rows or logs.
      → 重新拉起应用后走「学习 → 设置 → AI 服务与密钥」，列表 4 行元数据正常渲染；对 db 副本
      `PRAGMA table_info(ai_profiles)` 得 12 列、无 key/secret 值列；`adb logcat -d` 全量 5107 行中
      本应用 140 行，`sk-…|authorization|bearer|api_key` **零匹配**
      （`verification-logs/f2-02-logcat-probe.txt`）。
- [x] Run endpoint policy tests with loopback/private/HTTP fixtures; no live network request is allowed in this task.
      → `AiEndpointPolicyTest` 4 例纯函数夹具通过；本轮主源码无 HTTP 客户端接入，未发出任何 socket 请求。
- [x] Record exact counts, device, APK checksum, known unrelated Stage-0 failures, and exclusions.
      → README §2 §3 §9 §10。设备 `bf353dda`（MIUI V816 / Android 13）；主 APK md5
      `df9f971d00268fd4ac54502915ca3f2e`，测试 APK md5 `2a9903d87963fa910c786e3d6cb82323`；
      **本轮无失败用例，故无「无关失败」需要排除**；`grep -c "^Error in "` 的无匹配退出码 1 已注明非失败。
- [x] Review every changed file for plaintext Key, Authorization header, arbitrary parameter, and external-link execution paths.
      → README §6。四类扫描结果：`Authorization`/`Bearer` 无匹配、`Log.*key|secret|token` 无匹配、
      硬编码 `sk-…` 无匹配；`headers` 唯一命中是 PDF 表头（同名误报）；
      `ACTION_VIEW`/`startActivity` 仅命中既有 PDF 导出分享路径，**AI 路径无外链执行**。
      另记残留面一条：输入期间密钥以 `String` 短暂存在于 ViewModel 状态（README §7.1，未闭合）。
- [x] Commit the verification record only after fresh logs are present.
      → 文档中引用的日志（`f2-02-instrumented-final.txt`、`f2-02-logcat-probe.txt`）与四时点 db 副本
      均已先落盘再写文档，随本提交一并入库。

## Self-review checklist

- [x] F2-02 endpoint, profile, secret binding, confirmation, parameter allow-list, error mapping, and migration requirements each have a task.
- [x] No task introduces real AI transport or article generation.
- [x] No API Key is stored in Room, logs, test snapshots, or UI state.
- [ ] All interfaces use exact names and types defined above. —— **不满足，见偏差 3、4、5**（三处刻意收紧/改动，已记理由）。
- [x] Existing F2-01 and Stage-0 data semantics remain unchanged.

---

## 执行记录与计划偏差（2026-09-24 补记）

按事实记录，供后续复用：

1. **JVM 单元测试源集是 JUnit 5，不是 JUnit 4。** `app/build.gradle.kts` 挂的是
   `libs.junit.jupiter` 与 `libs.kotlin.test.junit5`；`androidTest` 才是 JUnit 4。写 `org.junit.Test`
   会直接编译失败。且 `kotlin.test` 的断言参数顺序是**实际值在前、消息在后**，与 JUnit 4 的
   `assertEquals(message, expected, actual)` 相反——按错顺序写会得到「参数类型不匹配」的怪错误。

2. **`AiProfileRepository` 此前根本没有接进 Hilt。** Task 2 建了 `RoomAiProfileRepository`
   与 `InternalAiProfileDao`，但 `di/AppModule.kt` 里没有任何 provider，所以 `AiProfileRepository`
   在应用里从没被实例化过。本轮补了三个 provider（仓储、密钥 UseCase、id 工厂）。教训：
   **「类存在」不等于「能力存在」**，接入点要单独核对。

3. **`requiredConfirmation` 的第三个参数由 `hasConfirmedTextHost: Boolean` 改成 `confirmedTextHost: String?`。**
   布尔量只说「确认过文本」，说不出「确认的是哪个域名」。用户换过 Endpoint 之后，旧域名的确认会
   被顺延到新域名上，等于绕过了 AGENTS.md 要求的「首次调用第三方 Endpoint 前展示域名」。
   改成用域名本身比较，换域名一定重新问。测试
   `textCallToAnotherHostAsksAgainInsteadOfReusingTheOldConfirmation` 锁住这一点。

4. **`requiredConfirmation` 返回 `Result<ConfirmationRequirement>` 而不是裸的 `ConfirmationRequirement`。**
   Endpoint 不合法时连「该向用户确认哪个域名」都算不出来，这必须是一个失败；用第三种状态去表达
   会把「配置坏了」伪装成「正常流程里的一个待办」。返回 `Result` 也与既有
   `validateEndpoint(...): Result<URI>` 保持一致。

5. **没有改 `AppError.kt`。** Task 5 原计划要改它，但 `AiFailure` 的九类失败全属 AI 传输层，
   而 `AppError` 是「存储/本机基础设施」的错误模型（`StorageUnavailable`、`KeyStoreUnavailable`…）。
   把两套语义混进一个枚举后，调用方无法从类型上判断「这个错误该不该重试网络」。因此
   `AiFailure` 自带 `AiFailureUiText` + `UserAction`，`AppError` 保持不动。

6. **Task 7（配置界面）是计划里漏掉的一项。** spec F2-02 明确要求 Profile 有名称、官网、
   Endpoint、模型、能力、高级参数与 Key 引用，但没有界面就无法满足，也会让「AI 学」页的头卡徽章
   只能继续写死。按 AGENTS.md「范围变化先更新 Spec/计划再实施」，先补了 Task 7 再动手。

7. **「默认 Profile」没有做。** spec F2-02 未要求默认选择；「阅读页记住默认 Profile」属于
   F2-03/F2-04 的阅读理解路径。此处不提前实现，避免出现一个没有消费方的设置项。

8. **高级参数用文本框 + 复用 `validateRequestParameters`，没有在界面里再写一套范围判断。**
   界面的职责是把字符串解析成数字，范围与白名单仍由 `ai/AiRequestPolicy.kt` 一处说了算。
   解析失败报 `ParameterNotNumeric`，范围失败报 `ParameterOutOfRange`，两个错误分开，
   用户才知道该改什么。

9. **新 profile 的 id 在「保存」时才生成，不是在打开编辑页时。** 打开就生成会在用户取消后留下
   一个占位 id；而 id 又是密钥别名的种子，占位 id 一多，将来排查「这个别名是谁的」会很难。

10. **`AiProfile` 的确认与授权之间加了防「确认 A、调用 B」的一致性检查。**
    `ConfirmationRequirement.resolvedBy(answer)` 要求 `answer` 的域名与载荷类型都与当前目标相同
    才转为 `Satisfied`。没有这层检查，先弹 A 域名的确认框、再改 Endpoint 去调 B，就能拿旧确认
    当新授权用。

11. **密钥在界面状态里以 `String` 存在（输入框的要求），保存时转 `CharArray` 交给 SecretStore，
    成功即随编辑页一起丢弃。** 这是本轮已知的残留面：输入期间密钥会以 String 形式短暂存在于
    ViewModel 状态中。它不进 Room、不进日志、不进备份、不回显，但也不是「零暴露」。若后续要收紧，
    方向是改用自定义 `TextFieldValue` + 立即洗写的缓冲，而不是继续在 Compose 里传 String。

12. **真机库里 4 行历史 AI 配置查明了来源：是本次方案要取代的那版实现的遗留。**
    这 4 行的 `secretAlias` 全是字面量 `'pending'`，而当前实现必定写 `ai-profile-{profileId}`；
    `'pending'` 这个字符串在整棵源码树与全部 git 历史中都不存在，
    `ui/AiProfileScreen.kt` / `ui/AiProfileViewModel.kt` 也**从未在本仓库出现过**。
    对照当日记忆（提交 `e2c9007` + UI 改版 `e9410b8`，含 `AiProfileScreen`/`AiProfileViewModel`），
    可判定它们来自**另一棵工作树里的早期实现**——那版每次保存新增一行、且用 `pending` 占位别名，
    正是本轮改用 `@Insert(onConflict = REPLACE)` 与「保存时才生成 id 并派生别名」要修的两个缺陷。
    **处置：未删除、未修改任何一行**（真机用户数据），只在 README §7.5 记录证据并提出清理建议。
    教训：跨工作树的开发会在真机上留下无法从当前仓库历史追溯的数据；排查这类「幽灵数据」时，
    先看**值本身是否可能由当前代码产生**，比翻历史更快定性。

