# 决策：窗口类别按内容宽度自判三档，导航换摆法不换语义（批 B）

日期：2026-11-06
分支：`stage-5-ui-r2-learning-path`（不合 main）
关联：`docs/specs/00-foundation-and-architecture.md`（非功能要求·窗口类别支持矩阵）、`docs/specs/05-release-quality-and-compliance.md` F5-02/F5-03、`docs/superpowers/plans/2026-11-06-motion-and-adaptive-layout.md` 批 B、`docs/decisions/2026-11-06-motion-foundation-and-system-reduced-motion.md`（批 A）

## 1. 背景

用户 2026-11-05 的话是「全部都做，然后横屏也要做适配，**就是不是要硬横过来哦**」，并在选项题里把批 B 的野心钉在「全面自适应（含平板/折叠 + 侧边导航）」。这句话把范围从「允许旋转」推到了「按窗口形态重排版面」，而 `AndroidManifest.xml` 里既没有 `screenOrientation` 也没有 `configChanges`——应用本来就会转，「允许横屏」早就不是待办；待办的是横过来之后那套装不下竖屏版面的地方怎么办。

动手前先取基线，不靠 grep 猜：**批 B 前的包在横屏窗口下跑同一批 connected 用例，红 35 枚；竖屏窗口下同一批跑，红 3 枚**（读数与重算命令见 §6）。也就是说 32 枚红是形态造成的，3 枚红与形态无关。2026-10-31 那次「29 枚横屏假红」的处置是把设备锁竖屏（`docs/decisions/2026-10-31-ui-review-three-rulings.md` 环境侧记录），属环境规避；批 B 要把它换成产品侧的真修，所以本决策的口径必须先落在文档上，再落代码。

## 2. 决定一：不引 `material3-window-size-class`，判定权收在自有纯函数

- **证据**：本机 Gradle 缓存里没有 `androidx.material3:material3-window-size-class` 这个 artifact（重算：`ls ~/.gradle/caches/modules-2/files-2.1/androidx.material3/ | grep -i window`，当前只有 `material3` 与 `material3-adaptive-navigation-suite` 两枚目录，均不含 window-size-class），而本仓构建口径是离线可跑（门禁一律 `--offline`）。为拿两枚断点数字去开一次联网下载，等于把「构建可复现」押在网线上。
- **决定**：`AppWindowLayoutPolicy.classify(widthDp)` 是纯函数，三档枚举 `AppWindowLayout.Compact/Medium/Wide`。断点与 Material 的 Range 定义一致（下界归高档），且能在 JVM 单测里把断点两侧穷举到 bit（`AppWindowLayoutContractTest` 逐 1dp 扫到 2000 并验单调不降）。官方那枚要跑在设备上才量得出来，自有这枚连「NaN/0f/负宽」都能当场定性。
- **代价**：Material 若日后改断点，本仓不自动跟随。这条代价是可接受的——断点已经是本仓文档里的承诺（Spec 00 矩阵），不跟随反而是想要的行为。

## 3. 决定二：判定面是内容宽度，不是屏幕宽度

- 判定源取 tab 宿主层 `BoxWithConstraints.maxWidth`，结果经 `LocalAppWindowLayout` 下发；**只有两处消费**：宿主自己（导航摆法 + 内容列上限）与 `AppScreenHeader`（hero 降级）。屏侧一律不读，由 `AppWindowLayoutContractTest` 的读点白名单闸钉住（名单外的 `.kt` 出现 `currentAppWindowLayout(` / `LocalAppWindowLayout` / `AppWindowLayoutPolicy.` 即红）。
- 为什么不用 `Configuration.screenWidthDp` 当唯一判定源：它是配置值，分屏、折叠铰链半开、`wm size` 这类「窗口变了但配置不一定按时反映」的场合上会滞后；实测约束不会。`currentAppWindowLayout()` 在**没有 provider** 时回落到 `screenWidthDp`——这是给单屏预览与既有 connected 夹具留的活路，不是第二套判定。
- 断点：**内容宽度 `600dp` 升中等，`840dp` 升宽**（Spec 00 矩阵原文，代码常量与之由契约闸互相对拼，改一处不动另一处当场见红）。

