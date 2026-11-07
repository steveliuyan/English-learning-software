# 决策：顶档动态字体（`fontScale > 2.0`）只换摆法，不换内容——四屏降级口径与阈值（QA3 A 组收口）

日期：2026-11-08
分支：`stage-5-ui-r2-learning-path`（不合 main）
关联：`docs/verification/visual-walkthrough-2026-11-08.md` §A2（七处破坏的原始取证）、
`docs/superpowers/specs/2026-10-25-full-layout-redesign-design.md` §5.1（本轮新增的逐屏降级口径）、
`docs/superpowers/specs/2026-10-17-ui-redesign-design.md` AC-R-07 / AC-R-24、
`docs/decisions/2026-11-06-adaptive-window-layout.md`（批 B「超过钉档才降级」的同源口径）、
`docs/specs/05-release-quality-and-compliance.md` AC5-03、`docs/specs/01-vocabulary-learning-and-review.md` AC1-22

## 1. 背景

QA 第三轮走查册在 `font_scale = 3.0`（Android「字体大小」顶档）量到七处破坏，AC5-03 承诺「最大档不破版、
不截断关键操作」，而既有 connected 护栏只覆盖到 2.0 档——顶档从来没有被任何一条用例量过。用户对这七条的
裁决是一句「**全部都做**」，即七条一律立为缺陷修，不接受「顶档不承诺」这类改口径认下。

动手前先把「破坏」分成三类，因为它们的健康判据完全不同：

| 类别 | 条目 | 性质 | 修法 |
|---|---|---|---|
| 流程断裂 | A2.5 | 回忆→揭晓的唯一出口落在视口外，顶档用户走不完核心流程（AC1-22 不达标） | 出口常驻吸底区 |
| 容器被挤爆 | A2.1 / A2.3 / A2.4 / A2.2 + A2.7 / A2.6② | 内容排得出来但放不下，于是裁切、折行撑高、越出容器 | 按档换摆法/换字号档/降列 |
| 首屏被散文吃掉 | A2.6① | 共享页头副标题腿没有行数上限，顶档排 4 行 645px | 上限 3 行 + 钉档不丢字护栏 |
| 滚动折叠的必然结果 | A2.6③ | 本页可滚，分段头落在折叠线上是滚动态而非破版 | 登记，不钉死 |

其中 A2.3 与 A2.4 在设备上是**同一件事的两半**：3.0 档三枚评分按钮各拿 `weight(1f)` ≈ 110dp 宽，
「不认识」三字在这一档就有 ≈110dp，于是左格排成四行，整条吸底区吃掉 226dp，可滚视口被压到只剩 220dp——
词头排到 449.5dp 高时既没地方去、也没有省略号可给，用户看到的是「confidenc」。
所以修复必须同时给两处：词头换档（少占高度）+ 评分条换摆法（不再靠折行换可读性）。

## 2. 决定一：阈值统一「严格大于 2f」，判定收在各屏自有纯函数

六处降级各自的判据都写成 `internal fun xxx(fontScale: Float)`，屏侧只读 `LocalDensity.current.fontScale`：

- `wordCardPinsRevealAction`（A2.5）、`wordCardLemmaUsesCompactTier`（A2.3）、`wordCardStacksFeedbackTiers`（A2.4）
- `todayPlanRemainingMovesBelowCount`（A2.1）
- `aiFeatureGridColumns`（A2.6②）
- `bottomBarSlotVerticalBreathing`（A2.7 / A2.2）

**为什么是 `> 2f` 而不是 `>= 2.5f` 或 `>= 3f`**：`2.0` 是 AC-R-07 钉住的组件大字号验收档，那一档的形状
已经由既有 connected 用例逐屏点名（词卡那条就叫 `lemmaAndRevealedMeaningStayFullyVisibleAtMaximumLargeFontScale`）。
把阈值降到 2.0 以下，等于用顶档整改偷走已定稿的钉档形状；把阈值抬到 3.0，Android 的 2.5 档（部分厂商可选）
就落在无人看管的缝里。严格大于钉档，是这个仓在批 B 已经立过的同一口径（`AppWindowLayoutPolicy.downgradesHeroHeader`），
本轮不另起一套。

