# 2026-11-09 设计系统收口四件套 工作报告

（m01343 「全部都做」：把上一轮报告末尾提的四件后续一次做完）

## 1. 本次目标

用户对上一轮列出的四条后续建议回复「全部都做」——即（1）当日计划/到期集合刷新，（2）Card 圆角 20↔18dp（B3），
（3）词库分段控件并入共享件，（4）屏侧裸圆角加守卫。

**追加（同日 m01571）**：用户对本报告 §5 第 1 条的文案建议回「**可以**」，于是本批还多出第五件——在首页
「接下来」卡下方补一枚「今日计划当天固定」的提示（纯文案、不动规则）。归位见 §2.2 末两条、§3、§5、§7。

## 2. 完成了什么

### 2.1 裁定（不改代码，只落文档）

- **（1）当日计划的到期集合维持不可变快照。** 改法被否决：`AGENTS.md`《学习与解锁规则》明写
  「每日计划…生成不可变快照」「当日计划生成后的新到期词不加入该计划」「已提交反馈后不得静默重建」。
  正解是「用户明确重建」通道（学习计划里换词书并保存 → `activeWordBookId` 变化 → 下次进入学习时
  `GetOrCreateTodayPlanUseCase` 重算并 `replaceForDate`），设备上已实证。详见
  `docs/decisions/2026-11-09-today-plan-same-day-immutable-ruling.md`。
- **（2）B3「Card 圆角 20→18dp」关闭为「不改」。** 18dp 出自被 2026-10-29 原型对齐批 0 取代的旧口径；
  现行权威是圆角五档 10/14/20/26/999（`docs/superpowers/specs/2026-10-17-ui-redesign-design.md` §2.3 第 159-162 行，
  `docs/decisions/2026-10-29-ui-prototype-token-alignment.md:75`），`AppShape.Card` = 20dp 就是它。
  详见 `docs/decisions/2026-11-09-card-radius-keep-20dp-b3-closeout.md`。

### 2.2 代码

- `ui/components/SegmentedControl.kt`：新增两个可选参数 `itemContentDescriptions: List<String>? = null`、
  `announceSelection: Boolean = false`（默认值不动设置页调用点）。段项链变成
  `selectable → testTag → semantics{ contentDescription / stateDescription } → padding`。
- `ui/WordBankScreen.kt`：删掉自画的 `Row`（`tint2` 凹槽 + 13dp 轨道 + 10dp 段项 + `AppType.Body` 半粗 +
  手写 `stateDescription`），改挂**一枚** `SegmentedControl`，屏侧只交标签 / tag / 读屏文本三张表 +
  `announceSelection = true`。`wordBankSegmentLabel` / `wordBankSegmentDescription` / 不可用标记「—」原样保留。
- `ui/PhotoTranslationScreen.kt`：输入框 `RoundedCornerShape(14.dp)` → `AppShape.Button`（同值具名），
  文件内已无 `RoundedCornerShape` / `CornerSize`。
- `ui/TodayPlanScreen.kt`：搜索框 `RoundedCornerShape(14.dp)` → `AppShape.Button`；计划图例 8dp 方点的
  `RoundedCornerShape(2.dp)` **故意保留**并记入豁免表（五档无 2dp，10dp 会把方点画圆）。
- `test/.../ui/WordBankScreenContractTest.kt`：新用例「the four segments come from the shared control」。
- `test/.../ui/ContainerContractTest.kt`：新字段 `BARE_RADIUS_CALL` / `BARE_CORNER_SIZE_CALL` +
  `rawRadiusAllowance` + 新用例「no listed screen hand rolls a corner radius」（逐屏 29 屏、反空转样本、豁免表自检）。
