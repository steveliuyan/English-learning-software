# F2-01 阅读访问与文章状态实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在阶段 1 的 TodayPlan 解锁状态之上，建立本地优先的文章访问、类型偏好、长度解析、版本复用与离线历史阅读闭环，不在本计划接入真实 AI 网络请求。

**Architecture:** 文章是独立的本地只读内容实体，按 `profileId + localDate + activeWordBookId + articleType + lengthTier + version` 保存；成功版本不可覆盖，当前版本通过索引查询。TodayPlan 只提供当天解锁判定和缺口原因，文章仓储负责历史读取与版本复用。AI 生成由后续 F2-02/F2-03 接入，本计划先提供可测试的文章写入/读取接口和 UI 状态模型。

**Tech Stack:** Kotlin、Jetpack Compose、Room、现有 Repository/UseCase/ViewModel/Compose 测试框架；不新增网络库、不新增图片/富文本库、不引入 DataStore。

**Spec:** `docs/specs/02-reading-and-ai-content.md` F2-01、AC2-01、AC2-03、AC2-05；`AGENTS.md` 本地优先、AI 输出不可信与安全约束。

## Global Constraints

- 仅 TodayPlan 已解锁时允许当天文章生成入口；未解锁必须显示新增/复习缺口，且不得发起生成。
- 历史已保存文章任意日期离线可读；断网、无 Key 或生成失败不得修改今日计划完成状态。
- 文章类型仅允许 `NEWS`、`STORY`、`SCIENCE`、`WORKPLACE` 四项；未知枚举值拒绝读取/写入，不静默降级。
- 文章长度仅允许 `SHORT`、`STANDARD`、`LONG` 三档；禁止任意用户输入词数。
- 长度解析优先级固定为：用户显式档位 > 词书等级默认 > 产品默认。
- 本计划不发起 HTTP 请求；AI Profile、SecretStore、Endpoint 安全校验和出站确认属于后续 F2-02。
- 不信任模型提供的高亮坐标；本计划只保存安全纯文本字段，禁止 HTML、脚本和自动外链。
- 所有新增数据库结构必须更新 Room schema、迁移和 `gradle.lockfile`（如依赖未变则锁文件不变）。

## 锁定的 F2-01 产品决策

### 文章长度

长度档位由 `ArticleLengthTier` 表示，下面是**目标词数范围**与**验收容差**：

| 词书等级 | SHORT | STANDARD（默认） | LONG |
|---|---:|---:|---:|
| 小学 / 初中 | 50–80 | 80–120 | 120–180 |
| 高中 | 80–120 | 120–180 | 180–260 |
| 四级 / 六级 / 考研 | 120–200 | 180–300 | 300–420 |

校验容差为目标区间上下各 10%；实际校验使用闭区间 `floor(min * 0.9)..ceil(max * 1.1)`。长度档位不是任意词数输入。

### 默认类型与偏好

- 默认文章类型：`STORY`（首次使用时使用，不依赖外部设置存储）。
- 用户选择的默认类型按 profile 保存；文章类型仅影响未来生成，不改写既有文章。
- 若当前 profile 没有偏好记录，解析为 `STORY`。

### 版本与历史

- 同一 `profileId + localDate + activeWordBookId + articleType + lengthTier` 下，存在成功版本时默认复用最新版本。
- 用户主动「换一篇」才创建 `version = previousMax + 1`；旧版本永远可读。
- 每日主动换一篇上限为 3 次（首次生成不计入换篇次数）；达到上限显示可操作的“明日再试/阅读历史”状态，不创建伪版本。
- 历史文章不自动清理；本阶段不做上限删除，后续历史统计/备份阶段另行设计迁移策略。
- 文章生成失败不写入成功文章表，不推进版本号，不修改 TodayPlan。

## 文件结构

