# 2026-10-25 Task 27 图标补形与入口配色收敛 工作报告

## 1. 本次目标

用户 2026-10-25 批准「立即做 Task 27」：图标描边层补形（含 `Star→Chart`、`BookOpen` 对称重绘）、
今日计划入口容器统一 `tint` 并同组同色、危险红归位。权威依据是
`docs/decisions/2026-10-22-ui-redesign-icon-legibility-and-entry-color.md`（裁决 22-1 / 22-2）。

## 2. 完成了什么

- `ui/components/AppIcon.kt`：新增 `CircleStroke(cx, cy, r)` 标记与 `circleStrokePathData`；
  与 `circleFillPathData` 共用私有 `expandCircleMarker`，同一 triple 展开串逐字相等；
  `pathNodesOf` 按两枚前缀分流。
- `ui/components/AppIcons.kt`：
  - `List` 三圆点、`Gear` 旋钮、`Image` 日头、`Newspaper` 报头带、`Document` 正文框、`Bell` 钟口横沿与钟舌
    补进 `strokePaths`（`List` 填充层同步由方块改为同 triple 的 `CircleFill`）；
  - `BookOpen` 重绘为对称双页（左 8 段 / 右 8 段，中线 x=12）；
  - 文件头与 `AppIconSpec` KDoc 的两层禁令改写为「`CircleFill` 只进 fillPaths、`CircleStroke` 只进 strokePaths」；
  - 枚数仍 33、`StrokeOnlyIcons` 仍 5 枚，未增删字形。
- `ui/components/IconBadge.kt`：`EntryIconTone.Tint` 底色 `lerp(colors.surface, palette.deep, 0.16f)`
  → `appColors().tint`，删除 `lerp` import，KDoc 同步 22-2。
- `ui/TodayPlanScreen.kt`：入口组三组同色——`词书单词` `Learn`；`我的词` / `学习记录` / `打卡与成就` `Review`；
  `学习工具与设置` / `调整词书与目标` `Settings`。字形：`Clock`→`List`、`Star`→`Chart`、`List`→`Book`，
  六行六形；常驻查词行仍在组外（`Library`）。
- `ui/AiProfileSettingsScreen.kt`：五处 `AppDomain.Reading` → `AppDomain.Danger`。
- 四屏出站确认按钮 `domain = AppDomain.Learn` → `AppDomain.AiSpeech`（各两枚）：
  `ImageStudioScreen` / `PhotoTranslationScreen` / `SentenceAnalysisScreen` / `WordAiQaScreen`。
- 文档：
  - 新增计划 `docs/superpowers/plans/2026-10-25-ui-icon-and-entry-color-convergence.md`；
  - 裁决文件状态改「已实施」并追加「实施记录」；
  - `docs/superpowers/specs/2026-10-17-ui-redesign-design.md` §4.3 入口行族默认弱档口径；
  - 视觉稿 `.ic-tint` 底色、`BookOpen` 七处路径、§6.2 说明（上一会话已改，本批随同提交）。
- 测试：新增 `TodayPlanEntryColorContractTest`、`DangerInkPlacementContractTest`；
  改写 `AppIconsStructureContractTest`（CircleStroke 形状 + 六枚识别锚点 + 段数守恒展开）、
  `IconBadgeContrastContractTest`（tint 配对 + 源码钉）、
  `SharedComponentGeometryContractTest`（弱档配方腿由 16% 混色改为 tint 正锚 + 旧混色禁形）。

## 3. 验证记录

RED（TDD 第一步）：
- 首跑 4 个目标类：编译失败 `Unresolved reference 'circleStrokePathData'`（新 API 未实现，6 处）。
- 补上 `CircleStroke` 解释层后再跑：`49 tests completed, 6 failed`——识别锚点缺失、旧混色仍在、
  今日计划旧分组、AiProfile/四屏域色全部按预期红。

