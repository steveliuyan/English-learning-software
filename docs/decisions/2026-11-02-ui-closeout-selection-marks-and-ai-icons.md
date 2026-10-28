# 决策记录 · 2026-11-02 UI 收尾轮（AI 入口图标去重 / 深套选中标记 / 吸底槽发丝线 / 底导定名）

## 背景

批 5（`docs/handoff/2026-11-02-layout-redesign-batch-5-report.md`）收口后，用户在真机
`bf353dda`（M2102J2SC）上看 AI 学页与设置页，提出一条观感意见，另有 §8 两条同因遗留待收口：

1. **AI 学页可点行「图标全一样」**：用户原话「怎么感觉全都是统一的 AI 学那个图标」。
2. **深套开关/滑杆轨道**：批 5 只修了语音合成页两处，`DetailToggleRow`、`OptionSwitchRow` 同因未修。
3. **吸底槽与软卡同色、槽顶无边界线**：批 5 只给导出屏补了发丝线，其余屏未扫。

本文钉的是「这三条怎么改、改到哪儿为止、判据怎么钉」。**没有改任何一个 token 取值**，
不动品牌渐变与 28 枚插画，不动数据层/FSRS/备份格式/AI 请求策略。

## 议题一 · AI 学页七枚入口的图标去重

- A. 全换成同一族线框图标 —— 否。从「七枚同一枚」退化成「七枚同一族」，扫列表仍只看到一种形状；
  且矢量线框与 28 枚位图插画不是同一笔形（2026-10-31 裁决 1 的教训：同一屏两种笔形＝「两个 App」）。
- B. **七枚功能各取一枚专属插画，列表行与功能页页头统一读 `feature.icon`** —— 采纳。
  取值：`WORD_PASSAGE`→`Newspaper` / `CLOZE`→`List` / `LISTENING`→`Speaker` / `COACH`→`Mic` /
  `SENTENCE_ANALYSIS`→`Document` / `PHOTO_TRANSLATION`→`Camera` / `IMAGE_STUDIO`→`Image`。
  理由：每枚对应该功能自身的形态（一篇题刊 / 一张清单 / 喇叭 / 麦克风 / 文档 / 相机 / 画报），
  用户扫列表不必读标题即可区分；且这七枚都在 28 枚位图母版里，不必新画几何。
- C. 连两处折叠/说明行（未上线功能、AI 学能做什么）也换掉 `AppIcon.Spark` —— 否。这两行不是功能入口，
  是本页的信息层；`AppIcon.Spark` 在本页是唯一的 AI 品牌锚点，与底导 AI tab 同形呼应。
  全换掉会把「这是 AI 页」的标识一起抹掉。
- 判据：`AiFeatureIconContractTest`（4 用例）双向钉住 —— 目录侧一枚功能一枚图标且不许重复、
  七枚逐条钉值；站点侧 `AiFeatureRow` 与 `AiFeatureBadge` 里 `AppIcon.Spark` 计数必须为 0。

## 议题二 · 深套开关/滑杆/复选/单选的「选中态消失」

起因是一次真实的控件消失：深套 `strongSurface = #22332C` 压在深套 `surface = #17201C` 上，
两者只差一层几乎看不见的亮度，「开」态轨道、滑杆拇指、复选勾、单选点等于没画。

- A. 改 `strongSurface` 的深套取值 —— 否。`strongSurface` 还有**非选中标记**消费点
  （`ArticleReadingScreen` / `ArticleImportScreen` / `GlobalVocabularySearchScreen` /
  `WorksheetPreviewScreen` / `ImageStudioScreen` 的主 CTA 实底，配 `onStrong` 墨；
  `WorksheetPreviewScreen` 的进度环），改 token 会连带动 CTA 外观。
- B. **屏内三元式：`if (LocalIsDark.current) <域色>.base else <现值>`** —— 采纳。
  浅套逐值不变（签署过的浅套版式不动），深套换域 `base` 提亮，在 `surface` 上重新可见。
- 站点（台账现值 12 枚）：`SpeechSettingsScreen` 3（批 5 已修的模板）、`AppScreen` 3
  （`DetailToggleRow` 开关 + 每日新增滑杆拇指/活动轨）、`WorksheetSettingsScreen` 4、
  `BackupExportScreen` 1 与 `ReminderSettingsScreen` 1（本就取域色，合法形状 2）。
- **本轮补入复选/单选 3 枚**（`DirectionRow` 的 `CheckboxDefaults.checkedColor`、
  `RangeSection` 与 `TemplateSection` 的 `RadioButtonDefaults.selectedColor`）：它们与开关轨道是
  同一条缺陷，控件形状不同不构成放行理由；原先被本闸排除在视野外、登记为待裁决项。
- 判据：`SwitchTrackColorGuardTest`（3 用例）—— 无条件取 `strongSurface` 的站点必须为 0；
  每个站点只许两种形状（深套三元式 / 始终域色）；台账（文件 → 枚数）与实际两向对齐。
- 明确不做：上列主 CTA 的 `containerColor = colors.strongSurface` 是实底按钮，不是选中标记，
  不在本闸视野，也不改。

## 议题三 · 吸底槽压在软卡上无边界线

