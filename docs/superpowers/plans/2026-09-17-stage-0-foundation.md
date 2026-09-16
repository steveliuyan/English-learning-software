# 阶段 0：工程基础、离线架构与安全基线 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 建立一套可离线启动、可安全保存本地资料、可为后续功能迭代提供稳定边界的 Android 应用基础工程；本阶段不交付背词、AI、TTS、OCR、提醒或备份功能。

**Architecture:** 采用单一 `:app` Gradle 模块和 package-by-feature 分层。Compose UI 只经 ViewModel 和 domain use case 访问领域契约；Repository 与 Provider 实现才可访问 Room、私有文件和 Keystore 支撑的安全存储；`di` 是唯一组装根。对模块边界使用架构测试守护，待独立交付或构建压力出现后再拆分 Gradle 模块。

**Tech Stack:** Kotlin、Android Gradle Plugin、Jetpack Compose、Room、Hilt、Android Keystore、AndroidX Test、JUnit 5、Robolectric、Compose UI Test、MockK、Turbine、kotlinx-coroutines-test、detekt、ktlint、Version Catalog、Gradle dependency locking。

**Spec:** `docs/specs/00-foundation-and-architecture.md`；前置和约束：`AGENTS.md`、`docs/specs/01-vocabulary-learning-and-review.md`、`docs/superpowers/specs/2026-09-17-android-ai-english-vocabulary-app-prd.md`

## Global Constraints

- 本阶段只实现工程骨架、离线数据/媒体/安全边界、Provider 契约、许可台账、基础 UI 与测试；不得实施词书内容、背词、FSRS 业务、AI 真实请求、TTS、OCR、提醒、备份导入导出或恢复。
- `applicationId` 与 `namespace` 暂定为 `com.example.englishlearning`；首次发布前必须替换为团队拥有的反向域名并更新文档与签名配置。
- `minSdk = 26`；`compileSdk`、`targetSdk` 使用实施日 Android 稳定 API，Kotlin/AGP/Compose/Room/Hilt 版本在实施前依据官方兼容矩阵锁定到 `libs.versions.toml`，不得臆造未来版本。
- 默认采用单 `:app` 模块；包边界为 `core`、`lexicon`、`learning`、`reading`、`language`、`ai`、`backup`、`notification`、`ui`、`di`，并用架构测试阻止不允许的依赖。
- 本地数据库和应用私有媒体目录是本地权威；核心 I/O 必须在调度器注入的非主线程执行。
- Room 只保存业务元数据和 `SecretReference(alias)`，绝不保存 API Key、Authorization、密码、私钥或可恢复的密钥材料；不得启用 destructive migration。
- SecretStore 必须由 Android Keystore 支撑；密钥材料不可通过业务层返回、日志、诊断、剪贴板、Room 导出 DTO 或未来备份出现。
- 媒体最终文件仅可位于 `filesDir/assets/<assetId>`；先写入同目录临时文件、计算 SHA-256 并校验后原子替换。缺失或哈希不符返回 `Unavailable/Rebuildable`，不得崩溃。
- 未来备份只允许逻辑快照和媒体清单；不得复制数据库文件。其未来协议必须是旧设备一次性挑战配对、完整性签名和可选密码，验证失败须清理临时区且正式数据零写入；阶段 0 仅定义契约。
- 所有依赖、数据集与模型必须登记至 `docs/third-party-notices.md`；缺少许可证、用途或数据流的项必须使校验任务失败，未经许可核验的资源不可打包。
- 提供小屏、深色模式、动态字体和 TalkBack 基础导航；无网络、存储不足、数据库迁移失败、Keystore 不可用均须可注入并渲染为可行动错误。
- 每一项实现均先写测试、运行并确认失败，再写最小实现使其通过；完成后运行相应测试及阶段验收命令。
- 每阶段验收、质量审查与阶段文档完成后，创建可恢复的 Git 提交并推送至已确认的 GitHub 远程仓库作为异地备份；推送前必须核验远程地址、目标分支和认证。推送失败时保留本地提交、报告原因并在修复后重试，不得称备份完成。

---

## Planned File Structure

