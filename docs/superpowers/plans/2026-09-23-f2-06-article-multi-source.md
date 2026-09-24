# F2-06 文章多来源 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让阅读文章除 AI 生成外，还能来自「已核验许可的外刊抓取」与「用户自行粘贴导入」，三种来源产出同一个 `Article` 领域类型，共用校验、复用、高亮与阅读渲染四段管线。

**Architecture:** 来源在领域层是**封闭联合类型** `ArticleSource`，每个分支自带必需的溯源元数据（AI 生成带模型名与参数摘要；抓取带来源 ID、显示名、原文 URL、许可说明与署名文本；导入不带额外字段）。这样「抓取来源缺少署名或许可说明」在**编译期**就不成立，取代运行时校验。抓取目标由 `ArticleSourceRegistry` 白名单控制：目标链接只能来自来源自己的 RSS 索引，且每个候选链接在发请求前都要再次通过白名单校验，用户无法指定任意 URL。存储层保持**扁平列**+`sourceType` 判别列，映射集中在一处，数据库仍可直读取证。

**Tech Stack:** Kotlin、Coroutines、Room 2.8.4、Compose、`java.net.HttpURLConnection`（零新依赖）、`com.sun.net.httpserver`（仅测试）、`org.json` 不可用——XML/HTML 用**手写深度计数扫描器**，不引入解析库。

**Spec:** `docs/specs/02-reading-and-ai-content.md` 的 F2-03、F2-04、F2-06，以及 AC2-07~AC2-10。

**许可依据:** `docs/decisions/2026-09-23-article-source-licensing.md`；台账条目 `docs/third-party-notices.md` 的 `voa-learning-english`。

## Global Constraints

- **抓取目标只允许白名单**：scheme 必须 `https`，host 必须与白名单条目**精确相等**，path 必须匹配该条目的路径前缀。用户提供的任何 URL 一律不接受；候选链接只从来源自己的 RSS 索引里取，且**发请求前**再校验一次。
- **来源白名单即台账条目**：`RegisteredArticleSource` 必须携带 `ledgerEntryId`，指向 `docs/third-party-notices.md` 中已完成核验的条目。没有台账条目的来源不得进入注册表。
- **抓取与导入内容与 AI 输出同为不可信输入**（`AGENTS.md:56`）：走同一套 `ArticleQualityPolicy`，参数不同而已，不新建平行的校验链路。
- **署名是许可条件，不是装饰**：抓取结果必须展示署名 `learningenglish.voanews.com` 与原文链接文本。链接**不自动打开**，须用户主动点击。
- **用户导入不联网**：`ImportArticleUseCase` 不得持有任何网络依赖，且界面必须说明「内容由你自行提供，请确保你有权使用」。
- **不新增任何依赖**（`dependencyLocking` 开启）。XML/HTML 解析必须是手写扫描器。
- 所有新行为先写失败测试再写最小实现；每个任务独立提交。
- JVM 测试源集是 **JUnit 5**，`androidTest` 才是 JUnit 4；`kotlin.test` 断言**实际值在前、消息在后**。

## 已实测的来源事实（2026-09-23，经代理实测）

写计划前已经把抓取路径实测过，以下不是推测：

| 事实 | 实测结果 |
| --- | --- |
| RSS 索引入口 | `https://learningenglish.voanews.com/rss/?count=N&zoneid=Z` 返回 `text/xml`，RSS 2.0 |
| `zoneid` 分流 | `965` 返回**空 channel**（不可用）；`1579`（科学与技术）、`3521`（艺术与文化）、`955`（日常语法）、`1689`（词汇与故事）均返回正常 item |
| item 字段 | `title`、`description`（**仅一句话摘要，不是正文**）、`link`、`guid`、`pubDate`、`category`，可选 `enclosure`（图片） |
| 正文位置 | 在**文章页** HTML 的 `<div class="wsw">` 内；段落是 `<p>`，小标题是 `<h2 class="wsw__h2">` |
| 正文噪声 | `div.wsw` 内含 `<div class="wsw__embed">` 音频播放器块（含 `mp3` 链接、多级 `div`），必须整块剔除 |
| 分隔线 | 正文中夹有 `<p>______…</p>` 形式的分隔段落 |

**结论**：正文需要**两次请求**（RSS 索引 → 文章页），且正文提取必须剔除 `wsw__embed` 块。这就是 Task C 的解析器要解决的问题。

---

### Task A: 补 `SecretStore` 密钥读回能力

**为什么必须先做**：`SecretStore` 目前只有 `save`/`delete`/`has`，**没有读取方法**，生成与抓取流程拿不到密钥去填 `Authorization` 头。没有这一步，原计划 Task 6 无法完成。