**为什么不用 `Configuration.fontScale` 或全局策略一处判**：`LocalDensity.current.fontScale` 是这一帧真正生效的缩放，
`Configuration` 在分屏/预览夹具里会滞后；而把六件事收进一枚「全站降级策略」枚举，判据就变成了
「策略知道词卡长什么样」，各屏反而失去可单测的自有函数。这与批 B 的读点白名单是同一条设计取向：**能穷举成纯函数的算术，
不要留在 `if` 链里**。

代价：六枚函数各自写了一遍 `> 2f`，改口径要动六处。缓解：`WordCardLayoutContractTest` 里那条
`fontScale > 2f` 计数 == 3 的锚（本屏三件事）把「漏登记」变成当场红；跨屏的口径一致性由本文档与 AC-R-24 承担。

## 3. 决定二：词卡三处降级，评分条两分支共用一份档位清单

- 词头 `AppType.Display`（46/500）→ 顶档 `AppType.StrongTitle`（27/500）。这是本屏唯一的顶档换档，
  且 `AppType.StrongTitle` 在整屏只出现 1 次、和 `AppType.Display` 同在一枚 `if` 的两个分支里。
- 三档反馈：钉档以内维持「三等宽横排 + 每枚两行居中」；顶档换成「三枚整宽上下堆叠 + 每枚一行（档名与副说明并排）」。
- 「显示释义」（A2.5，上一轮已修）常驻在评分条之上。

这里有一个必须记下来的取舍：两分支不能各写一遍三枚按钮，否则「同一档在两处措辞不同」就有了入口，
而 `uiautomator` 那类按文案取样的判据只会看到其中一份。于是档位内容（`CardFeedback`、档名、副说明、tag）
上移成 `FeedbackTiers` 单点清单，两分支各自 `FeedbackTiers.forEach`。
连带影响：`WordCardScreenContractTest` 那条「副标必须落在吸底条里」的取样区间从
「条定义 → 按钮定义」改成「清单那一行」，并新增「清单被引用恰 3 处（声明 + 两分支）」——
判据强度不降，只是换了对象。

另外踩实一条环境坑：按偏移区间取样时别用 `indexOf("\n\n")` 找声明结尾，本机 `core.autocrlf=true`，
源码是 CRLF，`\n\n` 永远找不到（本条首版就假红在 `-1` 上）。改成按行取样。

## 4. 决定三：底栏取「格高随字号」，不取「标签截断」

走查册给了两种修法（截断标签 / 格高随字号），取后者。`AI 助学` 截成 `AI …` 是把**目的地的名字**改没，
用户从此不知道第四格去哪；而栏子长高 12dp 是可接受的观感代价。

两条腿缺一不可：
① `Row.height(IntrinsicSize.Max)` + 每格 `fillMaxHeight` 让四格同一条矩形、同一块可点区（治 A2.2 的「不等高」）；
② 每格内部改顶对齐并按档留 `bottomBarSlotVerticalBreathing` 余量，让四枚字形落在同一条横线上（治 A2.7 的
「图标浮到栏沿 hairline 上、第二行掉到栏沿之下」）。
只做 ① 是第一版的错：矩形等高不等于内容等高，居中仍会把两行那格的图标顶到别人之上。

余量只在 `> 2f` 给：1.0 档内容自然高 58.5dp，被 `heightIn(min = 64.dp)` 兜住后上下各余 2.75dp，
一律给 6+6 会让**默认档凭空长高 6.5dp**；2.0 档四枚标签实测全单行（四格标签同沿 `y=1687`），
本来就没有折行格，加余量只会把栏从 68.7dp 顶到 80.7dp。

## 5. 决定四：AI 主页顶档降列；页头副标题给上限，但不许压到钉档丢字

顶档 3.0 下每张功能卡在半宽列里排到约 714dp 高，状态徽标「现在能用」被推出可视区。
`aiFeatureGridColumns` 在半宽列与整宽列之间二选一（`> 2f → 1` 列），末行不满列时补等宽 `Spacer`。

