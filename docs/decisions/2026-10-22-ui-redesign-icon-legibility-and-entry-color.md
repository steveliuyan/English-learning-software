# 2026-10-22 图标描边层可识别性 + 入口容器配色收敛

状态：**已拍板，待实施**（实施落点 = 批 3 之后的 Task 27；本文件是那份实施的权威依据）
取证：[`docs/verification/2026-10-22-icon-duotone-and-color-load-qa.md`](../verification/2026-10-22-icon-duotone-and-color-load-qa.md)

## 背景

用户在真机（Mi 10S，浅色 · fontScale 1.0 · 今日计划屏）上指认两件事：

1. 「有些图标是一半一半的，生成不全，或者会变形。」
2. 「图标底部的颜色不能太杂乱，一个页面的颜色过多之后会造成视觉疲劳。」

取证把 ① 的根因定在**渲染档位**而不是字形本身：入口弱档（`EntryIconTone.Tint`）与底部导航都传
`AppGlyphLayer.StrokeOnly`，而 `ui/components/AppIcon.kt` 只在 `Duotone` 分支画 `fillPaths` ——
于是凡「识别特征只写在填充层」的字形（`Gear` 的旋钮、`List` 的三枚圆点、`Book` 的左页、`Image` 的日头…）
在这两条主路径上整层蒸发。放大取样实测：底导「设置」= 三条横线且第三条在 x=14 断口（正是旋钮该在的位置）、
今日计划「学习工具与设置」(`Gear`) 与「调整词书与目标」(`List`) 在 22dp 下读起来是同一枚图标。
视觉稿的口径相反：`.ic-tint svg { stroke: currentColor }` + `svg.g { fill:none }`，
稿里的 `<circle>` 与内幅一律是**描边**，任何档位都可见。`Clock`/`Search`/`Camera`/`Person`/`Info` 五枚
在转录时做成了「双层同形」（同一个圆既在 `strokePaths` 又在 `fillPaths`），是正确先例；其余没跟上。

②里两半要分开算：入口行数从稿 §7② 定稿的 3 枚涨到 6 枚是**实现放大**；
「按功能域上色」本身是稿与 spec §2.1 定的（§7④ 设置页一页 5 个色相）。所以收敛配色属**契约变化**，
按 AGENTS.md 的口径必须先改 spec 与本决策，再动码。

## 候选方案与决定

### 裁决 22-1：字形缺件 —— 采「补描边层 + 再换撞形字形」两步走（用户选 C）

- 候选 A：只补描边层（救缺件，不碰撞形）。
- 候选 B：只换语义（`Star` 从今日计划撤下、`Chart` 归位），不碰路径数据。
- 候选 C：**两件都做，按 A→B 分两小批**。—— **采纳**（用户 2026-10-22 选定）。

**第一步（缺件）**：给圆形新增一枚**描边档**标记 `CircleStroke(cx, cy, r)`，由 `AppIcon.kt` 解释成
过上下极点的两段圆弧（与 `circleFillPathData` 同一算法）后进 `strokePaths`。
逐枚补形清单（凡 `fillPaths` 承载识别特征的都要核）：`Gear` 旋钮、`List` 三圆点、`Book` 左页/书脊、
`Image` 日头、`Newspaper` 报头带、`Document` 正文块、`Translate` 小 A、`Upload`/`Download` 箭头头、
`Play`/`Pause`/`Stop`、`Warning` 内三角、`Bell` 钟体、`Trash` 桶身、`Bookmark`/`BookOpen` 左页。

**禁令随之改写**：`AppIcons.kt` 文件头那句「`CircleFill` 绝不允许出现在 `strokePaths`」不再写成绝对禁令，改为
「圆形进 `strokePaths` 一律走 `CircleStroke` 标记；`CircleFill` 仍只进 `fillPaths`」——
禁令的原意是「别把实心圆当描边用」，不是「描边层不许有圆」，`Clock` 等五枚的双层同形先例本就违背了字面。
2026-10-18 决策的**裁决 4（两层豁免表）不动**：五枚开放折线（`Back`/`ChevronRight`/`ChevronDown`/`Close`/`Check`）
仍不得进入 ≥40dp 入口容器，`allowsInEntryContainer` 那道 `require` 保持原样。

**第二步（撞形）**：`Star` 与 `Bookmark` 是同一轮廓只差缩放（稿 `#i-star-o` 自己就这么画，属源真值缺陷），
且 `Star` 完全不像星 → 「打卡与成就」改回稿 §6.1 定稿形 `Chart`（决策清单原文就写着 `Chart` 用于「学习记录；打卡页」），
「学习记录」现在错挂的 `Clock`（清单里 `Clock` 的用途是「时刻与提醒行」）让位给提醒行；`Star` 从今日计划撤下。
`BookOpen` 左右页不等宽（左 9 单位 / 右 6 单位）+ 书脊下垂凸角一并重绘为对称双页，**重绘要先改视觉稿再改路径数据**。

