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

- [ ] Write tests for first text call requiring one host confirmation, later text calls reusing a recorded host confirmation, every image call requiring confirmation, and unconfirmed requests being rejected.
- [ ] Write tests rejecting unknown parameter names, wrong value types, and values outside exact ranges.
- [ ] Implement typed allow-list validation; never accept arbitrary headers, body fragments, tools, or authorization values.
- [ ] Ensure confirmation state contains host and payload kind only, never the Key.
- [ ] Run focused tests and commit `feat(ai): require explicit outbound confirmation`.

### Task 5: Map AI configuration errors to actionable UI state

**Files:**
- Modify: `app/src/main/java/com/example/englishlearning/core/error/AppError.kt`
- Create: `app/src/main/java/com/example/englishlearning/ai/AiFailure.kt`
- Test: `app/src/test/java/com/example/englishlearning/ai/AiFailureTest.kt`

**Interfaces:**
- `sealed interface AiFailure { data object NotConfigured; data object CapabilityUnsupported; data object NetworkUnavailable; data object Unauthorized; data object RateLimited; data object ServerUnavailable; data object Timeout; data object Cancelled; data object InvalidResponse }`
- `fun AiFailure.toUserAction(): UserAction` with actions such as configure profile, check network, retry later, or inspect generated content.

- [ ] Write tests for every F2-02 error class and action mapping, ensuring raw HTTP response bodies, endpoint URLs, and Key values are excluded.
- [ ] Implement stable sealed error types and UI actions; cancellation remains distinguishable from network failure.
- [ ] Run focused error tests and commit `feat(ai): expose actionable AI failures`.

### Task 6: F2-02 integration verification and documentation

**Files:**
- Create: `docs/verification/f2-02/README.md`

- [ ] Run all F2-02 JVM tests, storage/error regression, and Room migration instrumentation.
- [ ] Run a device test proving profile metadata can be read after process restart while Key material is not present in Room rows or logs.
- [ ] Run endpoint policy tests with loopback/private/HTTP fixtures; no live network request is allowed in this task.
- [ ] Record exact counts, device, APK checksum, known unrelated Stage-0 failures, and exclusions.
- [ ] Review every changed file for plaintext Key, Authorization header, arbitrary parameter, and external-link execution paths.
- [ ] Commit the verification record only after fresh logs are present.

## Self-review checklist

- [ ] F2-02 endpoint, profile, secret binding, confirmation, parameter allow-list, error mapping, and migration requirements each have a task.
- [ ] No task introduces real AI transport or article generation.
- [ ] No API Key is stored in Room, logs, test snapshots, or UI state.
- [ ] All interfaces use exact names and types defined above.
- [ ] Existing F2-01 and Stage-0 data semantics remain unchanged.