- `settings.gradle.kts`：仓库、单 app 模块与 dependency verification 基础配置。
- `gradle/libs.versions.toml`：经官方矩阵核验后的构建、AndroidX、测试和质量工具版本。
- `app/build.gradle.kts`：Compose、Room、Hilt、测试、schema export、lint 与 dependency locking 配置。
- `app/src/main/java/com/example/englishlearning/core/`：跨领域 model、错误、时间、调度器、脱敏日志、安全与媒体存储。
- `app/src/main/java/com/example/englishlearning/{lexicon,learning,reading,language,ai,backup,notification}/`：仅领域实体、DTO、Repository/Provider 契约及 fake。
- `app/src/main/java/com/example/englishlearning/ui/`：Compose shell、导航、ViewModel、可行动错误展示。
- `app/src/main/java/com/example/englishlearning/di/`：唯一 Hilt 组装根。
- `app/src/main/java/com/example/englishlearning/core/storage/`：Room schema、DAO（internal）与 repository 实现。
- `app/schemas/`：Room 每个版本的导出 schema，作为迁移测试输入。
- `app/src/test/`：JVM unit、架构、DTO、脱敏和媒体原子写入测试。
- `app/src/androidTest/`：Room migration、Keystore/FileOps 故障注入、Compose 可访问性与离线启动测试。
- `docs/third-party-notices.md`：第三方、数据和模型台账。
- `tools/verify-third-party-notices.main.kts`：校验台账必填字段的可执行脚本。
- `.github/workflows/android-stage0.yml`：在仓库初始化后运行质量、unit、lint、instrumented 验收的 CI。

### Task 1: 初始化可重复的 Android 构建、质量与许可台账校验

