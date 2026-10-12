# Kokoro Android Explicit Verb Spike Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Verify isolated Android synthesis and playback of `record` as a default noun and explicit `VB` verb for US/GB using audited Phonemis lexicon entries, without changing production pronunciation behavior.

**Architecture:** Add a Spike-only tagged input contract while retaining the current `synthesize(text, accent)` API as a compatibility overload. Resolve the explicit lexicon key before the existing neural frontend, using a minimal test fixture extracted from pinned source data; keep the lexicon resolver pure Kotlin and independently testable. Instrumentation exercises both accents, exports WAVs only to a separate test directory, and checks that production APK contents and Room data remain unchanged.

**Tech Stack:** Kotlin, coroutines, JUnit 5 JVM tests, AndroidJUnit4 instrumentation, ONNX Runtime Android 1.29.0 (androidTest only), adb.

**Spec:** `docs/superpowers/specs/2026-10-08-kokoro-android-explicit-verb-design.md`

## Global Constraints

- Do not modify `PronunciationRouter`, Hilt bindings, production Provider, word-card UI, MiMo/OpenAI routing, or pronunciation cache keys.
- Do not implement automatic POS tagging or load a complete lexicon.
- Do not add Kokoro/Phonemis/ORT assets or Spike implementation to the production APK.
- Do not use `connectedDebugAndroidTest`, uninstall the app, clear user data, commit, or push.
- Preserve `NO_GO_KEEP_SYSTEM_TTS`.
- Do not clean or revert existing dirty-worktree files; preserve the untracked `nul`.
- Before any device test, capture Room DB/SHM/WAL baseline; use only `adb install -r -t` and explicit `am instrument`; compare the three files bytewise afterwards.

---

## File Map

- `outputs/kokoro-spike-20261006/desktop_spike.py` and `frontend-results.json`: read-only source of the existing tag lookup semantics and recorded US/GB `record` VB phonemes. Do not edit these files for this Android task.
- `app/src/androidTest/assets/kokoro-notices/` and existing `outputs/kokoro-spike-20261006/` source records: inspect the pinned Phonemis license/revision and original lexicon files; do not infer or manually author phoneme strings.
- `app/src/androidTest/assets/kokoro-lexicon-fixture/record-pos.json` (new): compact exact fixture plus pinned source/license/hash manifest, only if Task 1 proves the source data and exact fallback key.
- `app/src/kokoroSpike/kotlin/com/example/englishlearning/language/spike/KokoroAndroidEngine.kt`: add tagged input and preserve the existing method as the no-tag entry point.
- `app/src/kokoroSpike/kotlin/com/example/englishlearning/language/spike/KokoroLexiconResolver.kt` (new): pure Kotlin exact-tag/POS-family/bare-word lookup and explicit resolution result.
- `app/src/test/java/com/example/englishlearning/language/spike/KokoroLexiconResolverTest.kt` (new): tests lookup precedence, misses and validation.
- `app/src/test/java/com/example/englishlearning/language/spike/OnnxKokoroEngineTest.kt`: add tests that explicit POS uses resolved phonemes and bypasses neural frontend; preserve existing lifecycle and two-argument behavior tests.
- `app/src/androidTest/java/com/example/englishlearning/language/spike/KokoroEngineAvailabilityTest.kt`: add US/GB noun-versus-verb synthesis/playback/export assertions and expected source/resolution logs.
- `outputs/kokoro-spike-20261006/` and `verification-logs/kokoro-record-verb-20261008/` (new evidence directory): save build/test logs, asset audit, APK inspection, Room snapshots, WAVs and checksums.
- `.superpowers/sdd/2026-10-07-kokoro-android-spike/progress.md`, `outputs/kokoro-spike-20261006/REPORT.md`, and `docs/decisions/2026-10-06-kokoro-word-pronunciation.md`: update after verification only; do not change the NO-GO decision.
- `D:/workbjddy-project/手机学习英语软件/.workbuddy/memory/2026-10-08.md`: append concise durable project result after substantive completion.

## Task 1: Audit and Extract the Minimal POS Fixture

**Files:**
- Read: `outputs/kokoro-spike-20261006/desktop_spike.py`
- Read: `outputs/kokoro-spike-20261006/frontend-results.json`
- Read: `app/src/androidTest/assets/kokoro-notices/`
- Create only after successful audit: `app/src/androidTest/assets/kokoro-lexicon-fixture/record-pos.json`
- Evidence: `outputs/kokoro-spike-20261006/record-pos-source-audit.json`

**Interfaces:**
- Produces: audited per-accent lookup keys and phoneme strings for `record` with explicit `VB`, plus the default no-tag baseline if present. Records original lexicon file path, Phonemis revision, license, source file SHA-256, selected key and SHA-256 of each extracted value.

