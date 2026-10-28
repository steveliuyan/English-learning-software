# 2026-10-30 版式重做 批 2（学习链路 5 屏 + 学习设置）交付报告

计划文档：`docs/superpowers/plans/2026-10-30-layout-redesign-batch-2-learning-path.md`
规范文档：`docs/superpowers/specs/2026-10-25-full-layout-redesign-design.md`
范围：`WordCardScreen` / `CardDetailScreen` / `VocabularyRelearnScreen` / `WorksheetSettingsScreen` / `WorksheetPreviewScreen` / `LearningSetupScreen`（住 `AppScreen.kt`）

## 1. 本次目标

把学习链路上的六屏从批 1 的骨架口径推到「能上真机看」的完成度：

- 词卡：顶栏 ×+计数+进度条，主词 44sp 与音标/发音同一视觉组，三档反馈常驻底部。
- 词义详情：撤 4:3 大图改 72dp 缩略图，五枚药片改两行 3+2 网格，「我的笔记」标题只留一处。
- 重新学习：返回并入共享页头，三档评分吸底。
- 默写纸设置：方向控件上移到范围之前、范围改单选。
- 默写纸预览：顶部有返回、导出固定底部、页纸取 token 不再硬编码白。
- 学习设置：返回去重、四开关收进弱卡分组、术语统一「不认识」、保存改实底主操作。

约束（全程未松）：只改版式与信息架构，**不动**数据层 / FSRS / AI 请求策略 / 备份格式 / Keystore / ViewModel 状态机；改期望值不放宽判据；TDD 先红；大字号（`fontScale = 2f`）不退化；一屏一个主操作。

## 2. 完成了什么

| Task | 屏 | 契约测试 | 实测状态 |
|---|---|---|---|
| 1 | 词卡 `WordCardScreen.kt` | `WordCardLayoutContractTest`（6） | ✅ 先红 6/6 → 绿；1837 tests |
| 2 | 词义详情 `CardDetailScreen.kt` | `CardDetailLayoutContractTest`（6） | ✅ 先红 6/6 → 绿；1843 tests |
| 3 | 重新学习 `VocabularyRelearnScreen.kt` | `RelearnLayoutContractTest`（6） | ✅ 先红 5/6 → 绿；1849 tests |
| 4 | 默写纸设置 `WorksheetSettingsScreen.kt` | `WorksheetLayoutContractTest` 设置段（7） | ⚠️ 同轮落地（偏差见 §5-2）→ 绿；1856 tests |
| 5 | 默写纸预览 `WorksheetPreviewScreen.kt` | `WorksheetLayoutContractTest` 预览段（6，新增） | ✅ 先红 5/13 → 绿；1862 tests |
| 6 | 学习设置 `AppScreen.kt` | `LearningSetupLayoutContractTest`（7 → 8） | ✅ 先红 7/7 → 绿；1869 tests |
| 7 | 全量门禁 + connected + 真机目视 + 报告 | 新增禁用态墨契约 2 | ✅ 1871 tests / 0 failures |

### 屏级变化摘要