**Files:**
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts`
- Create: `gradle/libs.versions.toml`
- Create: `app/build.gradle.kts`
- Create: `app/src/main/AndroidManifest.xml`
- Create: `docs/third-party-notices.md`
- Create: `tools/verify-third-party-notices.main.kts`
- Create: `app/src/test/java/com/example/englishlearning/notices/ThirdPartyNoticesTest.kt`

**Interfaces:**
- Produces: `verifyThirdPartyNotices(markdown: String): List<NoticeValidationError>`，供 Gradle `verifyThirdPartyNotices` 使用。
- Produces: 构建约束：Java 17、`minSdk 26`、稳定期 `compileSdk/targetSdk`、Compose、Hilt、Room schema export、detekt、ktlint、dependency locking。

- [ ] **Step 1: 创建失败的台账校验测试**

```kotlin
@Test
fun `notice entry missing data flow is rejected`() {
    val markdown = """
        ## androidx-room
        - 名称: Room
        - 版本: 2.x
        - 许可证: Apache-2.0
        - 用途: 本地关系数据
    """.trimIndent()

    assertThat(verifyThirdPartyNotices(markdown))
        .contains(NoticeValidationError.MissingField("androidx-room", "数据流"))
}
```

- [ ] **Step 2: 运行测试并确认 RED**

Run: `./gradlew :app:testDebugUnitTest --tests '*ThirdPartyNoticesTest'`

Expected: FAIL，因为 `verifyThirdPartyNotices` 尚不存在；不得以跳过测试代替失败验证。

- [ ] **Step 3: 以最小实现建立工程、Version Catalog 与台账校验**

实现 `NoticeValidationError` 与 `verifyThirdPartyNotices`，要求每一条目均含：名称、版本、许可证、用途、数据流、NOTICE 位置、替代方案、商业分发结论。创建 initial notices，至少登记 Kotlin、Compose、Room、Hilt、AndroidX Security、JUnit、AndroidX Test、Robolectric、MockK、Turbine、detekt、ktlint；数据集和模型在本阶段填写“未引入/不打包”。配置 `verifyThirdPartyNotices` 为 `check` 的依赖任务。

- [ ] **Step 4: 运行单测、校验与构建**

Run: `./gradlew verifyThirdPartyNotices :app:testDebugUnitTest :app:assembleDebug`

Expected: PASS；`docs/third-party-notices.md` 全部条目字段完整，且 APK 不包含词书、词典或模型资源。

- [ ] **Step 5: 提交**

```bash
git add settings.gradle.kts build.gradle.kts gradle app docs/third-party-notices.md tools
git commit -m "chore: initialize Android stage zero foundation"
```

### Task 2: 建立核心值对象、错误模型、时间与安全日志边界

**Files:**
- Create: `app/src/main/java/com/example/englishlearning/core/error/AppError.kt`
- Create: `app/src/main/java/com/example/englishlearning/core/time/ClockProvider.kt`
- Create: `app/src/main/java/com/example/englishlearning/core/logging/SafeLogger.kt`
- Create: `app/src/test/java/com/example/englishlearning/core/logging/SafeLoggerTest.kt`
- Create: `app/src/test/java/com/example/englishlearning/core/time/ClockProviderTest.kt`

**Interfaces:**
- Produces: `sealed interface AppError`，包括 `NetworkUnavailable`、`StorageInsufficient(requiredBytes: Long)`、`DatabaseMigrationFailed`、`KeyStoreUnavailable`、`IntegrityMismatch`、`PairingFailed`。
- Produces: `interface ClockProvider { fun instant(): Instant; fun zoneId(): ZoneId }`。
- Produces: `interface SafeLogger { fun info(event: String, attributes: Map<String, Any?> = emptyMap()) }`，只允许结构化 allowlist 属性。

- [ ] **Step 1: 编写日志不会泄露凭据的失败测试**

```kotlin
@Test
fun `logger redacts authorization keys passwords private keys and binary payloads`() {
    val sink = RecordingLogSink()
    val logger = SanitizingSafeLogger(sink)

    logger.info("ai_profile_saved", mapOf(
        "Authorization" to "Bearer secret-token",
        "apiKey" to "sk-secret",
        "password" to "p@ss",
        "imageBytes" to byteArrayOf(1, 2, 3),
        "profileId" to "profile-1"
    ))

    assertThat(sink.line).contains("profileId=profile-1")
    assertThat(sink.line).doesNotContain("secret-token", "sk-secret", "p@ss", "1, 2, 3")
}
```

- [ ] **Step 2: 运行测试并确认 RED**

Run: `./gradlew :app:testDebugUnitTest --tests '*SafeLoggerTest'`

Expected: FAIL，因为 `SanitizingSafeLogger` 不存在。

- [ ] **Step 3: 编写最小实现**

实现显式 `AppError`、可注入系统/固定 `ClockProvider`、基于敏感键名拒绝与默认不序列化 `ByteArray`/异常 message 的 `SanitizingSafeLogger`。日志只记录事件名与经过允许的标量字段；不得将 alias、绝对路径、hash、token 或异常原文加入 allowlist。

- [ ] **Step 4: 运行核心单测**

Run: `./gradlew :app:testDebugUnitTest --tests '*SafeLoggerTest' --tests '*ClockProviderTest'`

Expected: PASS；所有 `AppError` 均可稳定映射为 UI 文案资源 ID，且不含原始异常/机密内容。

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/com/example/englishlearning/core app/src/test/java/com/example/englishlearning/core
git commit -m "feat: add stage zero core error and safe logging boundaries"
```

### Task 3: 以 TDD 实现 Keystore 支撑的凭据引用与私有媒体完整性存储

**Files:**
- Create: `app/src/main/java/com/example/englishlearning/core/security/SecretStore.kt`
- Create: `app/src/main/java/com/example/englishlearning/core/security/AndroidKeyStoreSecretStore.kt`
- Create: `app/src/main/java/com/example/englishlearning/core/storage/FileOps.kt`
- Create: `app/src/main/java/com/example/englishlearning/core/storage/PrivateMediaStore.kt`
- Create: `app/src/test/java/com/example/englishlearning/core/storage/PrivateMediaStoreTest.kt`
- Create: `app/src/androidTest/java/com/example/englishlearning/core/security/AndroidKeyStoreSecretStoreTest.kt`

**Interfaces:**
- Produces: `data class SecretReference(val alias: String)`；`SecretStore` 仅有 `save(reference, secret: CharArray): Result<Unit>`、`delete(reference): Result<Unit>`、`has(reference): Result<Boolean>`；不得提供返回明文密钥的 API。
- Produces: `sealed interface MediaAvailability { data object Available; data object UnavailableRebuildable }`。
- Produces: `PrivateMediaStore.writeVerified(assetId: UUID, source: InputStream, expectedSha256: String): Result<Unit>` 与 `availability(asset: AssetRecord): MediaAvailability`。