- Create: `app/src/main/java/com/example/englishlearning/reading/domain/ArticleType.kt` — 四种文章类型。
- Create: `app/src/main/java/com/example/englishlearning/reading/domain/ArticleLengthTier.kt` — 三档长度、词书等级默认解析和容差。
- Create: `app/src/main/java/com/example/englishlearning/reading/domain/Article.kt` — 安全纯文本领域模型、版本与生成元数据。
- Create: `app/src/main/java/com/example/englishlearning/reading/domain/ReadingAccess.kt` — 当天入口状态与缺口原因。
- Create: `app/src/main/java/com/example/englishlearning/reading/ArticleLengthPolicy.kt` — 显式档位优先级、默认区间、范围校验。
- Create: `app/src/main/java/com/example/englishlearning/reading/ArticleRepository.kt` — 历史读取、复用最新版本、保存新版本、类型偏好接口。
- Create: `app/src/main/java/com/example/englishlearning/core/storage/entity/ArticleEntity.kt` — Room 文章版本表。
- Create: `app/src/main/java/com/example/englishlearning/core/storage/entity/ReadingPreferenceEntity.kt` — profile 的默认类型与可选长度档位。
- Create: `app/src/main/java/com/example/englishlearning/core/storage/dao/InternalArticleDao.kt` — 版本查询和写入 DAO。
- Create: `app/src/main/java/com/example/englishlearning/core/storage/dao/InternalReadingPreferenceDao.kt` — 偏好 DAO。
- Modify: `app/src/main/java/com/example/englishlearning/core/storage/AppDatabase.kt` — 注册表、版本升级与迁移。
- Modify: `app/src/main/java/com/example/englishlearning/di/AppModule.kt` — 提供仓储。
- Create: `app/src/main/java/com/example/englishlearning/reading/RoomArticleRepository.kt` — Room 与领域模型转换、复用/版本不变量。
- Create: `app/src/main/java/com/example/englishlearning/reading/ReadingAccessUseCase.kt` — TodayPlan 解锁与入口原因。
- Create: `app/src/test/java/com/example/englishlearning/reading/ArticleLengthPolicyTest.kt` — 长度边界、优先级、容差。
- Create: `app/src/test/java/com/example/englishlearning/reading/RoomArticleRepositoryTest.kt` — 版本复用、换篇、历史离线读取。
- Create: `app/src/test/java/com/example/englishlearning/reading/ReadingAccessUseCaseTest.kt` — 严格模式缺口与解锁状态。
- Create: `app/src/androidTest/java/com/example/englishlearning/core/storage/AppDatabaseArticleMigrationTest.kt` — Room 迁移后既有数据保留与新表可用。
- Create: `app/src/androidTest/java/com/example/englishlearning/ui/ReadingAccessScreenTest.kt` — 未解锁禁用入口、已解锁类型入口、历史文章入口。
- Create: `docs/decisions/2026-09-22-f2-01-reading-state.md` — 本计划锁定的产品与数据决策。

## Interfaces

### Domain types

```kotlin
enum class ArticleType { NEWS, STORY, SCIENCE, WORKPLACE }
enum class ArticleLengthTier { SHORT, STANDARD, LONG }

data class Article(
    val articleId: String,
    val profileId: String,
    val localDate: String,
    val activeWordBookId: String,
    val articleType: ArticleType,
    val lengthTier: ArticleLengthTier,
    val version: Int,
    val title: String,
    val englishText: String,
    val chineseText: String,
    val generatedAtEpochMillis: Long,
    val modelName: String?,
)
```

```kotlin
sealed interface ReadingAccess {
    data class Unlocked(val planId: String) : ReadingAccess
    data class Locked(val missingNew: Int, val missingDue: Int, val strict: Boolean) : ReadingAccess
}
```

### Repository contract

```kotlin
interface ArticleRepository {
    suspend fun findLatest(
        profileId: String,
        localDate: String,
        activeWordBookId: String,
        articleType: ArticleType,
        lengthTier: ArticleLengthTier,
    ): Result<Article?>

    suspend fun findHistory(profileId: String): Result<List<Article>>

    suspend fun saveNewVersion(article: Article): Result<Article>

    suspend fun getPreference(profileId: String): Result<ReadingPreference>
    suspend fun savePreference(preference: ReadingPreference): Result<Unit>
}
```

`saveNewVersion` 必须在 DAO 事务中计算同一复用键的最大版本并插入 `max + 1`；传入的 `version` 不作为可信版本来源。失败时不写入半条记录。

## Task 1: 锁定决策与领域长度策略

**Files:**
- Create: `docs/decisions/2026-09-22-f2-01-reading-state.md`
- Create: `app/src/main/java/com/example/englishlearning/reading/domain/ArticleType.kt`
- Create: `app/src/main/java/com/example/englishlearning/reading/domain/ArticleLengthTier.kt`
- Create: `app/src/main/java/com/example/englishlearning/reading/ArticleLengthPolicy.kt`
- Test: `app/src/test/java/com/example/englishlearning/reading/ArticleLengthPolicyTest.kt`

- [ ] Write failing tests for all six standard ranges, short/long ranges, 10% tolerance, explicit tier precedence, and unknown wordbook fallback to product default `STANDARD`.
- [ ] Run only `ArticleLengthPolicyTest`; expected failure because policy types do not yet exist.
- [ ] Implement enums and a pure policy function with exact signature:

```kotlin
fun resolveArticleLength(
    wordBookId: String,
    explicitTier: ArticleLengthTier?,
): ArticleLengthPolicy.Resolved
```

- [ ] Verify boundaries and invalid input are rejected without arbitrary word-count support.
- [ ] Write and review the decision document; explicitly state that F2-01 has no network calls and that F2-02 owns AI Profile/security.
- [ ] Run the policy tests; commit `feat(reading): define article access and length policy`.

## Task 2: Add local article and reading-preference schema

