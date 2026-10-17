# 决策记录 · UI 重设计批 1 开工前：对比口径两处缺陷裁决与图标两层豁免表（2026-10-18）

## 背景

设计规格（`docs/superpowers/specs/2026-10-17-ui-redesign-design.md`）与视觉稿定稿、图标 B 案选型
（见 `docs/decisions/2026-10-18-ui-redesign-icon-duotone.md`）之后，批 1（设计系统底座，计划 Task 2–12）
开工前的规格复核按实测发现两处缺陷，另「33 枚图标每枚两层」的口径需要一张豁免表。
三条都直接决定 Task 2 的允许配对表与 Task 6/7 的 `StrokeOnlyIcons` / `allowsInEntryContainer` 取值，
必须以本文钉死在任何代码之前。

本文全部比值按 WCAG 2.x 相对亮度公式当场复算（`c = srgb/255`；`c ≤ 0.04045` 取 `c/12.92`，
否则取 `((c+0.055)/1.055)^2.4`；`L = 0.2126R + 0.7152G + 0.0722B`；`ratio = (L_hi + 0.05)/(L_lo + 0.05)`），
非估算、非沿用陈旧值。入库后判定不依赖本文数字：`DesignTokenContractTest`（Task 2）与
`AiBadgeContrastFixTest`（Task 23）用同一公式在测试内现算。本文的「33 枚 / 5 枚豁免」是**决策钉定值**
而非现状计数，重算命令见「影响」节。

## 候选方案

**缺陷一 · `text-3 #7A9088` 达不到正文对比。** 实测压 `surface #FFFFFF` **3.40**、压 `bg #F1FBF5` **3.22**、
压 `tint #DDF7E8` **3.00**，全部低于 AA 正文 4.5；视觉稿却把它当常规字号辅助文字在用。
- A. 把 `text-3` 调深到通过 4.5 —— 否。调深后与 `text-2 #4E756A` 几乎同档，三级弱化在视觉上不再成立。
- B. 保留取值，角色降级为非正文 —— **采纳**（裁决 1）。

**缺陷二 · `text-2 #4E756A` 压 `tint-2` 不合格。** 实测压 `tint-2 #DCEAE1` **4.15**、压 `tint-3 #CDEDD9` **4.10**，
低于 4.5；压 `surface`/`bg`/`tint` 为 5.15/4.87/4.55 均合格。
- A. 把 `tint-2`/`tint-3` 调浅到通过 —— 否。两档是容器层次的中间调，改浅等于重排整组浅底梯度。
- B. 把 `text-2` 再调深 —— 否。与缺陷一 A 同病，且 `text-1`/`text-2` 两级会被压扁。
- C. 限定 `text-2` 可用底，`tint-2`/`tint-3` 上的正文一律 `text-1` —— **采纳**（裁决 2；
  `text-1` 压两底实测 13.69/13.54，余量充足）。

**缺陷三 · `on-mint #174C44` 压品牌渐变末端不合格。** spec §2.1 原文「前景一律 `on-mint`（实测 ≈4.6:1）」。
复算：`on-mint` 压渐变末端 `#35B9B5` 仅 **4.07**，压中段 `#5DDFB4` 5.89，压起点 `#A8F3C8` 7.58
——「≈4.6」只在浅薄荷底一侧宽松成立（压 `bg` 9.22、压 `tint` 8.61、压 `mint-light #D9FFF0` 9.06），
渐变末端跌破 4.5。
- A. 渐变上仍用 `on-mint`，按段位换字色（亮段 `on-mint`、暗段 `on-brand`）—— 否。切换点落在渐变中段，
  运行时不可计算，等价于放任。
- B. `mint` 渐变从浅到深，同一渐变上的文字**统一取 `on-brand #04241C`**（压末端 6.87、压中段 9.95、
  压起点 12.81，处处合格）；`on-mint` 保留给 `tint`/`mint-light`/`bg` 一类浅薄荷底 —— **采纳**（裁决 3）。