- **词卡**：根 `Column` 只留背景与 `testTag`（不再整屏滚动）；Ready 态 = 顶栏（× 48dp 可点区 + 「第 x / y 张」+ 4dp 线性进度条）+ `weight(1f)` 滚动区（`SoftCard` 词面卡）+ 吸底 `WordCardFeedbackBar`；非 Ready 五态收进私有 `WordCardStateBody`。主词 `AppType.Display`，IPA/词性合成一列、喇叭到行尾；`OutlinedButton` 清零，形状统一 `AppShape.Button`。新增 `internal` 纯函数 `wordCardProgressFraction()`（total ≤ 0 / 越界 / 末张可逐例点名）。
- **词义详情**：词头行 = 词 + 72dp 缩略图（常量 `illustrationThumbnailSize`），`aspectRatio` 彻底删除；两条图片渲染路径各自的 tag 与「解码失败只留一行可读提示」保留；五枚词动作药片 → 私有 `WordActionPillGrid`（3+2，每枚 `weight(1f)`）；弹窗标题改「管理笔记」；常驻 live region 移到单词卡列末位；`DetailCard` 内部改 `SoftCard(contentPadding = 18.dp)`。
- **重新学习**：整屏重写（56 → 253 行）：共享页头 → `weight(1f)` 唯一滚动正文 → 仅 Ready 出现的吸底三档评分（不认识 `tint` / 模糊 `tint2` / 认识 `learn.base`，与词卡同口径）；次要动作降 `TextButton`，实底只留给三档评分。
- **默写纸设置**：整屏重写，顺序 = 页头 → 说明 → 计数卡 → 方向（`requiresDirection()` 门内）→ 范围（单选）→ 模板（单选）→ 纸张样式（分支外）→ 提示 → 吸底「预览默写纸」。私有件 9 个，裸 `Switch` 带 `contentDescription`。
- **默写纸预览**：重写（155 → 236 行）：页头 → `weight(1f)` 正文（`共 N 页`、错误走 `InlineNotice(Error)`、加载态 `CircularProgressIndicator(strongSurface)`）→ 私有 `PreviewActionBar` 吸底；页纸 `clip(AppShape.Card)` + `colors.surface`，`RoundedCornerShape` 与 `Color.White` 双双归零。
- **学习设置**：`DetailToggleRow` **整个撤掉自有卡壳**（卡里套卡比「角值不一致」更根本），改裸 `Row` + `hairlineTop(colors.outlineSoft)` + `heightIn(min = 56.dp)`；`DailyTargetCard` 改名 `DailyTargetSection` 并撤壳；四开关分两枚 `SoftCard`（`StrongCard` 0 枚）；「忘记了」→「不认识」；保存按钮撤渐变与透明底，改 `colors.strongSurface` + `AppShape.Button`。

## 3. 验证记录

### 3.1 全量门禁（Step 1）

```
& $g :app:assembleDebug :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin `
    verifyThirdPartyNotices verifyAcTraceabilityMatrix --offline --no-daemon --no-build-cache
