# 2026-11-06 对话框排印统一 + 词书单词页令牌收口 工作报告

UI 重设计/质量轮（分支 `stage-5-ui-r2-learning-path`）第 8 批（未推送枚数现算：
`git rev-list --count origin/stage-5-ui-r2-learning-path..HEAD`）。
口径与取舍全部写在决策 `docs/decisions/2026-11-05-dialog-typography-and-wordbook-token-cleanup.md`，
本文件只记「做了什么、验到什么、还欠什么」，不重复决策正文。

## 1. 本次目标

用户批准的可见面收口批：弹窗排版统一（三档口径 + `DialogPanel` 高度上限与滚动 + 私有框接共享件）、
`WordBookWordsScreen` 补完（清 `Mint*` 与手搓字号）、逐词朗读明确裁决。取证 = 模拟器截图 + 用户目视，
**不新增源码正则闸**，门禁每批一次。

## 2. 完成了什么

- 三档口径（标题 `AppType.Title` / 正文 `AppType.Body` / 出口 `AppType.ButtonLabel`）落到
  `components/ConfirmDestructiveDialog.kt`、`components/OutboundConfirmDialog.kt` 与四屏
  （`CardDetailScreen` 笔记框、`AppScreen` 切书迁移框、`MyWordsScreen` 掌握回执、
  `WordBookTransferScreen` 导出选择框）；`ReminderSettingsScreen` 提醒时间框保留 M3 `AlertDialog`，
  只对齐标题档（理由在决策 §4）。
- `DialogPanel` 补「高度上限 → `verticalScroll` → `padding`」链与 `dialog_panel` 锚。上限走过三版：固定
  560.dp（把既有护栏打红）→ `LocalConfiguration.screenHeightDp`（红点挪到出口按钮）→ 终版
  `BoxWithConstraints.maxHeight`＝对话框窗口给面板的约束；宿主断言随之改成「正文首屏可见 + 出口滚到可见后仍可点」，
  三版实测数字与理由都写在决策 §3。
- `WordBookWordsScreen.kt`：7 枚 `Mint*` → `appColors()` / `appDomain(...)`，3 处手搓字号进六档。
- 新增 connected 护栏 `app/src/androidTest/…/ui/DialogTypographyEvidenceTest.kt`（4 例：三档字号比与字重、
  面板 ≤ 物理屏高 + 带滚动 + 出口「滚前必须不可见 / `performScrollTo()` 后可见」、笔记框真复用共享件）。
- 既有判据只随档位改两处、阈值不放宽：`OutboundDialogFamilyTest` 正文锚 `Footnote`→`Body`；
  `LearningSetupLayoutContractTest` 的 `AppType.Title` 禁令收窄到可滚表单段并逐字钉住那唯一一枚。
  `WordBookWordsScreenTest.letterGroupsRenderWithStickyHeadersAndRows` 改按下标滚到视口外的组
  （理由与先例都在文件内注释）。
- 逐词朗读裁决 = **已落地，零改动**（批 3 Task 26 Step 1，提交 `759126f`）；此前审计报成「被漏掉的项」
  来源是计划文档复选框缺勾，不是代码事实。同批更正：`Mint*` 是主题感知的 composable 桥，
  词书单词页此前**不是**深色档缺陷现场。

## 3. 验证记录

- 门禁（固定三任务、离线，上限改完后重跑）：`gradle.bat --offline --no-daemon :app:assembleDebug
  :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin` → `BUILD SUCCESSFUL`；
  枚数 `python .git/qa-scratch/tally.py .git/qa-scratch/gate-2026-11-06d.tally`
  → `classes=321 tests=2370 failures=0`（与上一批 `87034ae` 同数，本批只改判据内容、不增 JVM 用例）。
  **踩到一条脚本坑**：`tally.py` 的入参是**输出**文件，先前几批把门禁日志名当参数传，日志被覆盖成一行
  （`gate-2026-11-06b/c.log` 已只剩 tally 那行）；留档改用 `.git/qa-scratch/gate-2026-11-06d.log`
  + `.git/qa-scratch/gate-2026-11-06d.tally` 两个名字。
