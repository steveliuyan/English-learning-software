# UI 重设计轮 · 设计规格（2026-10-17）

**状态**：用户 2026-10-17 已审阅（「整体没问题」），唯一悬留的图标呈现选型已于 2026-10-18 定为 B 案并落进 §4。
本规格即为批 1 开工依据，由 `docs/superpowers/plans/2026-10-17-ui-redesign.md` 拆成代码级实现计划。
**上游依据**：
[`docs/design/2026-10-17-ui-redesign-mockups.html`](../design/2026-10-17-ui-redesign-mockups.html)（已定稿的视觉基线，token/组件/六屏真比例 + 深浅与三档字号对照）、
[`docs/design/2026-10-17-content-placement-audit.md`](../design/2026-10-17-content-placement-audit.md)（33 屏逐屏审计）、
[`docs/decisions/2026-10-17-ui-redesign-information-architecture.md`](../decisions/2026-10-17-ui-redesign-information-architecture.md)（六条信息架构裁决）。
**规范先行**：`docs/design/visual-style-guide.md` §11 要求「先改文档再进代码」，本文即该轮的文档落点；§9 要求视觉一致性结论必须有真机证据，故本文所有验收标准都区分「可自动验证」与「必须真机看」。

---

## 1. 目标、范围与不做的事

**目标**：把「所有页面不统一、太乱、有些页面不该出现这些东西」这一体验问题，一次解决到可发布水位——
统一的设计系统 + 统一的共享组件 + 每屏只承担自己的职责。

**范围**：仅 UI 层（Compose 可组合函数、token、组件、内容与入口的归属）。
共 3 批：底座 → 核心学习路径五屏 → 其余屏与入口分组重排。

**明确不做**（任何一条越界即视为范围变化，先改本规格再实施）：

1. 不新增任何业务功能、不动任何业务规则：FSRS 调度、每日计划不可变快照、严格/宽松解锁、
   备份认证与配对、删除级联确认、词书各自独立进度，一律保持现状。
2. 不新增依赖（`app/build.gradle.kts:163-165` `dependencyLocking { lockAllConfigurations() }`，
   两份既有计划亦写明不新增依赖）。
3. 不新增账号、云同步、订阅、广告、社交、远程代码执行或任意 AI 请求覆盖能力（AGENTS.md）。
4. 不改底部四栏骨架与 `AppTabTest` 钉住的顺序。
5. 打卡页动效**本轮不做**（用户裁决：先定 UI，动画另批；届时须单独处理 reduced-motion 与 TalkBack 朗读）。
6. 词卡的「内容深度」不排期：只用现有词书自带字段，不接柯林斯授权、词根词源库、真题例句库。
7. 外刊「AI 联网检索汇总」不做；改为 UI 轮之后独立小批做**扩白名单源**（见 §8）。

---

## 2. 设计系统 token

### 2.1 颜色（浅 / 深两套，落为 `AppPalette` 常量 + `MaterialTheme` 装配点）

**与既有令牌的关系（须核对，别当已就位）**：`ui/theme/AppPalette.kt` 已存在，全站 154 处引用
`AppPalette.*` / `DomainColors.*`（实测计数），且 `DomainColors` 七域取值与本文 §2.1 域色表**逐值一致**
（F5-03 轮建立）。本轮要动的是三件事：① 中性色取值按视觉稿校准（现状 `Background #F5F5F7` 是 iOS 灰，
本文定为浅薄荷 `#F1FBF5`；`TextSecondary #6E6E73` → `#4E756A`；`Separator #E5E5EA` → `#E4EFE9`；
`GlassFill #CCFFFFFF` → `#EFFFFFFF`）；② 补齐缺失 token（`tint/tint-2/tint-3/outline/on-brand/error-fill`）；
③ 给全部取值加深色一套，并建立消费它们的 `MaterialTheme` 装配点。改值属视觉校准，
不动任何语义（`OnMintPrimary #174C44`、`ErrorText #B30000` 两个已钉死的值保持原样）。