```

结果：**BUILD SUCCESSFUL**

| 项 | 结果 |
|---|---|
| `:app:testDebugUnitTest` | **1871 tests / 0 failures** |
| `:app:assembleDebug` | 通过 |
| `:app:compileDebugAndroidTestKotlin` | 通过 |
| `verifyThirdPartyNotices` | 76 条（与批 1 一致，本批未引入新依赖） |
| `verifyAcTraceabilityMatrix` | 56 ids（与批 1 一致） |

条数轨迹（每步都是实跑数，不是推算）：

| 里程碑 | tests | 增量 |
|---|---|---|
| 批 1 收口 | 1831 | — |
| Task 1 后 | 1837 | +6 |
| Task 2 后 | 1843 | +6 |
| Task 3 后 | 1849 | +6 |
| Task 4 后 | 1856 | +7 |
| Task 5 后 | 1862 | +6 |
| Task 6 后 | 1869 | +7 |
| Task 7 后 | **1871** | +2 |

### 3.2 模拟器 connected（Step 2，`emulator-5574`，`am instrument`，未用 `connectedDebugAndroidTest`）

9 个既有类（词卡族 3 + 词义详情族 2 + 重新学习 + 默写纸设置 + 默写纸预览 + 词书选择器）。

- 首轮两红，均为**期望值过时**而非实现缺陷：
  1. `WordCardScreenTest.feedback_actions_remain_reachable_at_large_font_scale` —— 2.0 字号下三档反馈落在滚动区外，`performScrollTo()` 语义拒收；改 `assertIsDisplayed()`，判据本体「三档都可达」一字未动。
  2. `WordbookPickerHeaderTest` —— 旧屏自绘「返回上一层」已并入共享页头；断言改钉页头 `contentDescription = "返回"` + `assertHasClickAction`。
- 修后合并跑 → **`OK (68 tests)`**；补跑 `AppScreenTest` 两例 + `SetupScreenScreenshotTest` → **`OK (3 tests)`**。
- Task 4/5 追加一轮：`WorksheetPreviewScreenTest`（4 条，含用真实 `WorksheetPdfRenderer` 产出 PDF 后解锁导出）+ `WorksheetSettingsScreenTest`（3 条）→ **`OK (7 tests)`**。

### 3.3 真机目视（Step 3，`bf353dda`，只 `install -r` 不打）

截图落 `%TEMP%\opencode\ui\b2_*.png`（`b2_00` … `b2_18`）。

| # | 目视项 | 结论 |
|---|---|---|
| 1 | 词卡顶栏 × + 计数 + 进度条 | 通过 |
| 2 | 主词 44sp 与音标/发音同一视觉组 | 通过 |
| 3 | 三档反馈常驻底部不被裁 | 通过 |
| 4 | 词义详情首屏能看到释义与例句（配图已降缩略图） | **首版不通过 → 已修**：美/英发音药片与音标同行超宽压字，改 `FlowRow` 后通过 |
| 5 | 重新学习三档吸底 | 通过 |
| 6 | 默写纸设置方向在范围之上且是单选 | 通过；**同一屏发现禁用态主操作文字不可见 → 已修**（见 §3.4） |
| 7 | 预览页顶部有返回、导出在首屏 | 通过 |
| 8 | 2.0 字号下六屏均不裁切 | 通过（回看 `b2_0x`/`b2_1x` 对） |

### 3.4 真机现形的两处真实缺陷（均已修并复验）

**缺陷 1：词义详情词头行超宽压字**

- 症状：词头「音标 + 美/英发音药片」`Row` 在真机上超宽，把词条文字压成竖排。
- 修法：改 `FlowRow`（`spacedBy(8.dp)`、`itemVerticalAlignment = CenterVertically`），删多余 `Spacer(4.dp)`。
- 验证：`CardDetailScreenTest` + `CardDetailPronunciationControlsTest` → **`OK (30 tests)`**；实拍确认不再压字。

**缺陷 2：主 CTA 禁用态文案不可见（本轮新修，系统性坑）**

- 症状：`WorksheetSettingsScreen`「预览默写纸」与 `LearningSetupScreen`「保存并开始学习」在禁用态看上去是**空底**——近白字压浅档底（`tint` / `tint3`），对比度近 1:1。
- 根因：按钮标签自钉 `color = colors.onStrong`（近白）。该墨色只在 `strongSurface` 深实底上成立；禁用态走 `disabledContentColor` / `disabledContainerColor` 时，自钉值压过下发值。又因 `AppType` 六档 getter 全部自带墨色（`Label`/`Footnote` = `text2`），这不是单点手滑，而是「按钮里显式补墨」这个写法的系统性坑。
- 修法（两次）：第一次「删掉 `color` 让它继承」——**被真机复验否决**（`AppType.Label` 的 `text2` 深绿灰压在 `strongSurface` 深底上偏暗，启用态反而不够）。最终改 **`color = LocalContentColor.current`**：启用态取 `contentColor`（`onStrong`），禁用态取 `disabledContentColor`（`text3`），两态各自正确。两文件各加 `import androidx.compose.material3.LocalContentColor`。
- 配套判据（TDD）：`WorksheetLayoutContractTest.the preview action keeps its label readable when it is disabled`、`LearningSetupLayoutContractTest.the save action keeps its label readable when it is disabled`。三条断言：①窗口内 `disabledContentColor = colors.text3` 恰 1；②窗口内裸 `color = colors.onStrong` == 0（正则 `(?<![A-Za-z])color = colors\.onStrong`，避开 `disabledContentColor`）；③含 `LocalContentColor.current`。
- 红取证两轮：首轮 1/0 的红因是取样窗口错（见 §5-5），换取样器后红因正确（`expected: <0> but was: <1>`）；改完第 ③ 条又独立红一次（当时是「删掉 color」而非继承）。三次红各自指向一件具体的事，无空转。

## 4. 改期望值清单（旧值 → 新值 + 依据）——请审核席核对没有偷偷放宽

| # | 位置 | 旧值 | 新值 | 依据 | 是否放宽 |
|---|---|---|---|---|---|
| 1 | `AppScreenTest` 取消路径 | `onNodeWithContentDescription("返回上一层")` | `onNodeWithContentDescription("返回")` | Task 6 删掉屏内自绘返回按钮，返回位由共享页头承载（R-9.1 一处定义） | 否。后续断言（回「我的」、重算次数、不可取消）一句未删 |
| 2 | `AppScreenTest` 强制首设路径 | 断言旧文案按钮存在 | 断言页头返回位**不存在** | 同上；`onCancel == null` 时 `AppScreenHeader` 的 R-9.2 自动不渲染 | 否，语义等价且更强（不只「没有那枚按钮」，而是「整条返回位都没有」） |
| 3 | `WordbookPickerHeaderTest` | 旧屏「返回上一层」文本 | 页头 `contentDescription = "返回"` + `assertHasClickAction` | 词书选择器返回位并入共享页头 | 否，多了可点性断言 |
| 4 | `WordCardScreenTest` 大字号用例 | `performScrollTo()` 后断言可见 | `assertIsDisplayed()` | 新骨架三档反馈是吸底条、在滚动区之外，`performScrollTo` 语义不适用 | 否，判据本体「三档都可达」未变 |
| 5 | `AccessibilityStateSemanticsContractTest` ×4 | `fun DailyTargetCard(` | `fun DailyTargetSection(` | 撤壳后它已不是卡，名字随实现改（类注释规则 5、防线第 3 条、offender 报错文案、`:186` 的 `assertSite` 锚与「每日新词目标」文本判据全留） | 否，纯改名镜像 |
| 6 | `WorksheetSettingsScreenTest` 返回用例 | 断言「返回设置」/「返回学习工具」按钮存在 | 改 `backEntryIsTheSharedHeaderBackAffordance`：钉 `app_screen_header` + `contentDescription = "返回"` 可点且回调恰一次 + 旧文案必须为 0 | Task 4 页头归并 | 否，**加严** |
| 7 | `VocabularyRelearnScreenTest` 三用例 | 只断言存在 + 点一处文本 | 文本断言保留，新增 4 枚 tag 锚，三档评分逐档断言回调（`Unknown`/`Fuzzy`/`Known`），不可用页按 `relearn_retry` 点击，完成页加「评分条不存在」 | Task 3 整屏重写 | 否，**加严** |
| 8 | `LearningSetupLayoutContractTest` | 窗口内 `SoftCard(` == 2 | 窗口内 `SoftCard` == 2 | 容器全缺省参数、调用点写尾随 lambda 不带左括号，照原写法恒为 0 而空转绿（见 §5-1） | 否，`StrongCard` 不含该子串，「恰两枚软卡」这层未变 |
| 9 | `WorksheetLayoutContractTest` 页纸令牌 | `"appColors().surface" in source` | 正则 `\.surface\b` 存在 | 同义写法 `colors.surface` / `appColors().surface` 都该绿 | 否，同一条测试内 `Color.White` == 0 仍是硬判据 |
| 10 | `WordCardLayoutContractTest` 反馈按钮 | 全屏 `FeedbackButton(` == 3 | 全屏 == 吸底条内 + 1（`private fun FeedbackButton(` 定义） | 定义处也含该子串 | 否，相对量更严：条外多出任何一枚调用都会红 |
| 11 | `CardDetailLayoutContractTest` 药片 | 全屏 == 网格内 + 1 | 全屏 == 网格内 + 2，且例外必须就是 `testTag("card_detail_notes_manage")` | 「我的笔记」小节的「添加/管理」是区块入口，不是对词的五枚动作，合法留在网格外 | 否，例外被点名到唯一一处，更准也更严 |
| 12 | `ContainerContractTest.screens` | 2 屏 | 7 屏（批 2 五屏 + 今日计划 + 词库） | 计划改动清单第 106 行 | 否，判据不变（`StrongCard(` ≤ 1） |
| 13 | `SharedComponentUniquenessRatchetTest` 页头族 seed | 含 `WordCardScreen.kt` / `WorksheetSettingsScreen.kt` / `WorksheetPreviewScreen.kt` | 三者摘除（剩余 11 枚） | 三屏均已迁移到共享页头，seed 必须跟着摘，否则「已迁移」无证据 | 否，摘除行都写了理由；未迁移屏一枚未动 |
| 14 | 新增：禁用态墨契约 ×2 | —（原无） | 见 §3.4 | 真机缺陷 2 | 否，**净增判据** |

## 5. 未完成 / 已知偏差

1. **git 提交未执行**（Task 1–7 各一次，共 7 次）：本机无 `git`。本轮一切「已提交」字样均不成立，权威账 = 计划文档 + 本报告。
2. **Task 4 的 RED 未在旧实现上单独取证**（实现与契约同轮落地）。补偿证据：摘 seed 之前 `SharedComponentUniquenessRatchetTest` 确有一红（`each_family_offender_set_equals_its_seed_exactly`），说明页头族门在「实现已迁移、seed 未摘」状态下有牙；此后每条判据都带「取不到本体即红」的反空转断言。Task 1/2/3/5/6 均严格先红后绿。
3. **学习设置不进 `ContainerContractTest` 的屏级表**：它住在 `AppScreen.kt`（一个文件装十来个屏），按文件数强档卡会把别的屏算到它头上；容器口径改由 `LearningSetupLayoutContractTest` 在函数窗口内判。故实际是「七屏按文件 + 一屏按窗口」。
4. **词卡左右滑切换未做**：需要第二枚 `LaunchedEffect`，与 `WordCardScreenContractTest` 的「整屏恰一枚」锚冲突。登记为批 2 之外，需要先决定「放宽该锚」还是「换实现路径」。
5. **取样助手 `bodyOf()` 的适用边界**：只返回尾随 lambda 体、不含 `colors = …` 参数段。用它取按钮整段会把 `disabledContentColor` 一起切掉，判据空转（且空转方向恰好掩盖缺陷）。已补 `callSpan(source, "Button(")` / `buttonSpanBefore(setup, anchorAt)` 两个整段取样器，配对走字符串感知的 `balancedEnd` / `balancedBodyEnd`。
6. **C 类必修（五屏滚动）** 里阅读历史 / 备份导出 / 备份恢复 / 提醒四屏不属批 2，归批 3 / 批 5。
7. **大字号 connected 单例依赖系统设置**：`AppScreenOverlayContainerTest.hostedWordBookDeletePanelStaysFullyReadableAndTappableAtDoubleFontScale` 需先 `adb shell settings put system font_scale 2.0`、跑完恢复 1.0，未纳入常规门禁；本批以真机 2.0 字号目视（第 8 项）替代。
8. **真机数据红线**：`bf353dda` 的本地数据曾被 `connectedDebugAndroidTest` 清空（不可恢复）。此后真机只 `install -r` + 手工目视，instrument 一律在 `emulator-5574`。

## 6. 下一批须知（批 3 前置事实）

- **按钮内不要自钉墨色**：改用 `color = LocalContentColor.current`；`AppType` 六档 getter 自带墨色是这条规则的根因。
- **取样窗口化**：`AppScreen.kt` 装十来屏，任何 `Mint*` / `MaterialTheme.typography` 清零判据必须落在函数窗口内，否则同文件其它屏会一起判红。
- **取「整段」而不是「lambda 体」**：`bodyOf()` 不含参数段；要覆盖 `colors=` / `disabledContainerColor=` 就用 `callSpan` / `buttonSpanBefore` 一类整段取样器，并带「取不到即 fail」的反空转断言。
- **页头族 seed 剩 11 枚**：批 3 每迁一屏，同批摘一枚，摘除行写理由。
- **主操作口径**：吸底 + 单一实底 + 中性档（`strongSurface`/`onStrong`）；一屏至多一枚 `StrongCard`。
- **门禁命令**：`$g :app:assembleDebug :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin verifyThirdPartyNotices verifyAcTraceabilityMatrix --offline --no-daemon --no-build-cache`；禁 `./gradlew`、禁 Gradle 9.x。

## 7. 待用户确认

1. 是否进入**批 3（阅读链路 5 屏）**？若是，建议先补 `docs/superpowers/plans/2026-XX-layout-redesign-batch-3-*.md`（含 §5 逐屏表落点 + TDD 步骤 + 既有锚影响面），再动代码。
2. 词卡左右滑切换（§5-4 的锚冲突）是「放宽 `LaunchedEffect` 恰一枚」还是「长期不做」——需要拍板。
3. git 提交欠账 7 次：是否需要在本机装 `git` 后一次性补提交（含批 1 的欠账）。
