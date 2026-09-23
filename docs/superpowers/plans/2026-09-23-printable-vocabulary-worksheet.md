# Printable Vocabulary Worksheet Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build an offline learning-tools flow that selects today's completed vocabulary, previews A4 bilingual dictation worksheets, and shares a locally generated PDF for printing.

**Architecture:** Keep selection, worksheet construction, and pagination pure Kotlin under `learning/worksheet`; render the same paginated model to PDF and preview so page breaks cannot diverge. Android-only storage, PDF rendering, FileProvider sharing, and Compose navigation remain behind focused adapters in `export/` and `ui/`.

**Tech Stack:** Kotlin 2.1, Compose Material 3, Hilt, Room 2.8.4, Android `PdfDocument`, `PdfRenderer`, Android Sharesheet, AndroidX Core `FileProvider`; no new PDF dependency.

**Spec:** `docs/superpowers/specs/2026-09-23-printable-vocabulary-worksheet-design.md`

## Global Constraints

- Target Android 13 device `bf353dda`; minSdk 26, compileSdk/targetSdk 36.
- Use existing mint theme tokens, stable `testTag` and `contentDescription`; no WebView.
- Only cards in the current immutable plan with successful feedback are “today learned”. Due cards precede new cards.
- Both `ZH_TO_EN` and `EN_TO_ZH` directions are selectable together; preview is mandatory before sharing. Only the `SPELLING_TEST` template consumes directions — `FULL_LIST` and `EBBINGHAUS_REVIEW` always print the word and its meaning.
- Rows per page come from the template, never from a single shared constant: `FULL_LIST` 40 (two 20-row columns), `SPELLING_TEST` 20, `EBBINGHAUS_REVIEW` 20, answers 20. Every rendered page shows a fixed 20-row grid so the last page keeps the same row height.
- PDF, preview, filtering and sharing remain offline; do not add INTERNET or storage permissions.
- Use framework `PdfDocument`/`PdfRenderer`; PDF stays in `cacheDir/worksheets`, and only a FileProvider `content://` URI is shared read-only.
- No copying code, templates, datasets or assets from the reviewed GitHub projects; all rendering is authored here.
- Any added Gradle dependency/configuration must be locked with `--write-locks` only in the successful build batch.
- Preserve append-only learning events and current immutable-plan semantics.

---

### Task 1: Add worksheet selection and feedback-query domain ports

**Files:**
- Modify: `app/src/main/java/com/example/englishlearning/learning/LearningEventRepository.kt`
- Modify: `app/src/main/java/com/example/englishlearning/learning/RoomLearningEventRepository.kt`
- Modify: `app/src/main/java/com/example/englishlearning/core/storage/dao/InternalLearningEventDao.kt`
- Create: `app/src/main/java/com/example/englishlearning/learning/worksheet/WorksheetModels.kt`
- Create: `app/src/main/java/com/example/englishlearning/learning/worksheet/BuildWorksheetContentUseCase.kt`
- Test: `app/src/test/java/com/example/englishlearning/learning/worksheet/BuildWorksheetContentUseCaseTest.kt`
- Test: `app/src/androidTest/java/com/example/englishlearning/learning/RoomLearningEventRepositoryWorksheetTest.kt`

**Interfaces:**
- Consumes: `TodayPlanRepository.findLatest(profileId): TodayPlanResult`, `LearningEventRepository.completedCardIds(planId)`, `WordCardSource.cards(cardIds)`.
- Produces: `WorksheetSource`, `WorksheetItem`, `WorksheetRange`, `WorksheetDirection`, `WorksheetSettings`, and `BuildWorksheetContentUseCase.invoke(profileId, range): Result<WorksheetSource>`.

- [ ] **Step 1: Write failing pure-domain tests for plan-safe card selection**