- A. 给吸底槽加投影 —— 否。与「容器不描边、靠底色分层」的口径冲突（`SoftCard` 不描边），
  投影在深套上还会糊出一圈灰。
- B. **自铺底色的吸底槽一律补 1dp `hairlineTop(colors.outline)`；透明槽登记豁免并写明理由** —— 采纳。
- 取 `colors.outline` 而非 `outlineSoft`：吸底槽要的是「硬分层」，`outlineSoft` 是列表行内分隔线档
  （`BareRow`），两档不能混用，否则吸底槽的分层弱到看不见。
- 判据：`StickyFooterHairlineGuardTest`（3 用例）—— 扫描站点（`align(Alignment.BottomCenter)` 与
  `testTag("*bar*")`）与台账双向对齐（8 处 / 11 枚认领符号）；自铺底色的必须画线；
  `paintsOwnFill = false` 的站点（词卡评分条、进度条）登记理由，并断言其源码里 `colors.surface`
  一次都不出现（不铺底就没有缝，也不需要线）。

## 议题四 · 本轮明确不做（功能线，交外部实现）

1. **aora-bot 吉祥物替换 + 「AI 状态 → emotionId」绑定**：现状 `ui/mascot/AiMascot*.kt` 已可用；
   aora-bot（`https://github.com/sam70361/aora-bot`）是纯 SVG + JS 的 32 表情 Emotion Ball，
   Compose 侧需 Canvas 重绘或 WebView 桥接，LICENSE 未确认，接入前必须先登记
   `docs/third-party-notices.md`。
2. **拍照翻译「原位覆盖译文」**：需 OCR 词框坐标 + 版式重排，当前 `PhotoTranslationScreen.kt`
   是分块展示结构；属功能改造，不是版式改。
3. **提醒权限注入夹具**（批 5 §8 第 6 条）：属测试基建，不是 UI。

记录原因：用户明确叫停功能类改动，功能实现换另一条工作线；本轮只保证 UI 侧不留半成品，
交接清单见批 5 报告 §11。

## 议题五 · 底导第三项定名「AI 助学」（批 5 §8 第 8 条）

起因：批 5 记录底导第三格文案为 `AI("AI 学")`，2× 字号实测不是截断，但「AI 学」读起来像被截断的
「AI 学习」，当时挂账「改『AI 学习』还是『AI』，需用户拍板」。

- 用户 2026-11-02 直接给出定名：**「AI 助学」**。
- 落地范围（一句定名，四处跟到，不做一半）：
  1. `AppTab.AI("AI 助学", "AI 助学")` —— 标签与无障碍播报；
  2. `AiLearningScreen` hero 标题（本屏唯一一枚 `"AI 助学"` 文本，仍不与页头标题撞名）；
  3. 说明折叠行的可见标题「AI 助学如何起作用？」与其可及名「AI 助学如何起作用说明」；
  4. `AiProfileSettingsScreen` 空态文案「添加一套之后，『AI 助学』里的功能才有可以调用的服务」。
- 不做：不改 AI 功能页自身的页名（拍照翻译 / 长难句分析 / AI 生图 / AI 问词仍各自独立命名），
  不改品牌渐变、图标与任何 token，不动功能实现。
- 判据：`AppTabTest` 两断言钉定名；新增
  `AiLearningLayoutContractTest.the hero title speaks the destination name the user settled on`：
  源侧 `"AI 助学"` 恰 1 处、独立成串的 `"AI 学"` 必须为 0；设备侧
  `AppBottomBarTest` / `AppScreenTest#readyPlanShowsTheFourSlotBottomNavigation` / `AiLearningScreenTest`
  按新文案点（真机 `bf353dda` 实跑 9 / 1 / 18 全绿）。
- 旧名只留在历史叙述里（退役药片「← 返回 AI 学」等述旧行文），断言侧对两代文案一律钉成不存在。
- 真机取证（`bf353dda`，1× 字号）：`docs/design/app-tab-label-ai-zhuxue-light-20261102.png`（浅套底导）、
  `app-tab-label-ai-zhuxue-dark-20261102.png`（深套底导）、`app-ai-home-hero-zhuxue-light-20261102.png`
  （AI 助学主页 hero 标题 + 选中态药片）。

## 验证

- 全量 JVM 门禁 **278 XML / 2043 tests / 0 failures / 0 errors / 0 skipped**
  （批 5 基线 275 / 2032：+3 XML、+11 tests = `SwitchTrackColorGuardTest` 3 +
  `StickyFooterHairlineGuardTest` 3 + `AiFeatureIconContractTest` 4 + 底导定名 1）。
- 本闸新增判据在修前实跑见红：`SwitchTrackColorGuardTest` 的两条断言点名 3 个违规站点
  （默写纸设置页的复选与两枚单选），修后转绿；台账一侧修前即对齐（源码 4 枚 vs 台账 4 枚）。
- **未做真机目视取证**：深套复选/单选只到源码闸 + JVM 门禁。取值与同屏 `OptionSwitchRow`
  及已取证的 `SpeechSettingsScreen` 同源，闸负责钉住取值形状；实物复看留待下一轮打通
  「我的 → 设置 → 导出默写纸」导航后补（详见批 5 报告 §11.4）。