- connected（`emulator-5554`，`adb install -r` + `am instrument`，未用 `connectedDebugAndroidTest`）：
  对话框族（`DialogTypographyEvidenceTest` + `AppScreenOverlayContainerTest` + `ConfirmDestructiveDialogTest` +
  `OutboundConfirmDialogTest`）17 例、页面族（`CardDetailScreenTest` + `WordBookWordsScreenTest` +
  `MyWordsScreenTest`）67 例，两族合并一次 84 例。
  - `font_scale = 2.0`：84 例**全绿**（`.git/qa-scratch/connected-final-2.0b.log`，终版构建 + 终版判据）。
  - `font_scale = 1.0`：同集合 81 绿 + 3 红，3 红**全是** `AppScreenOverlayContainerTest` 自带的
    「设备档字号没进对话框窗口」反空转闸（`assertScaleTookEffect`）自报前提不满足——那三条名字里就写着
    `AtDoubleFontScale`，只在 2.0 档跑；记进日志而不是混进「全绿」。
- 上限改完后的 2.0 复跑新抓到一条**与本批改动无关**的既有护栏红：
  `CardDetailScreenTest.askAiEntryInvokesCallback`（1.0 绿、2.0 恒红两次，`expected:<1> but was:<0>`）。
  机制＝本屏是整页 `verticalScroll`，2.0 下「问 AI」药丸在首屏之外，屏外节点被裁成 0×0，
  `performClick()` 落点不在它身上；处置＝点击前补 `performScrollTo()`，与本类「笔记管理」那条
  `performScrollTo().performClick()` 同先例（阈值一条没松，仍判回调恰一次）。
- 双字号 PNG（不入库）：`.git/qa-scratch/dialog-evidence-{1.0,2.0}/`（4 张 × 2 档，终版构建重取）、
  `.git/qa-scratch/wordbook-rail-{1.0,2.0}/`（2 张 × 2 档；词书单词页源码在那之后未再改动，本批只动判据）。
- detekt/ktlint 未跑（本仓验收门禁是单测，存量债 1500+ 条不是回归）。
- 设备侧收尾：取证用的 `adb root` 已复原（重启后 `id` 回 `uid=2000(shell)`）、`font_scale` 回 1.0、
  设备内取证目录已删。**收尾代价记一句**：`stop adbd; start adbd` 那两条是同一 shell 的子进程，
  第一条就把 shell 杀了，第二条永远不执行；本机 `C:\Windows\adb` 是 1.0.26 老客户端、没有 `unroot`，
  新客户端（`C:\adb\platform-tools`，1.0.41）与老 server 混用会互相踢掉，最终只能
  `taskkill` 两个 emulator 进程后按原命令行冷启（`-no-snapshot-load`，不 `-wipe-data`），
  应用与测试包、应用数据都还在。

## 4. Git 状态

- 分支 `stage-5-ui-r2-learning-path`；本批三枚提交：`a01cbab`（弹窗排印 + 面板上限 + 护栏 + 决策文档）、
  `d227bc2`（词书单词页令牌收口 + 该 connected 判据）、`3b233db`（面板上限回归修复 + 判据口径 + 本报告）。
- **未推送**（用户口径「推送等我发话」）、**未合 main**。未推送枚数现算：
  `git rev-list --count origin/stage-5-ui-r2-learning-path..HEAD`（本文件所在那枚提交落地后 = 10）。

## 5. 未完成 / 遗留问题

- 用户目视：本批 12 张 PNG + 此前累计的十一项真机目视（任务 #14）仍未做。
- 本批实测出的一条产品事实（不是缺陷，但值得用户拍板）：`font_scale = 2.0` 下，正文长的确认框
  （如清空学习记录）**必然**要滚一下才够得着两枚出口——探针证明这一屏的自然高本来就超过对话框窗口
  （40 行级联 3592dp vs 窗口约束 659.05dp），把上限取到多大都改变不了；现在的形状是「正文首屏可见 +
  出口滚到可见」。若要出口**常驻可见**，得给 `DialogPanel` 改成「正文滚动 + 动作粘底」，那是本批范围外的组件改动。
