# 目视走查册（累计待目视项合并版，2026-11-08）

一句话用途：把散在七份报告/决策/验证文档里的「只有人眼能判」项收成一张可勾选的单子，
一轮走完。QA 第三轮（`docs/handoff/2026-10-15-qa-round3-task.md`）把「可自动取证组」交给我在模拟器上跑完了，
剩下的判不了的一律进这本册子——**本册不替人眼判，也不替用户拍板**。

## 0. 怎么用

- 每条三个格：`- [ ]` 未看；看完在行末补 `→ 接受` 或 `→ 不接受：要改什么`。
  只回「都行 / 不行」也可以，但 A 组每一条都要落到「改不改」，否则下一棒无法开工。
- **A 组先走**：那五组是「要不要动代码」的裁决，B/C/D 组是观感与手感，不阻塞开工。
- 两条走法，任选或混用：
  1. **看图判**（快）：直接翻 `.git/qa-scratch/qa3/` 与 `.git/qa-scratch/{c4b,c4c,c6,c7,dialog-evidence-1.0,dialog-evidence-2.0}/`
     的 PNG（清单见 E 组）——本轮 emulator-5554（API 30，1080x1920 @420dpi）产的，浅色/深色/2.0/3.0/320dp 都有。
  2. **上手判**（准）：真机 `bf353dda`（Mi 10S / API 33）里程碑装机手工试用；
     D 组四条只有真手指能判，看图判不了。
- 证据目录全在 `.git/qa-scratch/` 下，**不入库**（AGENTS.md 隐私约束）；重算：
  `ls -1 .git/qa-scratch/qa3/*.png | wc -l`。

## A 组 · 需要拍板（决定要不要改码）

### A1 许可页「返回」把用户留在原地，再按一次直接退出应用

`AppScreen.kt` 里 `showLicenses` 全站只出现 4 次（`:190` 声明、`:1595` 打开、`:1597` 挂载、`:1612` 页内返回），
既不在 `overlayOpen` 表达式（`:581-584`，34 个标识符里没有它）里，也没有自己的 `BackHandler`——
于是系统返回键落到 `:587` 那条「不在学习 tab 就回学习 tab」的低优先级处理器上。
实测五份（1.0/2.0/3.0、浅色/深色、320dp）形状完全一致：

- 返回第 1 次：焦点仍是 `MainActivity`，节点数不变，人还停在许可页（等于没反应）；
- 返回第 2 次：直接退到桌面（`NexusLauncherActivity`），不崩。
  图证：`.git/qa-scratch/qa3/fs200-11-licenses-back1.png` → `fs200-12-after-back2.png`；
  `s320-g-licenses.png`、`fs300-g-licenses.png`、`dark-g-licenses.png` 同形。

对照：`showAbout` 有 `:714` 的 `BackHandler(enabled = showAbout) { showAbout = false }`，`showLicenses` 是
Ready 态二级屏里**唯一**缺「四件套」（状态位 + `overlayOpen` + `BackHandler` + 挂载分支）的那一个。
另有一条次生现象：`:1595` 打开许可页时顺手 `showAbout = false`，所以从许可页用**页内**返回会落回「我的」而不是「关于」。

- [x] 处置：**四件套 + 打开时不清 `showAbout` 一起改**（用户 2026-11-08「全部都做」）。
  落点：`showLicenses` 进 `overlayOpen` 表达式；声明在 `showAbout` 处理器**之后**的 `BackHandler(enabled = showLicenses)`
  （本文件「后声明者优先」）；`AboutScreen.onOpenLicenses` 改成只置 `showLicenses = true`，关于页留在栈下；
  挂载 `else if (showLicenses)` 分支提到 `else if (showAbout)` **之前**——两枚同时为真时先声明者赢，
  顺序不翻就是「点了许可行没反应」的第二半。
  判据：`LicensesHostBackStackContractTest` 五条（状态位 / 进链 / 自有处理器 / 分支次序 / 不再清 about）
  + 设备侧 `AppScreenTest.systemBackFromTheLicensesPageWalksBackToTheAboutPage`、
  `inPageBackFromTheLicensesPageReturnsToTheAboutPage`。本册不动生产码，改码与取证见
  `docs/handoff/2026-11-08-qa3-a-group-report.md`。

