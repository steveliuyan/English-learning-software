# 2026-10-25 Task 27 实施计划：图标补形 + 入口容器收敛 + 危险红归位

权威依据：`docs/decisions/2026-10-22-ui-redesign-icon-legibility-and-entry-color.md`
（裁决 22-1 两步走、22-2 容器中性化）。本文件只写**怎么实施**，不重复裁决理由。

## 范围（用户 2026-10-25「可以，立即做」批准）

1. **图标补形（22-1 第一步）**：新增 `CircleStroke(cx, cy, r)` 标记；把只存在于填充层的识别特征补进描边层。
2. **撞形处理（22-1 第二步）**：`Star` 撤出今日计划，「打卡与成就」改 `Chart`；`BookOpen` 重绘为对称双页。
3. **入口容器收敛（22-2）**：`EntryIconTone.Tint` 底色改为 `appColors().tint`；今日计划六行按三组同组同色。
4. **危险红归位**：`AiProfileSettingsScreen` 五处 `AppDomain.Reading` → `AppDomain.Danger`；
   四屏出站确认按钮的 `domain = AppDomain.Learn` → `AppDomain.AiSpeech`（AI 出站属 AI 语音域）。

## 非目标

- 目标页 18 屏 71 处 `MintPrimary` 迁移（R-B 三案，待用户拍板，Task 42）。
- `EntryIconTone.Gradient` 摘档：保留枚举与渲染，零生产消费方时按死码登记（本文件只在报告里记数）。
- 页头收编（Task 35）、AC-R-22 余项（Task 38）。
- 新增字形 / 改动豁免表：枚数仍 33、`StrokeOnlyIcons` 仍 5 枚。

## 实施顺序（TDD）

### Step 1 先改视觉稿（裁决 22-1 明确要求「重绘先改稿」）

- `docs/design/2026-10-17-ui-redesign-mockups.html`：
  - `.ic-tint` 底色从 `color-mix(in srgb,var(--d) 16%,var(--surface))` 改为 `var(--tint)`；
  - 七处 `i-book-open` 路径改为对称双页（左 8 / 右 8，中线 x=12）；
  - §6.2 说明补一句「B 案弱档容器 = tint、域色只染字形（2026-10-22 裁决 22-2）」。

### Step 2 RED：契约测试先红

`AppIconsStructureContractTest`：
- 标记形状判据支持 `CircleStroke`：描边层只许 `CircleStroke`，填充层只许 `CircleFill`；
- 新增「StrokeOnly 识别特征仍在」逐枚锚点表（`Gear`/`List`/`Image`/`Newspaper`/`Document`/`Bell`）；
- `CircleStroke` 展开产物与 `CircleFill` 同算法（两段圆弧、极点数值、坏标记抛错）。

`IconBadgeContrastContractTest`：
- 弱档配对由「`deep` 压 `surface` 混 16%」改为「`deep` 压活 token `tint`」，两套主题 × 7 域复算；
- 源码钉：`Tint` 档底色必须是 `appColors().tint`，`lerp(colors.surface, palette.deep` 归零；
- `inlineTintBackdrop` 计数由 1 改为 2（入口弱档 + 行内共用同一 token）。

新增 `TodayPlanEntryColorContractTest`：
- 今日计划六行按三组同组同色（学习 `Learn` / 记录 `Review` / 系统 `Settings`）；
- 字形唯一：六行 `AppIcon` 两两不同；`Star` 不再出现在今日计划；打卡行 = `Chart`；
- `AppDomain.Reading` 不再作为入口行的域色（「我的词」归记录组）。

新增 `DangerInkPlacementContractTest`：
- `AiProfileSettingsScreen` 不得出现 `AppDomain.Reading`；五处破坏性/错误墨必须是 `AppDomain.Danger`；
- 四个 AI 屏的出站确认按钮 `domain = AppDomain.Learn` 归零，改为 `AppDomain.AiSpeech`。

### Step 3 GREEN：最小实现

- `AppIcon.kt`：`CircleStroke` 解释（与 `circleFillPathData` 共用同一个圆→两段弧算法）；
- `AppIcons.kt`：补六枚识别特征、`BookOpen` 对称重绘、文件头禁令改写；
- `IconBadge.kt`：弱档底色改 `appColors().tint`，删 `lerp`；
- `TodayPlanScreen.kt`：三组同色 + 六枚字形唯一（`Clock`→`List`、`Star`→`Chart`、`List`→`Book`）；
- `AiProfileSettingsScreen.kt` 与四屏出站按钮：域色归位。

### Step 4 门禁

```
"$GRADLE" :app:assembleDebug :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin \
  verifyThirdPartyNotices verifyAcTraceabilityMatrix --offline --no-daemon --no-build-cache
```

单测基线（批 3 关账）241 类 / 1789 例 / 0 失败 / 0 跳过，只增不减。

### Step 5 收口

- 更新裁决 22-1/22-2 文件状态为「已实施」并记录实际落点；
- 更新 `docs/superpowers/specs/2026-10-17-ui-redesign-design.md` §4 第 3 条（弱档底 = `tint`）；
- 写工作报告（REPORT-TEMPLATE 七节）、按功能域提交；推送与合 `main` 另请用户批准。

## 风险与判据

- **弱档对比**：`deep` 压 `tint` 的最低配对是浅套 `AiSpeech`（3.55:1），契约必须按活 token 复算，
  不得抄数字；深套 `deep = base`，最低 5.24:1。
- **同组同色不等于同形**：颜色收成三组后，六行仍须六种不同字形——`Gear`/`List` 相邻同形的教训。
- **`CircleStroke` 与 2px 描边叠加**：圆形半径按「描边后外径 ≈ 原填充直径」取值（`Image` r=2 保持环状、
  `List` 圆点 r=0.5 得到 ≈3px 实心点），避免小图标被描边糊成一坨。
- **`BookOpen` 重绘**：只改几何与来历注释，不动枚举/豁免表；段数守恒契约（作者段数 == 解析节点数）必须继续绿。