## 4. 决定三：高度不等于类别，两栏资格另吃一条高度门

横屏手机是这一条存在的全部理由。批 B 基线在 `emulator-5554` 上用 `wm size 1920x1080` 造出真中等窗口，量得 **`w731dp h339dp`**：宽度落进 Medium（≥600），高度只有 339dp。按宽度就开主从两栏，详情列会在 339dp 里被切成看不全的两半——那正是用户说的「硬横过来」。

- 决定：两栏资格 = 宽度档 + `TWO_COLUMN_MIN_HEIGHT_DP = 480f` 双门；本批只落常量与判据，**接入逐个 flow 留给批 B-2**。
- 导航摆法与内容列上限不受高度门约束：339dp 高的横屏手机放得下侧栏四格（每格 `heightIn(min = 48.dp)` + 8dp 间隙），这一点由 §6 的三档 connected 与截图目视确认，不是纸面推算。

## 5. 决定四：侧边导航栏是底部导航的换摆法，不是第二套导航

- 侧栏手写（`AppSideNav.kt`），不用 M3 `NavigationRailItem`：那枚自带 indicator 与 scheme 取色，而本仓选中档前景是按套钉死的（浅套 `onMint`、深套 `deep`，判据在 `AppBottomBarContrastTest` 与 `AppBottomBarContractTest`）。交给 scheme = 新开一处没人复算过对比度的现场。
- 「同形」不是靠注释维持：`AppWindowLayoutContractTest` 把十枚锚点（testTag 命名、`contentDescription`、`Role.Tab`、`stateDescription` 双值、选中墨、标签墨定义/消费、徽标前缀、`backdrop = selected`、半透明常量）在两份文件的生效函数体内**逐一比对计数**，并要求侧栏那份在函数体外归零。任一侧单独改动即红。
- 为此把 `AppBottomBar.kt` 的两枚 private 成员放开成 `internal`：`TabGlyphIdleAlpha`（0.5f）与 `AppTab.glyph` 字形表。字形表继续住在 `AppBottomBar.kt`，因为 `AppBottomBarContractTest` 按「四枚 `AppIcon.*` 恰一处」在那份文件里数映射，搬家会掏空那道闸。
- 底栏在中等/宽档**条件挂载**（`if (!usesSideNavigation)`）：两枚导航同时在场会抢同一组 `app_tab_*` testTag，connected 会点到错的那一枚。
- 旋转与折叠不得重置当前 tab：`selectedTab` 一直是 `rememberSaveable`，本批不改这条，只在 connected 三档取证里复验它。

## 6. 决定五：宽档才降 hero；内容列上限 720dp 是设计判断，待目视

- `downgradesHeroHeader` 只在 `Wide` 为真：横屏手机（Medium）仍是 24sp `Headline`（`AppType.HeadlineSpec`，注意历史注释里的「28sp」是旧值，已按现值改口），平板/折叠展开才降成 17sp `Title`——24sp 封面字在侧栏 + 上限单列里会把页头拉成海报。
- `CONTENT_MAX_WIDTH_DP = 720f`：**取值不是量出来的**，依据是 Material 可读正文 600dp 口径放宽到本仓的卡片与统计行。它现在被 `AppWindowLayoutContractTest` 与决策文档双向钉住（文档必须出现 720 这个数字），最终形态待批 B 截图目视确认；目视若判「窄了/宽了」，改代码常量同时改本文件与 Spec 措辞。
- 紧凑档上限取 `Dp.Infinity`：宿主层因此不需要再写一条 if，且紧凑档行为与今天逐字等价。

## 7. 验证边界

能证的：断点分档（穷举 + 单调）、读点收口（源码扫描 + 合成 must-flag/must-not-flag 探针）、两份导航同形（体内计数）、hero 降级长在生效路径上、文档与代码同源（直接读 Spec 00 与本文件）。

不能证的，如实降级：
- 「720dp 上限在平板上好不好看」——JVM 单测与源码扫描都测不出观感，落点是 connected 截图 + 用户目视（累计目视项见 `docs/handoff/`）。
- 「两栏在 480dp 高度门上够不够用」——批 B-2 之前不承诺，本批只落常量。
- 「`material3-window-size-class` 的断点是否恰好 600/840」——该 artifact 不在场，本仓不声称与它「等价」，只声称与 **Spec 00 矩阵**等价。
- 侧栏四格在 `h339dp` 里是否真放得下——由 §8 的三档 connected + 截图给读数，不靠推算。