```kotlin
@Test
fun completed_cards_keep_due_then_new_plan_order() = runTest {
    val result = useCase("profile-1", WorksheetRange.COMPLETED_TODAY).getOrThrow()
    assertEquals(listOf("review", "new"), result.items.map(WorksheetItem::lemma))
}

@Test
fun plan_external_completed_card_is_never_exported() = runTest {
    val result = useCase("profile-1", WorksheetRange.COMPLETED_TODAY).getOrThrow()
    assertEquals(setOf("review", "new"), result.items.map(WorksheetItem::lemma).toSet())
}

@Test
fun missing_word_card_is_counted_without_reordering_remaining_items() = runTest {
    val result = useCase("profile-1", WorksheetRange.ALL_PLAN_ITEMS).getOrThrow()
    assertEquals(1, result.missingCardCount)
    assertEquals(listOf("review"), result.items.map(WorksheetItem::lemma))
}
```

- [ ] **Step 2: Run the domain tests to verify they fail**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.example.englishlearning.learning.worksheet.BuildWorksheetContentUseCaseTest" --no-daemon --no-build-cache --console=plain`

Expected: FAIL because worksheet types and use case do not exist.

- [ ] **Step 3: Add one batched event-feedback query**

Add this port, query and mapping rather than performing a per-card lookup:

```kotlin
data class PlanCardFeedback(val cardId: String, val feedback: ReviewFeedback)

suspend fun completedCardFeedback(planId: String): RepositoryResult<List<PlanCardFeedback>>
```

The SQL must select event card ID and feedback for `plan_id = :planId`, return only IDs that have an event, and preserve a deterministic card-ID secondary ordering. The use case, not the DAO, applies plan ordering. Keep the existing Room cancellation mapping unchanged.

- [ ] **Step 4: Implement `WorksheetModels.kt` and the minimum use case**

Define exactly:

```kotlin
enum class WorksheetRange { COMPLETED_TODAY, ALL_PLAN_ITEMS, COMPLETED_NEW, COMPLETED_REVIEW, DIFFICULT_TODAY }
enum class WorksheetDirection { ZH_TO_EN, EN_TO_ZH }

data class WorksheetItem(
    val cardId: String,
    val lemma: String,
    val ipa: String,
    val partOfSpeech: String,
    val meaningZh: String,
    val example: String?,
)

data class WorksheetSource(
    val localDate: LocalDate,
    val wordBookId: String,
    val items: List<WorksheetItem>,
    val missingCardCount: Int,
)

data class WorksheetSettings(
    val range: WorksheetRange = WorksheetRange.COMPLETED_TODAY,
    val directions: Set<WorksheetDirection> = setOf(WorksheetDirection.ZH_TO_EN),
    val useFourLineGrid: Boolean = true,
    val includeAnswerPage: Boolean = true,
)
```

`DIFFICULT_TODAY` includes completed plan cards whose latest plan feedback is `Unknown` or `Fuzzy`; add a focused test for it. Any `NotFound`, `MissingLearningSetup`, `StorageUnavailable`, repository failure, or no selected cards returns a typed failure mapped later to safe Chinese UI copy.

- [ ] **Step 5: Run focused unit and Room instrumentation tests**

Run JVM: `./gradlew.bat :app:testDebugUnitTest --tests "com.example.englishlearning.learning.worksheet.*" --no-daemon --no-build-cache --console=plain`

Run device: `./gradlew.bat :app:connectedDebugAndroidTest -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true --no-daemon --no-build-cache --console=plain`

Expected: worksheet selection tests and repository query tests pass; no existing learning-event regression.

- [ ] **Step 6: Commit the domain slice**

```bash
git add app/src/main/java/com/example/englishlearning/learning app/src/main/java/com/example/englishlearning/core/storage/dao/InternalLearningEventDao.kt app/src/test/java/com/example/englishlearning/learning/worksheet app/src/androidTest/java/com/example/englishlearning/learning/RoomLearningEventRepositoryWorksheetTest.kt
git commit -m "feat(worksheet): select planned vocabulary for export"
```

### Task 2: Build a deterministic A4 worksheet document and paginator

**Files:**
- Create: `app/src/main/java/com/example/englishlearning/learning/worksheet/WorksheetDocumentBuilder.kt`
- Create: `app/src/main/java/com/example/englishlearning/learning/worksheet/WorksheetPaginator.kt`
- Test: `app/src/test/java/com/example/englishlearning/learning/worksheet/WorksheetDocumentBuilderTest.kt`
- Test: `app/src/test/java/com/example/englishlearning/learning/worksheet/WorksheetPaginatorTest.kt`