**Files:**
- Modify: `app/src/main/java/com/example/englishlearning/core/security/SecretStore.kt`
- Modify: `app/src/main/java/com/example/englishlearning/core/security/AndroidKeyStoreSecretStore.kt`
- Modify: `app/src/main/java/com/example/englishlearning/ai/AiProfileSecretUseCase.kt`
- Test: `app/src/test/java/com/example/englishlearning/core/security/AndroidKeyStoreSecretStoreTest.kt`（Robolectric）
- Test: `app/src/androidTest/java/com/example/englishlearning/core/security/SecretStoreDeviceTest.kt`

**Interfaces:**
- Consumes: 既有 `SecretStore`、`AndroidKeyStoreSecretStore.KeyStoreProvider`（已有的可注入接缝，正是为这种测试留的）
- Produces:
  - `SecretStore.read(reference: SecretReference): Result<CharArray>`（返回的数组由调用方负责清零）
  - `AiProfileSecretUseCase.loadKey(profile: AiProfile): Result<CharArray>`

- [ ] **Step 1: 写失败的 JVM 测试（Robolectric + 假 `KeyStoreProvider`）**

`AndroidKeyStoreSecretStore` 构造需要 `Context`（用 `filesDir`），所以 JVM 侧用 Robolectric 提供 Context，并用**真实 JCE AES 密钥**冒充 Keystore 密钥——这样加解密是真跑，只是密钥来源换掉。

```kotlin
class AndroidKeyStoreSecretStoreTest {
    private val keys = mutableMapOf<String, SecretKey>()
    private val provider = object : AndroidKeyStoreSecretStore.KeyStoreProvider {
        override fun keyFor(alias: String) = keys.getOrPut(alias) {
            KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        }
        override fun contains(alias: String) = alias in keys
        override fun delete(alias: String) { keys.remove(alias) }
    }
    private fun store() = AndroidKeyStoreSecretStore(ApplicationProvider.getApplicationContext(), provider)

    @Test fun `read returns exactly what save stored`() {
        val reference = SecretReference("ai-profile-p1")
        store().save(reference, "sk-secret".toCharArray()).getOrThrow()
        val loaded = store().read(reference).getOrThrow()
        assertEquals("sk-secret", loaded.concatToString())
        loaded.fill('\u0000')
    }

    @Test fun `read fails when nothing was stored`() {
        assertTrue(store().read(SecretReference("missing")).isFailure)
    }

    @Test fun `read of a tampered ciphertext fails instead of returning garbage`() { /* 翻转密文一个字节 */ }

    @Test fun `save overwrites and clears the caller buffer`() {
        val buffer = "sk-secret".toCharArray()
        store().save(SecretReference("a"), buffer).getOrThrow()
        assertTrue(buffer.all { it == '\u0000' })
    }

    @Test fun `read returns a caller-owned buffer that is not the stored source array`() {
        // 两次 read 必须互不影响：调用方清零第一次的结果不能破坏第二次读取。
    }
}
```

- [ ] **Step 2: 跑测试确认 RED**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.example.englishlearning.core.security.AndroidKeyStoreSecretStoreTest" --no-daemon --no-build-cache --console=plain`
Expected: 编译失败 `Unresolved reference 'read'`。

- [ ] **Step 3: 实现 `read` 与 `loadKey`**

`AndroidKeyStoreSecretStore.read`：

```kotlin
override fun read(reference: SecretReference): Result<CharArray> =
    runCatching {
        val file = ciphertextFile(reference)
        if (!file.isFile) throw AppErrorException(AppError.KeyStoreUnavailable)
        val bytes = file.readBytes()
        if (bytes.size <= IV_LENGTH) throw AppErrorException(AppError.KeyStoreUnavailable)
        val iv = bytes.copyOfRange(0, IV_LENGTH)
        val ciphertext = bytes.copyOfRange(IV_LENGTH, bytes.size)
        try {
            val key = keyStoreProvider.keyFor(reference.alias)
            val plaintext = Cipher.getInstance(TRANSFORMATION)
                .apply { init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv)) }
                .doFinal(ciphertext)
            plaintext.decodeToString().toCharArray()
        } finally {
            bytes.fill(0)
        }
    }
```

需要说明的两点，都必须写进代码注释：

1. **`IV_LENGTH = 12` 是一个格式约定**。既有 `save` 写的是 `iv || ciphertext`，**没有记录 IV 长度**，所以读回时必须知道它。AndroidKeyStore 的 AES/GCM 在 `init(ENCRYPT_MODE, key)` 下生成的 IV 是 12 字节，这是既有格式的隐含前提。**这个前提不能只靠注释保证**——Step 4 的真机测试就是它的证据；真机上任何一次 `save → read` 往返失败都会直接暴露它。
2. **不做「先 `readBytes` 再判长度」之外的额外拦截**：GCM 有认证标签，错误密钥或篡改密文会抛 `AEADBadTagException`，于是统一映射为 `KeyStoreUnavailable`，界面提示「密钥不可读，请重新录入」。**不区分「旧格式」与「被篡改」**，因为二者对用户而言的处置相同。