## 8. 基线与重算（批 B-3 的处置输入）

| 形态 | 造法 | 窗口读数 | `am instrument -e package com.example.englishlearning.ui` 末行 |
|---|---|---|---|
| 横屏中等（批 B 前） | `adb -s emulator-5554 shell wm size 1920x1080` | `w731dp h339dp land` | `Tests run: 551, Failures: 35` |
| 竖屏紧凑（批 B 前） | `wm size reset` | 原生 411dp 宽 | `Tests run: 551, Failures: 3` |

读数原文（gitignored）：`.git/qa-scratch/b-baseline-landscape-ui.log`、`.git/qa-scratch/b-baseline-portrait-ui.log`。
重算：`python .git/qa-scratch/tally.py <输出文件>`；两侧失败清单求差用同一份脚本的 `(类, 方法)` 集合差，不在此抄录枚数。

35 − 3 的归属（批 B-3 逐条处置的输入，**不在此一次性点名全部 32 枚**，名单以两份 log 的差集现算为准）：

- **A 组｜语义锚点写成了竖屏几何**：按 y 坐标排序、按 `bounds.height` 判可见、`performScrollTo()` 依赖单列滚动的用例。处置=换成语义锚点（`positionInRoot` / `unclipped` / 按 tag 定位），与形态无关后两侧同绿。
- **B 组｜版面确实按形态分支**：底栏 vs 侧栏、内容列上限。处置=批 B 代码落地后按当前窗口类别取期望，并在三档各跑一遍。
- **C 组｜与形态无关的设备前置**：竖屏那 3 枚（`AppScreenOverlayContainerTest` 的 doubleFontScale 三条）要 `font_scale 2.0`，跑在 1.0 上必红。处置=在用例内部如实声明前置（`assumeTrue`/显式失败信息），**不再靠「跑之前把设备调好」这种环境侧口径**，也不写假用例。

上面 A / B / C 三组的处置自 §10.4 起改写：手机横屏形态随 §10.2 退场，A、B 两组不再逐条改造；C 组照做，并额外把 `assertScaleTookEffect` 的裸 px 阈值改成 dp 口径（§10.4）。

2026-10-31 那次「锁竖屏」的环境规避自本批起退役：取证按**受支持的形态**分别跑、分别留 `OK (n tests)` 原文；不再靠「跑之前把设备调成某个方向」。手机横屏这一形态本身被 §10 关掉（关掉的方式是产品侧的方向策略，不是测试前的环境手势）。

## 9. 不做

不做桌面/多窗口专属排版与鼠标悬停；不做第二套导航语义（宽窗不增删目的地）；不为横屏单独设计一套视觉；不承诺所有屏在宽档同时两栏（按 flow 逐个接入，未接入的屏在上限宽度内保持单列）。原文见 `docs/specs/00-foundation-and-architecture.md` 非功能要求。

**手机横屏（sw < 600dp 的横屏窗口）自 §10 起列入不做**：它是 §4 那条「高度不等于类别」推到设备侧之后的实际形态，实测证明竖排长列版面在这个形状里不成立。

## 10. 追加决定（2026-11-07 用户裁定）：横屏只在 sw ≥ 600dp 开放，手机锁竖屏

### 10.1 触发这件事的读数

批 B-4 落地后按 §8 的口径重取了 connected 基线，三种形态（原文 log：`.git/qa-scratch/b-baseline-landscape-ui.log`、`.git/qa-scratch/b-b3-wide-full-ui.log`）：

| 形态 | 造法（`emulator-5554`，原生 1080×1920 @420dpi） | 窗口读数 | `am instrument -e package com.example.englishlearning.ui` 末行 |
|---|---|---|---|
| 手机竖屏（Compact） | `wm size reset` + `wm density reset` | `w411dp h914dp` | `Tests run: 551, Failures: 3` |
| 手机横屏（Medium） | `wm size 1920x1080` | **`w731dp h339dp`** | `Tests run: 551, Failures: 35` |
| 平板形窗口（Wide） | `wm size 2560x1600` + `wm density 320`（系统按自然方向夹成 2160×1600） | **`w1080dp h737dp`** | `Tests run: 559, Failures: 5` |

