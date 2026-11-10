# 决策：词卡吸底槽改工具条、详情页「下一个单词」退役、补两套动效（m01676 三条）

日期：2026-11-09　范围：`ui/WordCardScreen.kt`、`ui/AppScreen.kt`、`ui/theme/AppTransition.kt`、`ui/WordCardViewModel.kt`、
`test/…/ui/StickyFooterHairlineGuardTest.kt`（+ 新增 `test/…/ui/DetailOverlayAndFooterSwapContractTest.kt`）

## 背景（用户原始三条，m01676）

1. 「评分条位置太低」——三档评分裸压在 `colors.background` 上，读起来像屏体最后一段内容，
   而不是一个一直在那儿的操作区。
2. 「点模糊后详情页不许再点『下一个单词』」——顶档/揭晓路径会打开词义详情，详情页底部的
   `card_detail_next`（「下一个单词」）让用户**跳过评分**直接翻下一张释义，绕开学习事件。
3. 「补动效 / 治排版太原始」——槽内换内容没有过渡，浮层是硬切。

用户在 m01880 明确「可以全部都做」，故三条一次落地，不分批。

## 决定

### 1. 吸底槽 = 工具条，只换槽里的内容

- `WordCardFeedbackBar` / `WordCardReceiptBar` 自铺 `colors.surface` + 槽顶 `hairlineTop(colors.outline)` +
  `navigationBarsPadding()`：底色铺到系统导航条后面，按钮不再被它压住（口径同 `BackupExportScreen`）。
  回执条同时补了一枚新锚点 `word_card_receipt_bar`（原先它没有自己的 testTag）。
- 两支按 `receipt` 双向互斥淡换：`AppTransition.FooterSwapEnter/Exit`（只淡、无位移）。
  **不给位移**是刻意的：槽位没变、变的只是槽里那排按钮，位移会被读成「换了一页」——
  水平 ¼ 那套是 `PageForward/Backward` 的语义，不许借。
- 槽体抽成顶层 `private fun WordCardFooterSlot(…)`。这是**编译硬约束**，不是排版偏好：
  `AnimatedVisibility` 有一枚 `ColumnScope` 扩展重载，写在 `Column` 的内容 lambda 里时 Kotlin 会解析到它，
  报「cannot be called in this context with an implicit receiver」——本轮第一次编译就是这样红的。
  抽出去后落到顶层版本，`Box` 负责让两支共用一个槽位。
- 淡出窗口里旧的一支没有可点节点（`receipt?.let` / `if (receipt == null)` 双向收口），
  「同一条回执上写第二个学习事件」的门仍然是关的。

### 2. 详情页不再有「下一个单词」

- 学习流宿主的 `CardDetailScreen(...)` 传 `onNext = null`；
  `WordCardViewModel.hasNextDetail()` / `nextDetail()` 一并退役（留方法就是给这条后门留接口，
  且它们除这一处外没有任何调用点）。学一张卡重新只有一条链：评分 → 回执 → 吸底主按钮（`advance`）。
- 五宿主共用的 `CardDetailScreen` 本体**未改**：其它四处的 `onNext` 语义与这里不同，收窄只落在学习流这一处。

### 3. 两套新动效，只长在 token 层

- `AppTransition.FooterSwapEnter/Exit`：`fadeIn/fadeOut(tween(AppMotion.Normal, EasingOut/EasingIn))`。
- `AppTransition.OverlayRiseEnter/Exit`：`fadeIn/fadeOut(tween(AppMotion.Slow, …)) + slideIn/OutVertically(tween(AppMotion.Slow, …)) { it / OverlayOffsetDenominator }`，
  新增 `private const val OverlayOffsetDenominator = 8`（浮层竖直 ⅛；页面那套水平 ¼ 是 `PageOffsetDenominator`）。