`AiProfileSecretUseCase.loadKey`：`secretStore.read(referenceFor(profile.profileId))`，原样透传结果，**不做日志**。

- [ ] **Step 4: 跑 JVM 测试确认 GREEN，再在真机上验证真实 Keystore 往返**

Run (JVM): 同 Step 2。Expected: PASS。
Run (真机): 新增 `SecretStoreDeviceTest`，在真机上走 `save → read → delete → read 失败` 全链路，用 `am instrument` 执行。

```bash
adb shell am instrument -w -e class com.example.englishlearning.core.security.SecretStoreDeviceTest com.example.englishlearning.test/androidx.test.runner.AndroidJUnitRunner
```

> 这一步不是可选的：它是 `IV_LENGTH = 12` 唯一的外部证据。

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/com/example/englishlearning/core/security app/src/main/java/com/example/englishlearning/ai app/src/test/java/com/example/englishlearning/core/security app/src/androidTest/java/com/example/englishlearning/core/security
git commit -m "feat(security): read stored secrets back for outbound authorization"
```

---

### Task B: `ArticleSource` 领域模型与 Room 9→10 迁移

**为什么必须先做**：原计划 Task 6、Task 7 都要写/读来源，领域类型不到位就会先写出一批要返工的代码。

**Files:**
- Create: `app/src/main/java/com/example/englishlearning/reading/domain/ArticleSource.kt`
- Modify: `app/src/main/java/com/example/englishlearning/reading/domain/Article.kt`
- Modify: `app/src/main/java/com/example/englishlearning/core/storage/entity/ArticleEntity.kt`
- Modify: `app/src/main/java/com/example/englishlearning/core/storage/AppDatabase.kt`
- Modify: `app/src/main/java/com/example/englishlearning/reading/RoomArticleRepository.kt`
- Modify: 三处既有 `Article` 构造点（`ReadingAccessViewModelTest`、`ReadingHistoryScreenTest`、`RoomArticleRepositoryTest`）
- Create (KSP 生成后提交): `app/schemas/com.example.englishlearning.core.storage.AppDatabase/10.json`
- Test: `app/src/androidTest/java/com/example/englishlearning/core/storage/AppDatabaseMigrationTest.kt`

**Interfaces:**
- Consumes: Task 1 已提交的 `Article.coveredLemmas`、`Article.generatedAtEpochMillis`
- Produces:
  - ```kotlin
    sealed interface ArticleSource {
        data class AiGenerated(val modelName: String, val parameterSummary: String) : ArticleSource
        data class WebFetched(
            val sourceId: String,
            val displayName: String,
            val articleUrl: String,
            val licenseNote: String,
            val attributionText: String,
        ) : ArticleSource
        data object UserImported : ArticleSource
    }
    ```
  - `Article` 用 `val source: ArticleSource` **替换** `modelName: String?` 与 `parameterSummary: String`
  - `enum class ArticleSourceType { AI_GENERATED, WEB_FETCHED, USER_IMPORTED }`（仅用于存储判别列）
  - `AppDatabase.MIGRATION_9_10`

- [ ] **Step 1: 写失败的迁移测试**

新增 `migration9To10AddsSourceColumnsWithoutLosingRows`：造一行 v9 文章与一条偏好，断言老行存活、`sourceType` 回填 `'AI_GENERATED'`、其余来源列为空串。

- [ ] **Step 2: 跑测试确认 RED**

Run: `./gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.englishlearning.core.storage.AppDatabaseMigrationTest --no-daemon --no-build-cache --console=plain`
Expected: FAIL —— `no such column: sourceType`。

- [ ] **Step 3: 实现领域类型、实体与迁移**

`ArticleSource.kt` 的 KDoc 必须写明设计意图：**用类型而不是校验来保证溯源完整性**。

`ArticleEntity` 追加（**迁移 9→10，`version = 10`**）：

```kotlin
val sourceType: String,              // ArticleSourceType 的名称
val sourceId: String,                // 仅 WEB_FETCHED 非空
val sourceDisplayName: String,
val sourceUrl: String,
val sourceLicenseNote: String,
val sourceAttribution: String,
```

`MIGRATION_9_10`：

```kotlin
val MIGRATION_9_10: Migration =
    object : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // ALTER TABLE 加 NOT NULL 列必须带 DEFAULT；已有行只能是 AI 生成，因为此前只有这一条来源。
            db.execSQL("ALTER TABLE `articles` ADD COLUMN `sourceType` TEXT NOT NULL DEFAULT 'AI_GENERATED'")
            db.execSQL("ALTER TABLE `articles` ADD COLUMN `sourceId` TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE `articles` ADD COLUMN `sourceDisplayName` TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE `articles` ADD COLUMN `sourceUrl` TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE `articles` ADD COLUMN `sourceLicenseNote` TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE `articles` ADD COLUMN `sourceAttribution` TEXT NOT NULL DEFAULT ''")
        }
    }