浅色（`--bg #F1FBF5` 系）：`bg #F1FBF5`、`surface #FFFFFF`、`surface-glass #EFFFFFFF`、`tint #DDF7E8`、
`tint-2 #DCEAE1`、`tint-3 #CDEDD9`、`outline #ADE7D2`、`outline-soft #E4EFE9`、
`text-1 #1C1C1E`、`text-2 #4E756A`、`text-3 #7A9088`、`on-mint #174C44`、`on-brand #04241C`、
`error #B30000`、`error-fill #FF3B30`、`success #1B5E20`。

品牌渐变保持现状不动：`mint-start #A8F3C8` → `mint-mid #5DDFB4` → `mint-end #35B9B5`，`mint-light #D9FFF0`。

深色：`bg #0E1512`、`surface #17201C`、`surface-glass #E617201C`、`tint #1C2C26`、`tint-2 #22322B`、`tint-3 #27392F`、
`outline #2E4238`、`outline-soft #233029`、`text-1 #E9F2EC`、`text-2 #9FB3AA`、`text-3 #7F938A`、
`on-mint #DFF6EC`、`error #FF8177`、`error-fill #FF6B61`、`success #6BD98A`。

功能域强调色（`base` = 图形/渐变起点，`deep` = 填充与文字）：

| 域 | 浅 deep / base | 深（deep = base） |
|---|---|---|
| AI | `#188F76` / `#2EC99C` | `#35E0AC` |
| 学习 | `#075EB4` / `#0A84FF` | `#5AC0FF` |
| 复习 | `#AB5700` / `#FF9F0A` | `#FFB340` |
| 词库 | `#8E34B8` / `#BF5AF2` | `#DA8CFF` |
| 阅读 | `#C81E46` / `#FF375F` | `#FF7A93` |
| 危险 | `#B30000` / `#FF3B30` | `#FF6B61` |
| 设置 | `#48484A` / `#8E8E93` | `#B0B0B5` |

**硬规则（F5-03 移交，不得回潮）**：浅薄荷底与品牌渐变上**禁止白字**；浅薄荷底前景取 `on-mint #174C44`，
**品牌渐变上的文字统一取 `on-brand #04241C`**（`on-mint` 压渐变末端 `#35B9B5` 实测仅 4.07:1，
原「≈4.6:1」口径只适用于浅薄荷底）。`text-3` 不作任何正文或标签前景（实测 3.00–3.40，需要第三级弱化用
`text-2` + 降字号）；`text-2` 只许压 `surface`/`bg`/`tint`，不得压 `tint-2`/`tint-3`。
三条裁决与图标两层豁免表见
[`docs/decisions/2026-10-18-ui-redesign-contrast-and-icon-layer-rulings.md`](../../decisions/2026-10-18-ui-redesign-contrast-and-icon-layer-rulings.md)。
深色下域色与 `base` 合一，避免暗底深字；危险色全站只用一套（`error` 文 / `error-fill` 填充），
禁止再出现裸 `Color(0xFF…)` 现场调色（现存残留：`TodayPlanScreen.kt:160-161` 的 `#68BDA6`/`#C7A47D`、`:247` 的 `#795B36`、
`AppScreen.kt:2223`/`:2336`）。

**域色容器上的字形对比（本轮新增，与图标选型无关的公共约束）**：图标容器**不得把浅色模式的 `base` 直接当白字形底**。
按 WCAG 相对亮度实测，白描边在 `base` 上为 AI `#2EC99C` **2.11:1**、复习 `#FF9F0A` **2.06:1**，
低于图形非文本对比 3:1 的下限（薄荷与橙恰是最亮的两域）；改用 `deep` 实底则为 4.02–9.12:1，全部合格。
因此若采用渐变容器，渐变亮端取 **`deep` 混 35% `base`**（不是 `base` 本体），七域白字形实测 **3.17:1–6.26:1**，最差为 AI。
深色模式容器使用同一组域色（`base = deep`）配 `#06231A` 深色字形，实测 **5.96:1–9.82:1**，最差为危险 `#FF6B61`。
这些数字由 `docs/design/2026-10-17-ui-redesign-mockups.html` §6.2 的同一套令牌算出，验收口径见 §9 AC-R-03。