### A2 动态字体顶档（`fontScale = 3.0`）的七处破坏

AC5-03 要求「最大档不破版、不截断关键操作」，既有 connected 护栏只覆盖到 2.0 档。本轮在 3.0 实测到七处，
前两处与 A2.6/A2.7 是形状变丑，**A2.5 是流程断裂**（其余为裁切与越出容器）。图证统一在
`.git/qa-scratch/qa3/fs300-*.png`，每处都给「1.0 对照 → 3.0」两张。

> **2026-11-08 收口（用户裁决「全部都做」）**：七条一律立为缺陷修，改动落 A2.1 / A2.2 / A2.3 / A2.4 / A2.5 /
> A2.6① / A2.6② / A2.7 八处；唯一**登记而不改**的是 A2.6③（分段头压在可滚页的折叠线上，理由见该条补正）。
> 统一口径：**只换摆法 / 换档，不换内容、不换术语、不增删控件**，阈值一律严格大于 2.0
> （AC-R-07 钉住的验收档一像素不动）。
> 决策与量法见 `docs/decisions/2026-11-08-max-font-scale-layout-degradation.md`，
> 验收标准补为 AC-R-24，逐屏口径见 `docs/superpowers/specs/2026-10-25-full-layout-redesign-design.md` §5.1。
>
> 收口复测（3.0 档逐屏实拍）另撞出**第八处**：词卡音标被喇叭药片挤成三行断字，已单独立为 **A2.8 待拍板**
> （它不在这七条的范围里，按规矩不顺手改码）。

- [x] A2.1 今日计划页计数行「0」字掉形：`0/12` 只读出一条斜杠（数字被裁到看不见）。
  对照 `fs100-01-today.png` vs `fs300-01-today.png`。→ 判：**修（用户 2026-11-08「全部都做」）**
  根因不是裁切而是**挤压**：大数 `0/12` 与右侧「还剩 N 个词」在同一行抢宽，顶档下先被挤没的是那个 `0`。
  修法：`> 2f` 时脚注挪到大数下方一行，计数行保持单行。
  判据 `TodayPlanScreenTest.summaryCountRowStaysSingleLineAtTopFontScale`（`GetTextLayoutResult.lineCount`）
  + `TodayPlanLayoutContractTest` 两条（阈值与单一渲染点）。
- [x] A2.2 底栏「AI 助学」折两行且图标上浮：该格可点区 270x352px，同排其余三格 270x226px，四格不再等高。
  对照 `fs100-08-mine-apps.png` 同屏底栏。→ 判：**修（与 A2.7 同一根因、同一次整改）**
  `Row.height(IntrinsicSize.Max)` + 每格 `fillMaxHeight` 让四格同一条矩形、同一块可点区。
- [x] A2.3 词卡词头折两行、**尾字母被裁**：`confidence` 的结尾字母缺半。
  注意这条**没有省略号**，`uiautomator` 的 bounds/text 判据量不出来（本轮自动判据报绿，人眼才看到），
  属于「客观判据的盲区」，登记在 QA3 报告 §5。图证 `fs300-20-wordcard.png`、`fs300-b-wordcard.png`。
  → 判：**修**　补正根因：排版侧其实是**完整**的（`GetTextLayoutResult` 末行行尾 = 10 = 词头长度，十个字母全排出来了），
  被裁的是**绘制**：词头真实占位 449.5dp，而顶档可滚视口只剩 220dp（被 A2.4 撑到 226dp 的吸底区压的），
  第三行落在视口外且不产生省略号。所以「补个 ellipsis」是错修——词头就是学习内容。
  修法：`> 2f` 词头从 `AppType.Display` 换到 `AppType.StrongTitle`（见 A2.4 同批）。
  判据 `WordCardScreenTest.lemmaKeepsEveryLetterLaidOutAndVisibleAtTopFontScale` 两条各管一半（排版完整 + 占位在视口内）。