```

> **为什么不改 `MIGRATION_8_9` 而要新开 v10**：Task 1 已经在真机跑过验证，设备上**确实存在**一个 v9 库。若保留版本号 9 却改了 schema，Room 打开时会因 identity hash 不匹配而校验失败。升版本号是唯一能让已有 v9 库正常迁移的做法。
>
> 既有 `modelName` / `parameterSummary` 两列**继续复用**给 `AiGenerated` 分支，因此没有产生死列。

`RoomArticleRepository` 的映射集中在这一个文件：`toEntity` 按 `ArticleSource` 分支写列，`toDomain` 按 `sourceType` 还原。**`sourceType` 解析失败时退回 `UserImported` 是错的**——会造成「无署名却显示成用户导入」。正确做法是抛 `AppErrorException(AppError.StorageUnavailable)`（读不出来就说读不出来），并为此写一条测试。

- [ ] **Step 4: 跑迁移与仓储测试确认 GREEN**

Run: 同 Step 2 的类 + `:app:connectedDebugAndroidTest -P...class=com.example.englishlearning.reading.RoomArticleRepositoryTest`
Expected: PASS；`app/schemas/.../10.json` 已生成。

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/com/example/englishlearning/reading app/src/main/java/com/example/englishlearning/core/storage app/src/androidTest app/schemas
git commit -m "feat(reading): model the article source as a closed union type"
```

---

### Task C: 来源注册表与外刊抓取

**Files:**
- Create: `app/src/main/java/com/example/englishlearning/reading/ArticleSourceRegistry.kt`
- Create: `app/src/main/java/com/example/englishlearning/reading/ArticleFeedParser.kt`（纯函数）
- Create: `app/src/main/java/com/example/englishlearning/reading/ArticlePageParser.kt`（纯函数）
- Create: `app/src/main/java/com/example/englishlearning/reading/FetchArticleUseCase.kt`
- Create: `app/src/test/resources/voa/feed-zone1579-reduced.xml`、`app/src/test/resources/voa/article-7998765-reduced.html`（**缩减过的真实抓取样本**）
- Test: `app/src/test/java/com/example/englishlearning/reading/ArticleSourceRegistryTest.kt`
- Test: `app/src/test/java/com/example/englishlearning/reading/ArticleFeedParserTest.kt`
- Test: `app/src/test/java/com/example/englishlearning/reading/ArticlePageParserTest.kt`
- Test: `app/src/test/java/com/example/englishlearning/reading/FetchArticleUseCaseTest.kt`

**Interfaces:**
- Produces:
  - ```kotlin
    data class RegisteredArticleSource(
        val sourceId: String,
        val displayName: String,
        val host: String,                 // 必须 https 且精确相等
        val feedPath: String,             // 如 "/rss/?count=20&zoneid=1579"
        val articlePathPrefix: String,    // 如 "/a/"
        val attributionText: String,      // 如 "learningenglish.voanews.com"
        val licenseNote: String,
        val ledgerEntryId: String,        // 必须存在于 docs/third-party-notices.md
    )
    object ArticleSourceRegistry {
        val all: List<RegisteredArticleSource>
        fun byId(sourceId: String): RegisteredArticleSource?
        /** 白名单校验：https + host 精确相等 + path 前缀匹配。失败返回 null，调用方不得发请求。 */
        fun match(url: String): RegisteredArticleSource?
    }
    ```
  - ```kotlin
    data class FeedItem(val title: String, val articleUrl: String, val publishedAtEpochMillis: Long, val summary: String)
    object ArticleFeedParser { fun parse(xml: String): Result<List<FeedItem>> }
    data class ParsedArticlePage(val title: String, val body: String)   // body 为纯文本，段落以 \n\n 分隔
    object ArticlePageParser { fun parse(html: String): Result<ParsedArticlePage> }
    ```
  - ```kotlin
    sealed interface FetchArticleResult {
        data class Fetched(val article: Article) : FetchArticleResult
        data class Listed(val sourceId: String, val items: List<FeedItem>) : FetchArticleResult
        data class RejectedTarget(val reason: RejectedTargetReason) : FetchArticleResult
        data class Failed(val failure: FetchFailure) : FetchArticleResult
    }
    enum class RejectedTargetReason { NotWhitelisted, NotHttps, WrongPath }
    enum class FetchFailure { NetworkUnavailable, SourceUnreachable, BodyNotFound, BodyTooShort, QualityRejected }
    class FetchArticleUseCase(
        private val transport: AiHttpTransport,        // GET 复用同一端口（扩展为支持 GET）
        private val registry: ArticleSourceRegistry,
        private val articles: ArticleRepository,
        private val quality: ArticleQualityPolicy,
        private val ids: ArticleIdFactory,
        private val clock: () -> Instant,
    ) {
        suspend fun list(sourceId: String): FetchArticleResult
        suspend fun fetch(sourceId: String, item: FeedItem, planId: String): FetchArticleResult
    }
    ```