### 2.2 排版：六阶语义尺度 + M3 角色钉值

新代码只写语义名（`AppType.Display/Headline/Title/Body/Footnote/Label`），M3 角色名留给旧调用点自然淘汰。

| 语义档 | 钉值 | 对应 M3 角色 | 变化 |
|---|---|---|---|
| Display | 34sp Bold，ls 0.4 | `displaySmall` | 36→34 |
| Headline | 24sp Bold | `headlineSmall` | 同值 |
| Title | 17sp SemiBold | `titleMedium` | 16→17 |
| Body | 15sp Regular | `bodyMedium` | 14→15 |
| Footnote | 13sp | `bodySmall` | 12→13 |
| Label | 12sp Medium，ls 0.3 | `labelMedium` | 同值 |

现状没有 `MaterialTheme(...)` 装配点，206 处 `MaterialTheme.typography.*` 直接吃 M3 默认值；
深色模式同样没有落点。批 1 的第一件事就是建立装配点，把这 206 处调用纳入统一尺度。

### 2.3 栅格

圆角两档 + 控件档：`hero 24dp` / `card 18dp` / `btn 14dp` / `icon 12dp` / `pill 999dp`。
间距 8 档：`4 / 8 / 12 / 14 / 16 / 18 / 20 / 24`；页边距 20dp、段间距 24dp、卡内间距 14dp。
触控下限 **48dp**（`heightIn(min = 48.dp)` 或 `defaultMinSize`，禁止靠 padding 凑）。
禁止 `Arrangement.spacedBy` 造成的幽灵间距（不可见但占位的分隔）——分隔一律用真实可见的 `outline-soft` 线或明确间距。

---

## 3. 共享组件（把「实测有 N 种写法」收成一个）

每个组件的验收口径：全站同一组件、同一语义、深浅两套均有对应 token、`fontScale` 2.0 下不裁文字。

| 组件 | 语义 | 现状要收的份数 | 证据 |
|---|---|---|---|
| `AppScreenHeader` | 返回 + 标题 + 副标题 + 右侧动作，唯一返回写法 | 14 个页头 / 6 种返回 | `CheckInScreen.kt:74-85`、`LearningRecordsScreen.kt:64-82`、`LicensesScreen.kt:61-82`、`ReadingHistoryScreen.kt:56-60`、`WorksheetSettingsScreen.kt:72-77`、`BackupRestoreScreen.kt:62`/`:184-188` |
| `ConfirmDestructiveDialog` | 级联条数 + 保留面 + 不可撤销 + 双按钮，措辞统一 | 4 份 AlertDialog + 1 份行内 | `AppScreen.kt:644-672`/`:1322-1345`/`:1699-1730`/`:1869-1905`、`BackupRestoreScreen.kt:141-154` |
| `WordRow` | 一行 lemma + 释义 + 状态/时间 + 右侧动作 | 7 份私有实现、0 复用 | `WordBookWordsScreen.kt:239-258`、`LearningRecordsScreen.kt:225-250`、`GlobalVocabularySearchScreen.kt:274-299`/`:319-358`、`VocabularyScreen.kt:133-195`、`WrongWordsScreen.kt:110-129`、`FavoritesScreen.kt:116-156` |
| `NotConfiguredCard` | AI 未配置：一句现状 + 一个「去配置 ›」出口，绝不再做只给文字的死屏 | 4 屏 12 行（3 份逐字 + 1 变体），4 处无出口 | `SentenceAnalysisScreen.kt:212/214/216`、`WordAiQaScreen.kt:267/269/271`、`PhotoTranslationScreen.kt:709/711/713`、`ImageStudioScreen.kt:253/255/257` |
| `InlineNotice` | 错误/回执行统一样式与措辞 | 11 份实现 | `LearningRecordsScreen.kt:153-168`、`ReminderSettingsScreen.kt:42-49`、`BackupExportScreen.kt:40-47`、`SettingsScreen.kt:269-279`/`:323-333` |
| `ScreenUnavailable` | 整屏不可用态：原因 + 一个动作 | 3+1 份退化页头 | 同上 + `RecoveryScreen.kt` |
| `SpeakerButton` + 播控组 | 喇叭药片放大、暂停/继续/停止文案一处定义 | 两套同构几何（注释自认「同款」） | `WordCardScreen.kt:410-425`/`:427-471`/`:280-301` vs `CardDetailScreen.kt:539-568`/`:570-616`/`:345-366` |
| `EntryRow`（彩色图标入口行） | 图标容器 + 主标题 + 副标题 + 状态或 `›` | 新统一件，替掉设置页与主页的多种行式 | 稿 `§7 ④` |
| `OutboundConfirmDialog` | 出站确认：按域名记忆、图片每次确认 | 5 份 | `ImageStudioScreen.kt:185-226`、`PhotoTranslationScreen.kt:668-703`、`SentenceAnalysisScreen.kt:171-206`、`WordAiQaScreen.kt:226-261`、`ReadingAccessScreen.kt:292-294` |

