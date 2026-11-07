# 2026-11-07 批 C-4c（AI 等待屏进行中语义 · 三屏收口）工作报告

## 1. 本次目标

用户原话：「确认推送，继续 C-4 任务」——推送那半在上一段已完成并核验（远端 tip `c7271e0`），
本棒做的是 C-4 的后半：**把「进行中语义」从 AI 问词 / 长难句两屏铺到今日文章生成、AI 生图（两臂）、
拍照翻译三屏**，并补上「在飞时不许把请求换掉重发」这道闸。

## 2. 完成了什么

**口径先落文档**（AGENTS.md：范围变化先改规格/决策再实施）

- `docs/decisions/2026-11-07-loading-skeleton-shimmer-prefetch.md` §C-4 交付口径补一段：
  取消的**落点按「用户已经拿到什么」决定**，不是一律清空——文章生成回 `Idle` 但保留今天那篇；
  起草描述回 `Idle`，生成图片回 `PromptReady(那份描述)`（描述是一次已付费的文本出站换来的）；
  拍照翻译取消后识别文本留在编辑框里可改可重试，并新增 `translating` 字段。

**生产侧**（提交 `22af6fc`，7 枚文件）

- `ReadingAccessScreen` / `ImageStudioScreen` / `PhotoTranslationScreen`：四处臂换成共享
  `RequestInProgress`（状态句 + 一枚取消键 + 全仓唯一那句取消陈述），新增三枚**必给**参数
  `onCancelGeneration` / `onCancelGeneration` / `onCancelTranslate`，不给默认空实现。
- `ReadingAccessViewModel.cancelGeneration` + `generationJob` 句柄；`runGeneration` 的 catch
  只在句柄仍指向自己时写状态（否则被取消的旧发会把重发的新一发按回 `Idle`——实现中发现的设计坑）。
- `ImageStudioViewModel.cancelGeneration`（按当前态决定停哪一发、落哪一档）+ `promptJob`/`imageJob`
  句柄；`reset()` 一并收掉在飞的那一发（离开功能页不该让请求继续跑）。
- `PhotoTranslationViewModel`：补 `translating`（识别与翻译是两件事，不用一个 `busy` 混装）、
  `cancelTranslate`、`onTranslateTapped` 在飞时直接 return、`onImageBytes` 作废旧图在飞译文。
- `AppScreen` 三处接线，逐处接到持有 `Job` 的那个 VM。

**判据侧**（提交 `29dec17` JVM / `c47b369` connected）

- JVM：`AiWaitInProgressContractTest` 契约表补三臂 + 宿主接线锚点 + 「每臂两枚稳定 tag 都在」；
  三枚 VM 测试类共新增 12 枚用例（`git diff` 实算 `+12 @Test`），全部走**按次挂起**的假 transport
  （`hangOn: (call) -> Deferred?`）——立即返回的假件证不了取消。
- connected：`RequestInProgressEvidenceTest` 2 例 → 6 例（三件齐 + `boundsInRoot.top` 严格递增）；
  三屏各补「取消恰好转发一次 + 面随之收起 + 在飞时生成入口真的灰着」。
- 取证册：`docs/verification/ui-redesign-2026-10.md` 新增「批 C-4c」一节（PNG 清单、复跑命令、
  被设备改判的那条断言、待目视项）。

## 3. 验证记录

- **RED**：契约表与 VM 用例先扩先跑，红在 Kotlin 未定义符号
  （`cancelGeneration` / `translating` / `cancelTranslate`）——缺 API 的红，不是笔误；随后 GREEN。
- **门禁**（三任务一次性、`--no-daemon --no-build-cache --rerun-tasks`，5m 01s `BUILD SUCCESSFUL`）：
  `:app:assembleDebug` → `:app:testDebugUnitTest` → `:app:compileDebugAndroidTestKotlin` 全绿。
  结果 XML 现算：`classes=329 tests=2449 failures=0 errors=0 skipped=0`。
  对照式：2449 = 批 B 在册基线 2398（`docs/handoff/2026-11-07-batch-b-adaptive-layout-report.md` §3）
  + 51（批 C-1…C-4c 累计新增，其中**本批 +12**，由 `git diff` 计得）；用例数只增不减，零跳过。
  注：不带 `--rerun-tasks` 时三任务 16s 即 `BUILD SUCCESSFUL`（全 UP-TO-DATE），**不能当现跑证据**。
- **connected**（`ANDROID_SERIAL=emulator-5554`，`adb install -r` 两枚 APK + `am instrument`，整类回归）：
  `RequestInProgressEvidenceTest` `OK (6 tests)`、`ReadingAccessScreenTest` `OK (21 tests)`、
  `ImageStudioScreenTest` `OK (14 tests)`、`PhotoTranslationScreenTest` `OK (9 tests)`，合计 50 例全绿。
- **截图取证**：6 张整屏 PNG 落 `.git/qa-scratch/c4c/`（**不入库**），逐张开过眼验：三屏的禁用出口
  都真的灰着、三件齐都在卡面上、拍照翻译取消后识别文本仍在编辑框。
- **detekt/ktlint**：未跑。门禁口径是单测全绿（`docs/decisions/2026-09-19-static-analysis-and-test-baseline.md`），
  存量约 1580 条为既有债，非本批引入，不当回归处理。