**Interfaces:**
- Consumes: `WorksheetSource` and `WorksheetSettings` from Task 1.
- Produces: `WorksheetDocument`, `WorksheetPage`, `WorksheetPageContent`, and `WorksheetPaginator.paginate(document): List<WorksheetPage>`.

- [ ] **Step 1: Write failing tests for direction order, answer content and page breaks**

```kotlin
@Test
fun both_directions_render_chinese_to_english_before_english_to_chinese() {
    val pages = paginator.paginate(builder.build(source, settingsWithBothDirections))
    assertEquals(WorksheetDirection.ZH_TO_EN, pages.first().direction)
    assertEquals(WorksheetDirection.EN_TO_ZH, pages.first { it.direction == WorksheetDirection.EN_TO_ZH }.direction)
}

@Test
fun answer_page_preserves_question_number_and_word_order() {
    val answers = paginator.paginate(builder.build(source, settingsWithAnswers)).last().answers
    assertEquals(listOf(1, 2), answers.map { it.number })
    assertEquals(listOf("ability", "achieve"), answers.map { it.lemma })
}

@Test
fun row_that_does_not_fit_starts_on_next_page() {
    val pages = paginator.paginate(documentWithRowsThatOverflowOnePage)
    assertEquals(listOf(1, 1, 2), pages.flatMap { it.questionRows }.map { it.pageNumber })
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.example.englishlearning.learning.worksheet.Worksheet*Test" --no-daemon --no-build-cache --console=plain`

Expected: FAIL because document builder and paginator are absent.

- [ ] **Step 3: Implement immutable document and layout types**

Use 595 × 842 pt A4, `42f` side margin, `54f` header reservation and `24f` footer reservation. Include title, local date, name/date fields, direction label, question number, prompt, answer line area and answer rows. The builder must omit a question section for an unselected direction and omit all question pages when `directions` is empty.

- [ ] **Step 4: Implement one paginator for both preview and PDF**

Use `34f` row height for ordinary lines and `60f` for four-line-grid rows. A row must be atomic: if `cursorY + rowHeight > pageBottom`, close the page and add the row to a new page. Generate final page count only after all logical pages exist, then assign `pageNumber` and `pageCount` to every page. Do not add Android classes to this package.

- [ ] **Step 5: Run unit tests and static checks**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.example.englishlearning.learning.worksheet.*" :app:detekt :app:ktlintCheck --no-daemon --no-build-cache --console=plain`

Expected: all worksheet unit tests, detekt and ktlint pass.

- [ ] **Step 6: Commit the layout slice**

```bash
git add app/src/main/java/com/example/englishlearning/learning/worksheet app/src/test/java/com/example/englishlearning/learning/worksheet
git commit -m "feat(worksheet): paginate A4 dictation documents"
```

### Task 3: Add private PDF rendering, preview bitmap generation and secure sharing

**Files:**
- Create: `app/src/main/java/com/example/englishlearning/export/WorksheetPdfRenderer.kt`
- Create: `app/src/main/java/com/example/englishlearning/export/WorksheetPreviewRenderer.kt`
- Create: `app/src/main/java/com/example/englishlearning/export/WorksheetShareLauncher.kt`
- Create: `app/src/main/res/xml/worksheet_file_paths.xml`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/java/com/example/englishlearning/di/AppModule.kt`
- Test: `app/src/test/java/com/example/englishlearning/export/WorksheetShareLauncherTest.kt`
- Test: `app/src/androidTest/java/com/example/englishlearning/export/WorksheetPdfRendererTest.kt`
- Test: `app/src/androidTest/java/com/example/englishlearning/export/WorksheetPreviewRendererTest.kt`

**Interfaces:**
- Consumes: `List<WorksheetPage>` from Task 2.
- Produces: `RenderedWorksheet(file: File, pageCount: Int)`, `List<Bitmap>` previews and `Intent` built by `WorksheetShareLauncher.createChooser(file)`.

- [ ] **Step 1: Write failing security and rendering tests**

```kotlin
@Test
fun sharing_pdf_uses_read_only_content_uri() {
    val intent = launcher.createChooser(pdfFile)
    val target = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
    assertEquals("application/pdf", target.type)
    assertTrue(target.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
    assertTrue(target.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)!!.scheme == "content")
}