GREEN（最小实现后）：
```
"$GRADLE" :app:assembleDebug :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin \
  verifyThirdPartyNotices verifyAcTraceabilityMatrix --offline --no-daemon --no-build-cache
```
`BUILD SUCCESSFUL`；JVM 单测结果汇总 **243 类 / 1798 例 / 0 失败 / 0 错误 / 0 跳过**
（批 3 关账基线 241 / 1789，本批 +2 类 +9 例，只增不减）。
`verifyThirdPartyNotices`、`verifyAcTraceabilityMatrix` 均通过。
detekt/ktlint 未运行：本仓门禁是单测，静态分析为存量债基线（`docs/decisions/2026-09-19-static-analysis-and-test-baseline.md`）。

真机目视：本批未做。`bf353dda` 只允许手工试用（`adb install -r` 里程碑），
入口弱档字形辨识与六行分组的目视结果列入遗留。

## 4. Git 状态

- 分支：`stage-5-ui-r2-learning-path`，**已推送**（用户 2026-10-25 批准）。
- 本批代码+测试提交：`65cf464`（14 文件，+664/-138）；文档提交 `a99dc9b`。
- 远端核验：`git ls-remote origin stage-5-ui-r2-learning-path` == `git rev-parse HEAD` ==
  `a99dc9bd40033bb5d441f3755ff962f8fc77d55a`。
- 真机装机（里程碑）：`adb -s bf353dda install -r` **Success**；monkey 启动后
  `mCurrentFocus=com.example.englishlearning/.MainActivity`。未在真机跑任何 instrument。
- 前序提交：`fb7def4`（圆形图标补显式圆弧，修「描边档只剩半圈」，Task 27 首项）。

## 5. 未完成 / 遗留问题

- **真机目视**：APK 已装到 `bf353dda` 并正常启动；待用户手工确认今日计划六行（弱档 `tint` 底、
  六枚字形辨识、`Chart`/`List`/`Book` 是否易区分）与 `AiProfileSettingsScreen` 危险红。
- **`EntryIconTone.Gradient`**：`SettingsScreen` 卡内主行仍有 2 处生产消费，故未登记死码、未摘档。
- **Task 42（R-B）**：目标页 18 屏 71 处 `MintPrimary` 迁移待用户拍板三案；本批未夹带。
- **批 3 结转**：Task 26 Step 8 真机全链、Task 35/36/37/38/39/40 仍在台账。
- `docs/verification/2026-10-25-ui-color-and-icon-consistency-audit.md` 是本轮审计产物，随本批文档提交。

## 6. 下一任接手须知

- 先读：本报告 → `docs/decisions/2026-10-22-ui-redesign-icon-legibility-and-entry-color.md`（已含实施记录）
  → 计划 `docs/superpowers/plans/2026-10-25-ui-icon-and-entry-color-convergence.md`。
- 契约读法：图标结构断言在 `AppIconsStructureContractTest`（形状按层分流：`strokePaths` 收 SVG/CircleStroke，
  `fillPaths` 收 SVG/CircleFill），弱档底色在 `IconBadgeContrastContractTest` 与
  `SharedComponentGeometryContractTest` 两处钉住，改底色要同步两处。
- 环境：`gradlew` 不可用；用本机 `gradle-8.13-bin`（hash 子目录随安装变化，用 `find` 发现），
  管道必须 `; exit ${PIPESTATUS[0]}`。
- 若要继续图标工作：`CircleStroke` 的半径按「描边后外径 ≈ 原填充直径」取值；
  新增识别特征先补 `AppIconsStructureContractTest` 的锚点表，再改生产。

## 7. 用户确认过的决策

- 2026-10-25：「可以，立即做」——批准 Task 27 四件（图标补形 + `Star→Chart` + 入口统一 `tint` + 危险红归位），
  无需再次拍板。
- 2026-10-22（继承）：裁决 22-1 选 C（补描边 + 换撞形两步走）；裁决 22-2 选 A（容器中性化、域色只染字形）。
- 本批补充裁定（裁决未明示处）：`学习记录` → `List`、`调整词书与目标` → `Book`，以满足「同组同色但六行六形」。
