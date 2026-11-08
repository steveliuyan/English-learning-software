# 2026-11-08 QA3 A 组（许可页返回栈 + 动态字体顶档七处降级 + 收口补修 A2.8）工作报告

分支 `stage-5-ui-r2-learning-path`。上游走查册：`docs/verification/visual-walkthrough-2026-11-08.md` §A。

## 1. 本次目标

用户 2026-11-08 对 QA 第三轮走查册 §A 挂「待拍板」的条目裁决 **「全部都做」**：A1（许可页返回键把用户留在原地、
再按一次退出应用）与 A2.1…A2.7（`fontScale = 3.0` 顶档七处破坏）一律立为缺陷修，逐条走 TDD（先红后绿），
收口跑门禁 + connected + 设备实拍，并写决策/Spec/走查册/台账四份文档。

收口复测（3.0 逐屏实拍）另撞出第八处 **A2.8**，同日用户拍板「立缺陷修，同批口径」，本文件末节与
§2 末尾的「收口后追加」记录它的红/绿、门禁与实拍；它沿用同一阈值口径，不另起一套。

## 2. 完成了什么

**A1 许可页返回栈**（`AppScreen.kt`）
`showLicenses` 补齐「四件套」：进 `overlayOpen`、专属 `BackHandler`（声明排在 `showAbout` 之后）、
挂载分支 `else if (showLicenses)` 移到 `showAbout` 之前、入口不再顺手 `showAbout = false`。
系统返回键恢复「一级一级往回退」，页内返回落回关于页而不是「我的」。

**A2 顶档降级**（阈值一律严格 `> 2f`，2.0 = AC-R-07 钉住的验收档一像素不动；口径「只换摆法/换档，
不换内容、不换术语、不增删控件」）

| 条 | 落点 | 改法 |
|---|---|---|
| A2.5 | `WordCardScreen.kt` | `wordCardPinsRevealAction`：`> 2f` 时「显示释义」常驻吸底区（唯一出口不再靠滚才够得着） |
| A2.3 | `WordCardScreen.kt` | `wordCardLemmaUsesCompactTier`：`> 2f` 词头 `AppType.Display` → `StrongTitle`（三行 449.5dp 换两行 192dp，整词进首帧视口） |
| A2.4 | `WordCardScreen.kt` | `wordCardStacksFeedbackTiers`：`> 2f` 三枚评分由 `weight(1f)` 横排改整宽上下堆叠、每枚一行；档位文案上移成 `FeedbackTiers` 单点清单，两分支共用 |
| A2.1 | `TodayPlanScreen.kt` | `todayPlanRemainingMovesBelowCount`：`> 2f` 脚注「还剩 N 个词」挪到大数下方一行，计数行保持单行（根因是**挤压**不是裁切） |
| A2.2/A2.7 | `AppBottomBar.kt` | `Row.height(IntrinsicSize.Max)` + 每格顶对齐 + `bottomBarSlotVerticalBreathing` 按档余量：四格同一条矩形、字形落在同一条横线 |
| A2.6② | `AiLearningScreen.kt` | `aiFeatureGridColumns`：`> 2f` 两列降一列，卡标题不再折两行、状态徽标回可视区 |
| A2.6① | `components/AppScreenHeader.kt` | 副标题腿 `maxLines = 3` + `Ellipsis`（取 3 不取 2：那句 31 字说明在 2.0 档恰好 3 行，压到 2 就是为顶档好看而让钉档丢字） |
| A2.6③ | — | **登记不修**：分段头压在可滚页折叠线上是滚动必然，与 A2.5 那类「藏唯一出口」的流程断裂不同类 |

**测试资产**
- 新增 JVM 契约类 2 枚：`LicensesHostBackStackContractTest`（返回栈四件套 + 分支顺序）、
  `TodayPlanLayoutContractTest`（阈值与单一渲染点）。
- 新增 connected 判据 6 枚：词卡 2（`lemmaKeepsEveryLetterLaidOutAndVisibleAtTopFontScale`、
  `feedbackTiersStayWholeAndInsideTheWindowAtTopFontScale`）、今日计划 1、底栏 2（整幅 + 320dp 窄屏）、
  AI 主页 1（三行封顶）；A1 侧 `AppScreenTest` 2 枚按真机同一条分派链驱动（`pressSystemBack` 注 KeyEvent）。
