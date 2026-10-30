# 移除「AI 助学」页头部卡的动态表情

- 日期：2026-10-31
- 状态：已接受（同日实施）
- 相关规格：`docs/superpowers/specs/2026-09-23-ai-learning-tab-design.md` §4.1、§4.5、§6（AC-10~AC-14 作废）、§7
- 相关台账：`docs/third-party-notices.md` 的 `app-authored-ai-mascot`（随功能删除，条目一并移除）
- 相关规范：`docs/design/visual-style-guide.md` §5.3 里「既有资产、不占插画名额」的那一句

## 背景

「AI 助学」页的头卡顶部有一枚自撰的玻璃质感表情（`ui/mascot/`，呼吸 + 眨眼，只把「AI 是否已配置」
映射到眼睛），由 spec §4.5 逐条约束，并有 JVM 几何断点与 instrumentation 语义断言。

2026-10-31 用户在真机走查中提出：「AI 助学那个表情给删掉吧，我不需要」。理由是装饰性形象不提供信息，
且它是全仓唯一的无限动画（虽随系统「减弱动效」停住，但仍增加每帧绘制与维护面）。

## 候选方案

1. **整枚删除**：去掉头卡里的表情调用、删除 `ui/mascot/` 三个源文件与两份测试、清掉债务台账里的
   `EyeColor` 裸 hex 站点、从许可台账移除 `app-authored-ai-mascot`（采用）
2. 只隐藏不删代码：留组件与测试，头卡不再调用 —— 留下死代码与恒绿的假测试，否决
3. 缩小成 24dp 装饰图标并入标题行 —— 用户说的是「不需要」，不是「换小」，否决

## 决定

采用方案 1：

- `AiHeaderCard` 只保留三段：主标题「AI 助学」→ 副标题「这里汇总 AI 能力与各自的接通进度」→ 状态胶囊。
  头卡的品牌渐变、`onBrand` 墨色与白胶囊静态墨**一字不动**（`AiBadgeContrastFixTest` 继续钉住）。
- 删除 `ui/mascot/AiMascot.kt`、`AiMascotExpression.kt`、`AiMascotGeometry.kt`，以及
  `AiMascotTest`（instrumentation）与 `AiMascotGeometryPolicyTest`（JVM）。
- `RawColorLiteralRatchetTest` 的 seed 表清零：表情是 `ui/` 内最后一处裸 hex（`EyeColor`），
  删除后 `ui/`（令牌定义本体除外）达到零裸 hex；分母不变，任何新裸 hex 仍当场红。
- 规格同步：§4.1 布局由三段改两段；§4.5 整节标注作废并留指针；§6 的 AC-10~AC-14 作废（保留编号，避免
  历史引用断链）；§7「明确不做」补一条「AI 表情与吉祥物」。
- 许可台账：`app-authored-ai-mascot` 条目移除（资产不存在即不登记，台账只描述真实随包内容）。
- 新增 connected 断言 `AiLearningScreenTest.header_no_longer_carries_the_ai_mascot`：以 `ai_mascot`
  testTag 不存在为判据，防止按旧设计图回加。

## 影响

- 头卡变矮，首屏可多露出一行「现在能用」；状态胶囊与标题的对比度约束不变。
- 全仓不再有无限动画；「动效减弱」的真机项少一处观察点（`AiMascot` 曾是唯一 `rememberInfiniteTransition`）。
- 规格与设计文档中的表情条款转为历史；后续若要重新引入形象，须新开决策，不得直接照 §4.5 回滚。
- 债务面：`RawColorLiteralRatchetTest` 从「1 处登记残留」变为「零残留」，这是收紧，不是放宽。