**骨架与预加载**：词书列表加载须预留每册骨架高度，避免「卡一下然后词书突然蹦出来」
（用户明确点名的体验缺陷，已在背词顺序批里做了词书页首帧词量可见；本轮补齐其余列表屏）。

---

## 4. 图标体系

1. **字形来源与选型（已定稿）**：用户 2026-10-18 选定 **B 案 · 双色层次（duotone）+ 域色渐变容器**
   （三案对照与选型理由见 [`docs/decisions/2026-10-18-ui-redesign-icon-duotone.md`](../../decisions/2026-10-18-ui-redesign-icon-duotone.md)）。
   因此**不复制 Material Symbols 的路径数据**，只以它的 24dp 网格、2px 视觉重量与 Outlined 无填充风格作度量基准，
   逐枚自行绘制矢量路径并内嵌为 `ImageVector` 常量，集中写入 `ui/components/AppIcons.kt`（约 30 枚上下，每枚两层）。
   落选的 A（单色描边 + `deep` 实底，直接内嵌上游路径）与 C（48dp 图块 + 实心图形 + 高光条 + 投影）
   仍可在 `docs/design/2026-10-17-ui-redesign-mockups.html` §6.2 复核；C 被否的决定性原因是
   行内 17dp 实心图形不可用，等于要维护两族字形。
2. **风格**：单一字形族 + 统一描边（2px 视觉重量、24dp 画布、圆头描边、无填充优先），
   禁止再混入 emoji、字母缩写徽标、自绘异形图标。**含文字的徽标**（如「AI」「♪」「⏰」）一律替换为矢量图标。
3. **两层结构与两种呈现**：每枚图标由「描边骨架层 + 内部半透明填充层」组成；
   入口层（≥40dp 容器）呈现为域色渐变容器 + 白色两层字形，容器渐变亮端取 **`deep` 混 35% `base`**、
   叠 `inset 0 0 0 1px rgba(255,255,255,.26)` 内高光，不得用 `base` 本体作底；
   行内层（30dp 容器 / 17dp 图形）**退化为单层描边**——浅底 `tint` + 域色 `deep` 字形，填充层不显示。
   **图形本身要按非文本对比 ≥3:1 当场计算**（WCAG 1.4.11 口径），不得再写「图标不参与对比度判定」；
   容器上的任何文字另受 §2.1 两条硬规则约束（浅薄荷底禁白字、渐变亮端不得用 `base`）。