重算：`python .git/qa-scratch/tally.py <log>`；559 = 551 + 批 B-4 新增的 `AppAdaptiveLayoutTest` 8 例。

宽窗那一档的 5 枚红与形态无关或属判据本身：3 枚是 §8 C 组的设备前置（`font_scale 2.0`），2 枚（`AppScreenHeaderTest.header_survives_font_scale_2_without_clipping_the_title`、`NotConfiguredCardTest.status_and_detail_wrap_instead_of_clipping_at_font_scale_2`）的判据写死了「长文案必须换成两行」——窄窗里必须换行才是事实，宽窗里一行就装得下。这 2 枚按 §8 A 组处置为**形态限定**（见 §10.4）。

同一批用例在平板形窗口只红 5 枚、在手机横屏红 35 枚，这个差把结论从「批 B 没做完」改成了「**手机横屏这个形状本身不成立**」：

- `WordAiQaScreen` 的三枚问法药片在 731dp 宽窗口里的 `top` 实测 **735dp**，而窗口只有 **339dp** 高——首选操作沉到两屏之下。
- 词卡详情、复习队列、今日词单等入口在横屏里点一次不落地（connected 侧表现为「回调计数 0」「节点找不到」），因为点击目标在折叠线下；同一批用例在平板形窗口全绿。
- 这不是「测试写死了竖屏」能解释的：把测试改成先滚动确实能让红消失，但真人用手机横屏仍然要划两屏才点到主操作。**测试绿 ≠ 可用**。

### 10.2 决定

- **手机形窗口（`smallestScreenWidthDp < 600`）锁竖屏**；**sw ≥ 600dp（平板竖屏、折叠屏内屏、大屏横屏）允许自由旋转**并继续吃批 B 的侧栏 + 内容列上限。
- 落点：`res/values/config.xml` 的 `allow_landscape_layout = false` + `res/values-sw600dp/config.xml` 的 `= true`，由 `AppOrientationPolicy.applyTo` 在两枚生产 Activity（`MainActivity`、`launch.BrandLaunchActivity`）的 `onCreate` 各调用一次。两档取的值：开放档 `ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED`（跟随系统旋转设置，不是「强制横屏」），手机档 `ActivityInfo.SCREEN_ORIENTATION_PORTRAIT`。
- 为什么用 `sw` 而不是方向：`sw` 取**最小边**，与当前旋转无关。手机转横屏后 sw 仍是那个 339dp 的短边，所以「手机横屏」这一形态从入口就不存在，不需要运行时猜方向，也不会出现转过去再扳回来的抖动。
- 为什么不走 manifest：`android:screenOrientation` 不接受资源引用，做不到按屏幕形大小分档；写死在 manifest 上等于把平板一起锁死。因此 manifest 里 `screenOrientation` 保持**零处**（`AppOrientationPolicyContractTest` 钉这条归零）。
- 不加 `configChanges`：跨过 600dp 阈值（折叠屏展开/合上）按系统默认重建 Activity，不做静默热切。学习态在 Room 与 ViewModel 里，重建不丢数据；这条留给批 B-5 的折叠屏目视确认，本批不承诺。

### 10.3 与 §3、§4 的关系

§3 说判定面是内容宽度、§4 说两栏资格另吃一条 480dp 高度门——这两条都不变。§10 补的是它们的上游：**哪些设备形态会真的进入 Medium/Wide**。改后 Medium/Wide 只可能出现在 sw ≥ 600dp 的窗口里，那类窗口的短边本身 ≥ 600dp，§4 那条高度门因此不再是拦路的例外，而是默认满足。

`AppWindowLayoutPolicy` 的三档常量（600/840/720/480）一字不动；本决定只改变「谁能进入这两档」。

### 10.4 批 B-3 的处置随之改写（2026-11-07 已执行，读数附后）