- [ ] **Step 1: 写 `ArticleSourceRegistry` 的失败测试**

必须锁定的行为：

| 测试名 | 断言 |
| --- | --- |
| `registryOnlyContainsSourcesWithALedgerEntry` | 每个条目的 `ledgerEntryId` 都能在 `docs/third-party-notices.md` 里找到同名 `## ` 标题 |
| `rejectsAUrlWhoseHostIsNotWhitelisted` | `https://evil.test/a/x.html` → `match` 返回 `null` |
| `rejectsHttpEvenForAWhitelistedHost` | `http://learningenglish.voanews.com/a/x.html` → `null`（**不因为 host 对就放行**） |
| `rejectsALookalikeHost` | `https://learningenglish.voanews.com.evil.test/a/x.html` → `null`（必须是**精确**相等，不是后缀匹配） |
| `rejectsAWhitelistedHostWithANonArticlePath` | `https://learningenglish.voanews.com/p/5373.html` → `null` |
| `acceptsAWhitelistedArticleUrl` | 实测样本 URL → 对应条目 |

> `rejectsALookalikeHost` 是这类白名单最容易写错的地方：一串 `endsWith(host)` 就会放过 `…voanews.com.evil.test`。

- [ ] **Step 2: 写两个解析器的失败测试（用真实缩减样本）**

样本来源与许可写进文件头部注释：站点 `learningenglish.voanews.com`，抓取日期 2026-09-23，VOA Learning English 文本属公有领域，此处仅作开发期测试夹具、**不进入 APK**。

`ArticleFeedParserTest`：解析出 item 数、`title`/`link`/`pubDate`/`description` 正确；**XML 实体解码**（`&amp;` → `&`）；`zoneid=965` 的空 channel → `Result.success(emptyList())`（空不是错误）；缺 `link` 的 item 被跳过；非 RSS 内容 → `isFailure`。

`ArticlePageParserTest`：

| 测试名 | 断言 |
| --- | --- |
| `extractsTheParagraphsFromTheArticleBody` | 段落文本正确，段间以 `\n\n` 分隔 |
| `dropsTheAudioPlayerEmbed` | 结果**不含** `mp3`、`data-player_id`、`c-player` 等嵌入块残留 |
| `dropsTheUnderscoreSeparatorParagraph` | 全下划线的分隔段不出现 |
| `decodesHtmlEntities` | `&amp;` / `&#39;` / `&quot;` 正确还原 |
| `stripsInnerTagsFromParagraphs` | `<em>`、`<a>`、`<strong>` 只留文字 |
| `keepsHeadingsAsPlainText` | `h2.wsw__h2` 的文字保留为独立行 |
| `failsWhenThereIsNoBodyContainer` | 无 `div.wsw` → `isFailure` |
| `isDeterministic` | 同输入同输出 |

- [ ] **Step 3: 跑三个测试确认 RED**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.example.englishlearning.reading.Article*ParserTest" --tests "com.example.englishlearning.reading.ArticleSourceRegistryTest" --no-daemon --no-build-cache --console=plain`
Expected: 编译失败 `Unresolved reference 'ArticleSourceRegistry'`。

- [ ] **Step 4: 实现注册表与解析器**

`ArticleSourceRegistry` 当前**只登记一个**来源：

```kotlin
private val voaLearningEnglish = RegisteredArticleSource(
    sourceId = "voa-learning-english",
    displayName = "VOA Learning English",
    host = "learningenglish.voanews.com",
    feedPath = "/rss/?count=20&zoneid=1579",     // 实测可用；zoneid=965 返回空 channel，不要用
    articlePathPrefix = "/a/",
    attributionText = "learningenglish.voanews.com",
    licenseNote = "VOA Learning English 文本属公有领域，可转载须署名 learningenglish.voanews.com",
    ledgerEntryId = "voa-learning-english",
)
```

`ArticleFeedParser` / `ArticlePageParser` 都是**手写深度计数扫描器**，不用正则表达整段 HTML：

