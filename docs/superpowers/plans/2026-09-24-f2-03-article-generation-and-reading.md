# F2-03 文章生成与 F2-04 阅读体验 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让用户按当天所学词获得一篇英文短文，并在应用内以受控文本渲染出来，附派生高亮与未覆盖词列表；文章可来自用户自配 AI 生成、已核验许可的外刊抓取或用户自行粘贴导入；任一来源不可用时都不影响学习完成状态与历史阅读。

> **范围变更（2026-09-23）**：用户确认「两条来源一起做」，本轮新增 F2-06 文章多来源（外刊抓取 + 用户粘贴导入）。多来源的实现任务另见 `docs/superpowers/plans/2026-09-23-f2-06-article-multi-source.md`。两份计划**共享同一套领域类型与阅读页**，任务顺序见下方「执行顺序」。

**Architecture:** 传输层抽成一个 suspend 端口 `AiHttpTransport`，生产实现用 `HttpURLConnection`（零新依赖），JVM 测试用 JDK 内置的 `com.sun.net.httpserver` 起真实本地 HTTP 服务打真实请求。请求构造、响应解析、质量校验、高亮派生都是**纯函数**，可完全在 JVM 上确定性测试。高亮**不落库也不信任模型**：库里只存生成时用到的词条 lemma 列表，渲染时按同一套纯函数重新派生，所以隔天重读历史文章得到的高亮与当天一致。三种来源产出**同一个** `Article` 领域类型，只是在 `ArticleSource` 联合类型上不同，因此校验、复用、派生、渲染四段管线零分叉。

**Tech Stack:** Kotlin、Coroutines、Room 2.8.4、Compose、`kotlinx-serialization-json`（**已在依赖中**，仅用 `Json.parseToJsonElement` 导航，不启用编译器插件）、`java.net.HttpURLConnection`、`com.sun.net.httpserver`（仅测试）、JDK 内置 TTS 不做（见「不在本轮范围」）。

**Spec:** `docs/specs/02-reading-and-ai-content.md` 的 F2-03、F2-04、F2-06，以及 AC2-01~AC2-10。

## Global Constraints

- 仅支持 HTTPS 公网 Endpoint；HTTP、`file:`、`content:`、环回、私网与重定向至这些地址一律拒绝（`validateEndpoint` 已实现，不得绕过）。
- API Key 不得进入 Room 业务表、日志、崩溃报告、剪贴板、导出包、文章元数据或截图诊断。Key 只允许出现在**出站请求的 `Authorization` 头**里。
- 首次调用第三方 Endpoint 前必须展示域名及「文本与 Key 将发送给该服务」的确认；图片发送每次都需确认。确认粒度是**域名**不是布尔量（`requiredConfirmation` 已实现）。
- 只允许 `temperature`(0–2)、`top_p`(0–1)、`max_tokens`(1–4096)、`timeout_seconds`(5–120) 与受控系统提示模板；拒绝未知参数。用户参数**不得**影响请求头。
- **不信任模型提供的高亮坐标**：高亮一律由系统从正文重新派生。
- 同一日期 + 活动词书 + 文章类型 + 长度档默认复用成功结果；换一篇必须由用户主动触发并保存为新版本，旧版本仍可读。
- 文章只以安全文本渲染，禁止 HTML、脚本、外部链接自动执行。
- **不新增任何依赖**（`dependencyLocking` 开启，无新依赖即无需改锁文件；若确实需要新增，必须在构建成功的同一批次用 `--write-locks` 落锁）。
- 所有新行为先写失败测试，再写最小生产实现；每个任务独立提交。
- JVM 测试源集是 **JUnit 5**（`libs.junit.jupiter` + `libs.kotlin.test.junit5`），`androidTest` 才是 JUnit 4。`kotlin.test` 断言**实际值在前、消息在后**。
- Kotlin JUnit4 `@Test` 必须最终返回 `Unit`。

### 本轮四项决定（2026-09-24 与用户确认）

1. **范围**：F2-03 与 F2-04 一次做完（含完整阅读体验）。
2. **HTTP**：零新依赖，`HttpURLConnection` + JVM 侧 JDK 内置 `HttpServer` 测试。
3. **端到端验证**：由用户在手机上录入真实密钥后，再做真机走查取证。
4. **成本护栏**：**暂不设**每日重生成次数上限与历史保留上限。→ 记为**有意偏差**：spec F2-03 要求「每日重生成次数与历史保留上限由实现计划决定并显示成本提示」。本轮实现复用语义与成本提示，但**不加次数/容量上限**；`ArticleRepository` 现有签名已支持后续在不改领域模型的前提下补上限。

### 范围变更后的补充决定（2026-09-23）

5. **来源范围**：三种来源一起做（AI 生成 / 外刊抓取 / 用户粘贴导入）。许可与署名边界见 `docs/decisions/2026-09-23-article-source-licensing.md`；抓取目标只允许白名单来源，当前仅登记 VOA Learning English。
6. **有意偏差**：spec F2-03 原文要求译文必需。本轮放宽为「AI 生成来源必须含中文翻译；抓取与导入来源允许缺失」，缺失时明确展示「该来源无中文翻译」并禁用全文翻译切换。原因是 VOA 等外刊原文本身不含中文译文，强制要求会让抓取路径永远无法通过质量校验——用「伪造成失败」比放宽更糟。该偏差已回写进 spec F2-03。

### 执行顺序（两份计划合并后）

| 序 | 任务 | 归属 |
| --- | --- | --- |
| 1 | 补 `SecretStore` 密钥读回能力（**阻塞 Task 6**） | F2-06 计划 Task A |
| 2 | `ArticleSource` 领域模型 + Room 9→10 迁移（**阻塞 Task 6、Task 7**） | F2-06 计划 Task B |
| 3 | Task 2 HTTP 传输端口（无依赖，可并行） | 本计划 |
| 4 | Task 3 / 4 / 5 提示词、解析校验、高亮派生（纯函数，无依赖） | 本计划 |
| 5 | 来源注册表、外刊抓取、用户导入用例 | F2-06 计划 Task C / D |
| 6 | Task 6 生成用例（写 `source = AiGenerated`） | 本计划 |
| 7 | Task 7 阅读页（含来源与署名展示） | 本计划 + F2-06 计划 Task E |
| 8 | Task 8 生成入口、出站确认与失败界面 | 本计划 |
| 9 | Task 9 Hilt 接入、网络权限与端到端验收 | 本计划 |

> **已确认的计划缺口 1**：`SecretStore` 只有 `save`/`delete`/`has`，**没有读取密钥的方法**，生成流程拿不到密钥去填 `Authorization` 头。这不是可绕过的细节，必须先补（执行顺序第 1 项）。
>
> **已确认的计划缺口 2**：Task 6 构造函数未注入 `TodayPlanRepository`，但步骤 4 需要 `planId`。改为让 `ArticleGenerationRequest` 直接携带 `planId`（调用方本来就有今日计划），Task 3 的请求类型据此增加该字段。

---

## 文件结构

| 文件 | 职责 |
| --- | --- |
| `ai/net/AiHttpTransport.kt` | 传输端口与请求/响应/结果类型（纯接口，无实现） |
| `ai/net/UrlConnectionAiHttpTransport.kt` | 唯一的生产 HTTP 实现；不跟随重定向；响应体大小受限 |
| `ai/net/AiChatRequestBuilder.kt` | 唯一构造出站请求头/地址/体的地方；Key 只在此处进入 `Authorization` |
| `reading/domain/ArticleGenerationRequest.kt` | 生成输入：词条、词书、类型、长度档、语言偏好、排除条件 |
| `reading/ArticlePromptPolicy.kt` | 把生成输入编成 system/user 提示词（纯函数） |
| `reading/ArticleResponseParser.kt` | OpenAI 响应 → `RawArticle`；忽略模型给的高亮/坐标 |
| `reading/ArticleQualityPolicy.kt` | 保存前校验与内容安全策略（纯函数） |
| `reading/ArticleHighlightPolicy.kt` | 从正文 + lemma 列表派生高亮与未覆盖词（纯函数） |
| `reading/GenerateArticleUseCase.kt` | 编排：复用判定 → 出站确认 → 请求 → 解析 → 校验 → 派生 → 落库 |
| `reading/domain/ArticleDisplayMode.kt` | 英文优先 / 双语对照 / 默认展开全文翻译 |
| `ui/ArticleReadingViewModel.kt` | 阅读页状态机（加载、显示模式、展开译文、点词） |
| `ui/ArticleReadingScreen.kt` | 阅读页（受控文本渲染 + 高亮 + 未覆盖词 + 词条入口） |
| `docs/decisions/2026-09-24-article-generation-and-display.md` | 四组「候选方案 vs 决定」 |
| `docs/verification/f2-03-04/README.md` | 验收记录 |
| `reading/domain/ArticleSource.kt` | 来源联合类型（AI 生成 / 外刊抓取 / 用户导入）——见 F2-06 计划 |
| `reading/ArticleSourceRegistry.kt` | 已核验许可的来源白名单——见 F2-06 计划 |
| `reading/FetchArticleUseCase.kt` | 外刊抓取、正文解析与不可信输入校验——见 F2-06 计划 |
| `reading/ImportArticleUseCase.kt` | 用户粘贴导入——见 F2-06 计划 |

---

### Task 1: 领域模型扩展与 Room 8→9 迁移

