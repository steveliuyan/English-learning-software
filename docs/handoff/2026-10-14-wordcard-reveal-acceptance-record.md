# 词卡回忆-揭晓-评分流程验收记录（2026-10-14）

## 1. 背景与范围变更先行

用户在真机使用中发现词卡一进卡片即显示中文释义，评分实际测的是"辨认"而非"回忆"，会系统性高估 FSRS 评分。用户拍板按「回忆 → 揭晓 → 评分」方案推进，并要求先改 Spec 与决策文档再实施（AGENTS.md 范围变化规则）。

- Spec 修订：`docs/specs/01-vocabulary-learning-and-review.md` F1-03 重写为回忆-揭晓-评分流程，新增 AC1-20～AC1-22（提交 `4aaa7e5`，已推送）。
- 决策文档：`docs/decisions/2026-10-14-wordcard-recall-reveal-flow.md`（方案 A 批准；B 评分后才显示、C 设置开关均否决并记录理由）。

## 2. 落地内容（提交 `e9d6726` + `585a0b8`，FF 合入 main）

- `WordCardUiState.Ready` 新增 `meaningRevealed`；`WordCardViewModel` 新增 `revealMeaning()`，以 `revealedCardId` 为权威：`load` 清空、`emit` 只对匹配卡回显（换卡自动回到隐藏态；保存失败重绘保持已揭晓；submitting/非 Ready 时忽略揭晓）。
- `WordCardScreen`：卡面只保留单词/IPA/词性/发音/英文例句（回忆语境线索）；中文释义与词形变化藏入「显示释义」（`word_card_reveal`）之后；三档评分按钮揭晓前后均可见可提交；揭晓不计完成度、不写学习事件。
- `AppScreen` 接线 `onReveal = wordCardViewModel::revealMeaning`。
- F1-06 详情页触发规则、FSRS 映射、事件幂等、完成与解锁判定零改动。

## 3. 验证证据

- TDD：`WordCardViewModelTest` 先加 4 条红测（揭晓不计事件/换卡复位/重进复位/非 Ready 忽略），实现后转绿。
- JVM 门控：`assembleDebug + testDebugUnitTest + compileDebugAndroidTestKotlin` 全绿，**1302 单测 0 失败**（基线 1298 + 4）。
- 真机 connected（bf353dda / API 33）：`WordCardScreenTest + CardDetailScreenTest` **38/38 通过**，覆盖 AC1-20（揭晓前隐藏/揭晓后展示）、AC1-22（评分按钮前后均可用）及详情页无回归。
  - 过程中修复一处测试自身缺陷：同一测试内两次 `setContent` 在 Compose 测试规则下非法，拆分为两个测试后全绿（`585a0b8`）。
- 推送核验：远程 `refs/heads/main = 585a0b8`（ls-remote）；`stage-1-wordcard-reveal` 已验证祖先并本地+远端删除。

## 4. 未验证 / 遗留（不得标已验证）

- TalkBack 下「显示释义」按钮的朗读顺序与焦点表现——归入攒批设备 QA（与 F4-04 清单、F3-03 补做项同轮）。
- 学习流程端到端真人走查（用户日常使用即是最有效验收；如反馈揭晓步骤多余或希望评分前强制先核对，回到决策文档重开）。