@Test
fun generated_pdf_has_same_page_count_as_paginated_model() {
    val rendered = pdfRenderer.render(pages)
    PdfRenderer(ParcelFileDescriptor.open(rendered.file, ParcelFileDescriptor.MODE_READ_ONLY)).use {
        assertEquals(pages.size, it.pageCount)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.example.englishlearning.export.WorksheetShareLauncherTest" --no-daemon --no-build-cache --console=plain`

Expected: FAIL because export adapters do not exist.

- [ ] **Step 3: Render `WorksheetPage` with framework APIs**

`WorksheetPdfRenderer` creates `context.cacheDir/worksheets` if needed, writes via a temporary file, closes `PdfDocument`, atomically renames only on success and deletes the temporary file on any failure. Use Canvas primitives for all title text, fields, normal writing line and four-line grid strokes; no third-party template or font asset. For the middle grid line use a red dashed `Paint` path effect; keep answers as text only.

- [ ] **Step 4: Add preview and FileProvider boundaries**

`WorksheetPreviewRenderer` opens the generated file with `PdfRenderer`, renders each page to a bounded ARGB_8888 bitmap using a maximum 1080-pixel long side, and closes each page, renderer and parcel descriptor using `use`. Add a FileProvider with exactly this declaration:

```xml
<provider
    android:name="androidx.core.content.FileProvider"
    android:authorities="${applicationId}.worksheetfiles"
    android:exported="false"
    android:grantUriPermissions="true">
    <meta-data android:name="android.support.FILE_PROVIDER_PATHS" android:resource="@xml/worksheet_file_paths" />
</provider>
```

The XML may expose only `<cache-path name="worksheets" path="worksheets/" />`. The share launcher uses ACTION_SEND, a FileProvider URI, `EXTRA_STREAM`, `application/pdf`, and read URI grant only.

- [ ] **Step 5: Run device renderer tests and package validation**

Run: `./gradlew.bat :app:connectedDebugAndroidTest -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true --no-daemon --no-build-cache --console=plain`

Expected: PDF contains all pages, renderer creates preview bitmaps, and share intent has a content URI; no storage permission is introduced.

- [ ] **Step 6: Commit the export slice**

```bash
git add app/src/main/java/com/example/englishlearning/export app/src/main/res/xml/worksheet_file_paths.xml app/src/main/AndroidManifest.xml app/src/main/java/com/example/englishlearning/di/AppModule.kt app/src/test/java/com/example/englishlearning/export app/src/androidTest/java/com/example/englishlearning/export
git commit -m "feat(worksheet): render and share private PDFs"
```

### Task 4: Add ViewModel state and learning-tool, settings and preview Compose screens

**Files:**
- Create: `app/src/main/java/com/example/englishlearning/ui/WorksheetViewModel.kt`
- Create: `app/src/main/java/com/example/englishlearning/ui/LearningToolsScreen.kt`
- Create: `app/src/main/java/com/example/englishlearning/ui/WorksheetSettingsScreen.kt`
- Create: `app/src/main/java/com/example/englishlearning/ui/WorksheetPreviewScreen.kt`
- Modify: `app/src/main/java/com/example/englishlearning/MainActivity.kt`
- Modify: `app/src/main/java/com/example/englishlearning/ui/AppScreen.kt`
- Modify: `app/src/main/java/com/example/englishlearning/ui/TodayPlanScreen.kt`
- Test: `app/src/test/java/com/example/englishlearning/ui/WorksheetViewModelTest.kt`
- Test: `app/src/androidTest/java/com/example/englishlearning/ui/LearningToolsScreenTest.kt`
- Test: `app/src/androidTest/java/com/example/englishlearning/ui/WorksheetSettingsScreenTest.kt`
- Test: `app/src/androidTest/java/com/example/englishlearning/ui/WorksheetPreviewScreenTest.kt`

**Interfaces:**
- Consumes: `BuildWorksheetContentUseCase`, `WorksheetDocumentBuilder`, `WorksheetPaginator`, `WorksheetPdfRenderer`, `WorksheetPreviewRenderer`, `WorksheetShareLauncher`.
- Produces: `WorksheetUiState`, `WorksheetEffect.SharePdf`, settings mutation methods and `preview(profileId)`.

- [ ] **Step 1: Write failing ViewModel and Compose behavior tests**

```kotlin
@Test
fun preview_requires_at_least_one_direction() = runTest(dispatcher) {
    viewModel.setDirections(emptySet())
    assertEquals("至少选择一种默写方向", viewModel.uiState.value.validationMessage)
}

@Test
fun successful_preview_exposes_rendered_page_count_before_share() = runTest(dispatcher) {
    viewModel.preview("profile-1")
    advanceUntilIdle()
    assertEquals(2, viewModel.uiState.value.previewPageCount)
    assertNotNull(viewModel.uiState.value.previewFile)
}

@Test
fun settings_screen_exposes_both_direction_controls_and_preview_action() {
    composeRule.setContent { WorksheetSettingsScreen(state = previewableState, onPreview = {}) }
    composeRule.onNodeWithTag("worksheet_direction_zh_to_en").assertExists()
    composeRule.onNodeWithTag("worksheet_direction_en_to_zh").assertExists()
    composeRule.onNodeWithTag("worksheet_preview_action").assertHasClickAction()
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.example.englishlearning.ui.WorksheetViewModelTest" --no-daemon --no-build-cache --console=plain`

Expected: FAIL because ViewModel and screens are missing.

- [ ] **Step 3: Implement state, safe effects and screens**

`WorksheetViewModel` owns `WorksheetSettings` in `SavedStateHandle`, clears a previous temporary preview before replacing it, maps domain/render failures to fixed Chinese copy, and emits `WorksheetEffect.SharePdf(file)` only after a successful preview exists. Do not put `Context`, `Intent`, `Bitmap`, `FileDescriptor` or API keys in saved state.

Use these stable tags:

```text
learning_tools_screen
learning_tools_export_word_list
learning_tools_generate_worksheet
worksheet_settings_screen
worksheet_range_selector
worksheet_direction_zh_to_en
worksheet_direction_en_to_zh
worksheet_four_line_grid
worksheet_include_answers
worksheet_preview_action
worksheet_preview_screen
worksheet_preview_page_<index>
worksheet_share_action
```

`WorksheetPreviewScreen` displays the rendered page bitmaps and a visible “返回修改” action. The share callback belongs in `AppScreen`/Activity, consumes the ViewModel effect and invokes `WorksheetShareLauncher`; it must never auto-share on state restoration.

- [ ] **Step 4: Wire local navigation without disturbing learning session**

Add `showLearningTools`, `showWorksheetSettings`, and `showWorksheetPreview` as profile-scoped `rememberSaveable` state in `AppScreen`. Add a secondary “学习工具” entry to `TodayPlanScreen` now; use it only as a bridge until the planned bottom settings navigation exists. Back handling must be: preview → settings → tools → today plan. Do not change `showLearning`, planned-card ordering or reading access.

- [ ] **Step 5: Run UI and ViewModel regressions**

Run JVM: `./gradlew.bat :app:testDebugUnitTest --tests "com.example.englishlearning.ui.WorksheetViewModelTest" --no-daemon --no-build-cache --console=plain`

Run device: `./gradlew.bat :app:connectedDebugAndroidTest -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true --no-daemon --no-build-cache --console=plain`

Expected: all worksheet controls are present/clickable, a preview requires a direction, existing TodayPlan and WordCard UI tests remain green.

- [ ] **Step 6: Commit the UI slice**

```bash
git add app/src/main/java/com/example/englishlearning/ui app/src/main/java/com/example/englishlearning/MainActivity.kt app/src/test/java/com/example/englishlearning/ui/WorksheetViewModelTest.kt app/src/androidTest/java/com/example/englishlearning/ui/LearningToolsScreenTest.kt app/src/androidTest/java/com/example/englishlearning/ui/WorksheetSettingsScreenTest.kt app/src/androidTest/java/com/example/englishlearning/ui/WorksheetPreviewScreenTest.kt
git commit -m "feat(worksheet): add preview-first learning tools flow"
```

### Task 5: Verify offline end-to-end behavior and document the feature

**Files:**
- Create: `docs/verification/printable-worksheet/README.md`
- Create: `docs/verification/printable-worksheet/01-tools.png`
- Create: `docs/verification/printable-worksheet/02-settings.png`
- Create: `docs/verification/printable-worksheet/03-preview.png`
- Create: `docs/verification/printable-worksheet/04-share-sheet.png`
- Modify: `docs/specs/01-vocabulary-learning-and-review.md`
- Modify: `docs/third-party-notices.md`

**Interfaces:**
- Consumes: the complete Task 1–4 feature.
- Produces: immutable verification evidence and updated product/notice documentation.

- [ ] **Step 1: Add a failing end-to-end test assertion for preview-before-share**

Extend `AppScreenTest` or create `WorksheetFlowTest` so the share action does not exist before successful preview and becomes actionable only after it. Use test fakes for the renderer/share adapter; do not launch an external Activity in the Compose test.

- [ ] **Step 2: Run the test and verify it fails before the final wiring**

Run: `./gradlew.bat :app:connectedDebugAndroidTest -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true --no-daemon --no-build-cache --console=plain`

Expected: FAIL only if the share control is reachable before a preview; fix the wiring before continuing.

- [ ] **Step 3: Build APK and perform Android 13 device validation**

Run:

```bash
./gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:connectedDebugAndroidTest -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true --no-daemon --no-build-cache --console=plain
```

Install `app/build/outputs/apk/debug/app-debug.apk`. On device `bf353dda`, create/retain a disposable plan with at least two completed cards, open 学习工具 → 生成默写纸, enable both directions and answers, preview every rendered page, then tap 导出/分享 PDF. Capture screenshots and a UI dump for each required artifact. Do not clear application data without explicit user confirmation.

- [ ] **Step 4: Inspect PDF and sharing privacy boundaries**

Copy only the generated PDF through the app’s FileProvider URI path or inspect its file from app-private context; verify page count against preview, both direction headers, grid/ordinary line behavior, answer page and no apparent API key. Capture the outgoing intent in an instrumentation test and assert `content://`, `application/pdf`, and read grant. Verify manifest has no added storage or network permission.

- [ ] **Step 5: Write verification and product documentation**

In `README.md`, record exact build/test totals from real logs, device model, APK checksum, steps observed, screenshots, expected local-only boundary and limitations. Add a concise capability section to `docs/specs/01-vocabulary-learning-and-review.md`. In notices state: “The printable worksheet feature uses self-authored Kotlin/Compose/PDF rendering; GitHub projects were reviewed for functional reference only and no third-party code, templates, assets or word data were incorporated.”

- [ ] **Step 6: Commit verification and documentation**

```bash
git add docs/verification/printable-worksheet docs/specs/01-vocabulary-learning-and-review.md docs/third-party-notices.md
git commit -m "docs(worksheet): verify printable vocabulary export"
```

### Task 6: Match the three reference worksheet templates (change request)

**Trigger:** the user supplied three reference exports (`我的词表-今日任务.pdf`, `(1).pdf`, `(2).pdf`) and three screenshots, and stated the export must follow those templates.

**Files:**
- Modify: `app/src/main/java/com/example/englishlearning/learning/worksheet/WorksheetModels.kt`
- Modify: `app/src/main/java/com/example/englishlearning/learning/worksheet/WorksheetDocumentBuilder.kt`
- Modify: `app/src/main/java/com/example/englishlearning/learning/worksheet/WorksheetPaginator.kt`
- Modify: `app/src/main/java/com/example/englishlearning/export/WorksheetPdfRenderer.kt`
- Modify: `app/src/main/java/com/example/englishlearning/ui/WorksheetSettingsScreen.kt`
- Modify: `app/src/main/java/com/example/englishlearning/ui/WorksheetViewModel.kt`
- Modify: `app/src/main/java/com/example/englishlearning/ui/WorksheetPreviewScreen.kt`
- Modify: `app/src/main/java/com/example/englishlearning/ui/AppScreen.kt`
- Test: `app/src/test/java/com/example/englishlearning/learning/worksheet/WorksheetTemplateTest.kt`
- Test: `app/src/test/java/com/example/englishlearning/learning/worksheet/WorksheetPaginatorTest.kt`
- Test: `app/src/androidTest/java/com/example/englishlearning/export/WorksheetPdfRendererTest.kt`
- Test: `app/src/androidTest/java/com/example/englishlearning/ui/WorksheetSettingsScreenTest.kt`

**Interfaces:**
- Consumes: the Task 1–4 pipeline.
- Produces: `WorksheetTemplate`, `WorksheetSettings.template`, `WorksheetSettings.requiresDirection()`, `WorksheetSettings.isReadyToPreview()`, `WorksheetQuestionRow(number, lemma, ipa, partOfSpeech, meaningZh, pageNumber)`, and `WorksheetViewModel.selectTemplate(template)`.

- [x] **Step 1: Write failing tests for the three templates**

`WorksheetTemplateTest` asserts `FULL_LIST` splits 45 words into `[40, 5]`, `SPELLING_TEST` and `EBBINGHAUS_REVIEW` into `[20, 20, 5]`, and that rows carry `number/lemma/ipa/partOfSpeech/meaningZh`.

- [x] **Step 2: Run the tests to verify they fail**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.example.englishlearning.learning.worksheet.*" --no-daemon --no-build-cache --console=plain`

Expected: FAIL — `WorksheetTemplate` and the printable row columns do not exist yet.

- [x] **Step 3: Model the templates and make pagination template-driven**

Add `WorksheetTemplate` (default `SPELLING_TEST`). `WorksheetPaginator.rowsPerPage` becomes `40` for `FULL_LIST` and `20` for the other two; answers stay at `20` per page. `WorksheetDocumentBuilder` emits a single `direction = null` section for non-directional templates so a two-direction selection never duplicates the full list.

- [x] **Step 4: Redraw the PDF to the reference layout**

`WorksheetPdfRenderer` draws a rounded teal frame, dark-teal header band, light-teal alternating rows and a light cell grid; `FULL_LIST` is two side-by-side 20-row columns, `SPELLING_TEST` is a mirrored left/right pair with blank cells and optional four-line guides, `EBBINGHAUS_REVIEW` has a two-row header with `D1…D90` review boxes. Grid size is fixed at 20 rows per column so every page looks identical and the last row never overlaps the footer.

- [x] **Step 5: Expose template selection in the settings screen**

`WorksheetSettingsScreen` gains a radio-card template picker with stable tags `worksheet_template_full_list`, `worksheet_template_spelling_test`, `worksheet_template_ebbinghaus`; the direction pickers and four-line switch only appear when `requiresDirection()` is true. `WorksheetPreviewScreen` renders each template in its own shape and labels every page with its template.

- [x] **Step 6: Run the template tests and the Android PDF renderer tests**

Run:
```bash
./gradlew.bat :app:testDebugUnitTest --tests "com.example.englishlearning.learning.worksheet.*" --no-daemon --no-build-cache --console=plain
./gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.englishlearning.export.WorksheetPdfRendererTest,com.example.englishlearning.ui.WorksheetSettingsScreenTest -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true --no-daemon --no-build-cache --console=plain
```
Expected: PASS — 14 worksheet domain tests, plus per-template rendering and the template picker on a real Android 13 device.

- [ ] **Step 7: Device-check all three templates and commit**

Export each template on device `bf353dda`, screenshot the settings page, the per-template preview and the generated PDF page, then commit the template change separately from the Task 5 documentation commit.

## Plan Self-Review

- Spec coverage: Tasks 1–2 implement safe source selection, both directions, answers and shared pagination; Task 3 implements local PDF, preview, FileProvider and system sharing; Task 4 implements tool/settings/preview UX and bridge navigation; Task 5 enforces preview-first end-to-end validation and documentation; Task 6 aligns the export with the user-supplied reference templates and makes pagination template-driven.
- Placeholder scan: no TBD/TODO or implicit error-handling steps remain; typed failures, cleanup and exact security behavior are specified.
- Type consistency: `WorksheetSource`, `WorksheetItem`, `WorksheetTemplate`, `WorksheetSettings`, `WorksheetDirection`, paginated `WorksheetPage`, `WorksheetQuestionRow`, `RenderedWorksheet`, and ViewModel effects are introduced in dependency order and used consistently.
