# 决策：词库四分段改挂共享 SegmentedControl（并给屏侧裸圆角加 ratchet）

日期：2026-11-09　范围：`ui/WordBankScreen.kt`、`ui/components/SegmentedControl.kt`、
`ui/TodayPlanScreen.kt`、`ui/PhotoTranslationScreen.kt`、两个契约测试

## 背景

设置页（m00649 第 3 项）已把主题选择换成共享 `SegmentedControl`。词库页 `WordBankScreen.kt` 仍是
第二套写法：自画 `Row` + `clip(RoundedCornerShape(13.dp))` 轨道 + `RoundedCornerShape(10.dp)` 段项 +
`colors.tint2` 凹槽 + `AppType.Body` 半粗 + 手写 `stateDescription = "已选中"/"未选中"`。
同一件东西两副脸：轨道色不同（`tint2` vs `surface2`）、圆角不同（13/10 vs 药片）、字号不同（15sp vs 12sp）。

## 决定

### 1. 共享件补两个可选参数，词库页只交「三张词汇表」

`SegmentedControl` 新增：

- `itemContentDescriptions: List<String>? = null` —— 段项的读屏文本（词库页要念「生词 3」而不是「生词 3」
  这种标签本身的溢出文本，且「一」档要念「词书」而非「词书 —」）。
- `announceSelection: Boolean = false` —— 选中态说「已选中 / 未选中」（本仓 a11y 惯用词，
  见 `AppBottomBar.kt:192`、`AppSideNav.kt:93`、`LearningRecordsScreen.kt:102`、`PickRow.kt:119` 等；
  而 `selectable` 自带的 Selected 说「已选择」，与之不同）。

默认值保持 `null`/`false`，**设置页调用点不动**。词库页调用点只交四样东西：`options`（标签）、
`itemTestTags`（枚举 tag）、`itemContentDescriptions`（读屏文本）、`announceSelection = true`；
`wordBankSegmentLabel` / `wordBankSegmentDescription` 两个纯函数与「—」不可用标记保留不变
（它们承载「条数未读到」与「词书不显示计数」两条既有语义，契约测试按次数钉着）。

### 2. 接受的可视差异（已记账）

| 面 | 原来 | 现在 |
| --- | --- | --- |
| 轨道底 | `colors.tint2` + 13dp | `colors.surface2` + `AppShape.Pill` |
| 段项底/圆角 | `surface` / 10dp | `surface` / `AppShape.Pill` |
| 段项文字 | `AppType.Body` 15sp 半粗 | `AppType.Caption` 12sp 600 |
| 轨道内距 | 3dp + 3dp | 3dp + 2dp（共享件） |
| 命中高度 | `heightIn(min = 48.dp)` | `heightIn(min = 48.dp)`（不变） |

判据：分段是「一颗药片里的一排段」，原型给的是 `--r-pill`；字号统一到 `Caption` 是为了让「词书 | 生词 3 |
错词 — | 收藏 0」四格在窄屏不折行。要改回去需要有原型的第二条依据，不接受「旧截图看着顺眼」。

### 3. 同批清掉屏侧裸圆角，并加 ratchet

清了三处历史债里可以清的三处（第四处留豁免）：

- 词库页 13dp / 10dp 两处：随分段收口一起消失。
- `PhotoTranslationScreen.kt` 输入框 `RoundedCornerShape(14.dp)` → `AppShape.Button`（同值，具名化）。
- `TodayPlanScreen.kt` 搜索框 `RoundedCornerShape(14.dp)` → `AppShape.Button`（同值）。
- **保留**：`TodayPlanScreen.kt` 计划图例的 8dp 方点 `RoundedCornerShape(2.dp)`。`AppShape` 五档里没有 2dp，
  拿 10dp 去画 8dp 方点会把它画成圆点——那是一次没有原型依据的视觉改动，不进本批。它作为**唯一一条**
  记在 `ContainerContractTest.rawRadiusAllowance` 里（`"TodayPlanScreen.kt" to 1`）。

## 守卫

- `WordBankScreenContractTest` 新用例「the four segments come from the shared control」：`SegmentedControl(` 恰 1 处、
  四串接线各恰 1 处、`wordBankSegmentDescription(` 恰 2 处（声明+调用）、`RoundedCornerShape(` 为 0、
  `tint2` 为 0，并有反空转样本。
- `ContainerContractTest` 新用例「no listed screen hand rolls a corner radius」：逐屏（29 屏清单）要求
  `RoundedCornerShape(` 计数等于豁免表值、`CornerSize(` 恒为 0；反空转样本 + 豁免表自检（名字必须在清单里、每屏最多 1 处）。

## 验证

- `gradlew.bat :app:testDebugUnitTest --offline` → BUILD SUCCESSFUL，**348 个测试类 / 2603 个用例全绿**。
- `connectedDebugAndroidTest`（Xiaomi M2102J2SC `bf353dda`）`com.example.englishlearning.ui.WordBankScreenTest`
  → Starting 4 / Finished 4 全绿：四格 tag 在、`assertHeightIsAtLeast(48.dp)`、`assertTextEquals("词书"/"生词 3"/
  "错词 —"/"收藏 0")`、StateDescription「已选中」/「未选中」、分段切页与「返回」contentDescription。