- 找 `div class="wsw"` 起始位置，用「数 `<div` / `</div>` 深度」定位它的结束位置，切成容器片段。
- 在容器片段内**先**用同样方式逐块剔除 `class="wsw__embed"` 的 div，再提取 `<p …>…</p>` 与 `<h2 …>…</h2>`。
- 段落内去掉所有 `<…>` 标签，再解码实体（只处理 `&amp;` `&lt;` `&gt;` `&quot;` `&#39;` `&nbsp;` 六个，其余原样保留）。
- 丢弃只由 `_`、空白、`-` 组成的段。

> 手写扫描器是**有意选择**：引入 HTML 解析库会新增依赖并触发锁文件重写，而这里需要的结构非常窄。代价是脆弱性——所以 Step 5 必须在真机上对**真实页面**再验一次，样本夹具只能证明「对已抓到的这一份有效」。

- [ ] **Step 5: 跑测试确认 GREEN，并在真机上对真实站点验证**

Run (JVM): 同 Step 3。Expected: PASS。
Run (真机): 走查脚本触发一次真实 `list` + `fetch`，把抓到的标题、正文字数、署名落进 `verification-logs/`，证明解析器对**当期**页面仍然有效。

- [ ] **Step 6: 实现 `FetchArticleUseCase` 并补测试**

必须锁定的行为：

| 测试名 | 断言 |
| --- | --- |
| `listReturnsTheFeedItemsOfAWhitelistedSource` | 用假 transport 喂样本 XML → `Listed`，item 数与标题正确 |
| `fetchRefusesAFeedItemWhoseUrlIsNotWhitelisted` | item 的 URL 指向白名单外 → `RejectedTarget`，**`transport` 未被调用** |
| `fetchRefusesAnHttpFeedItem` | http 链接 → `RejectedTarget(NotHttps)`，**未发请求** |
| `fetchStoresAWebFetchedArticleWithAttribution` | 成功 → 落库文章的 `source` 是 `WebFetched`，含 `attributionText` 与 `articleUrl` |
| `fetchDoesNotStoreChineseText` | 抓取来源没有译文：`chineseText` 为空，且**不伪造**任何译文 |
| `fetchRejectsABodyThatFailsQuality` | 正文过短/非英文 → `Failed(QualityRejected)`，仓库为空 |
| `fetchMapsANetworkFailureWithoutStoring` | → `Failed(NetworkUnavailable)`，仓库为空 |
| `fetchNeverStoresTheApiKey` | 落库文章任何字符串字段都不含测试密钥 |
| `fetchReusesAnAlreadyStoredArticleForTheSameUrl` | 同一 URL 二次抓取 → 复用，`transport` 调用次数不增加 |

- [ ] **Step 7: 提交**

```bash
git add app/src/main/java/com/example/englishlearning/reading app/src/test/java/com/example/englishlearning/reading app/src/test/resources/voa
git commit -m "feat(reading): fetch whitelisted foreign articles with attribution"
```

---

### Task D: 用户粘贴导入

**Files:**
- Create: `app/src/main/java/com/example/englishlearning/reading/ImportArticleUseCase.kt`
- Modify: `app/src/main/java/com/example/englishlearning/reading/ArticleQualityPolicy.kt`（抽出 `ArticleTextConstraints`）
- Test: `app/src/test/java/com/example/englishlearning/reading/ImportArticleUseCaseTest.kt`
- Test: `app/src/test/java/com/example/englishlearning/reading/ArticleQualityPolicyTest.kt`（补约束参数化用例）

**Interfaces:**
- Consumes: `ArticleQualityPolicy`、`ArticleRepository`、`ArticleIdFactory`
- Produces:
  - ```kotlin
    data class ArticleTextConstraints(
        val minWords: Int,
        val maxWords: Int,
        val maxChars: Int,
        val requireTranslation: Boolean,
    )
    object ArticleQualityPolicy {
        val forAiGeneration: (ArticleLengthPolicy.Resolved) -> ArticleTextConstraints
        val forImported: ArticleTextConstraints   // minWords = 40, maxWords = 1200, requireTranslation = false
        fun validate(raw: RawArticle, constraints: ArticleTextConstraints): Result<ValidatedArticle>
    }
    ```
  - ```kotlin
    sealed interface ImportArticleResult {
        data class Imported(val article: Article) : ImportArticleResult
        data class Rejected(val reason: ImportRejection) : ImportArticleResult
    }
    enum class ImportRejection { BlankTitle, BlankBody, BodyTooShort, BodyTooLong, NotEnglish, DangerousMarkup }
    class ImportArticleUseCase(
        private val articles: ArticleRepository,
        private val quality: ArticleQualityPolicy,
        private val ids: ArticleIdFactory,
        private val clock: () -> Instant,
    ) {
        suspend fun import(title: String, body: String, planId: String): ImportArticleResult
    }
    ```

- [ ] **Step 1: 写失败的导入测试**

