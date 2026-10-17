# UI 重设计轮的自绘插画：原创矢量、上限 5 张、只用于空态

- 日期：2026-10-17
- 状态：已接受（随 UI 重设计轮实施，尚无代码产物）
- 相关规格：`docs/superpowers/specs/2026-10-17-ui-redesign-design.md` §4.5、`docs/specs/05-release-quality-and-compliance.md` F5-03
- 相关决策：`2026-09-22-card-illustration-assets.md`（词卡说明图，AI 生成位图，**本记录不修订它**）、`2026-10-17-ui-redesign-information-architecture.md`（六条信息架构裁决）
- 相关规范：`docs/design/visual-style-guide.md` §5.3（说明图）、§11（文档先行）
- 相关台账：`docs/third-party-notices.md` 的 `app-authored-ui-illustrations`、`material-symbols-icon-paths`

## 背景

用户在 UI 重设计轮的授权口径是「矢量图标 + 彩色容器 + 少量自绘插画」，并明确要求插画数量受控、
许可结论先登记再实施。逐屏审计（`docs/design/2026-10-17-content-placement-audit.md`）同时发现两类相关缺陷：

- 空态只有文字、且文案指向界面上不存在的控件（收藏空态写「点星标」`FavoritesScreen.kt:101`，实际控件是
  `CardDetailScreen.kt:386-394` 的 `PillButton "☆ 收藏"`），错词本空态是 38 字长句（`WrongWordsScreen.kt:90`）。
- 图标体系混乱：emoji、字母缩写徽标（「AI」「♪」「⏰」）与多处自绘异形图标并存，F5-03 已把
  「裸 tag 当 contentDescription」挡在门禁里，但图形本身仍不统一。

因此需要区分两种图形资产，本记录只管后者：**Material Symbols 内嵌路径数据**（图标，见台账
`material-symbols-icon-paths`）与**原创矢量插画**（空态与首启的情绪锚点，本记录）。

## 候选方案

1. **自绘原创矢量插画，内嵌为 Compose `ImageVector`，上限 5 张，只用于空态与首启引导（采用）**
2. 复用词卡说明图那条路线：AI 生成位图 WebP，`painterResource` 渲染
3. 不放插画，空态一律纯文字 + 一个动作

## 决定

### 范围与数量：上限 5 张，逐张钉用途，不做装饰性铺陈

| # | 插画 | 落点 | 承担的信息 |
|---|---|---|---|
| 1 | 首启 / 尚未选择词书 | 词书选取页空态与首启引导 | 「还没有书，先选一本」 |
| 2 | 收藏为空 | 「我的词 · 收藏」段空态 | 「在词卡详情点『☆ 收藏』」 |
| 3 | 生词本为空 | 「我的词 · 生词本」段空态 | 「标为不认识的词会进这里」 |
| 4 | 错词本为空 | 「我的词 · 错词」段空态 | 「答错过的词会自动进出，无需清空」 |
| 5 | 阅读历史为空 | 阅读历史页空态 | 「读过一篇就会出现在这里」 |

- **数量是硬上限**：新增第 6 张属范围变化，须先改本记录与规格 §4.5 再实施。
- 已有的「AI 学」页表情（`ui/mascot/`，纯 Compose Canvas，台账 `app-authored-ai-mascot`）继续沿用，
  **不占用**这 5 张名额——它是既有资产、非本轮新增。
- 词卡说明图（12 张 AI 生成 WebP）**维持现状与本记录无关**：那是"辅助理解词义"的内容图，
  本轮不重做、不替换、也不改动其许可结论（含其「发布前须复核生成服务条款」的残留风险）。

### 形式：原创矢量，内嵌 `ImageVector`，不新增依赖、不引入素材文件

- 与图标同一条路线：图形以 Kotlin 常量形式的 `ImageVector` 路径数据入库，不放 `res/drawable` 位图，
  不引入插图素材库或 Lottie 类依赖（`app/build.gradle.kts:163-165` `dependencyLocking { lockAllConfigurations() }`
  与本轮「不新增依赖」约束下，这是唯一不动依赖树的选项）。
- 造型约束：与视觉稿同一套域色 token（规格 §2.1），只用 `tint` / 域色 `base` / `outline` 系构成，
  浅色与深色各一套取值（深色下不做低透明度叠加）；线条重量与图标一致（2px 视觉重量），
  避免插画比图标"更花"。
- 无障碍：插画是**情绪锚而非信息载体**，`contentDescription` 一律 `null`（装饰性），
  语义由旁边的标题、一句说明与动作按钮承担；禁止把状态写进图形里让 TalkBack 读不出来。
  这条与 F5-03 已确立的「tag 不当描述」禁令同向。
- 不做动画：本轮插画为静态。打卡页"华丽动效"是用户已登记的**视觉定稿后独立批次**。

### 许可结论：原创、无第三方素材许可依赖

- 每张插画的几何路径由本项目自行设计绘制，不复制、不派生自任何第三方图库、素材包、图标集或角色作品，
  因此不触发 CC BY-SA 4.0 等素材许可义务，也不产生署名义务。
- **不使用 Material Symbols 作为插画载体**：图标集与插画用途分开登记，避免把 Apache-2.0 图标路径
  与原创图形混在同一声明里。
- **不使用任何第三方品牌 / 吉祥物造型**，也不模仿既有 AI 产品形象（沿用 `app-authored-ai-mascot` 条目的同一口径）。
- 已登记台账条目 `app-authored-ui-illustrations`（8 字段齐全，随 `verifyThirdPartyNotices` 门禁校验）。

## 未纳入

- 首屏 banner、运营位、课程/商城导流——本产品无内购无广告，且不借扇贝的导流结构
  （见 `2026-10-17-ui-redesign-information-architecture.md` 的「借什么、不借什么」）。
- 真实词书的配图（属内容资产，须逐批许可核验）。
- 加载/骨架屏的图形化（用规格 §3 的骨架条，不用插画）。

## 验收

- 上表 5 张逐张落地，超出数量的新增须先改本记录。
- `verifyThirdPartyNotices` 通过，且台账含 `app-authored-ui-illustrations` 与 `material-symbols-icon-paths` 两条。
- 浅色 / 深色两套真机截图各覆盖 5 处空态（风格指南 §9/§10 要求视觉一致性结论必须有真机证据）。
- TalkBack 走查确认：插画不可被朗读为空描述，空态的可读信息来自文字与动作。