- 详情宿主改为 `AnimatedVisibility(visible = detailCard != null, enter = OverlayRiseEnter, exit = OverlayRiseExit)`，
  并用 `overlayCard`（`LaunchedEffect(detailCard)` 记最后一次非空值）在退场那 300ms 里继续画**上一张卡**：
  直接读 `detailCard` 的话它已经归 null，画面会先塌空再消失。两处发音代次闸同步改读 `overlayCard`。
- 动效规格一律写在 `ui/theme/AppTransition.kt`：`MotionSpecContractTest` 要求每枚 `tween(` 显式带 `AppMotion.Easing…`、
`fadeIn/scaleIn` 用 `EasingOut`、`fadeOut/scaleOut` 用 `EasingIn`，屏侧裸写会被判红。

### 4. 台账（`StickyFooterHairlineGuardTest`）

`WordCardScreen.kt` 从「1 处、2 枚认领（都不铺底）」拆成 **3 处**：`word_card_progress_bar`（不铺底）、
`word_card_feedback_bar` / `word_card_receipt_bar`（自铺底色，各 1 枚认领 + 发丝线）。台账总数 15 处 / 13 处自铺 / 2 处豁免 / 17 枚认领。

## 被否决的方案

- **把评分条整体上移（非吸底）**：会在 1.0 档把内容区压矮一截，且顶档（>2.0）的「显示释义」常驻逻辑
  （`wordCardPinsRevealAction`）本来就是吸底语义，移走要重排两处。
- **详情页保留「下一个单词」但置灰**：仍然是同一条出口，只是把「跳过评分」变成两步；退役才是根治。
- **给吸底槽加滑动位移**：见上，位移会被读成换页；页面转场的 ¼ 位移语义也不许复用。

## 影响

- 反空转：新增 `DetailOverlayAndFooterSwapContractTest`（5 条）逐条钉住上面四件事，并各配合成违例样本，
  形状改名 / 判据写反时数出 0。
- 设备侧既有用例不破：`WordCardScreenTest` 用 `word_card_feedback_bar` 的 `getUnclippedBoundsInRoot().top`
  当动态视口下沿（tag 仍在链尾）；`AppScreenTest` 里没有用例点学习流详情里的 `card_detail_next`。

## 验证

- `:app:compileDebugKotlin --offline` → BUILD SUCCESSFUL。
- `:app:testDebugUnitTest --offline` → BUILD SUCCESSFUL，**350 个测试类 / 2611 用例，0 失败 0 跳过**
  （上一轮基线 349 / 2606；本轮新增 1 类 5 例）。
- 设备（Xiaomi M2102J2SC bf353dda，`assembleDebug` + `install -r` 保留数据）实测：
  - 吸底槽自铺底色可量：左边缘竖切 x=20px，回执条 `y2095` 一处 `#DDE5DD` 发丝线 → `y2098` 起纯白 `surface` 一直铺到 `y2295`（导航条起点 2296），
    反馈条同形（`y2078` 发丝线 → `y2081` 白）。
  - 「模糊」→ 打开「词义详情」：UI 树里 `下一个单词` / `card_detail_next` 命中 **0** 次，
    底部动作只剩「重新学习」与「用 AI 追问这个词」。
  - 回执态：`word_card_feedback_bar` 的三档按钮整体消失，只剩底部「下一个词」（互斥生效）。
  - 截图：`.workbuddy/m01676_card.png`、`.workbuddy/m01676_detail.png`、`.workbuddy/m01676_receipt.png`、
    `.workbuddy/m01676_mid_swap.png`（淡换中途一帧）。

## 已知局限（如实记）

- 淡换只有 200ms 级，截图很难稳定抓到中间帧；互斥与规格由源码契约钉，观感靠人眼。
- `Box` 高度取两支的较大者：回执条比反馈条矮，淡换结束后槽高会瞬时收缩一次（幅度 = 两支差），
  本轮不为此加 `animateContentSize`——那会给「槽位没变」再引入一条竖向位移。