- [x] A2.4 三档反馈按钮（不认识/模糊/认识）文案折行撑破卡片。图证 `fs300-wc-1-front.png`。
  → 判：**修**　3.0 档每枚 `weight(1f)` ≈110dp 宽，而「不认识」三字 ≈110dp，左格排成四行、整条吸底区 226dp。
  修法：`> 2f` 三枚改整宽上下堆叠、每枚一行（档名与副说明并排）；档位文案上移成 `FeedbackTiers` 单点清单，
  两分支共用。判据 `WordCardScreenTest.feedbackTiersStayWholeAndInsideTheWindowAtTopFontScale`
  （每句 `lineCount == 1` + 整格在窗口内）。
- [x] A2.5 **回忆-揭晓流程断裂**：3.0 档「显示释义」按钮落在视口之外，而词卡本身不可滚动
  （`fs300-wc-2-scrolled.png` 证明滑动不产生位移，节点树原地不动）→ 顶档用户点不到揭晓，核心学习流程走不完。
  这条是 AC5-03 顶档判据的**不达标项**，不是观感取舍。→ 判：**立为缺陷修（已修）**
  补正一句：走查册那句「词卡本身不可滚动／滑动不产生位移」**不成立**——Ready 分支的内容段带 `verticalScroll`
  （`WordCardScreen.kt:305`），本轮 `performScrollTo()` 能把它带上来的。仍然成立、也确实要修的的是另一半：
  **首帧**出口在折叠线之下（3.0 档词头单枚就占 288dp，吸底评分条从 `y=1145` 起画，可滚视口 855px），
  靠滚才够得着的唯一出口等于把流程做成隐藏关卡。修法：`> 2f` 时「显示释义」常驻吸底区（评分条之上）。
  判据 `WordCardScreenTest.revealActionIsOnScreenAtTopFontScaleWithoutScrolling`（不滚动，首帧即可点）。
- [x] A2.6 **AI 助学页顶档三处**（本轮补拍，`fs300-ai-*.png` 5 张）：
  ① 页头副标题「每个功能都先做本机能做的部分…」在 3.0 占 4 行 645px，首屏几乎只剩标题区（`fs300-ai-01-hub.png`）；
  ② 功能卡标题折两行（「长难句 / 分析」「拍照翻 / 译」），状态徽标「现在能用」被推到卡片可视区之外，
  需滚动才看得到——本页根 `ScrollView [0,63][1080,1439]` **可滚**（`fs300-ai-04-hub-half.png`、
  `fs300-ai-03-hub-scrolled.png`），所以不是 A2.5 那种流程断裂，是排印取舍；
  ③ 「还在打磨 4 个」分段标题被视口下沿**切掉半行**（节点 bounds 已被父容器裁成 `[64,1365][818,1439]`，
  越界判据看不见这种裁，见下「判据盲区」）。
  → 判：**① ② 修；③ 登记不修（本页可滚，不是流程断裂）**
  ② 修法：`aiFeatureGridColumns` 在 `> 2f` 把两列降到一列，每张卡拿到整幅宽度，徽标回可视区
  （判据 `AiLearningScreenTest.featureCardsFallBackToOneColumnAtTopFontScale` + `AiLearningLayoutContractTest`；
  2.0 档双列形状由 `the_feature_cards_are_laid_out_in_two_columns` 一字未动地守着）。
  ① 修法：共享页头 `AppScreenHeader` 的副标题腿加 `maxLines = 3` + `Ellipsis`。上限取 3 不取 2，
  因为那句 31 字说明在 2.0 档恰好 3 行——压到 2 就是为顶档好看而让钉档丢字。
  判据两条互为正反：`headerSubtitleStaysWithinThreeLinesAtTopFontScale`（顶档不再无限长高）与
  `headerSubtitleKeepsEveryWordAtThePinnedFontTier`（钉档 `getLineEnd(last) == 整句长度`，一个字都不许丢）。
  代价如实记：超过 3 行的文案在顶档末行走省略号，语义文本仍是整句（TalkBack 读得到）。
  ③ **不改**：分段头压在 `ScrollView` 下沿是滚动折叠的必然结果，往上滚半格就完整；
  把它钉成「必须在首帧完整可见」等于要求每屏分段头都不许出现在折叠线上，是做不到的约束。
  与 A2.5 的分界在此：A2.5 藏的是**唯一出口**（流程断裂），③ 藏的是一段标题（观感取舍）。