**Files:**
- Create: `app/src/main/java/com/example/englishlearning/reading/domain/ArticleDisplayMode.kt`
- Modify: `app/src/main/java/com/example/englishlearning/reading/domain/Article.kt`
- Modify: `app/src/main/java/com/example/englishlearning/reading/domain/ReadingPreference.kt`
- Modify: `app/src/main/java/com/example/englishlearning/core/storage/entity/ArticleEntity.kt`
- Modify: `app/src/main/java/com/example/englishlearning/core/storage/entity/ReadingPreferenceEntity.kt`
- Modify: `app/src/main/java/com/example/englishlearning/core/storage/AppDatabase.kt`
- Modify: `app/src/main/java/com/example/englishlearning/reading/RoomArticleRepository.kt`
- Modify: `app/src/main/java/com/example/englishlearning/reading/RoomReadingPreferenceRepository.kt`
- Create (KSP 生成后提交): `app/schemas/com.example.englishlearning.core.storage.AppDatabase/9.json`
- Test: `app/src/androidTest/java/com/example/englishlearning/core/storage/AppDatabaseMigrationTest.kt`

**Interfaces:**
- Consumes: 既有 `ArticleEntity`、`ReadingPreferenceEntity`、`MIGRATION_7_8`
- Produces:
  - `enum class ArticleDisplayMode { ENGLISH_FIRST, BILINGUAL, FULL_TRANSLATION }`
  - `Article` 新增 `coveredLemmas: List<String>`、`parameterSummary: String`
  - `ReadingPreference` 新增 `displayMode: ArticleDisplayMode`
  - `AppDatabase.MIGRATION_8_9`

- [ ] **Step 1: 写失败的迁移测试**

在 `AppDatabaseMigrationTest` 内新增（沿用该文件既有的 `helper`/`Room.databaseBuilder` 写法）：

```kotlin
@Test
fun migration8To9AddsArticleGenerationColumnsAndDisplayMode() {
    helper.createDatabase(TEST_DB, 8).apply {
        // 造一行 8 版文章，验证迁移不丢数据
        execSQL(
            "INSERT INTO articles (articleId, profileId, localDate, activeWordBookId, articleType, " +
                "lengthTier, version, title, englishText, chineseText, generatedAtEpochMillis, modelName) " +
                "VALUES ('a1','p1','2026-09-24','cet4','STORY','STANDARD',1,'T','English body','中文正文',1,'m1')",
        )
        execSQL("INSERT INTO reading_preferences (profileId, defaultArticleType, explicitLengthTier) VALUES ('p1','STORY',NULL)")
        close()
    }
    val db = Room.databaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java, TEST_DB)
        .addMigrations(*AppDatabase.MIGRATIONS)
        .build()
    db.openHelper.writableDatabase.query("SELECT coveredLemmas, parameterSummary FROM articles WHERE articleId='a1'").use {
        assertTrue(it.moveToFirst())
        assertEquals("", it.getString(0))          // 老数据回填默认值
        assertEquals("", it.getString(1))
    }
    db.openHelper.writableDatabase.query("SELECT displayMode FROM reading_preferences WHERE profileId='p1'").use {
        assertTrue(it.moveToFirst())
        assertEquals("ENGLISH_FIRST", it.getString(0))
    }
    db.close()
}
```

- [ ] **Step 2: 跑测试确认 RED**

Run: `./gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.englishlearning.core.storage.AppDatabaseMigrationTest --no-daemon --no-build-cache --console=plain`
Expected: FAIL —— `articles` 表没有 `coveredLemmas` 列（`no such column`）。

- [ ] **Step 3: 扩展领域模型与实体**

`reading/domain/ArticleDisplayMode.kt`：

```kotlin
package com.example.englishlearning.reading.domain

/** 阅读页默认展示方式（spec F2-04）。切换只改变展示，不改写文章。 */
enum class ArticleDisplayMode {
    ENGLISH_FIRST,
    BILINGUAL,
    FULL_TRANSLATION,
}
```

`Article` 追加两个字段（放在 `modelName` 之前，避免破坏既有具名调用点的可读顺序）：

```kotlin
    val coveredLemmas: List<String>,
    val parameterSummary: String,
    val modelName: String?,
```

`ReadingPreference` 追加：

```kotlin
    val displayMode: ArticleDisplayMode = ArticleDisplayMode.ENGLISH_FIRST,
```

`ArticleEntity` 追加：

```kotlin
    val coveredLemmas: String,       // JSON 数组；解析见 RoomArticleRepository
    val parameterSummary: String,
```

`ReadingPreferenceEntity` 追加：

```kotlin
    val displayMode: String,
```

- [ ] **Step 4: 写迁移与仓储映射**

`AppDatabase`：`version = 9`，新增并注册迁移：

```kotlin
val MIGRATION_8_9: Migration =
    object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // ALTER TABLE 加 NOT NULL 列必须带 DEFAULT，否则已有行无法回填。
            db.execSQL("ALTER TABLE `articles` ADD COLUMN `coveredLemmas` TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE `articles` ADD COLUMN `parameterSummary` TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE `reading_preferences` ADD COLUMN `displayMode` TEXT NOT NULL DEFAULT 'ENGLISH_FIRST'")
        }
    }

val MIGRATIONS: Array<Migration> =
    arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9)
```

`RoomArticleRepository` 的映射（lemma 列表用 JSON 存，避免自定义分隔符与词内含分隔符相撞）：

```kotlin
private val json = Json

private fun List<String>.toLemmaJson(): String = json.encodeToString(coveredLemmas)   // 用 buildJsonArray 亦可
private fun String.toLemmaList(): List<String> =
    if (isBlank()) emptyList()
    else runCatching { json.parseToJsonElement(this).jsonArray.map { it.jsonPrimitive.content } }.getOrElse { emptyList() }
```

> `encodeToString` 需要 reified 序列化器；本项目**未启用 serialization 编译器插件**，因此这里改用
> `JsonArray` 手工构造（与 `SeedWordBooksUseCase` 的做法一致），不要引入 `@Serializable`：
> ```kotlin
> private fun List<String>.toLemmaJson(): String = JsonArray(map { JsonPrimitive(it) }).toString()
> ```

`RoomReadingPreferenceRepository` 映射 `displayMode` 用 `ArticleDisplayMode.valueOf(...)`；解析失败时退回 `ENGLISH_FIRST`（老数据或将来删枚举值都不能让阅读页崩）。

- [ ] **Step 5: 跑迁移与仓储测试确认 GREEN**

Run: 同 Step 2 的类 + `:app:connectedDebugAndroidTest -P...class=com.example.englishlearning.reading.RoomArticleRepositoryTest`
Expected: PASS。且 `app/schemas/.../9.json` 已生成。

- [ ] **Step 6: 提交**

```bash
git add app/src/main/java/com/example/englishlearning/reading app/src/main/java/com/example/englishlearning/core/storage app/src/androidTest app/schemas
git commit -m "feat(reading): store generation provenance and article display mode"
```

---

### Task 2: HTTP 传输端口与零依赖实现

**Files:**
- Create: `app/src/main/java/com/example/englishlearning/ai/net/AiHttpTransport.kt`
- Create: `app/src/main/java/com/example/englishlearning/ai/net/UrlConnectionAiHttpTransport.kt`
- Test: `app/src/test/java/com/example/englishlearning/ai/net/UrlConnectionAiHttpTransportTest.kt`

**Interfaces:**
- Produces:
  - `data class AiHttpRequest(val url: String, val headers: Map<String, String>, val body: String, val timeoutSeconds: Int)`
  - `data class AiHttpResponse(val statusCode: Int, val body: String)`
  - `sealed interface AiHttpResult { Responded(AiHttpResponse); NetworkUnavailable; TimedOut; Cancelled; ResponseTooLarge }`
  - `interface AiHttpTransport { suspend fun send(request: AiHttpRequest): AiHttpResult }`
  - `class UrlConnectionAiHttpTransport(private val ioDispatcher: CoroutineDispatcher, private val maxResponseBytes: Int = 512 * 1024) : AiHttpTransport`

- [ ] **Step 1: 写失败测试（JDK 内置 HTTP 服务器，无新依赖）**