- [ ] **Step 1: Read the implementation and report evidence.** Confirm `select_lexicon()` candidate order is `<word>|<tag>`, POS family, then bare word. Inspect `frontend-results.json` entries for `record` `mode=lexicon-verb` and `mode=lexicon-default` for both accents; record actual phoneme strings and ensure `source=lexicon`.
- [ ] **Step 2: Verify original source data.** From the already provisioned, pinned Phonemis lexicon, independently compute file SHA-256 and look up `record|VB`, POS-family key (if present), and bare `record` for `en-us` and `en-gb`. Do not transcribe phonemes manually. Verify the source license is the recorded MIT license and pin the exact revision/hash in the audit artifact.
- [ ] **Step 3: Stop if the source cannot be verified.** If source files are absent, hashes differ, license/revision cannot be substantiated, or desktop result metadata does not prove the chosen key, do not create the fixture or proceed to Android implementation. Report the missing evidence.
- [ ] **Step 4: Generate the fixture from source data.** Store only the exact records necessary to test explicit VB precedence and the no-tag baseline. Include a manifest with source revision, license, source file hashes, selected keys and extracted-value hashes. Keep full lexicons outside the repository and outside APKs.
- [ ] **Step 5: Independently verify fixture content.** Re-read JSON using a parser; compare each fixture entry byte/value-wise with its source entry; check fixture size is minimal and contains no unrelated lexicon rows.

## Task 2: Add Pure Kotlin POS Lookup with RED/GREEN

**Files:**
- Create: `app/src/kokoroSpike/kotlin/com/example/englishlearning/language/spike/KokoroLexiconResolver.kt`
- Create: `app/src/test/java/com/example/englishlearning/language/spike/KokoroLexiconResolverTest.kt`

**Interfaces:**
- Produces: `data class KokoroWordInput(val text: String, val partOfSpeechTag: String? = null)` and `data class KokoroLexiconResolution(val phonemes: String, val matchedKey: String)`.
- Produces: `KokoroLexiconResolver.resolve(word: String, accentKey: String, tag: String?, entries: Map<String, String>): KokoroLexiconResolution?`.
- Lookup order exactly follows the audited desktop behavior: normalized `word|tag`; mapped POS-family key for supported prefixes (VB→VERB, NN→NOUN, JJ→ADJ, RB→ADV); then normalized bare word. Reject unsupported accents, blank/malformed word and invalid tag. Return `null` on no match.

- [ ] **Step 1: Write tests first.** Cover exact tagged key wins over family and bare entries; family wins over bare when exact is absent; bare fallback works when tag is null; unknown tag falls back only according to audited rule; missing entry returns null; invalid word/accent/tag is rejected; returned `matchedKey` identifies the exact source row.
- [ ] **Step 2: Run the focused JVM test and verify RED.** Run from `D:/EnglishLearningWorktrees/f1-04-unlock-verify`:

```text
./gradlew.bat :app:testDebugUnitTest --tests 'com.example.englishlearning.language.spike.KokoroLexiconResolverTest' --no-daemon --no-build-cache --console=plain
```

Expected: compilation/test failure because resolver types are not implemented, not an unrelated Gradle or fixture failure.
- [ ] **Step 3: Implement the minimal pure resolver.** Normalize the word with the same trim/lowercase rules as the existing Spike frontend; preserve the audited exact/family/bare candidate order; return the matched key alongside phonemes. Do not add file I/O, Android APIs, global mutable state or automatic tagging.
- [ ] **Step 4: Run the focused JVM test and verify GREEN.** Repeat the command above; all resolver tests pass.
- [ ] **Step 5: Run the independent resolver mutations.** Separately move bare lookup before exact lookup, skip the family candidate, and return null for all tags. Each corresponding assertion must fail; restore source after each mutation and rerun focused tests green. Save each log and source SHA before/after.

## Task 3: Extend Engine Input While Preserving Existing Callers

**Files:**
- Modify: `app/src/kokoroSpike/kotlin/com/example/englishlearning/language/spike/KokoroAndroidEngine.kt`
- Modify: `app/src/test/java/com/example/englishlearning/language/spike/OnnxKokoroEngineTest.kt`
- Read: `app/src/androidTest/java/com/example/englishlearning/language/spike/OrtKokoroRuntime.kt`

**Interfaces:**
- `KokoroAndroidEngine.synthesize(text, accent)` remains available and behaves as a no-tag call.
- Add a Spike-only overload `synthesize(input: KokoroWordInput, accent: PronunciationAccent, lexicon: Map<String, String> = emptyMap()): Result<ByteArray>`; the resolver operates before frontend invocation. If resolver returns a lexicon result, convert its phonemes to Kokoro token IDs and continue through existing voice/style/audio/WAV code. If it returns null, use current frontend/CTC path unchanged.
- Runtime frontend call count is observable via the existing test fake or a narrowly scoped fake counter; no production interface changes.