**豁免需求 · 纯描边字形建不出第二层。** `Back`、`ChevronRight`、`ChevronDown`、`Close`、`Check` 五枚是开放
折线，没有可闭合的内部区域；强行加填充层等于把描边变实心，正是 B 案选型要避开的「两族字形」。
- A. 33 枚一律两层 —— 否，理由同上。
- B. 这五枚改为可闭合造型 —— 否。方向类符号改形即失可辨识性。
- C. 设两层豁免表，五枚只作描边骨架且不得进入 ≥40dp 入口容器 —— **采纳**（裁决 4）。

## 决定

1. **`text-3` 降级为「非正文角色」**：仅用于 ≥24sp 大数字/装饰性大字与纯图标描边，**不得作任何正文或标签前景**
   （实测 3.00–3.40，达不到 AA 正文 4.5）。需要第三级弱化文字时用 `text-2` + 降字号，不用 `text-3`。
2. **`text-2` 的可用底限于 `surface`/`bg`/`tint`**（5.15/4.87/4.55），**禁压 `tint-2`/`tint-3`**（4.15/4.10）。
   `tint-2`/`tint-3` 上的正文一律 `text-1`。允许配对与禁配表由 Task 2 的 `DesignTokenContractTest` 钉值。
3. **品牌渐变上的文字统一取 `on-brand #04241C`**：`on-mint` 在 `#35B9B5` 上只有 4.07；`on-mint` 保留给
   浅薄荷底。spec §2.1 的「≈4.6:1」原文口径只适用于浅薄荷底，该段已按本条改钉。
4. **图标两层结构豁免表**：以下 **5 枚只有描边骨架、不建填充层**——`Back`、`ChevronRight`、`ChevronDown`、
   `Close`、`Check`；且**这 5 枚不得进入 ≥40dp 入口容器**（只作行内/尾部指示）。
   AC-R-05b 的「每枚两层」据此改为「除豁免表外每枚两层」；`AppIconsStructureContractTest`（Task 6）双向钉：
   豁免表内出现 `fillPaths` 同样判失败。

### 33 枚图标逐枚钉定清单

名字与顺序以计划 `docs/superpowers/plans/2026-10-17-ui-redesign.md` Task 6 Interfaces 的
`enum class AppIcon` 枚举为准（本清单即 spec §10 第 5 条留给批 1 第一个任务钉进 `AppIcons.kt` 顶部注释的那份）。
用途按视觉稿 §6.1/§6.2 与批 2/批 3 任务推断；**推断不到具体屏的写「行内/入口通用」**，要用于具体站点前
先回本文补一行。