- [ ] **Step 1: 写入“完整性不匹配不发布最终媒体”的失败测试**

```kotlin
@Test
fun `hash mismatch leaves no final asset`() = runTest {
    val files = FakeFileOps()
    val store = PrivateMediaStore(files, testDispatcher)

    val result = store.writeVerified(UUID.fromString("00000000-0000-0000-0000-000000000001"),
        "tampered".byteInputStream(), "00")

    assertThat(result.exceptionOrNull()).isNotNull()
    assertThat(files.finalFiles()).isEmpty()
    assertThat(files.temporaryFiles()).isEmpty()
}
```

- [ ] **Step 2: 运行测试并确认 RED**

Run: `./gradlew :app:testDebugUnitTest --tests '*PrivateMediaStoreTest'`

Expected: FAIL，因为 `PrivateMediaStore` 不存在。

- [ ] **Step 3: 实现最小 SecretStore 和媒体原子写入**

在 Android 实现中使用 Android Keystore 生成/定位每个 alias 对应的不可导出 AES-GCM 密钥，密文仅存应用私有安全目录；Room 只保留 `SecretReference.alias`。禁止日志记录 alias 或密文。媒体先写 `filesDir/assets/<uuid>.tmp`，计算 SHA-256，匹配后通过同目录 atomic move 发布；任何 I/O 或校验失败删除临时文件并映射 `AppError`。

- [ ] **Step 4: 增补并运行失败模式测试**

新增测试：媒体被删除或哈希不符返回 `UnavailableRebuildable`；`FileOps` 注入磁盘不足时返回 `StorageInsufficient`；Keystore provider 抛错时映射 `KeyStoreUnavailable` 且不留下密文。

Run: `./gradlew :app:testDebugUnitTest :app:connectedDebugAndroidTest --tests '*PrivateMediaStoreTest'`

Expected: PASS；不存在最终半成品媒体文件，任何测试日志不含 secret。

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/com/example/englishlearning/core app/src/test app/src/androidTest
git commit -m "feat: add keystore credential and verified private media boundaries"
```

### Task 4: 建立禁止 destructive migration 的 Room schema、DAO 可见性和迁移测试基线

**Files:**
- Create: `app/src/main/java/com/example/englishlearning/core/storage/AppDatabase.kt`
- Create: `app/src/main/java/com/example/englishlearning/core/storage/entity/SchemaMetaEntity.kt`
- Create: `app/src/main/java/com/example/englishlearning/core/storage/entity/AssetRecordEntity.kt`
- Create: `app/src/main/java/com/example/englishlearning/core/storage/entity/KeyAliasEntity.kt`
- Create: `app/src/main/java/com/example/englishlearning/core/storage/dao/InternalAssetDao.kt`
- Create: `app/src/main/java/com/example/englishlearning/core/storage/AssetRepository.kt`
- Create: `app/src/androidTest/java/com/example/englishlearning/core/storage/AppDatabaseMigrationTest.kt`

**Interfaces:**
- Produces: `AppDatabase` version 1，导出 schema 到 `app/schemas`；DAO 必须为 `internal`，只允许 repository 访问。
- Produces: `AssetRepository.get(id: UUID): Result<AssetRecord?>`；不可向 UI 返回 DAO。
- Produces: Room 表仅含 schema 元数据、媒体记录和凭据 alias；不得插入词书、单词、学习事件、计划、文章或 API Key 实际内容。

- [ ] **Step 1: 写入 v1 schema 的迁移失败测试**

```kotlin
@Test
fun migrateAllHistoricalSchemasWithoutDestructiveFallback() {
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java.canonicalName,
        FrameworkSQLiteOpenHelperFactory()
    )
    helper.createDatabase(TEST_DB, 1).close()

    Room.databaseBuilder(context, AppDatabase::class.java, TEST_DB)
        .addMigrations(*AppDatabase.MIGRATIONS)
        .build()
        .openHelper.writableDatabase
        .close()
}
```

- [ ] **Step 2: 运行测试并确认 RED**

Run: `./gradlew :app:connectedDebugAndroidTest --tests '*AppDatabaseMigrationTest'`

Expected: FAIL，因为 `AppDatabase` 和导出 schema 尚不存在。

- [ ] **Step 3: 实现 v1 Room schema 与 repository**

使用 `fallbackToDestructiveMigration` 禁止项检查；配置 `room.schemaLocation`。`AssetRecordEntity` 包含 `id`、`sha256`、`relativePath`、`byteSize`、`createdAt`；`KeyAliasEntity` 仅包含 `purpose`、`alias`、`createdAt`。对后续每次 schema 变更要求新增显式 `Migration(N, N+1)`，绝不静默清库。Repository 将 SQLite/迁移错误映射 `DatabaseMigrationFailed`，不暴露原始 SQL/文件路径。

- [ ] **Step 4: 运行迁移和线程测试**

Run: `./gradlew :app:connectedDebugAndroidTest --tests '*AppDatabaseMigrationTest' :app:testDebugUnitTest`

Expected: PASS；schema 被导出；DAO 不可从 `ui` 包编译访问；数据库读写由注入 IO dispatcher 执行。

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/com/example/englishlearning/core/storage app/src/androidTest app/schemas app/build.gradle.kts
git commit -m "feat: add room schema and migration foundation"
```