- [ ] **Step 1: Add failing Engine tests.** Assert explicit `record` VB uses the source-derived US/GB fixture phonemes, reports/returns a lexicon resolution, and calls `frontend` zero times; assert the existing two-argument method still calls frontend as before; assert an unrecognized tagged word follows the current neural fallback path.
- [ ] **Step 2: Run `OnnxKokoroEngineTest` and verify RED.**

```text
./gradlew.bat :app:testDebugUnitTest --tests 'com.example.englishlearning.language.spike.OnnxKokoroEngineTest' --no-daemon --no-build-cache --console=plain
```

Expected: failures specifically for missing tagged overload/resolution behavior; no unrelated compilation errors.
- [ ] **Step 3: Implement minimal overload and resolution path.** Keep the old overload delegating to `KokoroWordInput(text, null)` with an empty lexicon. Add an explicit resolution result seam only if needed for the test/log contract; lexicon hit skips frontend, miss preserves existing behavior. Keep cancellation/resource cleanup and audio-buffer handling unchanged.
- [ ] **Step 4: Run Engine and resolver focused tests.** Run both focused test classes together and confirm zero failures/errors.
- [ ] **Step 5: Mutate Engine dispatch independently.** Temporarily force explicit tagged inputs through frontend and separately force selection of the bare key; the source-selection/frontend-count assertions must fail. Restore and rerun both focused test classes green.

## Task 4: Wire the Android Instrumentation Fixture and Audio Acceptance

**Files:**
- Modify: `app/src/androidTest/java/com/example/englishlearning/language/spike/KokoroEngineAvailabilityTest.kt`
- Read: `app/src/androidTest/java/com/example/englishlearning/language/spike/KokoroReviewAudioExporter.kt`
- Read: `app/src/androidTest/java/com/example/englishlearning/language/spike/OrtKokoroRuntime.kt`
- Use: `app/src/androidTest/assets/kokoro-lexicon-fixture/record-pos.json`

**Interfaces:**
- Fixture loader parses only the small checked-in test JSON and creates accent-specific lexicon maps passed to the explicit Spike overload.
- Export directory: `getExternalFilesDir("kokoro-spike-listen-record-verb")`; distinct from existing fixed-word and unknown-word output directories.
- Filenames: `record-noun-us.wav`, `record-verb-us.wav`, `record-noun-gb.wav`, `record-verb-gb.wav`; expand the existing exporter whitelist only to the exact required safe pattern and preserve refusal to overwrite.

- [ ] **Step 1: Add assertions before implementation changes.** For each accent, synthesize the no-tag baseline and explicit VB input; assert WAV format, nonempty payload, source key, resolved phoneme mapping, playback `Played`, buffer zeroing, and different noun/verb PCM/WAV digests. Assert the resolver is a lexicon hit and does not use frontend for the explicit fixture. Export each sample before playback.
- [ ] **Step 2: Build the focused instrumentation target.** Run `:app:compileDebugAndroidTestKotlin`; expected RED on missing tagged overload/export name support if not yet present.
- [ ] **Step 3: Implement fixture parsing and exporter name support.** Keep parser strict (expected schema/version/accent/key/value count); do not permit paths or arbitrary filenames. Check file hashes via fixture manifest in test setup, and never overwrite existing files.
- [ ] **Step 4: Compile and run focused JVM suites.** Re-run `:app:compileDebugAndroidTestKotlin` and Tasks 2/3 tests; require success.
- [ ] **Step 5: Verify exporter safety.** Add/adjust JVM test proving all four approved names write valid WAVs and a path traversal or unapproved filename is rejected; prove existing targets are not overwritten. Run focused exporter tests green.

## Task 5: Build and Prove APK Boundary

**Files:**
- Read: `app/build.gradle.kts`, `app/gradle.lockfile`, `gradle/libs.versions.toml`
- Write evidence only: `verification-logs/kokoro-record-verb-20261008/apk-boundary.json`