```kotlin
package com.example.englishlearning.ai.net

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import java.net.InetSocketAddress
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UrlConnectionAiHttpTransportTest {
    private lateinit var server: HttpServer
    private var port = 0
    private var lastSeenBody = ""
    private var lastSeenAuth = ""

    @BeforeTest fun start() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/ok") { exchange ->
            lastSeenBody = exchange.requestBody.readBytes().decodeToString()
            lastSeenAuth = exchange.requestHeaders.getFirst("Authorization") ?: ""
            respond(exchange, 200, """{"choices":[{"message":{"content":"hi"}}]}""")
        }
        server.createContext("/unauthorized") { respond(it, 401, """{"error":{"message":"bad key"}}""") }
        server.createContext("/redirect") { exchange ->
            exchange.responseHeaders.add("Location", "/ok")
            respond(exchange, 302, "")
        }
        server.createContext("/slow") { exchange -> Thread.sleep(2000); respond(exchange, 200, "late") }
        server.createContext("/huge") { exchange -> respond(exchange, 200, "x".repeat(600 * 1024)) }
        server.start()
        port = server.address.port
    }

    @AfterTest fun stop() = server.stop(0)

    private fun respond(exchange: com.sun.net.httpserver.HttpExchange, code: Int, body: String) {
        val bytes = body.toByteArray()
        exchange.sendResponseHeaders(code, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    private val transport = UrlConnectionAiHttpTransport(Dispatchers.IO)

    private fun request(path: String, timeout: Int = 5) = AiHttpRequest(
        url = "http://127.0.0.1:$port$path",
        headers = mapOf("Content-Type" to "application/json", "Authorization" to "Bearer test-key"),
        body = """{"model":"m"}""",
        timeoutSeconds = timeout,
    )

    @Test fun postsTheBodyAndReturnsTheStatusCodeAndBody() = runTest {
        val result = transport.send(request("/ok"))
        val responded = result as AiHttpResult.Responded
        assertEquals(200, responded.response.statusCode)
        assertTrue(responded.response.body.contains("hi"))
        assertEquals("""{"model":"m"}""", lastSeenBody)
        assertEquals("Bearer test-key", lastSeenAuth)
    }

    @Test fun returnsNon2xxAsRespondedSoTheCallerCanMapIt() = runTest {
        val responded = transport.send(request("/unauthorized")) as AiHttpResult.Responded
        assertEquals(401, responded.response.statusCode)
        assertTrue(responded.response.body.contains("bad key"))
    }

    @Test fun doesNotFollowRedirects() = runTest {
        // 跟随重定向会让攻击者用公网域名把请求引到私网地址，正是 validateEndpoint 要挡的事。
        val responded = transport.send(request("/redirect")) as AiHttpResult.Responded
        assertEquals(302, responded.response.statusCode)
    }

    @Test fun mapsAReadTimeoutToTimedOut() = runTest {
        assertEquals(AiHttpResult.TimedOut, transport.send(request("/slow", timeout = 1)))
    }

    @Test fun mapsAConnectionFailureToNetworkUnavailable() = runTest {
        val dead = AiHttpRequest(url = "http://127.0.0.1:1/nothing", headers = emptyMap(), body = "", timeoutSeconds = 2)
        assertEquals(AiHttpResult.NetworkUnavailable, transport.send(dead))
    }

    @Test fun refusesResponsesOverTheCap() = runTest {
        assertEquals(AiHttpResult.ResponseTooLarge, transport.send(request("/huge")))
    }
}
```

- [ ] **Step 2: 跑测试确认 RED**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.example.englishlearning.ai.net.*" --no-daemon --no-build-cache --console=plain`
Expected: 编译失败 `Unresolved reference 'AiHttpTransport'`。

- [ ] **Step 3: 写端口与实现**

`AiHttpTransport.kt`：只放上面 Interfaces 里的四个声明，**不含实现**（端口与实现分文件，JVM 测试才能只测实现而不引入 Android 依赖）。

`UrlConnectionAiHttpTransport.send`：

```kotlin
override suspend fun send(request: AiHttpRequest): AiHttpResult = withContext(ioDispatcher) {
    var connection: HttpURLConnection? = null
    try {
        connection = (URL(request.url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            // 不跟随重定向：跟随会把「公网域名 → 私网地址」这条被 validateEndpoint 挡掉的路重新打开。
            instanceFollowRedirects = false
            connectTimeout = request.timeoutSeconds * 1000
            readTimeout = request.timeoutSeconds * 1000
            doOutput = true
            request.headers.forEach { (name, value) -> setRequestProperty(name, value) }
        }
        connection.outputStream.use { it.write(request.body.toByteArray(Charsets.UTF_8)) }
        val status = connection.responseCode
        val stream = if (status in 200..299) connection.inputStream else connection.errorStream
        val body = stream?.use { readCapped(it, maxResponseBytes) } ?: ReadOutcome.Empty
        when (body) {
            is ReadOutcome.TooLarge -> AiHttpResult.ResponseTooLarge
            is ReadOutcome.Text -> AiHttpResult.Responded(AiHttpResponse(status, body.value))
        }
    } catch (timeout: SocketTimeoutException) {
        AiHttpResult.TimedOut
    } catch (cancellation: CancellationException) {
        AiHttpResult.Cancelled
    } catch (io: IOException) {
        AiHttpResult.NetworkUnavailable
    } finally {
        connection?.disconnect()
    }
}
```

`readCapped` 逐块读并累计字节数，超过上限立即返回 `TooLarge`（**不要**先 `readBytes()` 再判长度——那已经吃掉了整个响应体，上限就失去意义）。

> 注意 `catch (cancellation: CancellationException)` 必须在 `IOException` **之前**，且协程取消时
> 不要把它吞成 `NetworkUnavailable`——用户离开页面不等于网络坏了。

- [ ] **Step 4: 跑测试确认 GREEN**

Run: 同 Step 2。
Expected: PASS（6 例）。

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/com/example/englishlearning/ai/net app/src/test/java/com/example/englishlearning/ai/net
git commit -m "feat(ai): add a dependency-free HTTP transport port"
```

---

### Task 3: 出站请求构造

**Files:**
- Create: `app/src/main/java/com/example/englishlearning/reading/domain/ArticleGenerationRequest.kt`
- Create: `app/src/main/java/com/example/englishlearning/reading/ArticlePromptPolicy.kt`
- Create: `app/src/main/java/com/example/englishlearning/ai/net/AiChatRequestBuilder.kt`
- Test: `app/src/test/java/com/example/englishlearning/reading/ArticlePromptPolicyTest.kt`
- Test: `app/src/test/java/com/example/englishlearning/ai/net/AiChatRequestBuilderTest.kt`

**Interfaces:**
- Consumes: `validateEndpoint`、`AiProfile`、`AiAdvancedParameters`、`ArticleLengthPolicy.Resolved`、`WordCard`、`ArticleType`、`ArticleLengthTier`
- Produces:
  - ```kotlin
    enum class TranslationLanguage { ZH }
    data class ArticleGenerationRequest(
        val profileId: String,
        val localDate: String,
        val wordBookId: String,
        val articleType: ArticleType,
        val length: ArticleLengthPolicy.Resolved,
        val language: TranslationLanguage,
        val targetCards: List<WordCard>,
        val excludedLemmas: List<String>,   // 已用过、要求模型避开的词
    )
    ```
  - `data class AiPrompt(val system: String, val user: String)`
  - `object ArticlePromptPolicy { fun build(request: ArticleGenerationRequest): AiPrompt }`
  - `object AiChatRequestBuilder { fun joinEndpoint(endpoint: String): Result<String>; fun build(profile: AiProfile, parameters: AiAdvancedParameters, prompt: AiPrompt, apiKey: CharArray): Result<AiHttpRequest> }`

- [ ] **Step 1: 写失败的提示词测试**

```kotlin
class ArticlePromptPolicyTest {
    private fun request(excluded: List<String> = emptyList()) = ArticleGenerationRequest(
        profileId = "p1", localDate = "2026-09-24", wordBookId = "cet4",
        articleType = ArticleType.SCIENCE,
        length = resolveArticleLength("cet4", ArticleLengthTier.STANDARD),
        language = TranslationLanguage.ZH,
        targetCards = listOf(
            WordCard("c1", "cet4", "apple", "/ˈæpl/", "n.", "苹果", inflections = listOf("apples")),
            WordCard("c2", "cet4", "brief", "/briːf/", "adj.", "简短的"),
        ),
        excludedLemmas = excluded,
    )

    @Test fun promptCarriesEveryInputTheSpecRequires() {
        val prompt = ArticlePromptPolicy.build(request())
        assertTrue(prompt.user.contains("apple"), "缺少当天完成词")
        assertTrue(prompt.user.contains("apples"), "缺少词形变化")
        assertTrue(prompt.user.contains("cet4"), "缺少词书")
        assertTrue(prompt.user.contains("科普"), "缺少文章类型")
        assertTrue(prompt.user.contains("180"), "缺少长度下限")   // cet4 STANDARD = 180..300
        assertTrue(prompt.user.contains("300"), "缺少长度上限")
        assertTrue(prompt.user.contains("中文"), "缺少目标语言")
    }

    @Test fun promptListsExcludedLemmasSoARegenerationMovesOn() {
        val prompt = ArticlePromptPolicy.build(request(excluded = listOf("apple")))
        assertTrue(prompt.user.contains("apple"))
    }

    @Test fun promptDemandsJsonWithoutAnyCoordinates() {
        val prompt = ArticlePromptPolicy.build(request())
        // 系统提示必须只要求三个字段，且不得要求模型给高亮位置——坐标一律由系统派生。
        assertTrue(prompt.system.contains("title"))
        assertTrue(prompt.system.contains("english"))
        assertTrue(prompt.system.contains("chinese"))
        assertFalse(prompt.system.contains("highlight", ignoreCase = true))
        assertFalse(prompt.system.contains("offset", ignoreCase = true))
    }

    @Test fun promptIsDeterministicForTheSameInput() {
        assertEquals(ArticlePromptPolicy.build(request()), ArticlePromptPolicy.build(request()))
    }
}
```

- [ ] **Step 2: 写失败的请求体测试**

```kotlin
class AiChatRequestBuilderTest {
    private val profile = AiProfile(
        profileId = "p1", displayName = "d", websiteUrl = "https://x.test",
        endpoint = "https://api.test/v1", model = "gpt-x",
        capabilities = setOf(AiCapability.Text),
        secretReference = SecretReference("ai-profile-p1"),
        advancedParameters = AiAdvancedParameters(temperature = 0.3, topP = 0.9, maxTokens = 512, timeoutSeconds = 20),
    )
    private val prompt = AiPrompt(system = "SYS", user = "USR")

    @Test fun appendsTheChatCompletionsPathToTheEndpoint() {
        assertEquals("https://api.test/v1/chat/completions", AiChatRequestBuilder.joinEndpoint("https://api.test/v1").getOrThrow())
        assertEquals("https://api.test/v1/chat/completions", AiChatRequestBuilder.joinEndpoint("https://api.test/v1/").getOrThrow())
    }