A2.6①（页头副标题占 4 行 645px）走的是**另一条路**：页头副标题腿是共享 `AppScreenHeader` 里唯一能长成
整段散文的一腿，给它加行数上限。上限取 **3 而不是 2**——那句 31 字说明在 2.0 档恰好排 3 行，压到 2 就是
「为了顶档好看而让钉档丢字」，方向反了。代价如实登记：超过 3 行的文案在顶档末行走省略号
（语义文本仍是整句，TalkBack 读得到；`Clip` 与 `Ellipsis` 都不改语义）。
反向护栏是 `headerSubtitleKeepsEveryWordAtThePinnedFontTier`：用 `getLineEnd(last) == text.length`
判「钉档一个字都不许丢」，谁再把上限往下压当场红。

A2.6③（「还在打磨 4 个」分段头被视口下沿切半行）**不改**：本页根 `ScrollView [0,63][1080,1439]` 可滚，
分段头压在折叠线上是滚动折叠的必然结果，往上滚半格就完整。把它钉成「必须在首帧完整可见」，
等于要求每一屏的分段头都不许出现在折叠线上——那是一条做不到的约束。与 A2.5 的区别在这里：
A2.5 是**唯一出口**藏在折叠线下（流程断裂），③ 是一段标题（观感取舍）。

## 6. 今日计划：计数行单行，脚注换行首

A2.1 的原始描述「`0/12` 只读出一条斜杠」看着像裁切，实测量出来是**挤压**：大数与目标数同一行，
右侧还挂着「还剩 N 个词」，顶档下三者抢宽，先把 `0` 挤没了形。修法是 `> 2f` 时把脚注挪到大数下方一行，
计数行保持单行（`summaryCountRowStaysSingleLineAtTopFontScale` 用 `GetTextLayoutResult.lineCount` 量）。
这一条与词卡不同：没换字号档，只换了行的归属。

## 7. 验证与量法（顶档判据必须量真实排版）

本轮红/绿都取自 emulator-5554（1080×1920px、density 2.625、`font_scale` 用完即复原 1.0）：

- **RED（把两处阈值回拨成 `> 5f` 复现旧摆法，同一判据当场见红）**
  - A2.3：`词头 DpRect(bottom=561.90.dp) 越过视口下沿 332.19.dp`（屏根高 659.43dp）。
  - A2.4：`「不认识」排成 2 行（expected:<1> but was:<2>）`。
- **GREEN**：`WordCardScreenTest` 本轮点名的 8 条（两条新判据 + A2.5 那条 + 2.0 档护栏 + 三档副标/固定档位/
  骨架形状四条回归）设备侧 `OK (8 tests)`。
- **JVM 契约**：`WordCardLayoutContractTest` / `WordCardScreenContractTest` / `StickyFooterHairlineGuardTest`
  共 26 条全绿；两枚变异探针各自见红——阈值回拨（`> 5f`）红 2 条，把堆叠分支改回三行硬写文案红 3 条，
  证明「每句文案恰一处」「清单引用恰 3 处」「`fontScale > 2f` 恰 3 处」三条锚都不是空转。
  探针一律用 Edit 撤销，不用 `git checkout --`（会抹掉在途未提交改动）。

**踩过的坑（写进交接，别重复踩）**：`SemanticsNodeInteraction.getUnclippedBoundsInRoot()` 回 `DpRect`，
`fetchSemanticsNode().boundsInRoot` 回像素 `Rect`，两者判等恒红——本轮两条新 connected 判据的第一版
就红在这一句上（红的是单位，不是版面）。顶档判据因此统一：Dp 比 Dp（对吸底区/视口下沿），
换行与否用 `GetTextLayoutResult.lineCount` 且必须 `useUnmergedTree = true` 取字级节点。

## 8. 不做什么

- 不给词头加 `maxLines + ellipsis`：词卡的词头就是学习内容，截成 `confide…` 是把要学的词改没。
- 不加省略号以外的兜底（`TextOverflow.Visible` 保持）：溢出必须靠摆法解决，不靠遮住。
- 不为顶档新增任何 token 或依赖；六枚判定函数都是本屏自有的 `internal` 纯函数。
- 不动 1.0 / 2.0 档的任何一像素（本轮若出现钉档形状变化即视为回归）。