- [ ] **Step 1: Run focused Kokoro JVM regression.** Run the resolver, Engine, synthesis, format and exporter test classes; write complete output to `outputs/kokoro-spike-20261006/record-verb-jvm.log`; require `BUILD SUCCESSFUL` and zero test failures.
- [ ] **Step 2: Build debug and androidTest APKs together.** Use the configured Gradle environment and run `:app:assembleDebug :app:assembleDebugAndroidTest --no-daemon --no-build-cache --console=plain`, redirecting output to `record-verb-apk-build.log`. Do not use a pipe that can mask Gradle exit status.
- [ ] **Step 3: Inspect both APKs.** Record byte size, ZIP entry name/size/CRC inventory, DEX strings/classes and native library entries. Confirm production debug APK contains no `record-pos.json`, Kokoro Spike/Phonemis identifiers, ONNX Runtime classes/native libraries, model, voice or frontend assets. Confirm the androidTest APK contains only the intended fixture and already-approved test-only runtime/assets. Any production-boundary violation stops device installation.

## Task 6: Run Device Acceptance with Database Evidence

**Files:**
- Evidence: `verification-logs/kokoro-record-verb-20261008/baseline/`, `after/`, `record-verb-instrumentation.log`, `record-verb-logcat.txt`
- Audio: `outputs/kokoro-spike-20261006/listening-samples-record-verb/`

- [ ] **Step 1: Preflight device and database.** Confirm `adb devices -l` reports `bf353dda`; inspect `/data/data/com.example.englishlearning/databases` using `run-as`; capture `english-learning.db`, `-shm`, and `-wal` via `MSYS_NO_PATHCONV=1 adb exec-out run-as ... cat ...`; verify sizes are plausible and compute baseline MD5. If any file is unavailable, stop before install.
- [ ] **Step 2: Install only with in-place replacement.** Run `MSYS_NO_PATHCONV=1 adb install -r -t <exact androidTest APK path>`; do not reinstall the main APK unless its package version must match and installation is demonstrably necessary. Never uninstall, clear data, or run a connected Gradle test.
- [ ] **Step 3: Run only the explicit test class.** Run `MSYS_NO_PATHCONV=1 adb shell am instrument -w -e class com.example.englishlearning.language.spike.KokoroEngineAvailabilityTest com.example.englishlearning.test/androidx.test.runner.AndroidJUnitRunner`, redirecting the full output to `record-verb-instrumentation.log`. Require `OK (1 test)` and inspect logcat for US/GB noun/verb source, synthesis/playback times, byte counts, hashes and memory observations.
- [ ] **Step 4: Capture the database after run.** Re-read the same DB/SHM/WAL files to `after/`; compare each before/after file with `cmp` and MD5. All three must be byte-identical. Investigate and stop if not.
- [ ] **Step 5: Pull and independently validate WAVs.** Pull only the four newly named WAV files into the evidence output; parse each as RIFF/WAVE PCM16, 24 kHz, mono; compare local SHA-256 and byte length against the test log; confirm noun/verb digests differ for each accent and device/local files match.

## Task 7: Record Evidence and Final Review

**Files:**
- Modify: `outputs/kokoro-spike-20261006/REPORT.md`
- Modify: `.superpowers/sdd/2026-10-07-kokoro-android-spike/progress.md`
- Modify: `docs/decisions/2026-10-06-kokoro-word-pronunciation.md`
- Append: `D:/workbjddy-project/手机学习英语软件/.workbuddy/memory/2026-10-08.md`

- [ ] **Step 1: Update records only from fresh evidence.** Include source revision/license/hash/key audit, device and test result, per-accent noun/verb resolution path, synthesis/playback timings, WAV hashes/format, APK boundary, Room three-file MD5/byte comparison, and explicit limitations.
- [ ] **Step 2: Preserve the gate.** State that this proves only the fixed `record` explicit-tag test path on this device; it does not implement POS tagging, establish broad pronunciation accuracy, approve full-lexicon distribution, or authorize production integration. Keep `NO_GO_KEEP_SYSTEM_TTS`.
- [ ] **Step 3: Verify changed documentation.** Run `git diff --check`; inspect `git status --short --branch`; confirm no pre-existing change was reverted, no production route file was edited, and no commit/push occurred.
- [ ] **Step 4: Final evidence review.** Re-read all updated records, instrumentation result, WAV metadata/SHA, APK boundary inventory and DB comparison before reporting completion. If any verification is incomplete or failed, report it as a blocker and do not mark the Spike accepted.

## Self-Review Coverage

- Design target, explicit VB and default noun comparison: Tasks 1–4.
- Exact existing lookup behavior and verified source data: Task 1.
- RED/GREEN, isolated mutations and compatibility overload: Tasks 2–4.
- Android playback, distinct audios, safe independent exports: Task 4.
- Formal/test APK isolation: Task 5.
- In-place device test and Room byte integrity: Task 6.
- Evidence, limitations and persistent project log: Task 7.
- Production routing remains unchanged and `NO_GO_KEEP_SYSTEM_TTS` remains active in all tasks.

## Execution Handoff

Do not start implementation in the same turn as presenting this plan. The user must choose an execution mode. No commit or push is authorized by this plan.