- [x] A2.7 底栏「AI 助学」格在 3.0 档**画到栏外**：格 bounds `[540,1442][810,1794]`，而导航栏本体是
  `[*,1505][*,1731]`——图标浮到栏沿之上压住页面内容，文案第二行「学」掉到栏沿之下（`fs300-ai-01-hub.png` 可见）。
  A2.2 记的是「不等高」，这条记的是「越出容器」。→ 判：**修**
  补正根因：不是这格真的画到了栏外，而是**四格各自居中导致字形不在同一条横线**——两行标签那格的图标被顶到
  栏沿 hairline（`y=1442`）上、第二行落在栏沿之下，读起来像越界。修法两条腿：等高矩形（A2.2）+
  每格顶对齐并按档留 `bottomBarSlotVerticalBreathing` 余量（只在 `> 2f` 给，1.0/2.0 档一像素不动，
  实测理由见该函数注释与决策文档 §4）。不取「标签截断」：`AI 助学` 截成 `AI …` 是把目的地名字改没。
  判据 `AppBottomBarTest.slotsStayInsideTheBarAtTopFontScale`（+ 窄屏那条）。

- [ ] A2.8 **收口复测新发现（待拍板，不在「全部都做」那七条里）**：3.0 档词卡正面的**音标被压成三行断字**。
  图证 `.git/qa-scratch/qa3-a2-final/fs300-a23-a24-wordcard-front.png`（首帧）与 `fs300-a23-card-scrolled.png`
  （滚下之后），层级 `dump-wordcard-fs300.txt` / `dump-wordcard-scrolled-fs300.txt`；1.0 对照 `fs100-a23-a24-wordcard-front.png`。
  实测节点：音标 `/prəˈɡresɪv/` 在 3.0 排成 `/prə` `ˈgres` `ɪv/` 三行（节点 `[100,354][335,806]`，宽只有 235px = 89.6dp），
  喇叭药片那一枚占 `[466,556][938,727]`（472px = 180dp），词性 `adj.` 因此被顶到 `[100,817][263,928]`、首帧看不见。
  1.0 同屏对照：音标一行 `[100,518][275,558]`、药片 `[782,534][938,590]`（156px）、词性 `[100,569][156,606]` 一行到位。
  根因是 `WordCardScreen.kt:326-358` 那一行 `Row { Column(weight(1f)){ 音标 ; 词性 } ; SpeakerButton }`：
  `SpeakerButton` 的**可见文字**与朗读文案同源（`SpeakerButton.kt:50-52` 的口径），顶档下这枚文字连字形长到 180dp，
  把 `weight(1f)` 那一侧挤到只剩 89.6dp，于是 IPA 在音节中间断行。
  性质：**不是流程断裂**（内容段可滚，揭晓与三档评分在吸底区常驻，A2.5 那条已修），是排印破坏——
  和 A2.6② 同类，比 A2.3 更刺眼（音标是「怎么念」的权威写法，断成三行等于把读音也弄错了观感）。
  候选修法（按已定口径「只换摆法」）：`> 2f` 时把喇叭药片从音标那一行挪到**单独一行**（整行左对齐），
  音标与词性那一列拿到整幅宽度；1.0/2.0 档一像素不动。
  代价：顶档卡面多一行高度，词头那一屏的滚动量再涨一点（可滚，不影响出口常驻）。
  → 判：**待用户拍板**（本条是收口复测时撞出来的第八处，不属于 2026-11-08「全部都做」那七条的范围，
  所以按规矩先登记、不动码；拍板「修」则与 A2.1-A2.7 同一套阈值与判据形状落地）。