- 35 − 3 里那 32 枚手机横屏红：**不逐条改造**。它们所在的窗口形态从产品侧退场（§10.2），继续为它改判据等于为一张不再对用户开放的版面重写契约。此处如实记录，不写成「已修复」。
- C 组 3 枚（`font_scale 2.0` 前置）与形态无关，照原口径处置：`AppScreenOverlayContainerTest.requireDeviceDoubleFontScale()`
  在用例首行读设备实际 `configuration.fontScale`，不足 2.0 就 `assumeTrue` 跳过，消息点名前置命令与「跳过不等于判绿」。
  执行中发现并修掉一处**形态相关的假阳性**：`assertScaleTookEffect` 的哨兵阈值原本是裸 px（60 / 100），
  那是 420dpi 手机竖屏上的读数；同一枚文本在平板形窗口（320dpi）里 px 更小、**dp 不变**
  （实测 Footnote 样本 scale 1.0 = 56px/2.625 = 21.3dp 与 44px/2.0 = 22.0dp；标题样本 64px→24.4dp 与 49px→24.5dp），
  裸 px 阈值会把「scale 真的落进对话框窗口」判成没落进。阈值改为 dp 口径（Footnote 26dp、标题 32dp），
  两侧各留 ≥4dp 余量（scale 2.0 实测 Footnote 29.3/37.5dp、标题 38/42dp）。
- A 组 2 枚（必须换两行）改成**形态限定**，且这里对本 §10.4 的初稿作一处更正：**`!hasVisualOverflow` 不能作为
  形态无关的裁切护栏**——宽窗单行 CJK 标题实测恒为 `overflow=true`，连对照组 `fontScale = 1.0`
  （`lineCount=1 overflow=true size=506x49`）也一样，而截图证明字一个没少（见 §10.5）。
  现行判据把「真截断」换成两条形态无关的硬信号，换行要求只在装不下一行时追加：
  - `AppScreenHeaderTest`：`getLineEnd(末行) == 标题长度`（末行必须排到文本末尾）+ `lineCount <= maxLines`；
    仅当 `layout 实宽 ≥ 标题列可用宽 − 1dp`（可用宽 = 页头节点右沿 − 标题节点左沿，实测宽窗 1022dp、
    竖屏 353dp）时才要求 `lineCount >= 2`，并在该分支保留 `!hasVisualOverflow`（两行档实测为 `false`，强度原样）。
  - `NotConfiguredCardTest`：`getLineEnd(末行) == 文案长度` + 保留 `!hasVisualOverflow`（两形态实测均 `false`）；
    「必须换行」只在 `layout 实宽 ≥ 窗口宽 − 64dp` 时追加（64dp 是卡片两侧留白的保守容差，
    截断判定不由它承担）。
- 三档取证改为**两档 connected**：手机竖屏（Compact，主门禁）+ 平板形窗口（Wide，`wm size 2560x1600` + `wm density 320`）。Medium 只由 `AppWindowLayoutContractTest` 的穷举分档与 `AppAdaptiveLayoutTest` 的宿主侧用例覆盖，不再用手机横屏当真 Medium 床。

处置后的四格读数（`AppScreenHeaderTest` + `NotConfiguredCardTest` + `AppScreenOverlayContainerTest`，共 13 枚，emulator-5554）：

| 窗口形态 | 设备 `font_scale` | 结果 | 日志 |
|---|---|---|---|
| 手机竖屏 411dp | 1.0 | `OK (13 tests)`，其中 3 枚按前置跳过 | `.git/qa-scratch/b-b3-compact-fontscale1-skips.log` |
| 手机竖屏 411dp | 2.0 | `OK (13 tests)`，13 枚全跑全绿 | `.git/qa-scratch/b-b3-compact-fontscale2.log` |
| 平板形 1080dp（sw=800） | 1.0 | `OK (13 tests)`，3 枚按前置跳过 | `.git/qa-scratch/b-b3-wide-after-fix.log` |
| 平板形 1080dp（sw=800） | 2.0 | `OK (6 tests)`（选择器类）全绿 | `.git/qa-scratch/b-b3-wide-fontscale2-dp.log` |