- `ui/TodayPlanScreen.kt`（**追加，m01571**）：在「接下来」那条 `SoftCard(today_plan_secondary_card)` 之后、
  未学完分支收尾之前补一枚 `Text`——`testTag("today_plan_fixed_hint")`、`AppType.Footnote`、`colors.text2`，
  文案「今日计划当天固定：新到期的词不会自动加入。要按现在的到期情况重排，到「学习计划」重新选择词书并保存。」。
  其上方一并留 7 行注释记：这条规则的出处（`AGENTS.md`《学习与解锁规则》不可变快照 / 已提交反馈后不得静默重建）、
  它在用户侧留下的「看不见的出口」、唯一合法的重建通道（`GetOrCreateTodayPlanUseCase` 只认 `activeWordBookId` 变化）、
  以及为什么不提遗忘预测。落点在卡外：卡内是四条入口，塞进去会被读成第五条。
- `test/.../ui/TodayPlanFixedHintContractTest.kt`（**追加，新文件 3 用例**）：①整块形状 + 文案内容三钉
  （必须含「当天固定」「不会自动加入」「学习计划 … 重新选择词书并保存」，且**不许**出现「分钟 / 小时 / 遗忘」）；
  ②`today_plan_fixed_hint` 恰一处且索引在 `today_plan_secondary_card` 之后；③反空转样本
  （换排印档 Footnote→Body、改标签名，都必须当场判红）。

### 2.3 新增文档

- `docs/decisions/2026-11-09-today-plan-same-day-immutable-ruling.md`
- `docs/decisions/2026-11-09-card-radius-keep-20dp-b3-closeout.md`
- `docs/decisions/2026-11-09-wordbank-segments-on-shared-segmented-control.md`
- `docs/decisions/2026-11-09-today-plan-fixed-hint-copy.md`（追加，m01571）
- 本报告

## 3. 验证记录

- `gradlew.bat :app:testDebugUnitTest --offline --tests "…ContainerContractTest" --tests "…WordBankScreenContractTest"`
  → BUILD SUCCESSFUL in 21s（先 RED 后 GREEN 的一轮：新用例先按旧码写不出来，改完码才通过）。
- `gradlew.bat :app:testDebugUnitTest --offline` → BUILD SUCCESSFUL。
  测试报告合计 **348 个测试类 / 2603 个用例，0 失败 0 跳过**（上一轮 2601，本批 +2）。
- `gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.englishlearning.ui.WordBankScreenTest`
  （设备 Xiaomi M2102J2SC · `bf353dda`）→ **Starting 4 / Finished 4 全绿**，BUILD SUCCESSFUL in 39s。
  覆盖：四格 tag 存在、`assertHeightIsAtLeast(48.dp)`、`assertTextEquals("词书"/"生词 3"/"错词 —"/"收藏 0")`、
  `StateDescription`「已选中」/「未选中」、分段切页、页头「返回」contentDescription。
  这也是「共享分段控件替换后行为不退化」的直接证据（48dp 命中区与读屏语义都由共享件保证）。
- 设备冒烟（同一台机，`install -r` 后走完 onboarding → 小学英语词汇 · 保存计划 → 我的 → 收藏）：
  词库页四分段渲染为**一枚药片轨道**「词书 | 生词 0 | 错词 0 | 收藏 0」，四格 contentDescription 为
  「词书 / 生词 0 / 错词 0 / 收藏 0」（读屏文本走 `itemContentDescriptions`，不是标签本身），
  点击「生词」内容区正确切到生词 pane（「生词 / 0 个 / 还没有生词，提交“不认识”后会自动加入。」），
  返回后切「收藏」也正确；无崩溃、无布局挤压。截图留在 `.workbuddy\wbank_fav.png` / `.workbuddy\wbank_new.png`（未入库）。
  注：本机 `uiautomator dump` **不输出** `selected` / `stateDescription`（连「返回」按钮都是 `selected=false`），
  所以「已选中 / 未选中」这条只能由 Compose 侧 connected 测试作准（已绿）。