- **设备状态复原**：`adb root` 用完即复原（回读 `adb shell id` = `uid=2000(shell)`），
  `font_scale` 1.0、`animator_duration_scale` 1.0、`wm size` 1080x1920 / `density` 420 均为默认档。

## 4. Git 状态

- 分支 `stage-5-ui-r2-learning-path`（不合 `main`）。
- 本批三枚提交：`22af6fc`（生产）→ `29dec17`（JVM 判据）→ `c47b369`（connected 判据）；
  本报告与文档改动随第四枚 docs 提交入库。
- 远端 `origin` = `https://github.com/steveliuyan/English-learning-software.git`，
  远端 tip 实测 `c7271e0`（`ls-remote` 现算），本地 ahead 3（`git rev-list --count` 现算）。
  **尚未推送**——按在册口径「推送逐批请示」，等用户点头后推
  （命令：`git -c credential.interactive=false -c http.proxy= push origin stage-5-ui-r2-learning-path`）。
- 工作区无遗留未提交文件；`.git/qa-scratch/` 内证据不入库。

## 5. 未完成 / 遗留问题

- **待用户拍板（观感，本棒不替人眼判）**：① 进行中卡里的「取消」是 `PillStyle.Secondary` 白底胶囊，
  落在浅绿卡面上像一枚浮起的方块；② 活动指示 `18.dp`/`2.dp` 在静帧里只读出一小段弧（动帧正常）。
  本批四张图与 C-4b 两张**五屏一致** ⇒ 若要改，改的是组件而不是某一屏。
- **本棒改判但没改屏的一条**：「生成一张图片按张计费」那句长在 `PromptReady` 的描述编辑器里，
  生成图片在飞时界面上只剩通用费用句。把它搬进等待面是新的版面裁决，不在 C-4c 口径内，留给用户决定。
- 批 C 剩余：**C-5 真预取**（闪屏窗口 + tab 预读，硬约束「只用本地、不为预取新增任何网络或 AI 出站」）、
  **C-6 收口**（骨架/shimmer 的 `scale=0`/`scale=1` 双档截图册 + 门禁 + 报告）。批 D 转场未开工。
- 既挂账未销：#14 真机四项目视；QA 第三轮整轮未执行；批 B/批 C 既列的目视四项。

## 6. 下一任接手须知

1. **第一步**：批 C-5 真预取——先补决策册 C-5 段的开工实测（闪屏窗口能读到哪些本地表、tab 预读的
   取消语义），再写「零出站」探针用例，最后才动 `core/storage/StartupLocalDataWarmer`。
   红线：不得为预取新增任何网络或 AI 出站。
2. **环境坑（本棒实测，与在册 skill 有出入）**：
   - `android-repo-session-handoff` 里发现 gradle 的两行会拼出 `gradle-8.13-bin//gradle-8.13/...`（缺哈希层）
     而失败；实测路径 `/c/Users/steveliuyan/.gradle/wrapper/dists/gradle-8.13-bin/5xuhj0ry160q40clulazy9h7d/gradle-8.13/bin/gradle.bat`。
   - `app-debug.apk` 347 MB，`adb install -r` 单枚约 160 s；两条 install 串在一条命令里会把 adb server
     卡死（后续 `adb shell` 全 timeout）。处置：`taskkill //F //IM adb.exe` → `adb start-server`，
     再逐枚 install（本棒实测复原后一切正常）。
   - 取应用外部目录（`…/files/c4-in-progress-evidence/`）的 PNG 要临时 `adb root`，读完必须复原并回读 `adb shell id`。
   - 门禁要 `--rerun-tasks` 才算现跑；`ls` 结果 XML 用 `python -c` 解析 `tests/skipped/failures/errors`
     比 grep 稳（控制台经 GBK 管道会乱码，grep 需 `-a`）。
3. **相关联文档**：`docs/decisions/2026-11-07-loading-skeleton-shimmer-prefetch.md`（本批口径唯一落点）、
   `docs/verification/ui-redesign-2026-10.md`（截图册与复跑命令）、`docs/specs/05-release-quality-and-compliance.md`
   F5-05、`app/src/test/.../AiWaitInProgressContractTest.kt`（判据闸）、上一棒 `docs/handoff/2026-11-07-batch-b-adaptive-layout-report.md`。

## 7. 用户确认过的决策

- 「直接进批 C」；批 C 骨架「全站都铺」、shimmer 关动画时「末尾=静息骨架、无光泽」、
  预取深度「闪屏窗口 + tab 预读」且**只用本地、不为预取新增任何网络或 AI 出站**。
- 「方案通过，直接开始实施」（文档先行，随后 TDD）。
- 取消的落点按「用户已经拿到什么」决定：文章生成保留今天那篇；生图取消图片回那句已付费的描述；
  拍照翻译保留识别文本可改可重试。
- 在飞时不许把请求换掉重发：屏侧禁用是第一道防线，VM 侧「旧的先取消、只许一发在飞」是第二道。
- 分支不合 `main`；推送逐批请示（本批三枚提交待批）。
- 真机 `bf353dda` 只手工试用，instrumented 一律在模拟器；截图与密钥类证据不入库。