**判据盲区（本轮两次撞上，登记以免下一棒误信自动绿）**：`uiautomator` 的 `bounds` 是节点与父容器的
**可视交集**，被裁掉的节点永远「不越界」；文案被裁也**不产生省略号**（Compose 的 `Text` 溢出裁剪不走 ellipsis）。
所以「越界 0 / 省略号 0 / 可点高<48dp 0」三条全绿的屏，人眼仍能看到 A2.3、A2.6③、A2.7 三类裁切。
凡顶档屏都要**图证 + 人眼**双轨，不能只交判据。

### A3 长正文确认框：出口必须滚一下才够得着

2026-11-05/06 对话框排印批实测的产品事实（`docs/decisions/2026-11-05-dialog-typography-and-wordbook-token-cleanup.md` 末节）：
`font_scale = 2.0` 下正文长的确认框自然高 3592dp，而窗口给面板的约束只有 659.05dp——
现在的形状是「正文首屏可见 + 出口滚到可见」，上限取多大都改变不了这一点。

- [ ] 是否把 `DialogPanel` 改成「正文滚动 + 动作粘底」（组件级改动，在那批范围外）？→ 判：改 / 不改（维持现状）
  图证：`.git/qa-scratch/dialog-evidence-2.0/`（4 张）+ `dialog-evidence-1.0/`（4 张）

### A4 深色档是否还有「半迁移」残留（D-2 复测的目视半边）

批 1 里程碑装机实勘记过一条：系统深色下今日计划屏「白底 + 主题浅墨」、计数卡仍浅档外观
（`AppPalette.Surface` 写死 `Color(0xFFFFFFFF)` 不跟主题）。本轮 2026-11-07 在 emulator-5554 深色档走查 8 站
并逐站取样：今日页、词卡（正/反）、设置、我的、关于、许可页、打卡图都呈 token 化深底 + 可读墨色，
 sampled 站点里没再出现「白底压浅墨」。真机侧（`bf353dda`）未复测。

- [ ] 请用人眼在 `dark-*.png`（8 张）上确认「D-2 已消」，或指出仍在的站点。→ 判：已消 / 仍在（站点：____）

### A5 学习页字母栏与发音药片：只有真手指能判

任务书 §3 的两条「必查」，判据本身依赖手指而不是截图：

- [ ] 词书字母 Rail 现命中区 ≈24x13dp（纵向未铺满整槽；代偿=整槽内命中 + 整轨拖动），真手指可命中率。→ 判：可用 / 不可用
- [ ] 喇叭药片靠 `minimumInteractiveComponentSize` 放大到 ≈60dp，在词卡/音标行是否与相邻控件打架（误触）。→ 判：不打架 / 打架（位置：____）

## B 组 · 观感与取舍（改不改都在人眼，不阻塞开工）

批 1 在册的四条同时在「真机四项目视」待办里（#14），看图判也算走完成本条，标一下走法即可。

- [ ] B1 六档 `AppType` 摘掉 `lineHeight` 后正文是否偏紧（判「太紧」→ 在装配点补，并把取值先写进规格 §2.2）。→
- [ ] B2 排印换档的整体观感：逐屏看有没有被撑破或挤扁的头部。→
- [ ] B3 M3 控件默认色由库紫转薄荷 + Card 圆角 20→18dp + `colorScheme.outline` 换成 `text2` 是否可接受。→
- [ ] B4 跟随系统的深色档、以及 `fontScale 2.0` 下底导与设置页不裁。
  本轮截图：`dark-*.png`（8 张）、`fs200-04-settings.png`、`fs200-06-mine.png`。→