- 同步既有判据：`WordCardLayoutContractTest`（+2 条：阈值成套、换容器不重复决策/文案）、
  `WordCardScreenContractTest`（`FeedbackTiers` 清单取样）、`AiLearningLayoutContractTest`、
  `AppBottomBarContractTest`、`ProfilePageContractTest`（许可入口不再关关于页）。

**文档**
- 新增决策 `docs/decisions/2026-11-08-max-font-scale-layout-degradation.md`（8 节，含「不做什么」）。
- Spec 05 补 **AC-R-24**；版面重设计 Spec 补 §5.1 逐屏降级表。
- 走查册 §A 逐条改判 + 四处根因补正；台账 `docs/verification/ui-redesign-2026-10.md` 补 QA3 A 组一节。

**收口后追加（用户拍板「立缺陷修，同批口径」）——A2.8**：
- 第七枚判档函数 `wordCardStacksPronunciationRow(fontScale) = fontScale > 2f`；顶档把发音行从
  `Row { Column(weight(1f)){音标;词性} ; SpeakerButton }` 换成 `Column { 注脚列(整幅) ; 药片 }`，
  钉档以内走原 `Row` 一像素不动。注脚两枚 `Text` 抽成 `WordCardPronunciationFootnotes`，
  药片抽成同一枚 `val speakerPill: @Composable () -> Unit` 槽位——**两个分支各只有一个渲染点**，
  `SpeakerButton(` 调用窗口仍恰 2 枚（治理闸不许它变 3）。
- 新 connected 判据 `WordCardScreenTest.pronunciationKeepsTheIpaOnOneLineAtTopFontScale`；
  契约闸同步：阈值三值同判多一枚函数、`fontScale > 2f` 计数 3→4、`word_card_ipa`/`word_card_part_of_speech` 各恰 1 处。

## 3. 验证记录

**RED（先红，逐条留读数）**

| 判据 | 红样 |
|---|---|
| `AppScreenTest.systemBackFromTheLicensesPageWalksBackToTheAboutPage` | 第一下返回后 `licenses_screen` 仍在，第二下整屏消失（落到最低优先级「切回学习 tab」处理器） |
| `WordCardScreenTest.lemmaKeepsEveryLetterLaidOutAndVisibleAtTopFontScale` | 词头 `getUnclippedBoundsInRoot().bottom = 561.9048dp` > 视口下沿 `332.1905dp`（屏根 bottom 659.4286dp） |
| `WordCardScreenTest.feedbackTiersStayWholeAndInsideTheWindowAtTopFontScale` | `「不认识」排成 2 行 expected:<1> but was:<2>` |
| `TodayPlanScreenTest.summaryCountRowStaysSingleLineAtTopFontScale` | 计数行 `lineCount` 超 1，`0` 被挤掉 |
| `AppBottomBarTest.slotsStayInsideTheBarAtTopFontScale` | AI 格上沿 1442px、其余 1505px；字形线差 24dp |
| `AiLearningScreenTest.headerSubtitleStaysWithinThreeLinesAtTopFontScale` | 副标题 `lineCount = 4` |
| `WordCardScreenTest.pronunciationKeepsTheIpaOnOneLineAtTopFontScale`（A2.8，收口后补） | `音标「/prəˈɡresɪv/」排成 3 行 … expected:<1> but was:<3>`——红在旧摆法的真机实拍路径上，不是人为回拨阈值 |

**变异探针 2 枚**（验 JVM 新契约不是空断言，跑完逐处 Edit 回写撤销，**未用 `git checkout --`**）
- 阈值 `> 2f` → `> 5f`：`23 tests completed, 2 failed`（正是本批新增那两条）。
- 堆叠分支直写三枚 `FeedbackButton`（绕开 `FeedbackTiers`）：`23 tests completed, 3 failed`。

**门禁**（顺序不可颠倒，`--rerun-tasks` 才算真跑）

```bash
GRADLE=/c/Users/steveliuyan/.gradle/wrapper/dists/gradle-8.13-bin/5xuhj0ry160q40clulazy9h7d/gradle-8.13/bin/gradle.bat
"$GRADLE" :app:assembleDebug :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin \
  --no-daemon --no-build-cache --rerun-tasks 2>&1 | grep -aE 'FAILED|expected|BUILD|tests completed|^e:' ; exit ${PIPESTATUS[0]}
```