4. **许可结论**：本轮入库的图标路径全部为原创自绘，登记于台账条目 `app-authored-ui-icons`
   （无第三方许可依赖、无署名义务、不新增依赖）。`material-symbols-icon-paths` 条目改述为
   「仅参考其 24dp 网格与 2px 描边度量，本轮未内嵌其路径数据」，并保留 Apache-2.0 的主动署名口径——
   若后续某枚图标改为直接内嵌上游路径数据，该条目对这一枚重新生效并须逐个列出。
   第三方 logo 类图标仍不在使用范围内（Google 明示因法务原因该集合不含第三方标志）。
   两条描述都必须与批 1 实际入库代码一致，不得保留与实现不符的措辞。
5. **自绘插画**：上限 5 张，仅用于空态与首启引导（逐张落点见
   [`docs/decisions/2026-10-17-ui-redesign-illustrations.md`](../../decisions/2026-10-17-ui-redesign-illustrations.md)）。
   产出为原创矢量 `ImageVector`（非位图、不含第三方素材、装饰性 `contentDescription = null`），
   许可结论登记于台账 `app-authored-ui-illustrations`；新增第 6 张属范围变化，先改决策记录再实施。

---

## 5. 深色模式与技术落点

1. 建立唯一的 `MaterialTheme(...)` 装配点（`ui/theme/`）：Typography、Colors、Shapes 三处一次装配，
   `DomainColors` 由 `isSystemInDarkTheme()` + 用户在设置页选择的三态（跟随系统 / 浅色 / 深色）共同决定。
2. 主题三态（跟随系统 / 浅色 / 深色）**当前不存在**：全仓库无 `isSystemInDarkTheme()` 调用、
   无主题相关设置项，`learning_settings` 表（`LearningSettingsEntity`，4 个开关 + `showVocabularySearchCount`）
   也没有该字段。落法：用 `Context.getSharedPreferences`（框架 API，零新增依赖，项目此前未使用）持久化，
   **不加 Room 列、不做 v33 迁移、不进备份包**——显示偏好不是学习数据，
   与 AGENTS.md「备份可包含学习数据…绝不包含…」的口径保持一致，避免牵动备份格式与认证链路。
   该项属批 1 交付（装配点的输入），不属批 3。
3. 206 处 `MaterialTheme.typography.*` 调用点在装配点后自动生效；语义名迁移按批推进，不设一次性重写。
4. 玻璃材质（半透明层）在深色下的边界：`surface-glass` 深色值 `#E617201C`（90% 不透明），
   禁止在深色上再叠低透明度导致文字对比跳水；每处玻璃层须能在真机截图核验后才算通过（风格指南 §9）。

---

## 6. 信息架构（六条裁决 + 两条补充）

裁决全文见决策记录，此处只留实施口径：

| # | 裁决 | 批次 |
|---|---|---|
| ① | 词汇域收成 3 入口：**查词 / 词表 / 我的词**（收藏 · 生词本 · 错词 = 一页三段） | 批 3 |
| ② | 设置页 **4 组 9 行**；删「账户」整组与两条「后续版本」占位行；「学习提醒」归「学习」组；资料卡不再显示今日计划数字 | 批 3 |
| ③ | 打卡页回归统计职责，头部两条裸文字链移出（依赖 ① 的新入口先落地） | 批 3 |
| ④ | AI 主页默认展开「现在能用」，未上线压成一条折叠行并标「未上线」；状态徽章须与 `AiFeature.implemented` 一致 | 批 3 |
| ⑤ | 阅读主页：主任务上提第一屏；外刊 Feed 改独立二级层（带返回）；类型与长度偏好下沉「生成前参数」；「AI 生成」与「换一篇」合并 | 批 3 |
| ⑥ | 三个备份门合成 1 个「备份与恢复」入口进子页三节；「删除全部学习数据」下沉子页末节，级联 + 二次确认规则零改动 | 批 3 |
| 补 1 | 学习主路径四屏（词书选取 / 今日计划 / 词卡 / 词卡详情）按审计结论重排 | 批 2 |
| 补 2 | 词卡内容深度不排（只用现有字段）；外刊多源另批 | 不排 |