- [ ] B5 `GlassOverlay` 浮层**关闭**时的收拢观感：退场缓动现为 `AppMotion.EasingIn = cubic-bezier(.4,0,.8,.3)`
  （`AppMotion.kt:31-32`，原 `cubic-bezier(.2,.7,.3,1)`），是行为变化，只有 JVM 形状闸与评审保证「按口径写」。
  需真机/模拟器上手看动帧，静帧判不了。→
- [ ] B6 宽窗（平板/折叠）侧栏 + 720dp 内容列的实际观感。图证 `.git/qa-scratch/b-wide-1080dp-side-nav.png`
  与 `b-b3-header-probe-wide.png`（后者是「裁切」假阳性的对照）。→
- [ ] B7 宽档把 hero 降级为 24sp→`Title` 的层级读感（今日页在宽窗下还压不压得住）。→
- [ ] B8 手机竖屏：批 B 把方向锁到竖屏后，回归确认「旋转不发生」这条本身无异常（真机上手转一遍）。→
- [ ] B9 进行中卡的「取消」是 `PillStyle.Secondary` 白底胶囊（`RequestInProgress.kt:84`），落在浅绿卡面上像一枚浮起的方块。
  C-4b/C-4c 五屏一致 ⇒ 若要改是**改组件**，不是改某一屏。图证 `.git/qa-scratch/c4c/*.png`（6 张）+ `c4b/`（2 张）。→
- [ ] B10 活动指示 `18.dp` / `2.dp` 在静帧截图里只读出一小段弧（动帧正常），观感是否够「在做事」。→
- [ ] B11 浅档骨架条从 1 级跳到 20 级（C-7 改档后），是否偏重——它现在与 `outline` 描边同色，只是不带 alpha。
  嫌抢眼有两条独立旋钮：光带 alpha 落 `0.55f`、或换填充档。图证 `.git/qa-scratch/c7/shots/`（六档目录：`{light,dark}-scale{0,1}`、`{light,dark}-scale1-dense`，重算 `find .git/qa-scratch/c7/shots -name '*.png' | wc -l`）。→
- [ ] B12 光带 `Screen` 提亮在白卡面上是否过亮（浅档峰值 +19 级、落色 `#F0F4F0`）。→
- [ ] B13 「闪屏结束 → 今日计划可读」这段骨架停留是否更顺、有没有「骨架闪一下就变内容」的更难看的形状。
  **这条是唯一没有读数的**：C-5 只证明了主线程不再堵那几十毫秒，`Displayed .MainActivity` 差 6ms 落在噪声里，
  骨架时长前后差需要临时桩才能量，本轮未做。→
- [ ] B14 对话框排印两档共 8 张 PNG + 词单页 4 张 PNG（`.git/qa-scratch/dialog-evidence-{1.0,2.0}/`、
  `.git/qa-scratch/color-audit/wordbook-words.png`）。→
- [ ] B15 打卡页页头两条裸文字链被 Task 22-B 摘除后，收藏/错词的替代路径（底部「我的词」进合并页分段）
  在手里可达且可接受——自动化只证明合并页能走通，证明不了「用户知道要去这儿」
  （`MyWordsScreen.kt:53/70-71/162` 是这段的在册说明）。→

## C 组 · AI 出站确认粒度（行为确认，看图判不了）

2026-11-05 批落地的「按域名一次」放宽，四条都是「有没有渗漏」的手感确认（模拟器/真机都行，需已配置 AI Profile）：

- [ ] C1 长难句分析首次输入出域名确认框，**同一次使用内第二次提问不再弹**。→
- [ ] C2 去查词页对同一个域名问 AI，也应**不弹**。→
- [ ] C3 把 Endpoint 换成另一域名应**重新弹**。→
- [ ] C4 拍照翻译 / 生图出图 / 文章「换一篇」**每一次都还弹**（放宽没有渗漏到这三处）。→