`BUILD SUCCESSFUL in 4m 15s`；本轮新 XML 实跑 **334 个测试类 / 2479 条 / 0 失败 / 0 错误 / 0 跳过**
= 上一基线 332 类 / 2467 条 **+2 类 / +12 条**，基线只增不减。
detekt/ktlint 非门禁（依据 `docs/decisions/2026-09-19-static-analysis-and-test-baseline.md`，存量约 1580 条勿当回归），本批未跑。

**A2.8 收口后重跑同一门禁**（改名一枚契约用例方法名之后，按「门禁必须覆盖最终字节」的口径整 trio 再跑一遍，不数陈旧 XML）：
`BUILD SUCCESSFUL in 7m 8s`、61 项任务全部 executed（`--rerun-tasks` 生效）；
XML 现算 **334 类 / 2479 条 / 0 失败 / 0 错误 / 0 跳过**，且 334 份文件全部落在本轮时间窗内（`stale=0`）。
枚数与上一轮**持平**是如实结果：A2.8 只往既有的两枚 JVM 契约方法里加了断言，新增的那枚 connected 方法不进 JVM 门禁。

**connected**（`adb install -r` 双包 + `am instrument`，禁 `connectedDebugAndroidTest`；`-s emulator-5554`）
- 顶档降级组：`OK (15 tests)` = `WordCardScreenTest` 8（新增 2 + A2.5 + 2.0 档护栏 + 4 回归）
  + `TodayPlanScreenTest` 1 + `AppBottomBarTest` 2 + `AiLearningScreenTest` 4。
- A1 返回栈：`OK (2 tests)`。
- 一条如实记录：`systemBackFromTheLicensesPageWalksBackToTheAboutPage` 在**合并跑**里红过一次
  `ComposeTimeoutException … after 5000 ms`；单跑两次 `OK (1 test)`、再配对 `OK (2 tests)`。
  判定为装包后首轮冷启动/dexopt 抖动，不是生产回归。

**connected（A2.8 收口补跑，同一台 `emulator-5554`、同一套 `install -r` 双包）**
- `WordCardScreenTest#pronunciationKeepsTheIpaOnOneLineAtTopFontScale` 单跑 `OK (1 test)`（RED 见上表）。
- `WordCardScreenTest` **整类**复跑 `OK (27 tests)`——本批顶档那几条与 2.0 档护栏同在一类，改容器后全绿。
- 周边回归：`WordCardPronunciationControlsTest OK (3 tests)`、`WordCardRevealFocusTest OK (4 tests)`、
  `AppScreenTest` 四枚发音路由/离场停播用例 `OK (4 tests)`（1.0 档，证明换容器没动播控链路与 tag）。

**设备实拍**（`.git/qa-scratch/qa3-a2-final/`，**不入库**；3.0 与 1.0 各一册，截屏 + `uiautomator` 层级成对）
- 词卡 3.0：词头 `progressive` 两行全出、三档整宽堆叠每枚一行、揭晓态释义与三档全在窗口内、出口常驻。
- 今日计划 3.0：`还剩 21 个词` `[106,330][678,477]` 单行落在大数之下（1.0 同条仍在右侧同行 → 降级只在 `> 2f` 生效）。
- 底栏 3.0：四格同为 `[*,1410][*,1778]`，`AI 助学` 标签落在本格内（1.0 四格 `[*,1626][*,1794]`）。
- AI 主页 3.0：功能卡单列（`长难句分析` `[53,248][1027,1103]` 拿整幅），副标题 3 行封顶带省略号（1.0 双列）。
- 收工前设备已复原：`font_scale = 1.0`（实测回读 1.0），`wm size/density` 保持 Physical 无 Override。

**设备实拍（A2.8 收口补拍，同一目录）**：3.0 档词卡正面音标 `[100,311][624,426]` **一行**、词性一行、
药片 `[100,574][719,745]` 单独一行完整可点（`fs300-a28-wordcard-front.png` / `fs300-a28-scrolled.png`）；
1.0 档音标 `[100,518][275,558]` + 药片 `[677,499][980,625]` 与改动前逐位一致（`fs100-a28-wordcard-front.png`），
即降级只在 `> 2f` 生效、钉档一像素没动。拍完 `font_scale` 再次复原 1.0（回读 1.0）。