    @Test fun rejectsAnEndpointThatAlreadyCarriesAQueryOrFragment() {
        assertTrue(AiChatRequestBuilder.joinEndpoint("https://api.test/v1?x=1").isFailure)
        assertTrue(AiChatRequestBuilder.joinEndpoint("https://api.test/v1#f").isFailure)
    }

    @Test fun sendsExactlyTwoHeadersAndNothingElse() {
        val request = AiChatRequestBuilder.build(profile, profile.advancedParameters, prompt, "sk-secret".toCharArray()).getOrThrow()
        assertEquals(setOf("Content-Type", "Authorization"), request.headers.keys)
        assertEquals("application/json", request.headers["Content-Type"])
        assertEquals("Bearer sk-secret", request.headers["Authorization"])
    }

    @Test fun neverPutsTheKeyIntoTheBody() {
        val request = AiChatRequestBuilder.build(profile, profile.advancedParameters, prompt, "sk-secret".toCharArray()).getOrThrow()
        assertFalse(request.body.contains("sk-secret"))
    }

    @Test fun bodyMatchesOpenAiChatCompletionsShape() {
        val body = Json.parseToJsonElement(
            AiChatRequestBuilder.build(profile, profile.advancedParameters, prompt, "k".toCharArray()).getOrThrow().body,
        ).jsonObject
        assertEquals("gpt-x", body["model"]!!.jsonPrimitive.content)
        assertEquals(512, body["max_tokens"]!!.jsonPrimitive.int)
        assertEquals(0.3, body["temperature"]!!.jsonPrimitive.double)
        val messages = body["messages"]!!.jsonArray
        assertEquals("system", messages[0].jsonObject["role"]!!.jsonPrimitive.content)
        assertEquals("SYS", messages[0].jsonObject["content"]!!.jsonPrimitive.content)
        assertEquals("user", messages[1].jsonObject["role"]!!.jsonPrimitive.content)
        assertEquals("USR", messages[1].jsonObject["content"]!!.jsonPrimitive.content)
    }

    @Test fun timeoutComesFromTheAdvancedParameters() {
        val request = AiChatRequestBuilder.build(profile, profile.advancedParameters, prompt, "k".toCharArray()).getOrThrow()
        assertEquals(20, request.timeoutSeconds)
    }

    @Test fun failsOnAnEndpointThatFailsTheSecurityPolicy() {
        val hostile = profile.copy(endpoint = "http://192.168.1.9/v1")
        assertTrue(AiChatRequestBuilder.build(hostile, hostile.advancedParameters, prompt, "k".toCharArray()).isFailure)
    }
}
```

- [ ] **Step 3: 跑测试确认 RED**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.example.englishlearning.reading.ArticlePromptPolicyTest" --tests "com.example.englishlearning.ai.net.AiChatRequestBuilderTest" --no-daemon --no-build-cache --console=plain`
Expected: 编译失败 `Unresolved reference 'ArticlePromptPolicy'`。

- [ ] **Step 4: 实现提示词与请求构造**

`ArticlePromptPolicy.build` 的要点（纯字符串拼接，无模板引擎）：

- `system`：声明「只输出 JSON，字段恰好 `title` / `english` / `chinese`；不要输出任何位置、偏移或标记；english 只用英文，chinese 只用中文；不得包含 HTML 或脚本」。**不出现 highlight / offset 字样**。
- `user`：逐行给出 词书 / 类型（用中文 label，如 `SCIENCE → 科普`）/ 目标长度（`length.targetWords` 与 `acceptedWords`）/ 目标语言 / 必须使用的词表（lemma + 词形变化）/ 排除条件（`excludedLemmas`）。
- 词表用固定顺序（`targetCards` 传入顺序），保证同一输入产出同一提示词。

`AiChatRequestBuilder.joinEndpoint`：

```kotlin
fun joinEndpoint(endpoint: String): Result<String> {
    val uri = validateEndpoint(endpoint).getOrElse { return Result.failure(it) }
    if (uri.query != null || uri.fragment != null) {
        return Result.failure(AppErrorException(AppError.InvalidAiConfiguration))
    }
    val base = endpoint.trimEnd('/')
    return Result.success("$base/chat/completions")
}
```

`build`：先 `joinEndpoint`，再用 `JsonObject`/`JsonArray` 手工构造 body（**不用 `@Serializable`**），headers 只放 `Content-Type` 与 `Authorization`。Key 用 `String(apiKey)` 拼进 header 后立即不再持有。

- [ ] **Step 5: 跑测试确认 GREEN**

Run: 同 Step 3。
Expected: PASS（4 + 7 例）。

- [ ] **Step 6: 提交**

```bash
git add app/src/main/java/com/example/englishlearning/reading app/src/main/java/com/example/englishlearning/ai/net app/src/test/java/com/example/englishlearning
git commit -m "feat(reading): build the generation prompt and outbound request"
```

---

### Task 4: 响应解析与保存前质量校验

**Files:**
- Create: `app/src/main/java/com/example/englishlearning/reading/ArticleResponseParser.kt`
- Create: `app/src/main/java/com/example/englishlearning/reading/ArticleQualityPolicy.kt`
- Test: `app/src/test/java/com/example/englishlearning/reading/ArticleResponseParserTest.kt`
- Test: `app/src/test/java/com/example/englishlearning/reading/ArticleQualityPolicyTest.kt`

**Interfaces:**
- Consumes: `ArticleLengthPolicy.Resolved`、`AiFailure`
- Produces:
  - ```kotlin
    data class RawArticle(val title: String, val englishText: String, val chineseText: String)
    object ArticleResponseParser {
        fun parse(httpStatusCode: Int, body: String): Result<RawArticle>   // 失败用 AppErrorException(AiFailure...) 见下
    }
    ```
    失败映射规则（HTTP 先映射，再解析）：

    | 状态码 | 失败 |
    | --- | --- |
    | 401 | `AiFailure.Unauthorized` |
    | 429 | `AiFailure.RateLimited` |
    | 500..599 | `AiFailure.ServerUnavailable` |
    | 200 但体不合规 | `AiFailure.InvalidResponse` |
    | 其他非 2xx | `AiFailure.InvalidResponse` |

  - ```kotlin
    data class ValidatedArticle(val title: String, val englishText: String, val chineseText: String)
    object ArticleQualityPolicy {
        const val MAX_TEXT_CHARS = 20_000
        fun validate(raw: RawArticle, length: ArticleLengthPolicy.Resolved): Result<ValidatedArticle>
    }
    ```
    失败一律 `AppErrorException(AiFailure.InvalidResponse)`（界面文案与操作由 `AiFailure.toUserAction()` 决定，不再新造一套错误枚举）。

- [ ] **Step 1: 写失败的解析测试**

```kotlin
class ArticleResponseParserTest {
    private fun envelope(content: String) =
        """{"choices":[{"message":{"role":"assistant","content":${JsonPrimitive(content)}}}]}"""

    private val good = """{"title":"A Day","english":"Apple is brief.","chinese":"苹果很简短。"}"""

    @Test fun extractsTheInnerJsonFromTheOpenAiEnvelope() {
        val raw = ArticleResponseParser.parse(200, envelope(good)).getOrThrow()
        assertEquals("A Day", raw.title)
        assertEquals("Apple is brief.", raw.englishText)
        assertEquals("苹果很简短。", raw.chineseText)
    }

    @Test fun ignoresAnyCoordinatesTheModelVolunteers() {
        // spec：禁止信任模型提供的高亮坐标。多出来的字段只能被丢掉，绝不能进入领域模型。
        val withOffsets = """{"title":"T","english":"apple","chinese":"苹果","highlights":[{"start":0,"end":5}]}"""
        val raw = ArticleResponseParser.parse(200, envelope(withOffsets)).getOrThrow()
        assertEquals("apple", raw.englishText)
    }

    @Test fun maps401ToUnauthorized() {
        assertEquals(AiFailure.Unauthorized, ArticleResponseParser.parse(401, "{}").exceptionOrNull().appFailure())
    }

    @Test fun maps429ToRateLimited() { assertEquals(AiFailure.RateLimited, ArticleResponseParser.parse(429, "{}").exceptionOrNull().appFailure()) }

    @Test fun maps5xxToServerUnavailable() { assertEquals(AiFailure.ServerUnavailable, ArticleResponseParser.parse(503, "{}").exceptionOrNull().appFailure()) }

    @Test fun mapsAnUnknownStatusToInvalidResponse() { assertEquals(AiFailure.InvalidResponse, ArticleResponseParser.parse(418, "{}").exceptionOrNull().appFailure()) }

    @Test fun rejectsABodyThatIsNotJson() { assertTrue(ArticleResponseParser.parse(200, "not json").isFailure) }

    @Test fun rejectsAnEnvelopeWithoutChoices() { assertTrue(ArticleResponseParser.parse(200, """{"choices":[]}""").isFailure) }

    @Test fun rejectsContentThatIsNotTheRequestedJson() { assertTrue(ArticleResponseParser.parse(200, envelope("Sure! Here is your article:")).isFailure) }

    @Test fun rejectsContentMissingAnyOfTheThreeFields() {
        assertTrue(ArticleResponseParser.parse(200, envelope("""{"title":"T","english":"E"}""")).isFailure)
    }
}
```

> `appFailure()` 是本测试文件里的私有辅助：从 `AppErrorException` 里取出 `AppError` 再断言它是 `AiFailure` 的一员。写成一条小函数，不要在九个测试里各写一遍。

- [ ] **Step 2: 写失败的质量校验测试**