## D 组 · 只有真机能判的（`bf353dda` 里程碑装机）

- [ ] D1 A5 两条（字母栏命中率、药片打架）。
- [ ] D2 B5/B8（浮层收拢动帧、竖屏锁定手感）。
- [ ] D3 B 组前四条在真机字体档（MIUI 的字体缩放档 ≠ 系统 `font_scale`）下的复判。
- [ ] D4 C 组四条在真机 + 真实 Endpoint 下的复判（同时可销 B5/T2 的「零出站」部分）。
- 纪律：真机**只手工试用**，不跑 instrument；MIUI 会自动卸载 adb 装的包，`adb install -r` 后立刻测。

## E 组 · 本轮证据索引（`.git/qa-scratch/qa3/`，不入库）

| 前缀 | PNG 数 | 条件 | 说明 |
|---|---|---|---|
| `fs100-*` | 15 | `font_scale=1.0` 浅色 | 全路线基线（今日→词卡→设置→我的→关于→许可→打卡图）+ 收工基线留影 `fs100-99-final-state.png` |
| `fs200-*` | 20 | `font_scale=2.0` 浅色 | 全路线；含许可页返回两拍（`fs200-11`、`fs200-12`） |
| `fs300-*` | 32 | `font_scale=3.0` 浅色 | 全路线 + 词卡正反面与「不可滚动」取证（`fs300-wc-1/2`）+ AI 助学页补拍 5 张（`fs300-ai-*`，见 A2.6/A2.7） |
| `dark-*` | 10 | 深色档 | 主题切换后 8 站 + 切换前设置页对照 |
| `s320-*` | 8 | `wm size 840x1600`（=320dp @420dpi） | 小屏核心页 |
| `off-*` | 18 | 飞行模式 | 六条链路里的背词/复习/查词/AI 入口 |
| 散图 | 2 | `font_scale=1.0` 浅色 | `light-check.png`（深色路线前的浅色今日页对照，进度 0/23）、`final-after-guard-rerun.png`（connected 复跑并 `unroot` 后的收工基线留影） |
| `worksheet-evidence/` | 3 PNG + `report.tsv` | 工作表预览屏 | 取色核对：浅色 1.0 `#1F4739`/`#E9F2EC` **9.10**、深色 1.0 `#22332C`/`#E9F2EC` **11.65**、浅色 3.0 **9.10**（AA 正文门槛 4.5；实跑 `OK (4 tests)`，详见 QA3 报告 §3；`adb root` 拉取后已 `unroot`） |

日志同目录：`route-fs{100,200,300}.log`、`probe-fs300*.log`、`probe-dark.log`、`probe-s320.log`、
`reaudit-all.log`、`offline-*.log`、`worksheet-{red,green}.log`、`worksheet-green-guard.log`（取样面护栏加入后的复跑，
同为 `OK (4 tests)`、读数逐位不变）、`gate-qa3.log`（整仓门禁 `BUILD SUCCESSFUL in 5m 33s` / 2467 条全绿）、
`build-*.log`。
判据脚本同目录：`walk.py`（dump/tap/审计）、`route.py`（17 站路线）、`probe.py`（逐屏独立导航）。

设备状态在收工时已复原：`font_scale=1.0`、`wm size`/`wm density` 回 Physical（1080x1920 @420dpi）、
主题回浅色、飞行模式关、`adb unroot`（重算：`adb -s emulator-5554 shell id` 应为 `uid=2000(shell)`）。

## F 组 · 已从累计清单剔除的项与依据（写在这里，免得下一棒又去找）

**用户裁定不做**（原话「这个先不用做，我感觉没什么意义」，2026-11-07）——TalkBack 朗读链与「出声类」全部出局：

