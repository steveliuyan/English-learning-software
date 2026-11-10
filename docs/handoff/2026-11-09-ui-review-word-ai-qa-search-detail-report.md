# 交接：UI 审查 + 查词/词义详情页接上 AI 问词（N-2 收口的下一步）

日期：2026-11-09

## 一、UI 审查结论（ui/ 全量 127 个 .kt）

- 死代码 / TODO / FIXME / 未实现标注扫描：全 ui 仅命中一处 `AiProfileSettingsScreen.kt:662 onClick = {}`，
  经查是 `UnsupportedOption` 禁用行（`enabled=false`，`EntryRow` 必填形参走空体），**非缺陷**。
- 结论：ui 包成熟度高、文档完备，泛化审查收益低；有价值的缺口集中在「查词/词义详情」这条主线。

## 二、修复的功能缺口（本轮交付）

### 背景（N-2 的回声）
上一批把 `CardDetailScreen` 页末的「长难句分析、AI 问词……」提示与 `AskAiButton` 收进

```
onAskAi?.let { ... }
```

守卫（诚实化：入口与提示同生同灭）。而搜索/查词详情宿主 `AppScreen.kt` 的
`AppOverlayRoute.VocabularyDetail` 分支此前传 `onAskAi = null`——诚实，但整页没有 AI 问词入口，
与「学习流」「文章页」两个宿主不一致。本轮把这条主线补上。

### 改动
`app/src/main/java/com/example/englishlearning/ui/AppScreen.kt`（VocabularyDetail 分支）
1. `onAskAi = null,` → 真 lambda（同学习流/文章页模式）：
   `{ showWordQaCard = card; wordQaViewModel?.reset(); wordQaViewModel?.loadNotes(state.profile.id, card.lemma, card.wordBookId, card.cardId) }`
2. 在 `CardDetailScreen(...)` 调用之后新增 `showWordQaCard?.let { qaCard -> WordAiQaScreen(...) }`，
   复用 `wordQaUiState` / `wordQaViewModel`，接 `onAsk/onCancelAsk/onConfirmOutbound/onSaveNote/
   onSavePersonalNote/savedNotes/onBack/onOpenAiSettings`。
   `onOpenAiSettings` 关闭问词层后 `openAiProfilesOrSettings()`（未配置时进设置，契约自动平衡）。

### 测试（TDD）
- 新增 `app/src/test/.../ui/AppScreenWordQaWiringTest.kt`：断言
  `WordAiQaScreen(` 计数 == `onAskAi = {` 计数且 ≥ 3，且 AppScreen 里 `onAskAi = null` 为 0。
- 更新 `ui/AiWaitInProgressContractTest.kt`：`wordQaViewModel?.cancelAsk()` 期望值 2 → 3
  （问词宿主持有取消入口的计数随「第三处宿主」同步；注释同步为「学习流、文章页、查词/词义详情」）。

### 验证
- `:app:testDebugUnitTest --offline --no-daemon` ✅（2569 例）
- `:app:assembleDebug --offline --no-daemon` ✅（app-debug.apk 20:04 重新打包）

## 三、备注
- 本仓无 git 可执行文件，未提交。
- 编辑 AppScreen.kt / 测试文件均保留 CRLF 行尾。
- 后续可选项：若希望「查词详情」的问答层支持在该分支内继续翻词（onNext），可把 QA 层的 onBack 与
  `closeSearchDetail`/翻词联动进一步打磨；当前与学习流/文章页行为一致，先对齐。