变异必红探针（同一台、竖屏、`font_scale 2.0`，改完即回滚）：`AppScreenHeader` 标题 `maxLines = 2 → 1` 与
`NotConfiguredCard` 两句加 `maxLines = 1` 两处同时注入 → `Tests run: 7, Failures: 2`，
红落在 `标题必须整段被布局… expected:<15> but was:<12>` 与 `文案必须整段可读… lineCount=1 overflow=true`
（`.git/qa-scratch/b-b3-mustflag-compact.log`）。即：放宽的是「必须长出第二行」这一形态代理，
「丢字」这条真判据在两形态都还咬得住。

**§10.2 方向锁定的设备侧证据（emulator-5554，手机形窗口 sw=411dp）**：`settings put system accelerometer_rotation 0`
+ `user_rotation 1` 强转显示时，`dumpsys activity activities` 报 `mCurrentAppOrientation=SCREEN_ORIENTATION_PORTRAIT`、
本应用 Task `mOrientation=1`，显示 `rotation` 仍为 0（系统按前台应用的锁定请求拒绝旋转），截图
`.git/qa-scratch/b-b3-portrait-lock-while-user-rotation-1.png`；验完 `user_rotation 0` + `accelerometer_rotation 1` 复原。
JVM 侧只钉形状（`AppOrientationPolicyContractTest` 五枚），「真转不动」这条只能这样在设备上取。


### 10.5 待办与不承诺

- 手机横屏退场后，「横屏适配」这句话在产品侧的实际承诺 = 平板/折叠大屏。用户 2026-11-05 的原话是「不是要硬横过来」，本决定没有违背：大屏确实换了版面（侧栏 + 内容列上限 + hero 降级，`b-wide-1080dp-side-nav.png` 为证），手机则不给半吊子横屏。
- 折叠屏展开态（跨过 600dp 阈值时的 Activity 重建）已在 emulator-5554 上做过**等效阈值取证**（2026-11-07 批 B-5）：
  应用停在手机竖屏首页时把窗口一次性抬到 `wm size 2560x1600` + `wm density 320`（sw 411dp → 800dp），
  `mCurrentFocus` 的窗口 token 由 `2b21fa0` 变 `3b23a3d` = Activity 确实重建（清单不声明 `configChanges`，
  重建是预期行为），重建后左沿换成侧栏四槽（学习/阅读/AI 助学/我的）、内容列收上限、hero 降级为常规页头，
  今日计划读数零丢失（`b-b3-before-600dp-cross.png` / `b-b3-after-600dp-cross.png`）。
  **真折叠屏内屏铰链形态仍未做设备验证**：这里证的是阈值跨越的代码路径，不是铰链传感与厂商分屏策略。
- 宽窗页头标题 `lineCount=1 且 hasVisualOverflow=true`（`size=861x84`）这一读数**已定性：不是产品缺陷，是读数假阳性**。
  取证两路（平板形窗口 1080×728dp、sw=800、densityDpi=320）：
  - 临时用例把同一枚 `AppScreenHeader` 在 `fontScale = 2.0` 与对照组 `fontScale = 1.0` 下并排组成，
    读 `TextLayoutResult.layoutInput`：`inputConstraints.maxWidth = 2044px = 1022dp`
    ——标题列**吃满了**页头剩余宽度，`weight(1f)` 没被压；两档都是 `lineCount=1 overflow=true`，
    且 `line0Right`（859.8 / 504.9px）恰好等于各自 `layout.size.width`（861 / 506px），
    即「单行实宽就是文本自然宽」，没有任何一行被推到盒外。
  - 同一时刻的窗口截图（`.git/qa-scratch/b-b3-header-probe-wide.png`）里，大字号标题
    「学习记录与复习统计（长标题用）」15 字**整串排在同一行**，右沿距窗口右边界还有一大段空白，零裁切。
  结论：`hasVisualOverflow` 对单行 CJK 文本在这套 `LocalDensity` 覆盖下不可用作裁切证据（它读的是行盒与字体
  自然行高的差，不是丢字）；判据已按 §10.4 换成 `getLineEnd` + 逐行右沿口径，组件侧零改动，
  本批不静默放宽判据。探针用例 `TmpHeaderWidthProbeTest` 属一次性取证，跑完即删，不入库。