- 任务书 §1 AC5-03 TalkBack 六链走查；§2 许可页 TalkBack 朗读。
- §3 攒批：I-8 四控真出声冒烟、系统 TTS 下「只有停止」的可理解性、真机 1.25x→1.0x 降速取证、
  滑动删除 `customActions(label="删除词书")` 可达性（终审 I-A2）、语速切换播报（终审 I-A1）、
  揭晓焦点播报、退出路径不停发声（终审 M-A2）。
- 批 B 待目视第 ③ 条「侧栏选中态与朗读（TalkBack）」。

**前提已失效**（本轮逐条实跑 `grep`/读源码核实，不是按台账推断）：

- 待目视 ⑤「打卡页完成度环轨道色」——那枚 132dp Canvas 环已整块删除，
  今日卡是完成度唯一出处（`CheckInScreen.kt:107-108` 注释 + `CheckInDisplayAuthorityTest` 钉住）。
- 待目视 ⑧「AI 学页副标题与稿冲突」——文案已改中性句：`AiLearningScreen.kt:79`
  「每个功能都先做本机能做的部分，再把必要的文字交给已配置的服务。」
- 待目视 ⑨「今日计划头部无 Hero」——Hero 已落地：`TodayPlanScreen.kt:128` 写明页头走
  `AppScreenHeader(hero = true)`（眉标=计划日期 + 28sp 大标题），`:191` 为实际调用。
- 待目视 ⑩「阅读锁定态仍画 4 枚 `enabled=false` 类型块」——`enabled` 已退役，四枚类型按钮只出现在
  「生成前参数」展开区（`ReadingAccessScreen.kt:414-417` 挂载，`:618-619` 注释「本函数只在展开区出现，
  `enabled` 随之退役」）。
- 任务书 §3 攒批「两处含字徽标顶档」的两个锚点都已失效（本轮实跑 `grep` 核实）：
  `AiFeatureScreen.kt:84 size(44.dp)` 现在在 `:175`，且 44dp 盒里装的是 `AppGlyph(size = 22.dp)` **图标**
  （2026-11-02 换字形，注释在 `:165-170`）——dp 不随 `fontScale` 缩放，「字形顶出底块」在这一屏已不成立；
  `SettingsScreen.kt:395 size(32.dp)` 整文件已无任何 `.size(` 定尺盒（现 170 行，`grep -n 'IconBadge\|AppGlyph\|\.size('` 零命中）。
  这条的**实质**（定尺底块 + 文案在顶档的排布）落在同屏的 `StatusPill`（`:184-206`，padding 包 `AppType.Label`，
  随字号长）与功能卡上，已转为 A2.6 / A2.7 取证，不再按原锚点登记。
- 任务书 §3 攒批「三屏零测试引用族」→ 只剩一屏：`FavoritesScreen`、`WrongWordsScreen` 两屏
  已在 Task 22-B 随页头文字链一起从生产树下线（只剩 ViewModel），高度断言与取色核对只落在
  `WorksheetPreviewScreen`，且「两枚按钮行」现在只剩吸底一枚「导出 PDF」（返回已上移共享页头）。
- 任务书里 `AppPalette.OnMintPrimary` / `MintPrimary` 两个名字已被 UI 重设计轮 token 化为
  `appColors().onStrong` / `strongSurface`（`WorksheetPreviewScreen.kt:190-191`），语义没变，本轮按新名量。
- 任务书 §0/§1 写的 `dumpsys connectivity | grep -m1 "Airplane mode"` 判据串在本 API 30 镜像上不存在；
  本轮改用 `settings get global airplane_mode_on` = 1 且 `Active default network: none` 双证。
- 待目视 ⑥「打卡页环内完成度数位是否偏小」——批 1 收口时已随虚假代价登记一并撤销（等价改写，字号未变），
  本轮沿用该撤销，未重新入册。

重算本册在册格数：

```bash
grep -c '^- \[ \]' docs/verification/visual-walkthrough-2026-11-08.md
```