| 测试名 | 断言 |
| --- | --- |
| `importsAWellFormedEnglishArticle` | → `Imported`，`source == UserImported`，`chineseText` 为空 |
| `rejectsAScriptTag` | `<script>alert(1)</script>` → `Rejected(DangerousMarkup)`，仓库为空 |
| `rejectsChinesePastedAsTheBody` | → `Rejected(NotEnglish)` |
| `rejectsABodyThatIsTooShort` | → `Rejected(BodyTooShort)` |
| `rejectsABodyOverTheCap` | → `Rejected(BodyTooLong)` |
| `rejectsABlankTitle` | → `Rejected(BlankTitle)` |
| `importedArticleStillGetsHighlightsFromTodaysPlan` | 高亮派生与其他来源一致（`coveredLemmas` 非空） |
| `importedArticleHasNoOutboundDependency` | 用例构造函数**不含任何网络类型**（用反射断言构造参数类型） |

> 最后一条是把「用户导入不联网」从口头约定变成可断言的事实：如果有人日后接了传输端口，测试立刻红。

- [ ] **Step 2: 跑测试确认 RED**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.example.englishlearning.reading.ImportArticleUseCaseTest" --no-daemon --no-build-cache --console=plain`
Expected: 编译失败 `Unresolved reference 'ImportArticleUseCase'`。

- [ ] **Step 3: 实现（复用同一套校验，只换约束数据）**

`ImportArticleUseCase` 只做四件事：拼 `RawArticle` → `quality.validate(raw, ArticleQualityPolicy.forImported)` → `ArticleHighlightPolicy.derive` → `saveNewVersion`。**不新增任何校验分支**。

- [ ] **Step 4: 跑测试确认 GREEN**