- 仍开放的产品决策（沿用上一批，未变）：4 张未实现的 AI 能力卡做哪几张；生图是否落收藏；
  图片是否也改「按域名一次」；每日文章生成上限数值。
- 批 2/批 3 结转清单（`docs/handoff/2026-11-05-batch2-verification-gaps-todo.md`）未动。

## 6. 下一任接手须知

- 第一步：读决策 `docs/decisions/2026-11-05-dialog-typography-and-wordbook-token-cleanup.md`
  与本文件，再决定「粘底动作」要不要做。
- 坑（本批实测）：① 仓库源码是 CRLF，源码契约里的多行锚点**不要带 `\n`**，会假红；
  ② `LazyColumn` 只组合视口内的项，对未组合的 tag 调 `performScrollTo()` 直接抛「找不到节点」，
  要走 `performScrollToIndex(下标)`；③ `SemanticsNode.isVisible` 不算滚动容器裁剪，
  判「被上限裁掉的按钮」必须用 `assertIsDisplayed`；④ 这一版 `androidx.compose.ui.test` 没有
  `assertHeightIsAtMost`，按界取 `getUnclippedBoundsInRoot()` 的 `bottom - top` 自证；
  ⑤ **整页 `verticalScroll` 的屏**在 2.0 下也会把首屏外的节点裁成 0×0，`performClick()` 静默落空
  （表现为回调计数停在 0，而不是报错）——点击类断言在放大档一律先 `performScrollTo()`。
- **设备档字号与判据档要分开**：`AppScreenOverlayContainerTest` 那族自带 `assertScaleTookEffect`，
  设备 `font_scale` 不是 2.0 时它们**必然**红——那是反空转闸在报前提，别当成回归，也别把它们拖进 1.0 轮凑「全绿」。
- `tally.py` 的第二个参数是**输出**路径（`io.open(argv[1], 'w')`）：拿门禁日志名去当参数会把日志抹掉。
  枚数取自 `app/build/test-results/testDebugUnitTest/*.xml`，所以数字本身没错，只是日志没了。
- 环境口径不变：本机 Gradle 8.13 解压版、离线三任务门禁、instrument 只在 `emulator-5554`、
  真机 `bf353dda` 只手工试用；API 30 模拟器读 `/sdcard/Android/data/...` 要先 `adb root`。
  **复原比原先记的麻烦**：PATH 上的 `adb` 是 `C:\Windows\adb`（1.0.26，无 `unroot`），
  `C:\adb\platform-tools\adb`（1.0.41）与老 server 混用会互踢；`adb shell "stop adbd; start adbd"`
  第一条就杀掉自己那条 shell，第二条不会执行。可用路径 = 控制台 `avd stop/start` 只是暂停/恢复（救不回 adbd），
  实测只有 `taskkill` 两个 emulator 进程后按原命令行冷启
  （`C:\adb\emulator\emulator.exe -avd xtool-s04-api30 -no-window -no-audio -no-boot-anim -gpu auto -port 5554 -no-snapshot-load`，
  **不要** `-wipe-data`），起来后 `id` 回 `uid=2000(shell)`、应用与测试包和应用数据都还在。

## 7. 用户确认过的决策

- 「按你看得见的结果切批；文档砍到一份（开工 10 行、报告整轮结束才写）；TDD 只留给有行为的代码，
  纯排版用 connected + 截图，不加源码正则闸；门禁每批一次，不是每项一次。」
- 弹窗排版统一 + 词书单词页补完 + 逐词朗读裁决这一批的口径（原话批准「可以可以」）。
- 不推送、不合 main，逐批请示；验收以单测全绿为准。