- （追加，m01571）`gradlew.bat :app:testDebugUnitTest --offline --tests "com.example.englishlearning.ui.TodayPlanFixedHintContractTest"`
  → BUILD SUCCESSFUL in 17s，该文件 3 用例 0 失败。
- （追加，m01571）`gradlew.bat :app:testDebugUnitTest --offline` → BUILD SUCCESSFUL in 33s，
  测试报告合计 **349 个测试类 / 2606 个用例，0 失败 0 跳过**（四件套时是 348 / 2603，追加这枚 +1 类 +3 例）。
- （追加，m01571）设备冒烟（同机 `bf353dda`，`install -r` 保留数据后打开首页）：提示渲染在「接下来」卡下方，
  文本节点 `[55,1942][1025,2051]` = **两行**（50 字，Footnote 档），左边距 55px 与卡内文字同一条 20dp 竖线，
  颜色为次级 `text2`，不与底部导航重叠；未滚动时它落在首屏折叠线以下（整页本就满屏，需滚一下才整段可见）。
  截图留在 `.workbuddy\home_hint.png`（未入库）。connected `TodayPlanScreenTest` 28 条**未重跑**：本改动只加一枚
  无点击语义的 `Text`，而该份测试里唯一的「枚数」判据是 `onAllNodes(hasClickAction())` 的闭合集合
  （静态文本不进该集合），其余都是逐 tag / 逐文案断言；实际渲染已由上面的设备 dump 直接确认。
- 设备侧的既有坑：onboarding 姓名框用 `adb shell input text` 会**延迟提交**（出现过清空前文字才落库的现象），
  稳妥做法是 `input text` 后等 2-3 秒再 dump 确认；键盘弹出会顶移按钮坐标，点击前重新 dump 取 bounds。
- 编译期只剩两条既有 deprecation：`PhotoTranslationScreen.kt:460`（`LocalLifecycleOwner`）、`:552`（`LocalClipboardManager`），
  与本批无关。

## 4. Git 状态

- 分支：无法确认。**沙箱内无 git 可执行文件**（`where git` 为空，Git for Windows / chocolatey / scoop 路径均不存在），
  但 `H:\EnglishLearningSoftware\.git` 存在。
- 结论：本批改动**未提交、未推送**。无 git 可执行文件，未提交 / 推送。
- 未提交文件：上述 6 个源码/测试文件（`SegmentedControl.kt`、`WordBankScreen.kt`、`PhotoTranslationScreen.kt`、
  `TodayPlanScreen.kt`、`WordBankScreenContractTest.kt`、`ContainerContractTest.kt`）+ 3 份决策文档 + 本报告；
  **追加（m01571）**：`TodayPlanScreen.kt`（二次修改）与新增的 `TodayPlanFixedHintContractTest.kt`、
  决策文档 `2026-11-09-today-plan-fixed-hint-copy.md`、以及本报告的这轮补充。

## 5. 未完成 / 遗留问题

1. ~~首页没有告诉用户「今日计划是当天固定的」~~ —— **已关闭（m01571，用户回「可以」）**：
   首页「接下来」卡下方已有 `today_plan_fixed_hint` 一枚 Footnote 提示，契约由
   `TodayPlanFixedHintContractTest` 钉住；裁定与文案口径见 `docs/decisions/2026-11-09-today-plan-fixed-hint-copy.md`。
2. `ui/ReminderSettingsScreen.kt` 仍用 M3 `AlertDialog` + Material `TimePicker`（已登记为「平台选择器例外」，
   不属收口范围）。
3. `TodayPlanScreen.kt` 计划图例 8dp 方点的 `RoundedCornerShape(2.dp)` 是唯一豁免；若将来 `AppShape` 增 2dp 档，
   记得同步删掉 `rawRadiusAllowance` 那一行。
4. 复习队列浮层是「打开时快照」，答题后不自动刷新；拼写测验不推进当日计划进度（均为设计如此，已在裁定文档记账）。
5. 已知 flaky：`AppScreenOverlayContainerTest.hostedBackupRowOpensTheHubAndBackReturnsToTheProfileHub`
   （`AppScreenOverlayContainerTest.kt:422`）批量跑偶发 `ComposeTimeoutException`，单跑必过，与本批改动无关。