```kotlin
class ArticleQualityPolicyTest {
    private val length = resolveArticleLength("cet4", ArticleLengthTier.STANDARD)   // acceptedWords = 162..330
    private fun raw(
        title: String = "A Day",
        english: String = List(200) { "word" }.joinToString(" "),
        chinese: String = "这是一段中文译文。" .repeat(5),
    ) = RawArticle(title, english, chinese)

    @Test fun acceptsAWellFormedArticle() { assertTrue(ArticleQualityPolicy.validate(raw(), length).isSuccess) }

    @Test fun rejectsABlankTitle() { assertTrue(ArticleQualityPolicy.validate(raw(title = "   "), length).isFailure) }
    @Test fun rejectsABlankEnglishBody() { assertTrue(ArticleQualityPolicy.validate(raw(english = ""), length).isFailure) }
    @Test fun rejectsABlankChineseBody() { assertTrue(ArticleQualityPolicy.validate(raw(chinese = "  "), length).isFailure) }

    @Test fun rejectsAnEnglishBodyThatIsActuallyChinese() {
        assertTrue(ArticleQualityPolicy.validate(raw(english = "这是一段中文。".repeat(20)), length).isFailure)
    }

    @Test fun rejectsAChineseBodyThatIsActuallyEnglish() {
        assertTrue(ArticleQualityPolicy.validate(raw(chinese = "this is english ".repeat(10)), length).isFailure)
    }

    @Test fun rejectsABodyFarShorterThanTheAcceptedRange() {
        assertTrue(ArticleQualityPolicy.validate(raw(english = "too short"), length).isFailure)
    }

    @Test fun rejectsABodyFarLongerThanTheAcceptedRange() {
        assertTrue(ArticleQualityPolicy.validate(raw(english = List(600) { "word" }.joinToString(" ")), length).isFailure)
    }

    @Test fun rejectsOversizedOutput() {
        assertTrue(ArticleQualityPolicy.validate(raw(chinese = "中".repeat(ArticleQualityPolicy.MAX_TEXT_CHARS + 1)), length).isFailure)
    }

    @Test fun rejectsRawHtmlAndScript() {
        // 危险片段必须连同它的分隔符一起判，避免 "conscript" 这类词被误伤。
        assertTrue(ArticleQualityPolicy.validate(raw(title = "<script>alert(1)</script>"), length).isFailure)
        assertTrue(ArticleQualityPolicy.validate(raw(english = "click javascript:void(0) now"), length).isFailure)
        assertTrue(ArticleQualityPolicy.validate(raw(english = "<img src=x onerror=alert(1)>"), length).isFailure)
    }

    @Test fun doesNotFalsePositiveOnInnocentWords() {
        val innocent = raw(english = "The conscript described a scriptwriter's scripted letters.", chinese = "中文" .repeat(1) + "这段译文提到剧本与文字，长度足够通过检查。" .repeat(4))
        assertTrue(ArticleQualityPolicy.validate(innocent, length).isSuccess)
    }

    @Test fun rejectsControlCharacters() {
        assertTrue(ArticleQualityPolicy.validate(raw(english = "ab\u0000cd" + " word".repeat(200)), length).isFailure)
    }
}
```

- [ ] **Step 3: 跑测试确认 RED**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.example.englishlearning.reading.Article*Test" --no-daemon --no-build-cache --console=plain`
Expected: 编译失败 `Unresolved reference 'ArticleResponseParser'`。

- [ ] **Step 4: 实现解析与校验**

`ArticleResponseParser.parse`：
1. 状态码映射（Step 1 的表）优先于解析。
2. `Json.parseToJsonElement(body)` → 失败即 `InvalidResponse`。
3. `choices[0].message.content` → 必须是非空字符串。
4. `content` 里可能被包在 ```` ```json ```` 代码块里：先剥掉围栏再解析（模型常见行为，不剥会让「合格响应」被判成畸形）。
5. 解析出 `JsonObject` 后**只读 `title`/`english`/`chinese` 三个字段**，其余一律忽略（含坐标）。
6. 任一字段缺失或不是字符串 → `InvalidResponse`。

`ArticleQualityPolicy.validate` 的检查项与顺序（**先便宜后昂贵**，便于定位）：

1. 三字段非空白；
2. 长度上限（`MAX_TEXT_CHARS`，逐字段）；
3. 控制字符（除 `\n`、`\t`）；
4. 危险标记：`<script`、`<iframe`、`<img`、`<a `、`javascript:`、`on\w+=`（**带分隔符匹配**，不裸词匹配）；
5. 英文语言判定：`CJK 字符数 / 非空字符数 < 0.1` 且 ASCII 字母占比 `> 0.5`；
6. 中文语言判定：含 CJK 字符且 `CJK / 非空字符数 > 0.2`；
7. 词数落在 `length.acceptedWords` 内（英文按空白切分）。

- [ ] **Step 5: 跑测试确认 GREEN**

Run: 同 Step 3。Expected: PASS。

- [ ] **Step 6: 提交**

```bash
git add app/src/main/java/com/example/englishlearning/reading app/src/test/java/com/example/englishlearning/reading
git commit -m "feat(reading): parse and quality-check generated articles"
```

---

### Task 5: 派生高亮与未覆盖词

**Files:**
- Create: `app/src/main/java/com/example/englishlearning/reading/ArticleHighlightPolicy.kt`
- Test: `app/src/test/java/com/example/englishlearning/reading/ArticleHighlightPolicyTest.kt`

**Interfaces:**
- Consumes: `WordCard`（含 `lemma`、`inflections`）
- Produces:
  - ```kotlin
    data class WordHighlight(val cardId: String, val lemma: String, val matched: String, val start: Int, val end: Int)
    data class ArticleCoverage(val highlights: List<WordHighlight>, val uncoveredLemmas: List<String>)
    object ArticleHighlightPolicy {
        fun derive(englishText: String, cards: List<WordCard>): ArticleCoverage
    }
    ```
    另需一个按 `List<String>` 重载，供阅读页从库里的 lemma 列表重算（隔天重读历史文章时用）：
  - `fun derive(englishText: String, lemmas: List<String>): ArticleCoverage`

- [ ] **Step 1: 写失败的测试**

```kotlin
class ArticleHighlightPolicyTest {
    private fun card(id: String, lemma: String, inflections: List<String> = emptyList()) =
        WordCard(id, "cet4", lemma, "", "", "", inflections = inflections)

    @Test fun matchesCaseInsensitivelyAndKeepsTheOriginalOffsets() {
        val coverage = ArticleHighlightPolicy.derive("An Apple a day.", listOf(card("c1", "apple")))
        val hit = coverage.highlights.single()
        assertEquals(3, hit.start)
        assertEquals(8, hit.end)
        assertEquals("Apple", "An Apple a day.".substring(hit.start, hit.end))
        assertTrue(coverage.uncoveredLemmas.isEmpty())
    }

    @Test fun respectsWordBoundariesSoAppDoesNotMatchInsideApple() {
        val coverage = ArticleHighlightPolicy.derive("apple app grapple", listOf(card("c1", "app")))
        assertTrue(coverage.highlights.isEmpty())
        assertEquals(listOf("app"), coverage.uncoveredLemmas)
    }

    @Test fun matchesInflectionsAndAttributesThemToTheSameCard() {
        val coverage = ArticleHighlightPolicy.derive("apples and apple", listOf(card("c1", "apple", listOf("apples"))))
        assertEquals(2, coverage.highlights.size)
        assertTrue(coverage.highlights.all { it.cardId == "c1" })
    }

    @Test fun highlightsEveryOccurrence() {
        val coverage = ArticleHighlightPolicy.derive("apple apple apple", listOf(card("c1", "apple")))
        assertEquals(3, coverage.highlights.size)
    }

    @Test fun listsMostUncoveredLemmasFirstInInputOrder() {
        val coverage = ArticleHighlightPolicy.derive("only apple here", listOf(card("c1", "apple"), card("c2", "brief"), card("c3", "zebra")))
        assertEquals(listOf("brief", "zebra"), coverage.uncoveredLemmas)
    }

    @Test fun returnsHighlightsSortedByStartOffset() {
        val coverage = ArticleHighlightPolicy.derive("zebra then apple", listOf(card("c2", "apple"), card("c1", "zebra")))
        assertEquals(listOf("zebra", "apple"), coverage.highlights.map { it.lemma })
    }

    @Test fun handlesNonAsciiTextWithoutCrashingOrMatching() {
        val coverage = ArticleHighlightPolicy.derive("这是一段中文。", listOf(card("c1", "apple")))
        assertTrue(coverage.highlights.isEmpty())
        assertEquals(listOf("apple"), coverage.uncoveredLemmas)
    }

    @Test fun doesNotMatchAcrossAHyphenOrApostrophe() {
        val coverage = ArticleHighlightPolicy.derive("apple-shaped, apple's", listOf(card("c1", "apple")))
        assertEquals(2, coverage.highlights.size)
    }

    @Test fun returnsEmptyCoverageForEmptyInputs() {
        assertEquals(ArticleCoverage(emptyList(), emptyList()), ArticleHighlightPolicy.derive("", listOf(card("c1", "apple"))))
        assertEquals(ArticleCoverage(emptyList(), emptyList()), ArticleHighlightPolicy.derive("apple", emptyList()))
    }

    @Test fun isDeterministic() {
        val cards = listOf(card("c1", "apple"), card("c2", "brief"))
        assertEquals(
            ArticleHighlightPolicy.derive("apple and brief", cards),
            ArticleHighlightPolicy.derive("apple and brief", cards),
        )
    }