## 4. Git 状态

- 分支 `stage-5-ui-r2-learning-path`；本批 5 枚提交（基线 = 上一远端 tip `109c472`）：
  - `1bd70a4` fix(ui): QA3 A1 许可页返回栈四件套
  - `e5d8311` fix(ui): QA3 A2.3/A2.4/A2.5 词卡顶档三处降级
  - `2ed5776` fix(ui): QA3 A2.1/A2.2/A2.6/A2.7 今日计划、底栏与 AI 主页顶档降级
  - `536ff2f` docs(ui): QA3 A 组决策册 + AC-R-24 顶档口径 + 走查册/台账收口
  - `912ee64` docs(handoff): QA3 A 组工作报告（本文件）
- **已推送并核验**（用户 2026-11-08 请示后批准）：
  `git -c credential.interactive=false ls-remote origin stage-5-ui-r2-learning-path` 回
  `912ee649609e1a928ff227c23f78941e34358189`，与 `git rev-parse HEAD` **逐字相同** → 远端备份账写实。
  分支不合 `main`（用户口径）。
- 工作树无未提交的源码改动；证据（截图与层级文本）全部在 `.git/qa-scratch/qa3-a2-final/`，按红线不入库。
- **A2.8 收口追加三枚提交，已推送并核验**（用户 2026-11-08 请示后批准）：
  - `7f48be1` fix(ui): QA3 A2.8 词卡顶档发音行换容器
  - `f5b240e` docs(ui): QA3 A2.8 拍板与修法入决策册、AC-R-24、走查册与台账
  - `b9bce11` docs(handoff): QA3 A2.8 收口补记入 A 组工作报告
  `git -c credential.interactive=false push origin stage-5-ui-r2-learning-path` 回 `912ee64..b9bce11`；
  随后 `ls-remote` 回 `b9bce117bbe8e770c4f1f4c7cfd39f30c5d5228f`，与 `git rev-parse HEAD` **逐字相同**，
  `git status -sb` 不再显示 ahead → 异地备份账写实。
  本条补记本身另成第 4 枚 `docs(handoff)` 提交随后推送，故下一棒读到的远端 tip 会比 `b9bce11` 新；
  现算口径：`git -c credential.interactive=false ls-remote origin stage-5-ui-r2-learning-path` 对 `git rev-parse HEAD`。

## 5. 未完成 / 遗留问题

- **A2.8 已修（本批收口复测新发现的第八处，用户 2026-11-08 拍板「立缺陷修，同批口径」）**：3.0 档词卡正面音标被挤成三行断字。
  `SpeakerButton` 的可见文字与朗读文案同源，顶档下连字形长到 472px = 180dp
  （节点 `[466,556][938,727]`），把 `Row { Column(weight(1f)){音标;词性} ; SpeakerButton }` 另一侧挤到 89.6dp，
  `/prəˈɡresɪv/` 断成 `/prə` `ˈgres` `ɪv/`，词性 `adj.` 掉出首帧。
  修法与判据见 §2 末节；修后 3.0 实拍音标 `[100,311][624,426]` 一行、药片 `[100,574][719,745]` 单独一行完整可点。
- **A2.8 留下一条没修也没改差的旧账**：顶档药片在首帧之下、要滚一下才够得着（改前层级里连药片节点都没有）。
  出口由 A2.5 常驻，故按 A2.6③ 的同一分界记为观感取舍、不钉判据；要不要进一步把药片也钉进吸底区，待用户目视后定。
- **待用户目视**（本棒不替人眼判）：A2.4 三档堆叠后吸底区变三行的观感；A2.6① 顶档副标题末行省略号；
  A2.7 顶档 `AI 助学` 仍折两行（另一条腿是截断成 `AI …`，已否）；A2.8 修后顶档卡面多一行、药片需滚一下。