| # | 字形 | 用途（落点） | 两层 |
|---|---|---|---|
| 1 | `Back` | 二级层与浮层页头返回键（Task 9 `AppScreenHeader`） | 豁免（描边only） |
| 2 | `ChevronRight` | 入口行/词行尾部「›」指示（Task 10 `EntryRow`） | 豁免（描边only） |
| 3 | `ChevronDown` | 展开/折叠指示（AI 主页折叠行、生成前参数） | 豁免（描边only） |
| 4 | `Close` | 对话框、搜索框与浮层关闭键 | 豁免（描边only） |
| 5 | `Check` | 完成与成功回显对勾（`InlineNoticeKind.Success` 行） | 豁免（描边only） |
| 6 | `Search` | 查词入口与全局搜索框（§6.1「查词」；词汇域三入口） | 两层 |
| 7 | `Book` | 底导「学习」tab 合上的书，现状形状（Task 17） | 两层 |
| 8 | `BookOpen` | 摊开的书，词书/阅读页头（§6.1「词书」双页形） | 两层 |
| 9 | `List` | 列表与任务条目行（今日计划入口行组、词表行） | 两层 |
| 10 | `Bookmark` | 收藏（§6.1「收藏」；「我的词」收藏段） | 两层 |
| 11 | `Star` | 行内/入口通用（批 2/3 计划未钉站点，用前先补本文） | 两层 |
| 12 | `Clock` | 时刻与提醒行（§6.1「提醒」即时钟形；到期时间显示） | 两层 |
| 13 | `Chart` | 统计与学习记录（§6.1「学习记录」折线形；打卡页） | 两层 |
| 14 | `Spark` | AI 功能与生成入口（底导「AI」tab；Task 23 替「AI」含字徽标） | 两层 |
| 15 | `Speaker` | 发音药片（§6.1「发音」；Task 12 `SpeakerButton`） | 两层 |
| 16 | `Play` | 播放控件起播（发音/音频播放） | 两层 |
| 17 | `Pause` | 播放控件暂停（`SpeakerState.Paused` 对应形） | 两层 |
| 18 | `Stop` | 播放控件停止（「退出即停播」链路） | 两层 |
| 19 | `Mic` | 语音输入与「听/讲」类 AI 功能入口（七功能映射由 Task 23 钉定） | 两层 |
| 20 | `Translate` | 翻译功能（文章翻译、拍照翻译译文） | 两层 |
| 21 | `Camera` | 拍照翻译取图入口（AI 七功能「拍」） | 两层 |
| 22 | `Image` | 图像类 AI 功能（`ImageStudioScreen`，七功能「画」） | 两层 |
| 23 | `Document` | 文本类 AI 功能与生成结果行（七功能「文」） | 两层 |
| 24 | `Newspaper` | 底导「阅读」tab 与今日文章（Task 17） | 两层 |
| 25 | `Gear` | 底导「设置」tab 与设置域入口（Task 17） | 两层 |
| 26 | `Person` | 资料卡（裁决②：资料卡不再显示今日计划数字） | 两层 |
| 27 | `Info` | 关于/许可行（§6.1）与 `ScreenUnavailable` 默认图标（Task 9） | 两层 |
| 28 | `Bell` | 行内/入口通用（§6.1 提醒行取的是时钟形，本枚落点未钉屏） | 两层 |
| 29 | `Shield` | 认证备份入口（§6.1「认证备份」盾形；备份与恢复子页） | 两层 |
| 30 | `Warning` | 警示与破坏性操作行（`InlineNoticeKind.Error`、删除确认） | 两层 |
| 31 | `Trash` | 删除动作（§6.1「删除」；删词书/清空学习数据） | 两层 |
| 32 | `Upload` | 导入口（§6.1「导入」箭头向上形） | 两层 |
| 33 | `Download` | 导出入口（§6.1「导出」箭头向下形） | 两层 |

## 影响

- spec §2.1 硬规则段、§9 AC-R-03、§9 AC-R-05b 已按本文同步修订；
  `docs/design/visual-style-guide.md` §2.3 与 §12 各补一行同口径。该文档 §2.2 表中
  `OnMintForeground`「浅薄荷底与品牌渐变专用（≈4.6:1）」与 `OnBrandDeep`「唯一替代」两行的旧措辞
  自本文起被裁决 3 覆盖，批 1 Task 2 校准 token 时同步改写。
- 下游钉值关系：Task 2 `DesignTokenContractTest` 的允许/禁配表来自裁决 1/2/3；
  Task 6 `StrokeOnlyIcons` 与 `AppIconsStructureContractTest` 来自裁决 4 与上表；
  Task 7 `IconBadge` 的 `allowsInEntryContainer` 来自裁决 4 的「≥40dp 入口容器」半句；
  Task 23 渐变徽标前景取 `on-brand` 来自裁决 3。
- 枚数重算（Task 6 入库后在仓库根目录执行，Expected: `33`）：
  `grep -o 'enum class AppIcon {[^}]*}' app/src/main/java/com/example/englishlearning/ui/components/AppIcons.kt | tr ',' '\n' | grep -cE '[A-Z][A-Za-z]+'`。
  增删字形或改动豁免表属范围变化：**先改本文清单，再改枚举**，不得静默改数（与计划 Task 6「枚数核对」同一条铁律）。
- 深色模式不在本裁决范围：深色下 `on-mint` 为 `#DFF6EC`，渐变与深底前景口径按 AC-R-11 真机实测核验，
  跌破阈值时另立补充决策，不在此预判。
- 许可台账不受影响：豁免表 5 枚路径仍为原创自绘描边，`app-authored-ui-icons` 与
  `material-symbols-icon-paths` 的既有措辞不变。