### Task 5: 定义后续域的纯契约、逻辑导出 DTO 与可替换 fake

**Files:**
- Create: `app/src/main/java/com/example/englishlearning/learning/domain/ReviewScheduler.kt`
- Create: `app/src/main/java/com/example/englishlearning/ai/domain/AiProvider.kt`
- Create: `app/src/main/java/com/example/englishlearning/language/domain/PronunciationProvider.kt`
- Create: `app/src/main/java/com/example/englishlearning/language/domain/OcrProvider.kt`
- Create: `app/src/main/java/com/example/englishlearning/backup/domain/BackupProvider.kt`
- Create: `app/src/main/java/com/example/englishlearning/notification/domain/NotificationProvider.kt`
- Create: `app/src/main/java/com/example/englishlearning/core/export/LogicalSnapshot.kt`
- Create: `app/src/test/java/com/example/englishlearning/contracts/ProviderContractTest.kt`
- Create: `app/src/test/java/com/example/englishlearning/contracts/LogicalSnapshotSecurityTest.kt`

**Interfaces:**
- Produces: `ReviewScheduler.schedule(state: ReviewState, feedback: ReviewFeedback, now: Instant): ScheduledReview`，仅声明、不实现 FSRS。
- Produces: `AiProvider.capabilities(): Set<AiCapability>`；不得出现 HTTP、URL、Header 或 vendor payload 类型。
- Produces: `OcrProvider` 返回 `OcrResult(text, regions: List<OcrRegion>)`，其中 region 仅有坐标与置信度。
- Produces: `BackupProvider` 输入 `LogicalSnapshot` 与 `List<MediaManifestItem>`；接口不得接收 DB path、`File`、`SecretReference` 或 Key。

- [ ] **Step 1: 写入 Provider 能替换且不泄露供应商协议的失败测试**

```kotlin
@Test
fun `ai provider is replaceable and exposes capabilities only`() {
    val provider: AiProvider = FakeAiProvider(setOf(AiCapability.Text))

    assertThat(provider.capabilities()).containsExactly(AiCapability.Text)
    assertThat(AiProvider::class.memberProperties.map { it.returnType.toString() })
        .containsNoneOf("OkHttpClient", "Request", "Header", "HttpUrl")
}
```

- [ ] **Step 2: 运行测试并确认 RED**

Run: `./gradlew :app:testDebugUnitTest --tests '*ProviderContractTest' --tests '*LogicalSnapshotSecurityTest'`

Expected: FAIL，因为 Provider 与 LogicalSnapshot 尚不存在。

- [ ] **Step 3: 编写最小契约和 fake**

定义所有 provider 的 domain 数据类型和仅测试使用的 fake。`LogicalSnapshot` 只保留未来可导出的逻辑领域 DTO；用反射测试拒绝字段名或类型中出现 `apiKey`、`authorization`、`password`、`privateKey`、`secret`、`SecretReference`。`BackupProvider` 文档明确 future protocol 的一次性挑战、签名、可选密码和任何失败零写入要求，但不得实现加密或文件备份。