    @Test fun lemmaListOverloadMatchesTheCardOverload() {
        assertEquals(
            ArticleHighlightPolicy.derive("apple and brief", listOf(card("c1", "apple"), card("c2", "brief"))).highlights.map { it.lemma },
            ArticleHighlightPolicy.derive("apple and brief", listOf("apple", "brief")).highlights.map { it.lemma },
        )
    }
}
```

- [ ] **Step 2: 跑测试确认 RED**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.example.englishlearning.reading.ArticleHighlightPolicyTest" --no-daemon --no-build-cache --console=plain`
Expected: 编译失败 `Unresolved reference 'ArticleHighlightPolicy'`。

- [ ] **Step 3: 实现**

正则用 `\b` + `RegexOption.IGNORE_CASE`，词形变化按长度降序拼进 alternation（**长词优先**，否则 `apple` 会先吃掉 `apples` 的前五个字符，留下 `s`）：

```kotlin
private fun patternFor(variants: List<String>): Regex =
    Regex(variants.sortedByDescending { it.length }.joinToString("|") { Regex.escape(it) }, RegexOption.IGNORE_CASE)
```

匹配时用 `\b` 边界；`-` 与 `'` 在 Java 正则的 `\b` 语义下本身就是边界，所以 `apple-shaped` 与 `apple's` 都能各算一次（测试已锁定）。

重叠处理：把所有卡片的匹配收集成 `(start, end, cardId, lemma, matched)`，按 `start` 升序、`end` 降序排序后**贪心去重**——只保留不与已选中区间重叠的匹配，保证同一段文字不会既标为 `apple` 又标为 `app`。

- [ ] **Step 4: 跑测试确认 GREEN**

Run: 同 Step 2。Expected: PASS（11 例）。

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/com/example/englishlearning/reading app/src/test/java/com/example/englishlearning/reading
git commit -m "feat(reading): derive highlights and uncovered words from the body"
```

---

### Task 6: 生成用例（复用 / 换一篇 / 失败映射）

**Files:**
- Create: `app/src/main/java/com/example/englishlearning/reading/GenerateArticleUseCase.kt`
- Test: `app/src/test/java/com/example/englishlearning/reading/GenerateArticleUseCaseTest.kt`

**Interfaces:**
- Consumes: `AiProfileRepository`、`AiProfileSecretUseCase`、`AiHttpTransport`、`ArticleResponseParser`、`ArticleQualityPolicy`、`ArticleRepository`、`LearningEventRepository`、`WordCardSource`、`ArticlePromptPolicy`、`AiChatRequestBuilder`、`requiredConfirmation`、`ConfirmationRequirement.resolvedBy`
- Produces:
  - ```kotlin
    sealed interface GenerateArticleResult {
        data class Reused(val article: Article) : GenerateArticleResult
        data class Generated(val article: Article) : GenerateArticleResult
        /** 需要用户先给出站确认；界面拿 host 与 payloadKind 去弹确认框。 */
        data class NeedsConfirmation(val host: String, val payloadKind: AiPayloadKind) : GenerateArticleResult
        data class Failed(val failure: AiFailure) : GenerateArticleResult
        data class NotConfigured(val reason: NotConfiguredReason) : GenerateArticleResult
    }
    enum class NotConfiguredReason { NoProfile, NoKey, ProfileUnreadable }
    ```
  - ```kotlin
    class GenerateArticleUseCase(
        private val profiles: AiProfileRepository,
        private val secrets: AiProfileSecretUseCase,
        private val transport: AiHttpTransport,
        private val articles: ArticleRepository,
        private val events: LearningEventRepository,
        private val cards: WordCardSource,
        private val ids: ArticleIdFactory,
        private val clock: () -> Instant,
    ) {
        suspend fun findReusable(request: ArticleGenerationRequest): Result<Article?>
        suspend fun generate(
            request: ArticleGenerationRequest,
            confirmedTextHost: String?,
        ): GenerateArticleResult
    }
    ```
  - `interface ArticleIdFactory { fun newId(): String }`（仿 `AiProfileIdFactory`，放在 `reading/`）

- [ ] **Step 1: 写失败的用例测试**（JVM，全部注假依赖）

测试夹具用可记录调用的假实现：`FakeTransport`（记录被调用与否、按脚本返回 `AiHttpResult`）、`FakeArticleRepository`（内存 `MutableList`，`saveNewVersion` 复刻 max+1 语义）、`FakeEventRepository`（`completedCardIds` 返回固定集合）、`FakeWordCardSource`、`FakeProfileRepository`、`FakeSecrets`。

必须覆盖的用例（每条都断言**行为**，不只是返回值）：

| 测试名 | 断言 |
| --- | --- |
| `reusesTheStoredArticleWithoutCallingTheAi` | 已存在同键文章时返回 `Reused`，且 `FakeTransport.callCount == 0` |
| `generatesAndStoresANewArticleWhenNothingMatches` | 无同键文章 → `Generated`，落库后能用 `findLatest` 读回，`callCount == 1` |
| `regeneratingSavesANewVersionAndKeepsTheOldOneReadable` | 第二次生成 → `version == 2`，且 `version == 1` 的那篇仍能读到 |
| `asksForConfirmationBeforeTheFirstTextCallToAHost` | `confirmedTextHost = null` → 返回 `NeedsConfirmation(host)`，且 `callCount == 0`（**未确认就不许发请求**） |
| `proceedsOnceTheHostIsConfirmed` | `confirmedTextHost = host` → `Generated`，`callCount == 1` |
| `asksAgainWhenTheEndpointHostChanged` | 确认的是 `a.test`，Profile 现在指向 `b.test` → `NeedsConfirmation("b.test")`，`callCount == 0` |
| `reportsNotConfiguredWhenNoProfileExists` | → `NotConfigured(NoProfile)`，`callCount == 0` |
| `reportsNotConfiguredWhenTheProfileHasNoStoredKey` | → `NotConfigured(NoKey)`，`callCount == 0` |
| `maps401ToUnauthorizedAndStoresNothing` | → `Failed(Unauthorized)`，仓库仍为空（**失败不得写入伪造文章**） |
| `maps429ToRateLimited` / `mapsTimeoutToTimeout` / `mapsNetworkFailureToNetworkUnavailable` | 同上，且仓库仍为空 |
| `mapsMalformedContentToInvalidResponseAndStoresNothing` | 传输返回 200 + 垃圾体 → `Failed(InvalidResponse)`，仓库仍为空 |
| `neverStoresTheKeyInTheArticle` | 生成成功后，落库文章的每个字符串字段都不含测试密钥 |
| `requestPromptUsesTheCardsCompletedToday` | 生成的提示词含 `completedCardIds` 对应的 lemma，且不含未完成的词 |
| `excludesPreviouslyUsedWordsOnRegeneration` | 换一篇时 `excludedLemmas` 含上一篇的 `coveredLemmas` |
| `recordsAGenerationParameterSummaryWithoutTheKey` | 落库文章的 `parameterSummary` 含 `temperature`/`max_tokens`/模型名，且不含密钥 |

- [ ] **Step 2: 跑测试确认 RED**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.example.englishlearning.reading.GenerateArticleUseCaseTest" --no-daemon --no-build-cache --console=plain`
Expected: 编译失败 `Unresolved reference 'GenerateArticleUseCase'`。

- [ ] **Step 3: 实现**

`findReusable`：直接委托 `articles.findLatest(profileId, localDate, activeWordBookId, articleType, lengthTier)`。

`generate` 的编排顺序（顺序本身是需求，写注释说明不可重排）：

1. 取 Profile（无 → `NotConfigured(NoProfile)`，读失败 → `NotConfigured(ProfileUnreadable)`）。
2. 取 Key（无 → `NotConfigured(NoKey)`）。
3. **`requiredConfirmation(profile, AiPayloadKind.Text, confirmedTextHost)`** → `Required` 则返回 `NeedsConfirmation(host)`，**在发请求之前**。
4. 收集当天完成词：`events.completedCardIds(planId)` → `cards.cards(ids)` → `targetCards`。`planId` 由请求携带的 `localDate`+`profileId` 经 `TodayPlanRepository.find` 取（读不到就用空词表并把 `excludedLemmas` 一并清空——**空词表也要能生成通用短文，不能因此判失败**）。
5. `ArticlePromptPolicy.build` → `AiChatRequestBuilder.build`。
6. `transport.send` → 把 `AiHttpResult` 映射成 `AiFailure`（`TimedOut→Timeout`、`NetworkUnavailable→NetworkUnavailable`、`Cancelled→Cancelled`、`ResponseTooLarge→InvalidResponse`）。
7. `ArticleResponseParser.parse` → `ArticleQualityPolicy.validate`。
8. `ArticleHighlightPolicy.derive`（只为拿到 `coveredLemmas` 的稳定顺序与未覆盖词，**不落坐标**）。
9. `articles.saveNewVersion(...)` → `Generated`。
10. Key 用 `CharArray` 传递，用完在 `finally` 里 `fill('\u0000')`。

`parameterSummary` 形如：`"model=gpt-x temperature=0.3 top_p=0.9 max_tokens=512 timeout_seconds=20 template=default-reading-v1"`。**绝不拼入 endpoint 与 Key**。

- [ ] **Step 4: 跑测试确认 GREEN**