- `showRecoveryExport` 与许可页同族的返回栈形态：本批只钉了 licenses 一处，其余同族入口仍待逐条核。
- 结转未动：#14 真机四项目视、#38 批 D 页面转场、AI 页开放项（每次都问 / 生图不落盘 / nsc / 按域名一次口径）。
- 走查册 §A3…§F 其余组（长正文确认框、深色半迁移残留、B/C/D 组）不在「全部都做」的 A 组范围内，仍按原状态。

## 6. 下一任接手须知

1. 第一步：读本文件 + `docs/decisions/2026-11-08-max-font-scale-layout-degradation.md` +
   走查册 §A（含 A2.8）+ 台账 `docs/verification/ui-redesign-2026-10.md` 末节。
2. A2.8 已按这套口径落地，下一棒若再加顶档降级就照它的形状抄：阈值仍是屏侧自有纯函数 `> 2f`，
   判据用 `CompositionLocalProvider(LocalDensity provides Density(d, fontScale = 3f))` 模拟顶档，
   **换容器时不许把控件复制成两枚**——两分支共用同一枚 `@Composable` 槽位/同一份清单，
   并把「调用窗口计数」写进契约闸（本屏 `SpeakerButton(` 恰 2 枚、`FeedbackTierSpec(` 恰 4 处就是这道闸）。
3. 顶档量法三坑（本批实测，别再踩）：
   - `getUnclippedBoundsInRoot()` 返回 `DpRect`，`fetchSemanticsNode().boundsInRoot` 返回 px `Rect`，**永不相等**；顶档判定一律 Dp 对 Dp。
   - 字级文本判 `lineCount` 要 `useUnmergedTree = true`，否则拿到的是合并节点、行数不对。
   - `uiautomator` 的 `bounds` 是节点与父容器的**可视交集**：被裁掉的永远「不越界」，被挤没的行干脆不出现在层级里
     ——A2.3 / A2.6③ / A2.8 三处都是自动判据报绿、实拍才看到。顶档屏一律「截屏 + 层级 + 人眼」三样都交。
4. 契约测试取样坑：本机 `core.autocrlf=true`，源码里找「声明结束」用 `source.indexOf("\n\n")` 会返回 −1 而假红，
   一律按 `source.lines()` 取样。
5. 门禁坑：`--rerun-tasks` 才算真跑；跑一半别数 `test-results/*.xml`；管道末必须 `; exit ${PIPESTATUS[0]}`；
   变异探针撤销禁用 `git checkout --`（工作树常有在途改动）。
6. 设备：instrumented 只在 `emulator-5554`（真机 `bf353dda` 只手工试用，MIUI 会自动卸载 adb 装的包）；
   走查完把 `font_scale` 复原 1.0。GBK 控制台会把 IPA 字符打崩，层级文本用 `PYTHONIOENCODING=utf-8` 重定向落盘。

## 7. 用户确认过的决策

- **「全部都做」**（2026-11-08）：走查册 §A 挂待拍板的条目一律立为缺陷修，不做「只修流程断裂、观感项搁置」的取舍。
- 统一降级口径：**只换摆法/换档，不换内容、不换术语、不增删控件**；阈值一律严格大于 2.0，AC-R-07 钉的 2.0 档一像素不动。
- A2.7 底栏**不取标签截断**（`AI 助学` → `AI …` 等于把目的地名字改没），取「格高随字号 + 四格等高 + 顶对齐按档留余量」。
- A2.6③ 分段头压在可滚页折叠线上**登记不修**：可滚必然路过折叠线，钉成「首帧必须完整」是做不到的约束。
- A2.6① 副标题上限取 3 行不取 2 行：2.0 档那句恰好 3 行，压到 2 就是为顶档好看而让钉档丢字。
- **A2.8「立缺陷修，同批口径」**（2026-11-08 收口复测后拍板）：按已定口径「只换摆法」把喇叭药片挪成单独一行、
  音标列拿整幅宽度；不缩药片文字（那就是朗读文案，缩掉等于改没「点下去会发生什么」），不给音标加 `maxLines`
  （音标是「怎么念」的权威写法），也不把药片钉进吸底区（它不是唯一出口）。
- 分支不合 `main`；推送逐批请示；真机只手工试用、instrumented 只在模拟器；截图/密钥/挑战码明文一律不入库；
  破坏性 git/设备动作先取得确认；TalkBack/出声类走查「先不用做」。