- [ ] **Step 4: 运行契约与安全测试**

Run: `./gradlew :app:testDebugUnitTest --tests '*ProviderContractTest' --tests '*LogicalSnapshotSecurityTest'`

Expected: PASS；阶段 0 无网络 client、无 TTS/OCR 实现、无通知调度、无备份文件格式或恢复代码。

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/com/example/englishlearning/{learning,ai,language,backup,notification} app/src/main/java/com/example/englishlearning/core/export app/src/test/java/com/example/englishlearning/contracts
git commit -m "feat: define replaceable stage zero provider contracts"
```

### Task 6: 使用 Hilt 组装基础壳、离线 Profile 与可行动错误 UI

**Files:**
- Create: `app/src/main/java/com/example/englishlearning/di/AppModule.kt`
- Create: `app/src/main/java/com/example/englishlearning/profile/LocalProfileRepository.kt`
- Create: `app/src/main/java/com/example/englishlearning/profile/CreateLocalProfileUseCase.kt`
- Create: `app/src/main/java/com/example/englishlearning/ui/AppViewModel.kt`
- Create: `app/src/main/java/com/example/englishlearning/ui/AppScreen.kt`
- Create: `app/src/main/java/com/example/englishlearning/MainActivity.kt`
- Create: `app/src/test/java/com/example/englishlearning/ui/AppViewModelTest.kt`
- Create: `app/src/androidTest/java/com/example/englishlearning/ui/AppScreenTest.kt`

**Interfaces:**
- Produces: `CreateLocalProfileUseCase.invoke(displayName: String): Result<LocalProfile>`，在 Room 中写入不含学习设置和密钥的本地 profile。
- Produces: `AppUiState`：`Loading`、`NeedsProfile`、`Ready(profile)`、`Error(AppError)`。
- Produces: `AppViewModel` 只依赖 use case、repository interface、ClockProvider；不得 import DAO、HTTP client、SecretStore 实现。

- [ ] **Step 1: 编写离线创建 Profile 后重启可读取的失败测试**

```kotlin
@Test
fun `create profile persists and reloads without network`() = runTest {
    val repository = InMemoryLocalProfileRepository()
    val viewModel = AppViewModel(repository, CreateLocalProfileUseCase(repository), fixedClock)

    viewModel.createProfile("本地学习者")
    val reloaded = AppViewModel(repository, CreateLocalProfileUseCase(repository), fixedClock)

    assertThat(reloaded.uiState.value).isEqualTo(AppUiState.Ready(LocalProfile("default", "本地学习者")))
}
```

- [ ] **Step 2: 运行测试并确认 RED**

Run: `./gradlew :app:testDebugUnitTest --tests '*AppViewModelTest'`

Expected: FAIL，因为 profile repository/use case/ViewModel 尚不存在。

- [ ] **Step 3: 实现最小本地 Profile 与 Compose shell**

Room 增加仅包含 `id`、`displayName`、`createdAt` 的 profile 元数据表（不得预置词书或每日计划字段）。Hilt 绑定 repository、IO dispatcher、ClockProvider 和 fake provider；Compose 展示创建资料页与 Ready 状态。错误页按 `AppError` 展示可行动文字，不展示堆栈、密钥、路径或底层异常。所有控件必须有 `contentDescription`/语义标签，文本支持动态字体和深色主题。

- [ ] **Step 4: 运行本地与设备验收测试**

Run: `./gradlew :app:testDebugUnitTest :app:connectedDebugAndroidTest :app:lintDebug`

Expected: PASS；飞行模式可创建并读回 Profile；TalkBack 测试能定位创建输入与提交按钮；模拟 Keystore、磁盘、迁移故障时界面不崩溃并提供可行动状态。

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/com/example/englishlearning/{di,profile,ui} app/src/main/java/com/example/englishlearning/MainActivity.kt app/src/test/java/com/example/englishlearning/ui app/src/androidTest/java/com/example/englishlearning/ui
git commit -m "feat: add offline profile and accessible stage zero shell"
```

### Task 7: 建立架构约束、发布前质量门与完整阶段验收