Run: 同 Step 2。Expected: PASS。

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/com/example/englishlearning/reading app/src/test/java/com/example/englishlearning/reading
git commit -m "feat(reading): generate articles with reuse and regenerate semantics"
```

---

### Task 7: 阅读页（F2-04）

**Files:**
- Create: `app/src/main/java/com/example/englishlearning/ui/ArticleReadingViewModel.kt`
- Create: `app/src/main/java/com/example/englishlearning/ui/ArticleReadingScreen.kt`
- Test: `app/src/test/java/com/example/englishlearning/ui/ArticleReadingViewModelTest.kt`
- Test: `app/src/androidTest/java/com/example/englishlearning/ui/ArticleReadingScreenTest.kt`

**Interfaces:**
- Consumes: `ArticleCoverage`、`ArticleDisplayMode`、`WordCard`、`ReadingPreferenceRepository`
- Produces:
  - ```kotlin
    data class ArticleReadingUiState(
        val article: Article,
        val mode: ArticleDisplayMode,
        val translationExpanded: Boolean,
        val highlights: List<WordHighlight>,
        val uncoveredLemmas: List<String>,
    )
    class ArticleReadingViewModel(...) : ViewModel() {
        val uiState: StateFlow<ArticleReadingUiState?>
        fun setMode(mode: ArticleDisplayMode)
        fun toggleTranslation()
        fun cardFor(cardId: String): WordCard?     // 点词开词卡详情用；未命中返回 null
    }
    ```
  - `fun ArticleReadingScreen(state: ArticleReadingUiState?, onBack: () -> Unit, onOpenCard: (WordCard) -> Unit, onModeChange: (ArticleDisplayMode) -> Unit, onToggleTranslation: () -> Unit, onOpenDictionaryPlaceholder: () -> Unit, onOpenPronunciationPlaceholder: () -> Unit, modifier: Modifier = Modifier)`

- [ ] **Step 1: 写失败的 ViewModel 测试**

| 测试名 | 断言 |
| --- | --- |
| `defaultsToTheStoredDisplayMode` | 偏好是 `BILINGUAL` 时初始 `mode` 是 `BILINGUAL` |
| `englishFirstHidesTheTranslationUntilToggled` | `ENGLISH_FIRST` 下 `translationExpanded == false`，`toggleTranslation()` 后为 `true` |
| `fullTranslationStartsExpanded` | `FULL_TRANSLATION` 下初始 `translationExpanded == true` |
| `changingTheModePersistsThePreference` | `setMode` 后偏好仓储收到新值 |
| `changingTheModeDoesNotRewriteTheArticleText` | `setMode` 前后 `article.englishText` 与 `chineseText` 逐字不变 |
| `derivesHighlightsFromTheStoredLemmasNotFromTodaysPlan` | 用文章的 `coveredLemmas` 派生，即使今天的计划已换词，高亮仍与原文一致 |
| `exposesTheUncoveredLemmas` | 未覆盖词列表来自 `ArticleCoverage` |

- [ ] **Step 2: 写失败的 Compose 测试**

覆盖：三种显示模式各自的可见性（`ENGLISH_FIRST` 隐藏译文、`BILINGUAL` 显示但可折叠、`FULL_TRANSLATION` 默认展开）；高亮词可点击并回调 `onOpenCard`；未覆盖词列表渲染；点未覆盖词（无词卡）走 `onOpenDictionaryPlaceholder`；发音入口走 `onOpenPronunciationPlaceholder`；正文里的 `<script>` 字样以**纯文本**出现而不是被执行/丢弃（用 `assertTextContains` 断言它原样显示为文字）。测试 tag：`article_reading_screen`、`article_title`、`article_english`、`article_translation`、`article_translation_toggle`、`article_uncovered`、`article_mode_{english_first|bilingual|full_translation}`、`article_pronunciation`。

- [ ] **Step 3: 跑测试确认 RED**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.example.englishlearning.ui.ArticleReadingViewModelTest" --no-daemon --no-build-cache --console=plain`
Expected: 编译失败。

- [ ] **Step 4: 实现**

渲染要点：

- 正文用**单个 `Text` + `AnnotatedString`** 分段渲染：普通段直接 `append`，高亮段 `withStyle(color = 薄荷强调色, fontWeight = Bold)` 并挂 `ClickableText`/`LinkAnnotation` 风格的回调。**不要**用 `buildAnnotatedString` 拼 HTML，也不要用 WebView。
- 高亮区间来自 `ArticleCoverage` 的 `(start, end)`；渲染前按 `start` 升序**校验区间合法**（`0 <= start < end <= text.length`）并对越界区间直接丢弃，避免一处脏数据让整页崩。
- 未覆盖词列表用 `FlowRow` 或 `LazyRow` 排 chips；点击时若 `cardFor` 命中就 `onOpenCard`，否则 `onOpenDictionaryPlaceholder`。
- 显示模式用三段 `SegmentedButton`/`FilterChip`。

- [ ] **Step 5: 跑测试确认 GREEN**

Run: 同 Step 3；再跑 `:app:connectedDebugAndroidTest -P...class=com.example.englishlearning.ui.ArticleReadingScreenTest`。
Expected: PASS。

- [ ] **Step 6: 提交**

```bash
git add app/src/main/java/com/example/englishlearning/ui app/src/test/java/com/example/englishlearning/ui app/src/androidTest/java/com/example/englishlearning/ui
git commit -m "feat(reading): add the article reading screen"
```

---

### Task 8: 生成入口、出站确认与失败界面

**Files:**
- Modify: `app/src/main/java/com/example/englishlearning/ui/ReadingAccessScreen.kt`
- Modify: `app/src/main/java/com/example/englishlearning/ui/AppScreen.kt`
- Modify: `app/src/main/java/com/example/englishlearning/MainActivity.kt`
- Modify: `app/src/androidTest/java/com/example/englishlearning/ui/ReadingAccessScreenTest.kt`
- Modify: `app/src/androidTest/java/com/example/englishlearning/ui/AppScreenTest.kt`

**Interfaces:**
- Consumes: `GenerateArticleResult`、`AiFailure` + `AiFailureUiText` + `UserAction`、`AiPayloadKind`
- Produces: `ReadingAccessScreen` 新增参数 `state`（扩展为含生成态的 `ReadingAccessUiState`）、`onGenerate: (ArticleType, ArticleLengthTier) -> Unit`、`onOpenArticle: () -> Unit`、`onConfirmOutbound: (Boolean) -> Unit`、`onOpenAiProfiles: () -> Unit`

- [ ] **Step 1: 更新受影响的既有测试（先 RED）**

`ReadingAccessScreenTest` 里原先断言「本阶段不会发起网络生成」的用例必须改为断言新文案。`AppScreenTest` 增加「阅读栏点生成 → 进阅读页」的路径。

- [ ] **Step 2: 实现界面**

要点：

- 删掉 `ReadingAccessScreen` 里 `"文章已解锁", "选择文章类型；本阶段不会发起网络生成。"` 这句**已经变成假话**的文案（这正是项目里反复踩过的「文案说谎」缺陷）。
- 已解锁时：类型选择 + 长度档选择 + 「生成文章」按钮；按钮下方一行成本提示：**「每次生成都会真实调用你配置的 AI 服务并可能产生费用」**。
- 已存在同键文章时，按钮改为「阅读今天的文章」，旁边给一个次要操作「换一篇」——换一篇前弹**确认对话框**，内容含成本提示（满足 spec「换一篇必须由用户主动触发」+「显示成本提示」）。
- `NeedsConfirmation` → 弹确认对话框，正文包含 **host** 与「你的文本与该服务的密钥将发送给 <host>」；确认后把 host 回传（对应 `confirmedTextHost`），取消则不发请求。
- `Failed(failure)` → 显示 `failure.uiText.message`，按钮文案用 `failure.toUserAction().label`；`Unauthorized`/`NotConfigured` 的按钮跳到 AI 配置页（`showAiProfiles = true`）。
- `Cancelled` 不显示错误横幅（用户自己取消的，不该弹红字）。
- 生成中禁用按钮并显示进度；**失败后今日学习完成状态与今日计划不受影响**（用例本身不写今日计划，界面也不得因失败清任何状态）。

- [ ] **Step 3: AppScreen 接线**

- 新增 `var showArticle by rememberSaveable(state.profile.id) { mutableStateOf(false) }`，加进 `overlayOpen`。
- `BackHandler` **按优先级从低到高声明**：`showArticle` 的 handler 声明在阅读栏 handler 之后（后声明者优先），否则返回键会关错层。已有注释的排序规则照旧扩写。
- 点词卡：复用既有 `CardDetailScreen` 分支，把选中的 `WordCard` 存进状态。
- `MainActivity` 挂 `articleReadingViewModel`。

- [ ] **Step 4: 跑测试确认 GREEN**

Run: `./gradlew.bat :app:connectedDebugAndroidTest -P...class=com.example.englishlearning.ui.ReadingAccessScreenTest -P...class=com.example.englishlearning.ui.AppScreenTest --no-daemon --no-build-cache --console=plain`
Expected: PASS。

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/com/example/englishlearning/ui app/src/main/java/com/example/englishlearning/MainActivity.kt app/src/androidTest
git commit -m "feat(reading): add generation entry, outbound confirmation and failure actions"
```

---

### Task 9: Hilt 接入、网络权限与端到端验收

**Files:**
- Modify: `app/src/main/java/com/example/englishlearning/di/AppModule.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Create: `docs/decisions/2026-09-24-article-generation-and-display.md`
- Create: `docs/verification/f2-03-04/README.md`

**Interfaces:**
- Produces: `AiHttpTransport`、`GenerateArticleUseCase`、`ArticleIdFactory`、`ArticleReadingViewModel` 的 Hilt provider

- [ ] **Step 1: 加 `INTERNET` 权限**

```xml
<uses-permission android:name="android.permission.INTERNET" />
```

> 这是本应用**第一次**申请网络权限。它必须与「出站确认」同时在位：权限让请求成为可能，确认框让用户知情。
> 不允许顺手加 `ACCESS_NETWORK_STATE` 等本轮用不到的权限。