**Files:**
- Create: `Article.kt`, `ReadingPreference.kt`, `ArticleEntity.kt`, `ReadingPreferenceEntity.kt`
- Create: `InternalArticleDao.kt`, `InternalReadingPreferenceDao.kt`
- Modify: `AppDatabase.kt`, `AppModule.kt`
- Test: `AppDatabaseArticleMigrationTest.kt`

- [ ] Write a migration test from the current Room version, asserting all existing tables and rows survive and new article/preference tables are usable.
- [ ] Run it first; expected failure because the entities/DAOs and migration are absent.
- [ ] Add `articles` with a unique reuse index on `profileId`, `localDate`, `activeWordBookId`, `articleType`, `lengthTier`, `version`; add a query index on the reuse key and generated time.
- [ ] Add `reading_preferences` keyed by `profileId`, with default type `STORY` and nullable explicit length tier.
- [ ] Upgrade Room version and export the new schema; do not alter existing learning tables.
- [ ] Verify migration and fresh database creation on device; commit `feat(reading): persist article versions and reading preferences`.

## Task 3: Implement repository versioning and offline history

**Files:**
- Create: `ArticleRepository.kt`, `RoomArticleRepository.kt`
- Test: `RoomArticleRepositoryTest.kt`

- [ ] Write failing tests for: first save creates version 1; same reuse key `findLatest` returns latest; second explicit replace creates version 2; version 1 remains readable; history is profile-scoped; malformed article text is rejected before write.
- [ ] Run the repository tests and confirm failure before implementation.
- [ ] Implement domain/entity mapping and transactional max-version insertion; reject blank title/English/Chinese text and HTML/script markers.
- [ ] Map Room cancellation with the existing behavior discriminator: active caller → `StorageUnavailable`; cancelled caller → rethrow.
- [ ] Verify all repository tests and commit `feat(reading): add offline article repository`.

## Task 4: Implement TodayPlan reading access state

**Files:**
- Create: `ReadingAccess.kt`, `ReadingAccessUseCase.kt`
- Test: `ReadingAccessUseCaseTest.kt`
- Modify only existing TodayPlan read contract if a query is missing; do not alter plan generation semantics.

- [ ] Write failing tests for strict locked state with `missingNew > 0`, strict locked state with `missingDue > 0`, unlocked when both targets are complete, and relaxed mode unlocked when new target is complete while due remains.
- [ ] Run tests and confirm failure.
- [ ] Implement a pure mapping from the existing TodayPlan progress/unlock result; do not recalculate or mutate TodayPlan.
- [ ] Verify errors distinguish “还差 X 个新词” and “还差 X 个复习词”; commit `feat(reading): gate article access by today plan`.

## Task 5: Add local reading-access UI

**Files:**
- Create: `ReadingAccessScreen.kt`
- Modify: existing TodayPlan screen entry point only
- Test: `ReadingAccessScreenTest.kt`

- [ ] Write Compose tests first for locked disabled entry + explicit reason, unlocked four-type choices, saved default type, and history entry visible offline.
- [ ] Run tests and confirm failure.
- [ ] Implement a UI-only state screen; no HTTP, no fake article, no optimistic success state.
- [ ] Keep article generation action disabled unless `ReadingAccess.Unlocked` is present; route history to local repository only.
- [ ] Run Compose tests and commit `feat(reading): expose offline reading access`.

## Task 6: F2-01 integration verification and documentation

**Files:**
- Modify: `docs/specs/02-reading-and-ai-content.md` only if wording conflicts with locked decisions.
- Create: `docs/verification/f2-01/README.md` and evidence files.

- [ ] Run all new JVM tests plus existing storage/error regression.
- [ ] Run fresh-device instrumentation migration/UI tests with `-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true`, locked portrait, and required MIUI appop.
- [ ] Verify offline history after process restart and verify TodayPlan completion remains unchanged after a failed/no-network generation attempt (generation remains a no-op in this plan).
- [ ] Record exact test counts, existing unrelated Stage-0 failures, device, APK checksum, and known exclusions.
- [ ] Commit the verification record only after logs and screenshots are present.

## Self-review checklist

- F2-01 requirements covered: unlock gating (Task 4/5), four types and preference (Task 2/5), length precedence/ranges (Task 1), same-key reuse and versioned replacement (Task 3), offline history (Task 3/5), no network in F2-01 (Global Constraints/Task 1).
- F2-02/F2-03/F2-04/F2-05 are intentionally not implemented by this plan; they require separate decisions for endpoint validation, SecretStore binding, response validation, highlighting, safe rendering and failure UI.
- No arbitrary word-count API exists; only the three enum tiers are accepted.
- No placeholder prose, fake article, HTML rendering, external link execution, API Key or Authorization field is introduced.
- Existing TodayPlan generation and immutable-snapshot semantics remain unchanged.