**Files:**
- Create: `app/src/test/java/com/example/englishlearning/architecture/LayeringRulesTest.kt`
- Create: `app/src/test/java/com/example/englishlearning/architecture/StageZeroScopeTest.kt`
- Create: `.github/workflows/android-stage0.yml`
- Modify: `app/build.gradle.kts`
- Modify: `docs/third-party-notices.md`

**Interfaces:**
- Produces: 架构质量门：UI/ViewModel 不得引用 `dao`、`OkHttpClient`、`Retrofit`、`SecretStore` 或 `AndroidKeyStoreSecretStore`；feature 包之间不得横向导入。
- Produces: 范围质量门：阶段 0 生产代码不得声明网络 transport、TTS/OCR 实现、WorkManager/AlarmManager 调度、备份压缩/导入或加密恢复实现。

- [ ] **Step 1: 写入 UI 与范围边界的失败测试**

```kotlin
@Test
fun `view models do not depend on dao network or secret store`() {
    val forbidden = setOf(".dao.", "OkHttpClient", "Retrofit", "SecretStore", "AndroidKeyStoreSecretStore")
    val imports = kotlinSourcesUnder("src/main/java/com/example/englishlearning/ui")
        .flatMap { it.importLines() }

    assertThat(imports.none { line -> forbidden.any(line::contains) }).isTrue()
}
```

- [ ] **Step 2: 运行测试并确认 RED**

Run: `./gradlew :app:testDebugUnitTest --tests '*LayeringRulesTest' --tests '*StageZeroScopeTest'`

Expected: FAIL，直至 `kotlinSourcesUnder` 和质量门辅助工具实现；测试不得只检查空目录。

- [ ] **Step 3: 实现架构与范围质量门，并配置 CI**

实现对生产 Kotlin 源与已编译类的架构检查：排除测试目录，至少要求存在 `AppViewModel`、`LocalProfileRepository`、`PrivateMediaStore`、`SecretStore` 后再判断禁用 import。添加 CI 顺序：`verifyThirdPartyNotices` → `detekt` → `ktlintCheck` → unit tests → `lintDebug` → connected device migration/Compose tests。将所有最终依赖版本、许可证、用途和数据流回填台账。

- [ ] **Step 4: 运行完整阶段 0 验收**

Run: `./gradlew verifyThirdPartyNotices detekt ktlintCheck :app:testDebugUnitTest :app:lintDebug :app:connectedDebugAndroidTest`

Expected: PASS，并人工验证：小屏（至少 320dp 宽）、深色模式、字体缩放 1.3x 与 TalkBack 基础导航；无网络不阻止首次建档；日志、Room 逻辑导出 DTO 和应用私有媒体不包含 API Key/Authorization；删除或篡改媒体不会崩溃。

- [ ] **Step 5: 提交**

```bash
git add app/src/test/java/com/example/englishlearning/architecture app/build.gradle.kts docs/third-party-notices.md .github/workflows/android-stage0.yml
git commit -m "test: enforce stage zero architecture and release checks"
```

## Plan Self-Review

- **Spec coverage:** F0-01 由任务 1、2、5、6、7 覆盖；F0-02 由任务 3、4、6 覆盖；F0-03 由任务 5 覆盖；F0-04 由任务 1、7 覆盖。AC0-01 至 AC0-05 分别由任务 6、3/5、7、3、1/7 验证。
- **非范围检查:** 所有任务仅定义后续 Provider/DTO；未计划词书数据、学习 UI/流程、HTTP、TTS、OCR、提醒、备份实现、账号或同步。
- **一致性检查:** `AppError` 在任务 2 定义并被任务 3、4、6 使用；`SecretReference` 在任务 3 定义且任务 4 仅存 alias；`LogicalSnapshot` 在任务 5 定义且 `BackupProvider` 只使用逻辑 DTO；最终阶段质量门在任务 7 强制全部边界。
- **待确认项:** 实施前须由用户确认 minSdk 26、阶段稳定 API/依赖版本锁定、Hilt、单模块 package-by-feature、临时 `com.example.englishlearning` 包名、SQLCipher 延后至性能与密钥生命周期设计评估。若这些决策变更，先更新本计划与 `docs/decisions/` 再实施。