- [ ] **Step 2: 补 Hilt provider 并核对接入点**

在 `AppModule` 加：`AiHttpTransport`（`UrlConnectionAiHttpTransport(ioDispatcher)`）、`ArticleIdFactory.Random`、`GenerateArticleUseCase`、`ArticleReadingViewModel` 所需的仓储注入。

**「类存在」不等于「能力存在」**（F2-02 的教训）：逐个核对 provider、`MainActivity` 的 `hiltViewModel` 收集、`AppScreen` 的渲染分支三处都在位，再进入验证。

- [ ] **Step 3: 全量测试**

Run: JVM 全量 + 真机全量 `am instrument`。
Expected: 全绿。记录精确计数与基线差。

- [ ] **Step 4: 请用户在手机上录入真实密钥**

**这一步必须由用户完成**（本轮已确认的验证方式）。给出精确路径：设置 → AI 服务与密钥 → 新增或编辑配置 → 填入真实 Endpoint / 模型 / 密钥 → 保存。**不要让用户把密钥发到聊天里**。

- [ ] **Step 5: 真机端到端走查**

按既有方式取证（`adb install -r -t` + 走查脚本，**禁用 `connectedDebugAndroidTest`**）：

1. 阅读栏 → 类型与长度 → 生成前成本提示可见；
2. 出站确认框展示**真实域名**；
3. 确认为「取消」时**不发请求**（用 `logcat` 或界面状态证明）；
4. 确认为「是」时生成成功，阅读页显示标题/英文/中文；
5. 派生高亮与未覆盖词非空且与正文位置对得上；
6. 退出再进 → 复用同一篇且**未再产生请求**；
7. 「换一篇」→ 版本 +1，且历史里旧版本仍可读；
8. 断网后 → 已缓存文章仍可读；生成给出「检查网络」而非伪造内容。

数据完整性按既有约定做**三文件 MD5 四时点比对**（db / `-wal` / `-shm`，必须 `MSYS_NO_PATHCONV=1`）。注意本轮与以往不同：**成功生成会真实写入文章行**，所以 MD5 **预期会变**——必须如实说明哪一次变化是生成导致的正常写入，而不是假装一致。首次写库前先取基线。

- [ ] **Step 6: 写决策记录与验收文档**

`docs/decisions/2026-09-24-article-generation-and-display.md` 四组「候选方案 vs 决定」：

1. **HTTP 实现**：零依赖 `HttpURLConnection` + JDK `HttpServer` 测试 / 引入 OkHttp + MockWebServer → 选前者（项目一贯不为便利引依赖，且 JDK 内置服务器足以覆盖真实 HTTP 行为）。
2. **高亮来源**：信任模型返回的坐标 / 系统从正文重新派生 → 选后者（spec 明确要求）。
3. **高亮持久化**：落库坐标 / 只落 lemma 列表、渲染时重算 → 选后者（隔天重读历史文章仍要对得上，且坐标是模型的产物不该入库）。
4. **成本护栏**：设每日次数上限 + 历史容量上限 / 暂不设上限只给提示 → 选后者（用户 2026-09-24 决定），**记为有意偏差**并写明后续补上限时不需要改领域模型。

`docs/verification/f2-03-04/README.md` 逐条对照 AC2-01~AC2-06，写清：精确计数、设备、APK md5、三文件 MD5 各时点（含说明哪次变化来自真实生成）、哪些条目是**契约级**验证（未真机触达）、以及所有已知限制。**不要抄当前 HEAD**。

- [ ] **Step 7: 提交并推送**

```bash
git add app/src/main/AndroidManifest.xml app/src/main/java/com/example/englishlearning/di docs
git commit -m "feat(reading): wire article generation and record its verification"
git push
```

推送后按约定逐字核对 `git rev-parse HEAD` 与 `ls-remote origin <branch>`。

---

## 不在本轮范围（必须先说清楚，避免验收时误判）

- **端侧 TTS/OCR**：spec 明确排除。F2-04 的「发音入口」与「离线词典入口」本轮只做**诚实的入口**：命中今天计划内的词 → 打开既有词卡详情页（离线可用，数据来自 `WordCardSource`）；未命中或点发音 → 显示「后续版本提供」（沿用项目既有的未实现标注方式），**不制造点开就坏的假功能**。
- **真实词典数据**：`WordCardSource` 目前是 `PlaceholderWordCardSource`，词书内容许可仍未闭合。
- **每日重生成上限与历史保留上限**：见 Global Constraints 第 4 条的用户决定。
- **图片能力（Vision）与出站图片确认**：本轮只走文本链路。
- **`GET /models` 模型列表与「测试连接」**：仍属未做项。

## Self-review

**1. Spec 覆盖**

| Spec 要求 | 落点 |
| --- | --- |
| F2-03 生成请求含完成词/词书/类型/长度/语言/排除条件 | Task 3（`ArticlePromptPolicy`）+ Task 6 第 4 步 |
| F2-03 返回含标题/英文/中文 | Task 4（`ArticleResponseParser`） |
| F2-03 系统派生命中位置与未覆盖词，禁止信任模型坐标 | Task 5 + Task 4 的 `ignoresAnyCoordinatesTheModelVolunteers` |
| F2-03 保存前校验（字段/非空/语言/长度/大小/危险富文本/内容安全） | Task 4（`ArticleQualityPolicy`，7 项检查） |
| F2-03 复用同一结果 + 用户主动换一篇 + 新版本已读 + 记录生成时间/模型名/参数摘要 | Task 6（`reusesTheStoredArticleWithoutCallingTheAi`、`regeneratingSavesANewVersionAndKeepsTheOldOneReadable`、`recordsAGenerationParameterSummaryWithoutTheKey`） |
| F2-03 成本提示 | Task 8 第 2 步 |
| F2-04 原文 / 可展开译文 / 高亮 / 未覆盖词 / 词典与发音入口 | Task 7 |
| F2-04 默认显示模式可设置，切换不改写文章 | Task 7（`changingTheModeDoesNotRewriteTheArticleText`） |
| F2-04 安全文本渲染，禁止 HTML/脚本/外链 | Task 4 校验 + Task 7 的 `AnnotatedString` 渲染与 Compose 测试 |
| AC2-01 未解锁时入口不可用并说明原因 | Task 8（复用既有 `ReadingAccess`）+ 既有 `ReadingAccessScreenTest` |
| AC2-02 生成成功后含标题/英文/中文/派生高亮/未覆盖词 | Task 6 + Task 7 + Task 9 Step 5 |
| AC2-03 同日同类型同长度复用；换一篇新版本；旧版本可读 | Task 6 |
| AC2-04 未配置/HTTP/私网/401/429/超时/畸形 → 可操作错误且学习状态保持 | Task 3（Endpoint 拒绝）+ Task 4（状态码映射）+ Task 6（失败不落库）+ Task 8（错误界面与操作） |
| AC2-05 断网后已缓存可读、不可生成、不损坏今日计划 | Task 7（纯本地读取）+ Task 6（失败不写库）+ Task 9 Step 5 第 8 项 |
| AC2-06 只存非敏感字段与脱敏诊断 | Task 1（列设计）+ Task 6（`parameterSummary` 不含 Key）+ Task 9 Step 5 扫描 |
| F2-06 三种来源与来源元数据随文章保存 | F2-06 计划 Task B（`ArticleSource` 联合类型 + Room 9→10） |
| F2-06 来源白名单，禁止任意 URL 抓取 | F2-06 计划 Task C（`ArticleSourceRegistry`） |
| F2-06 署名与原文链接展示，不自动打开 | F2-06 计划 Task E + 本计划 Task 7 |
| F2-06 用户导入不联网且用户自负其责 | F2-06 计划 Task D + Task F（界面说明） |
| F2-06 三来源共用同一管线，不分叉 | 本计划 Task 4 / 5 / 6 / 7 直接复用，实现中不存在按来源分支的展示链路 |
| AC2-07 来源标识展示（不含 Key / Endpoint） | F2-06 计划 Task E |
| AC2-08 白名单外 URL 被拒绝且不发请求 | F2-06 计划 Task C |
| AC2-09 导入内容走不可信输入校验 | F2-06 计划 Task D |
| AC2-10 抓取 / 导入失败保留今日完成状态 | F2-06 计划 Task C / D 的失败分支 + 本计划 Task 8 |

**2. Placeholder 扫描**：无 TBD/TODO；每个测试步骤都给了具体断言或可直接落地的用例名与期望行为；实现步骤给了关键代码或明确的算法与顺序。Task 6、Task 7 的测试以「用例名 + 断言」表列而非全量代码，是因为这两个文件的测试夹具较长且模式与 Task 2~5 一致——**夹具必须用假实现，不得用 `mockk` 造出「可被任意调用」的宽松桩**，否则「未确认就不发请求」这类关键断言会失去意义。

**3. 类型一致性**：`AiHttpRequest`/`AiHttpResponse`/`AiHttpResult`（Task 2）在 Task 3、Task 6 使用同一组名字；`ArticleGenerationRequest`/`TranslationLanguage`（Task 3）在 Task 6 复用；`RawArticle`（Task 4）字段名 `title`/`englishText`/`chineseText` 在 Task 4、Task 6 一致；`WordHighlight`/`ArticleCoverage`（Task 5）字段名在 Task 6、Task 7 一致；`GenerateArticleResult`/`NotConfiguredReason`（Task 6）在 Task 8 一致；Task 1 的 `Article.coveredLemmas` 是 `List<String>` 而实体是 `String` 的 JSON，映射只在 `RoomArticleRepository` 一处，未在别处重复。