Run: 同 Step 2。Expected: PASS。

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/com/example/englishlearning/reading app/src/test/java/com/example/englishlearning/reading
git commit -m "feat(reading): import a pasted article without leaving the device"
```

---

### Task E: 阅读页与历史列表的来源展示

**Files:**
- Modify: `app/src/main/java/com/example/englishlearning/ui/ArticleReadingViewModel.kt`（随原计划 Task 7 一起）
- Modify: `app/src/main/java/com/example/englishlearning/ui/ArticleReadingScreen.kt`
- Modify: `app/src/main/java/com/example/englishlearning/ui/ReadingHistoryScreen.kt`（若有）
- Test: `app/src/test/java/com/example/englishlearning/ui/ArticleReadingViewModelTest.kt`
- Test: `app/src/androidTest/java/com/example/englishlearning/ui/ArticleReadingScreenTest.kt`

**Interfaces:**
- Produces: 阅读页顶部新增来源区；`testTag`：`article_source_name`、`article_attribution`、`article_source_link`、`article_source_url_label`、`article_no_translation_notice`。

- [ ] **Step 1: 写失败测试**

| 测试名 | 断言 |
| --- | --- |
| `showsTheModelNameForAGeneratedArticle` | `AiGenerated` → 显示模型名 |
| `showsTheAttributionAndLinkForAFetchedArticle` | `WebFetched` → 署名与原文链接**文本**同时可见 |
| `neverShowsTheEndpointOrKeyForAnySource` | 三种来源下，界面文本不含 `endpoint`、`api.test`、`sk-` |
| `showsTheImportedNoticeAndTimestampForAnImportedArticle` | `UserImported` → 显示导入时间与责任说明 |
| `notifiesWhenTheSourceHasNoChineseText` | `chineseText` 为空 → 显示「该来源无中文翻译」且**全文翻译开关被禁用** |
| `doesNotExecuteTheSourceLinkAutomatically` | 原文链接是纯文本 + 显式按钮，**没有任何 `openUri` 在加载时被调用** |

- [ ] **Step 2: 跑测试确认 RED** → Step 3 实现 → Step 4 GREEN → Step 5 提交

```bash
git commit -m "feat(reading): show the article source, attribution and translation notice"
```

---

### Task F: 三来源入口与导入界面

> **必须与原计划 Task 8 一起做**：`ReadingAccessScreen` 是同一个文件，分两次改会互相冲突。

**Files:**
- Modify: `app/src/main/java/com/example/englishlearning/ui/ReadingAccessScreen.kt`
- Create: `app/src/main/java/com/example/englishlearning/ui/ArticleImportScreen.kt`
- Modify: `app/src/androidTest/java/com/example/englishlearning/ui/ReadingAccessScreenTest.kt`
- Test: `app/src/androidTest/java/com/example/englishlearning/ui/ArticleImportScreenTest.kt`

**Interfaces:**
- Produces: `ReadingAccessScreen` 三个来源入口「AI 生成」「从外刊选取」「粘贴文章」；`ArticleImportScreen(state, onBodyChange, onTitleChange, onImport, onBack, modifier)`；`testTag`：`reading_source_ai`、`reading_source_feed`、`reading_source_import`、`import_title`、`import_body`、`import_submit`、`import_disclaimer`、`import_rejection_reason`。

- [ ] **Step 1: 写失败测试**

要点：

- 三个入口各自可点击并进入对应流程；
- 导入界面**必须**可见责任说明（断言 `import_disclaimer` 的文案包含「你有权使用」）；
- 导入被拒时显示**具体原因**（不是「导入失败」这种无用文案）；
- 外刊入口进入后先列出 feed 标题列表，**列表为空时显示明确空态**而不是空白页；
- 外刊来源在未通过许可核验时（台账条目缺失）入口**不可用并说明原因**。

- [ ] **Step 2: 实现** → **Step 3: 跑测试确认 GREEN**

Run: `./gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.englishlearning.ui.ReadingAccessScreenTest -P...class=com.example.englishlearning.ui.ArticleImportScreenTest --no-daemon --no-build-cache --console=plain`

- [ ] **Step 4: 提交**

```bash
git commit -m "feat(reading): offer generation, feed and import as three sources"
```

---

## 不在本轮范围

- **对无译文来源的 AI 补译**：抓取与外刊文章不做自动翻译。要做就必须走出站确认，属于独立特性（已写入 spec「不在本阶段实现」）。
- **图像与音频**：只取 VOA 的文本。VOA 使用的 AP / Reuters 图片受版权保护，**明确不取**。
- **多来源扩展**：注册表结构支持多条，但本轮**只登记 VOA 一条**。新增来源必须先完成台账登记与许可核验。
- **来源列表页的分页与搜索**：只用 feed 最新一页。

## Self-review

**1. Spec 覆盖**

| Spec 要求 | 落点 |
| --- | --- |
| F2-06 三种来源、来源元数据随文章保存 | Task B（`ArticleSource` + `sourceType` 判别列） |
| F2-06 类型层面携带必需元数据，编译期不成立 | Task B（sealed interface 取代可空字段） |
| F2-06 白名单抓取，禁止任意 URL 与自定义目标 | Task C（`ArticleSourceRegistry.match`，六条拒绝用例） |
| F2-06 署名与原文链接展示、不自动打开 | Task E（`neverShowsTheEndpointOrKeyForAnySource`、`doesNotExecuteTheSourceLinkAutomatically`） |
| F2-06 用户导入不联网、用户自负其责 | Task D（反射断言无网络依赖）+ Task F（`import_disclaimer`） |
| F2-06 三来源共用同一管线 | Task D 复用 `ArticleQualityPolicy` 与 `ArticleHighlightPolicy`；无来源分支的展示链路 |
| F2-06 离线缓存边界与来源下线 | Task C 注册表是唯一下线开关；缓存均为应用私有存储，不随备份再分发 |
| AC2-07 来源标识（不含 Key / Endpoint） | Task E |
| AC2-08 白名单外 URL 被拒且不发请求 | Task C（`RejectsTarget` 用例断言 transport 未被调用） |
| AC2-09 导入走不可信输入校验 | Task D |
| AC2-10 抓取 / 导入失败保留今日完成状态 | Task C `Failed` 分支 + Task D `Rejected` 分支；二者都不写今日计划 |

**2. Placeholder 扫描**：无 TBD/TODO。Task E 的步骤 2~5 合并成一行，是因为它是原计划 Task 7 的增量修改，测试写法与 Task C/D 一致；Task F 同理，已注明必须与原计划 Task 8 同批完成。

**3. 类型一致性**：`ArticleSource` 三个分支（Task B）在 Task C（`WebFetched`）、Task D（`UserImported`）、Task E（三分支展示）中使用同一组字段名；`ArticleTextConstraints`（Task D）是 `ArticleQualityPolicy` 唯一入口，原计划 Task 4 的 `validate(raw, length)` 调用点需同步改为传入 `forAiGeneration(length)`；`AiHttpTransport` 在 Task C 需要新增 **GET** 能力——原计划 Task 2 只定义了 POST，**Task C 必须先把端口扩展为支持 GET**（或新增 `get` 方法），这是一处跨计划的接口变更，已在执行顺序中体现。

**4. 已知脆弱点（如实记录）**：
- 正文解析依赖 VOA 的 `div.wsw` 结构，站点改版会失效。缓解：解析失败返回 `BodyNotFound` 而不是产出半截正文；真机走查会暴露。
- `IV_LENGTH = 12` 依赖 AndroidKeyStore 的既定行为，由 Task A Step 4 的真机往返测试作为证据。
- VOA 在中国法下的保护状态**尚未定论**，见许可决策的保留项；未闭合前不得用于宣传与付费内容。