## 6. 下一任接手须知

- 第一步：读本报告 + 三份决策文档；三条裁定（当日计划不可变、圆角 20dp、分段控件统一）**不要重新推翻**，
  否则每次走查都会重新讨论一遍。
- 分段控件是二手回归点：任何屏再自画一排「凹槽 + 段项」，`WordBankScreenContractTest` / `ContainerContractTest`
  会当场判红，别改测试去迁就屏。
- 设备侧坑：第三方输入法会让 `adb shell input text` 静默无效（先切 IME）；connected 测试会卸载重装、
  清掉学习数据。
- `AGENTS.md`《学习与解锁规则》是硬约束，任何「刷新当日计划」的念头先读它。

## 7. 用户确认过的决策

- 用户对上一轮列的四条后续建议回「**全部都做**」——即授权本批四件全部实施；其中（1）（2）因与 `AGENTS.md`
  硬约束 / 原型 spec 冲突，按「落裁定文档、不改代码」实施，并把理由写进决策文档。
- 用户此前已确认：卡片圆角争议（B3）由本次裁定结案——**维持 20dp**；若下次仍想要 18dp，需用户明确推翻本裁定。
- （m01571）用户对本报告 §5 第 1 条的文案建议回「**可以**」：批准在首页显示「今日计划当天固定…要按现在的到期情况
  重排，到「学习计划」重新选择词书并保存。」这句话，**只加文案、不动规则**。

---

## 附录：追加这枚提示的落点坐标（便于复核）

- 代码：`app\src\main\java\com\example\englishlearning\ui\TodayPlanScreen.kt` 第 517-529 行（7 行注释 + 一枚 `Text`）。
- 测试：`app\src\test\java\com\example\englishlearning\ui\TodayPlanFixedHintContractTest.kt`（LF，3 用例）。


---

## 附录二：m01676 三条（词卡吸底槽 / 详情页 / 动效）的收口坐标

用户 m01676 提三条（评分条位置太低、点模糊后详情页不许再点「下一个单词」、补动效治排版），m01880 回「可以全部都做」⇒ 三条一次落地。

- 源码改动 5 个：`ui/theme/AppTransition.kt`（+`FooterSwapEnter/Exit`、`OverlayRiseEnter/Exit`、`OverlayOffsetDenominator = 8`）、
  `ui/WordCardScreen.kt`（吸底槽抽出顶层 `WordCardFooterSlot`，两条互斥 `AnimatedVisibility` 淡换；两条各补 `surface` 底 + `hairlineTop` + `navigationBarsPadding`；回执条新增锚点 `word_card_receipt_bar`）、
  `ui/AppScreen.kt`（学习流详情宿主改 `AnimatedVisibility + overlayCard` 保末张卡退场；`onNext = null`）、
  `ui/WordCardViewModel.kt`（`hasNextDetail()` / `nextDetail()` 退役）、
  `test/…/ui/StickyFooterHairlineGuardTest.kt`（词卡台账 1 处 2 认领 → 3 处 5 认领）。
- 新增契约测试 `test/…/ui/DetailOverlayAndFooterSwapContractTest.kt`（5 例）。
- 决策文档 `docs/decisions/2026-11-09-word-card-footer-toolbar-and-detail-overlay.md`。
- 验证：`:app:testDebugUnitTest --offline` → **350 类 / 2611 用例 / 0 失败 / 0 跳过**（前基线 349/2606）；
  设备 bf353dda 实测：详情页 `下一个单词` / `card_detail_next` 命中 0 次，吸底条自铺白底到导航条起点（y2295），截图在 `.workbuddy/m01676_*.png`。
- 未提交、未推送（本机无 git 可执行文件），口径同本报告 §4。