**新增契约（缺口封堵）**：现有图标契约只钉容器尺寸、墨色、层开关、枚数与豁免表，
**没有任何一条钉「StrokeOnly 下字形仍可区分」**（`AppIconsStructureContractTest` 只保证两层都在）。
本裁决要求补两枚：
① JVM 侧——上面清单里的字形，其识别特征必须同时出现在 `strokePaths`（圆形标记按新口径判）；
② connected 侧——同一枚字形在 `Tint` 与 `Gradient` 两档下的非背景像素占比差不得超过定值（抓「整层蒸发」这类回归）。

### 裁决 22-2：入口容器配色 —— 采「容器中性化 + 域色只染字形」（用户选 A）

- 候选 A：**入口容器底统一走 `AppColors.tint`（全站一档），域色只留在字形描边上，同组同色、跨组才换色**。—— **采纳**。
- 候选 B：每屏限 2 个域色（改动最小但灰块变多）。
- 候选 C：容器 16%→8–10% 且字形墨 `deep`→`text1`（等于放弃「域色 = 语义」这条编码）。
- 候选 D：容器一律薄荷、域色只留给数据可视化（最干净，与用户当初「设置页要有彩色图标」的诉求冲突最大）。

落地口径：

1. `EntryIconTone.Tint` 的底色从 `lerp(surface, deep, 0.16)` 改为 `appColors().tint`；字形仍取域 `deep` 单层描边。
   于是今日计划一屏的**彩色块数从 9 降到 0（容器同色）+ 6 枚细线色相**，品牌薄荷重新成为页面主调。
2. **同组同色**：一组入口行取该组域色，不逐行换色。今日计划 6 行收成 3 组——学习类 `Learn`、记录类 `Review`、
   系统类 `Settings`（这一条同时收掉「两枚相邻粉书签」「两枚相邻灰三线」）。
3. `EntryIconTone.Gradient`（域渐变实底 + 白/墨两层字形）**不删**，但退出入口行族，只留给稿 §7② 那种
   「卡内主行」与后续 AI 能力目录一类的强调块；若 Task 27 收口时它零生产消费方，按死码登记、由批收口裁决是否摘档。
4. 对比度**已当场复算，无阻塞**（WCAG 相对亮度，awk 现算，本文件之外的任何文档不得抄这四个字的结论而要抄算式）：
   浅套 7 域 `deep` 压 `tint #DDF7E8` = **3.55（AiSpeech）/ 5.67（Learn）/ 4.53（Review）/ 5.56（Library）/
   4.97（Reading）/ 6.36（Danger）/ 8.06（Settings）**，全部过图形非文本 3:1；
   深套 7 域（`deep = base`）压 `tint #1C2C26` = **5.24–8.63:1**，同样全过。
   最低配对是 `AiSpeech`，且它是**图形**配对，不受正文 4.5:1 约束——但**不得**在 `tint` 容器上放正文级文字
   （批 1 已就深色灰域容器留过同一句观察）。
   `IconBadgeContrastContractTest` 里那 28 个「`deep` 压 16% 混色底」的组合要按新配对（`deep` 压 `tint`）重算，
   旧配对的断言不得靠放宽阈值蒙过去。

## 影响

- **要改的文档**：本文件（权威）→ `docs/superpowers/specs/2026-10-17-ui-redesign-design.md` §2.1 硬规则 + §4 第 3 条 →
  `docs/design/2026-10-17-ui-redesign-mockups.html` 的 `.ic-tint` 底色、§6.2 三案说明与 §7②/§7④ 样张（稿改版列入 Task 27）。
- **要改的代码**：`ui/components/AppIcon.kt`（新标记解释）、`AppIcons.kt`（补形 + 文件头禁令改写 + 决策清单同步）、
  `IconBadge.kt`（`Tint` 档底色）、`TodayPlanScreen.kt` 与 `SettingsScreen.kt` 的 `domain` 具名实参（同组同色）、
  `AppBottomBar.kt`（字形受益，无需改码）。
- **要同步的测试**：`AppIconsStructureContractTest`（枚数不变、两层形状口径变）、`IconBadgeContrastContractTest`（新配对）、
  本裁决新增的两枚可识别性契约、三道棘轮（`RawColorLiteral` / `DarkTokenConsumption` / `SharedComponentUniqueness`）**只减不增**。
- **不受影响**：4 tab 骨架（用户 2026-10-17 拍板不动）、F5-03 的三条配色禁令（浅薄荷底禁白字、`text-3` 不作前景、
  裸 `Color.Red`）、`allowsInEntryContainer` 的豁免表准入。
- **代价与风险**：填充层在 `Tint`/底导两条主路径上仍不出现（第一步只是让描边层自足），所以
  「B 案 = 双色 duotone」这句话的实际生效面收窄到 `Gradient` 档与少数组件——
  若日后要恢复全站双层呈现，得另立决策，不得顺手把 `StrokeOnly` 改回 `Duotone`（浅底铺实心层会把字形糊成一坨，
  `InlineIconBadge` 的注释已写明）。
- **排期**：不塞进 Task 22-A/22-B（席位在飞、`AppIcons.kt` 与 `IconBadge.kt` 都牵动契约测试），
  登记为批 3 之后的 **Task 27**，与 Task 26 的真机 + TalkBack 取证同批做目视。