**入口迁移铁律**：先建新入口，再摘旧入口。收藏与错词在「我的词」可用之前，**绝不能**先从打卡页删掉。

---

## 7. F5-03 移交账（本轮必须还）

| 事项 | 现状 | 本轮处置 |
|---|---|---|
| 字母 Rail 真 48dp | 视觉 48 但可点区不足，需信息架构级改动 | 批 3 随「词表」屏重排一起做 |
| `fontScale` 大字号头部挤压 | `CheckInScreen.kt:66-73` 注释自认归本轮 | 批 1 定 Header 版面 + 批 3 逐屏验收 |
| 喇叭药片放大 | 触控不足 | 批 1 组件样张已画，批 2 落地 |
| `ClickableText` 逐词 | 无障碍朗读不逐词 | 批 3（与 `AppIcons`/组件收口同批） |
| `AiSpeech.deep ≈4.1:1` 临界 | 浅底文字对比不足 4.5 | 批 1 token 已按实测重定（AI deep 提到 `#188F76`） |
| `spacedBy` 幽灵间距 | 不可见占位 | §2.3 禁令 + 批 1 落 token |
| 含字徽标 | 「AI」「♪」「⏰」等文字当图标 | §4.2 禁 |
| 退出学习不停播 | 退出浮层后音频继续 | 批 2（词卡屏流程内） |
| 裸 `Color(0xFF…)` 残留 | 见 §2.1 列举 | 批 1/批 2 token 化 |
| 每册骨架高度预留 | 首屏跳动 | §3 骨架条 |
| 契约测试正则局限 M-B1/M-B2 | 覆盖不全 | 批 3 补测试，不改门禁语义 |

---

## 8. 紧随其后的独立批次（不占本轮）

**外刊多源（B 路线）**：再登记 2–3 个许可可核验的免费 / 公有领域英语学习来源，逐源走
`ArticleSourceRegistry` 白名单 + 解析器适配 + `docs/third-party-notices.md` 八字段台账 + AC 矩阵补非空证据行。
不新增依赖，不做任意 URL 抓取（`docs/specs/02-reading-and-ai-content.md:51` 与
`docs/decisions/2026-09-24-article-source-licensing.md:21-24` 的否决继续有效）。
**排期在 UI 轮三批之后**，因为逐源许可核验是法务动作，不应阻塞界面重设计。

---

## 9. 验收标准

**可自动验证（进门禁）**

- AC-R-01：`MaterialTheme` 装配点存在，且 `AppType` 六档钉值与本规格 §2.2 一致（契约测试读常量，非截图）。
- AC-R-02：全站无裸 `Color(0xFF…)` 字面量出现在 `ui/` 布局代码中（沿用 F5-03 已有的禁令契约测试并扩展）。
- AC-R-03：浅底与品牌渐变上的前景色只能是 `on-mint`/`on-brand` 系；对比度由 WCAG 相对亮度函数计算 ≥ 4.5:1，禁止估。
  同一测试口径覆盖图标：白色字形/图形在域色容器上按非文本 ≥3:1 计算（浅底 tint + 域色字形另按 ≥3:1），
  渐变容器亮端取 `deep` 混 35% `base`，出现以浅色 `base` 直接作白字形底的容器即判失败（AI 2.11:1 / 复习 2.06:1）；
  允许配对与禁配（`text-2` 禁压 `tint-2`/`tint-3`、渐变上取 `on-brand`）由 `DesignTokenContractTest` 的表钉住
  （取值见 `docs/decisions/2026-10-18-ui-redesign-contrast-and-icon-layer-rulings.md`）。
- AC-R-04：所有可交互控件高度 `≥ 48.dp`（`heightIn`/`defaultMinSize` 断言，沿用 F5-03 测试口径）。
- AC-R-05：`verifyThirdPartyNotices` 通过，且台账含 `material-symbols-icon-paths`（改述为仅参考度量）、
  `app-authored-ui-icons` 与 `app-authored-ui-illustrations` 三条（8 字段均非空），
  描述与 `AppIcons.kt` 实际入库的路径来源一致，应用内许可页渲染出同一份内容。
- AC-R-05b：`AppIcons.kt` 除 `docs/decisions/2026-10-18-ui-redesign-contrast-and-icon-layer-rulings.md`
  豁免表内 5 枚（`Back`/`ChevronRight`/`ChevronDown`/`Close`/`Check`，只有描边骨架、不建填充层）外
  每枚图标为两层结构（描边骨架 + 填充层），豁免表内 5 枚不得进入 ≥40dp 入口容器（只作行内/尾部指示）；
  逐枚清单以该决策文档钉定的 33 枚为准，与本规格 §4.1 的规模相符；
  行内呈现下填充层不显示；全站 `Icons.` 引用仍为 0、不新增 `material-icons-extended` 依赖
  （`dependencyLocking` 锁文件零变化）。
- AC-R-06：`AppTabTest` 与既有业务规则测试零修改通过；JVM 单测基线只增不减（当前基线 1499 测试 / 206 套件 / 0 失败，须每次重算不得引用陈旧数）。
- AC-R-07：`fontScale = 2.0` 的 connected 用例覆盖六屏重排后的标题与卡片（护栏沿用 `CheckInScreenTest` 口径）。
- AC-R-08：§3 每个共享组件有对应「全站唯一实现」的静态检查或组件测试；被替换的私有实现数从 7/14/5/11 降到 1。

**必须真机核验（不得凭代码宣称一致）**

- AC-R-10：六屏在 360dp 宽真机上浅/深两套截图，与设计稿逐屏比对（风格指南 §9/§10）。
- AC-R-11：玻璃层与域色容器在深色下文字可读性实测。
- AC-R-12：TalkBack 走查重做一遍（图标替换后描述、`ClickableText` 逐词、未配置卡的出口可读）。

**内容归属验收**

- AC-R-20：今日计划页 Ready 态整宽按钮数从 8 降到 **1 CTA + 一组入口行**；今日数字在同一屏只出现一处权威呈现。
- AC-R-21：设置页为 4 组 9 行、零不可点行、无「账户」组、无开发者术语（`F4-04`、「AI Profile」、「Salvage 包」全部改为用户语言）。
- AC-R-22：§4 缺陷清单（审计文档 §4）逐条关闭：历史页可滚到底、问词页不再有假按钮、词卡 MissingSetup 有出口、
  备份文案不再否认自身已实现的认证恢复。
- AC-R-23：AI 主页「选能力」在默认状态可见，未上线项折叠且标「未上线」。

---

## 10. 风险与未核实

1. `AppScreen.kt` 实测 2424 行且无 `NavHost`（23 个 `var show*` + 22 个 `BackHandler`）。
   「我的词」分段容器让三个 ViewModel 同宿主并存，是本轮最大实现风险；批 3 前需评估是否先做宿主拆分。
2. 未核实项（审计文档 §7）：「重新学习」是否被详情层盖住、三集合实际重叠比例、提醒时间只给 8 个整点是否为有意收窄、
   旧「浏览词书」入口现挂载点、`CardDetailScreen` 另 4 个宿主的传参、各屏骨架在小屏/大字号下表现。
   这些不进本规格的验收标准，实施时逐条现场核实。
3. 各屏 `testTag` 变更对 connected 用例的影响面尚未逐屏统计，批 2/批 3 开工前须列清单。
4. Material Symbols 台账条目描述的是「内嵌路径数据的改作」，若上游字形版本更新，需同步台账「版本」字段。
5. **图标呈现选型已定**：用户在 2026-10-18 选定 B 案（双色层次 + 渐变容器），§4 已按此定稿，
   选型过程与许可影响见 `docs/decisions/2026-10-18-ui-redesign-icon-duotone.md`。
   仍待用户在批 1 开工前确认的是：约 30 枚的逐枚清单（批 1 第一个任务即产出该清单并钉进 `AppIcons.kt` 顶部注释）。
