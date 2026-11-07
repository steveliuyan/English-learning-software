# 决策：加载反馈统一为骨架族 + shimmer 定格即静息 + 预取只用本地（批 C）

日期：2026-11-07
分支：`stage-5-ui-r2-learning-path`（不合 main；批 B 的推送仍被本地代理挡住，本批同样逐批请示）
关联：`docs/superpowers/plans/2026-11-06-motion-and-adaptive-layout.md`（批 A–D）
　　　`docs/decisions/2026-11-06-motion-foundation-and-system-reduced-motion.md`（批 A §3.4：循环动画在 `scale=0` 定格在周期末尾值）
　　　`docs/decisions/2026-11-06-placeholder-text-is-not-learning-content.md`（占位文本不是学习内容）
　　　`docs/specs/05-release-quality-and-compliance.md` F5-05 / AC5-06（本批新增口径）

## 1. 背景

用户 2026-11-05 提出「做各个页面的动画动效以及预加载的动画特效」，批 A（动效基建）与批 B（全面自适应）已收口，本轮进批 C。开工前三项拍板（选项题原文）：

- 骨架铺面 = **全站都铺**（不挑重点页）。
- shimmer 在系统关闭动画下的定格值 = **末尾 = 静息骨架、无光泽**。
- 真预取深度 = **闪屏窗口 + tab 预读**，硬约束「只用本地、不为预取新增任何网络或 AI 出站」。

「全站都铺」和「真预取」这两条在动手前各自压着一个事实问题：铺面对象到底是几处、现在长什么样；预取窗口里到底有什么活可干。所以第 2 节先把现状钉死，第 3 节才是四条决定。

## 2. 证据（全部为本机 grep 实测，重算命令见 §2.5）

### 2.1 骨架真身只有一处，另一族是死代码

- 生产里真正在画的骨架是 `ui/AppScreen.kt:2991` 的**私有** `WordBookRowSkeleton`（配 `:2884` 的 `repeat(WORD_BOOK_PLACEHOLDER_ROWS)`，常量在 `:2825` = 3）。它被两枚闸钉住：`LearningSetupLayoutContractTest:913-921`（`wordbook_row_skeleton_` 前缀在选词书屏恰好 1 处 + 必须读生产常量）与 `androidTest/WordbookPickerHeaderTest:85-121`（逐行存在、通栏、实测高度不矮于真行）。
- `ui/components/Skeleton.kt` 的 `ListSkeleton` / `SkeletonRow` / `SkeletonBar` **全仓零引用**（`grep -rn "ListSkeleton" app --include=*.kt` 只命中定义处）。它的 KDoc 第 49 行写着「connected 判据依赖 `wordbook_placeholder_$row`」——**这句是假的**：`wordbook_placeholder_` 只出现在该文件自身（定义 `:62` 与注释 `:49`），`app/src/test` 与 `app/src/androidTest` 零命中。批 C 若照这份 KDoc 保留旧前缀，是在保护一个不存在的判据。

### 2.2 shimmer 生产侧零命中

`grep -rniE "shimmer|infiniteRepeatable|rememberAnimatable" app/src/main` 的命中只有：`ui/theme/AppMotion.kt` 的 KDoc（批 A 留给本批的那句「骨架 shimmer 这类循环动画必须按『减弱动效下定格在周期末尾值』设计静态外观」）、`ai/domain/AiVoiceCatalog.kt` 里一枚叫 Shimmer 的云端音色名、以及三册词书 JSON 里的英文单词。**即：没有任何 shimmer 基建可复用，也没有任何循环动画在被系统缩放管着。**

### 2.3 等待反馈的现状是 10 个文件 15 处裸转圈，两处 tab 根本没有等待态

`CircularProgressIndicator` / `LinearProgressIndicator` 的真调用点（不含 import 与注释）：
`AppScreen:2239`（品牌闪屏的确定性进度条）、`BackupPairingExportScreen:176,255`、`BackupRestoreScreen:315`、`GlobalVocabularySearchScreen:233`、`LearningRecordsScreen:218`（+ `:294` 条状）、`PhotoTranslationScreen:740`（条状）、`TodayPlanScreen:210`、`VocabularyRelearnScreen:104`、`WordBookWordsScreen:159`、`WordCardScreen:124`（+ `:271` 条状）、`WorksheetPreviewScreen:117`。
除选词书一屏，其余慢屏的第一帧都是**一枚转圈或一句「正在读取…」的裸文本**——`ReadingAccessScreen:213` 就是 `Text("正在读取本地阅读状态…")`。

更关键的是缺口：`grep -n "Loading|Progress|Skeleton"` 在 `ui/AiLearningScreen.kt` 与 `ui/ProfileScreen.kt` **零命中**。这两个 tab 没有等待分支，第一帧画的是「空列表」——对用户来说和「没有数据」是同一个形状。所以「全站都铺」不是给已有 Loading 态换皮，而是要先补出两屏的态，再统一铺骨架。

**C-2 实测订正（2026-11-07，读完两屏源码后）**：上面这段「补态再铺」对两屏的落点都判错了方向，此处按实测改写，
否则下一任会去 `AiLearningScreen` 找一个根本不该存在的分支。

- `AiLearningScreen` **没有任何异步读盘**：功能目录由 `AiFeature.entries` 静态派生，屏侧不收 UiState、没有 ViewModel，
  「零命中」不是漏了等待态而是根本没有可等的东西。给它补 `Loading ->` 分支就是为一个不存在的过程画占位。
  处置：本屏不进铺面，改由一条闸钉住它的位置——静态页**不许**长出没有骨架的 `Loading` 分支
  （`PrimaryTabSkeletonContractTest.static ai tab keeps no loading arm of its own`），将来真接异步态时必须同时铺骨架。
- `ProfileScreen` 的等待态不在屏内：它的数字来自宿主注入的打卡快照与阅读历史，屏侧「零命中」的原因是
  宿主把还没读到的值写成 `checkInReady?.streakDays ?: 0` ——首帧因此念「连续打卡 0 天 · 累计学过 0 个词」并把
  统计格画成 `0`。这不是「画成空列表」，而是 F1-01 那枚**假数字**换了承载。处置：三枚入参改 `Int?`，
  宿主只在快照 `Loading` 那一档传 `null`（`Unavailable` 不动，那是读到了「读不到」），屏侧 `null` 画占位格、
  读屏念「正在统计」。于是「我的」页的骨架落在**格子里**（`SkeletonBar` 两条），不是整屏一个 `Loading` 分支。

### 2.4 预取窗口里确实有活，但只有一处先例可抄形状

- 闪屏侧：`launch/BrandLaunchActivity.kt` 的 `LaunchedEffect(Unit) { delay(1200); startActivity(MainActivity) ; finish() }` —— **1.2 秒纯等，零工作**。而 `di/AppModule.kt:112 provideDatabase` 是 `@Provides @Singleton` 惰性方法：真正的 `DatabaseStartup.open(context)`（建库 + 迁移）发生在 MainActivity 首次要 `AppDatabase` 的那一刻，即闪屏之后。这就是那 1.2 秒该干的活。
- 装载点侧：既有「预读」先例只有两处——`AppScreen:417` 的词库三段进页预读（`vocabulary / wrongWords / favorites` 各 `load` 一次，**无测试钉住**）与 `EnglishLearningApplication` 的 Kokoro 预热（`StartupPronunciationWarmer`：尽力而为、`runCatching`、失败静默）。后者是本批要复用的形状。
- 红线（不是推断，是既有规格）：AGENTS.md「学习反馈必须先成功写入本地，网络或 AI 调用不得阻塞提交反馈」、`docs/specs/02` AC2-11「取消即零出站」、AC2-14 四条腿逐次弹框确认。任何「为了预取提前打一次 `models`/生成一次文章/合成一次云端 TTS」都直接撞这三条。

### 2.5 重算命令

```bash
# §2.1 死代码与假 KDoc
grep -rn "ListSkeleton" app --include=*.kt | grep -v "components/Skeleton.kt"   # 期望 0 行
grep -rn "wordbook_placeholder_" app                                            # 期望只命中 Skeleton.kt 自身
# §2.2 shimmer 零基建
grep -rniE "shimmer|infiniteRepeatable" app/src/main --include=*.kt
# §2.3 等待指示器调用点数（改判据前先重算）
grep -rnE "^\s*(CircularProgressIndicator|LinearProgressIndicator)\(" app/src/main --include=*.kt | wc -l
grep -c "" app/src/main/java/com/example/englishlearning/ui/AiLearningScreen.kt >/dev/null && \
  grep -n "Loading\|Progress\|Skeleton" app/src/main/java/com/example/englishlearning/ui/{AiLearningScreen,ProfileScreen}.kt   # 期望 0 命中
# §2.4 闪屏窗口
grep -n "delay(1200)\|provideDatabase" app/src/main/java/com/example/englishlearning/launch/BrandLaunchActivity.kt app/src/main/java/com/example/englishlearning/di/AppModule.kt
```

## 3. 决定

### 决定 1：全站骨架收口成一族，落在 `ui/components/Skeleton.kt`，旧私有骨架迁入且锚不动

`Skeleton.kt` 重写为唯一骨架族：`SkeletonBar`（条）、`SkeletonRow`（通栏行，抄 `WordBookRowSkeleton` 的形状：22dp 圆面 + 0.42f/0.24f 双条 + 12dp 上下内边距 + `hairlineTop` 分隔）、`SkeletonList`（按生产行数常量渲染 N 行）、`SkeletonCard`（卡片块，给今日计划/词卡这类非列表屏）、`SkeletonStatGrid`（分段计数格，给词库/我的这类格子页）。

三条不可动的约束：
- `AppScreen` 的私有 `WordBookRowSkeleton` **删除函数体、改为调用共享族**，testTag 前缀仍由调用方传 `wordbook_row_skeleton_$index`、行数仍读 `WORD_BOOK_PLACEHOLDER_ROWS`——这样 `LearningSetupLayoutContractTest:913-921` 与 `WordbookPickerHeaderTest` 两枚既有闸继续绿，批 C 不动已验收的选词书版面（§2.1 的 tag 必须留在 `hairlineTop` 的**外侧** padding 之前，否则骨架比真行高 1dp，这条经验在 `AppScreen:2991` 的注释里，随函数一起搬）。
- `ListSkeleton` 的假 KDoc（「connected 判据依赖 `wordbook_placeholder_`」）删除，不得原样搬进新族。旧前缀 `wordbook_placeholder_` **随死代码一起退役**：它没有任何判据依赖，留着只会让下一任误以为有两套骨架 tag。
- 骨架一律**零文案、零数字**（沿用 `Skeleton.kt` 原有的无障碍口径）：占位行手里没有数据，向 TalkBack 播报「已学 0 词」比什么都不念更糟。

### 决定 2：shimmer 用 `drawBehind` + 渐变偏移，并把「定格 = 静息」做成结构而不是补丁

规格：`Modifier.shimmer()` 扩展（落在 `ui/components/Shimmer.kt`）内部用 `infiniteRepeatable(animation = keyframes { durationMillis = AppMotion.Slow * 4 … })` 驱动一个 0→1 的相位，`drawBehind` 里按相位画一条斜向渐变光带；**周期末尾 1/4 相位停在「光带完全移出画面」的位置**。

为什么这样写而不是「加个开关判断系统缩放」：批 A §3.4 实测证明 Compose 自己遵守 `animator_duration_scale`，`scale=0` 时循环动画落在**一个周期的末尾值**后不再要帧（emulator-5554：`infiniteRepeatable(tween(600))` 在 scale=0 的 2.5s 窗口只观测到 3 次重组，对照 scale=1 的 150 次）。所以只要**末尾值就是无光泽的静息骨架**，关闭动画时用户看到的就是设计内的静态骨架——不需要、也不允许任何应用侧的降级分支。反过来说，如果末尾相位停在半亮，`scale=0` 下全站骨架会永久糊在一半的亮度上，那是缺陷不是动效。

配套三条禁令（各由契约测试钉住，见 §4）：
- 颜色只走 `appColors()`（`outlineSoft` / `tint` 族），骨架与光带**禁裸 hex**、禁 `Color(0x…)` 字面量。
- 时长只引 `AppMotion` token；**不得新增任何时长换算入口**（批 A 已删 `durationMs` / `animatorDurationScale`，`MotionSpecContractTest` 钉着不得接回）。
- 光带是**修饰而非信息**：不许承载颜色语义（不许用它暗示「已加载完成」），也不许在减弱动效档位长出闪烁。

### 决定 3：铺面分四层，等待语义按「数据来自哪里」分两类

- C-2 一级页（学习 / 阅读 / AI / 我的）：`TodayPlanScreen` 与 `ReadingAccessScreen` 的转圈/裸文本换骨架；
  `AiLearningScreen`、`ProfileScreen` **先补 Loading 分支**（当前零处理，§2.3）再铺骨架。补的分支只处理「本地读盘中」，不引入新状态机语义。
  ——C-2 交付时按 §2.3 的订正落点执行：AI 屏实测为静态、不进铺面（改由一条「静态页不许长出没骨架的等待分支」钉住），
  「我的」页的等待态补在**宿主的入参**上（三枚统计改可空 + 屏侧占位格），屏侧不长 `Loading` 分支。
- C-3 本地读盘子页（词卡流、词书单词、生词复习、学习记录、查词结果、**文章整篇阅读**、试卷预览、备份导出/恢复的本地校验段）：转圈换骨架，形状照各自真实行/卡。判据是「第一帧就有一条正确形状的行」，不是「转圈更好看」。
- C-3 开工实测把等待分成**三类**而不是原写的两类（§2.3 那份 15 处清单只数了转圈，漏了一处只有裸文案的读屏，也混进了动作进行中那几处）：
  1. **等数据**（首帧该有形状）→ 换骨架：`WordCardScreen`、`WordBookWordsScreen`、`VocabularyRelearnScreen`、`LearningRecordsScreen`、
     `GlobalVocabularySearchScreen` 的 `Loading`，外加 `ArticleReadingScreen`——它没有转圈，只有一句 `Text("正在加载…")`，
     与 C-2 之前阅读 tab 同一形状缺陷，属于「本地读盘」所以进 C-3 而不是 C-4。
  2. **动作进行中**（不是等数据，是在做事）→ **保留活动指示 + 真话文案，不画骨架**：`BackupPairingExportScreen` 的
     认证中/导出中两枚、`BackupRestoreScreen` 的恢复中一枚、`GlobalVocabularySearchScreen` 的「正在清空历史…」、
     `WorksheetPreviewScreen` 的「正在生成 PDF 预览…」。骨架承诺的是「马上出现的就是这个形状的内容」，
     而这五处接下来出现的是**结果回执或整屏换掉**，画骨架等于预告一个不会以那个形状落地的东西；
     它们的文案本就说清了在做什么，属于 C-4 的进行中语义族，只是动作在本地。台账里这五行不删，逐条写明豁免理由。
  3. **网络 / AI 等待** → C-4（原文那条不变）。
  另：`AppScreen` 闪屏那枚 `LinearProgressIndicator` 是确定性进度（品牌页动画），永远不在铺面对象里。
- **C-3 交付实况**（本轮，六屏收完）：
  1. 分类 1 全部落地——`LearningRecordsScreen` / `WordCardScreen` / `VocabularyRelearnScreen` / `WordBookWordsScreen` /
     `GlobalVocabularySearchScreen` 的 `Loading` 与 `ArticleReadingScreen` 的 `current == null` 共六处换成同形状骨架。
     `ArticleReadingScreen` 用 `SkeletonBar` 逐条镜像而**不是** `SkeletonCard`：真版面页头之后是一列裸排内容，没有卡壳，
     套壳就是预告一个不会以那个形状落地的东西。
  2. 骨架族为此长出的是**两种镜像能力**，不是第六件形状：`leading`（关掉行首圆点，供 `WordRow` 那族无前导图标的行）与
     `framed`（把一行画成带壳卡行 + 10dp 缝，供 `Card` 包 `WordRow` 那一族）。两条各由
     `LocalSubpageSkeletonContractTest` 钉「族里恰两枚声明 + 确有屏在用」，防止参数长出来没人用；
     **无壳那一支的 modifier 链逐字未动**，C-1 那两条 connected 行高判据照旧读同一份形状。
     已知局限如实记录：族里一行只画两条横，四行的查词结果行靠 `rowHeight = 96.dp` 承接总高，不为其长第三种行内版式。
  3. 数据量型列表（学习记录 / 词书单词 / 查词结果）的行数判据只钉**写法**（读 `*_PLACEHOLDER_ROWS` 常量、不写字面量），
     取值由每屏常量自己的算术交代；C-2 那两屏是版面常量组，仍跑**值判据**（`rows == 真实裸行数`）。
     两种写法不是两套口径，是「首帧知不知道有几条」这一事实的两种结果。
  4. 转圈台账 9 行 → **5 行**：删掉三行「等数据」站点（生词复习 / 词书单词 / 词卡流），保留四行豁免
     （配对 ×2、恢复、清空历史、生成 PDF）。`GlobalVocabularySearchScreen` 那一行**留着**：
     `GlobalSearchProgress` 原先一支两用，本批把 `Loading` 拆出骨架后它只剩 `ClearingHistory` 一种语义，
     删行的判据是「这处转圈的调用面只剩动作进行中」。
  5. connected 侧按 tag 的落地判据新增 7 条（六屏各一条；生词复习的 `Idle` 与 `Loading` 共用同一支，
     因「同一测试两次 `setContent` 真机必崩」拆成两个方法），逐条判「骨架在、行数上界对、进行中文案与假数字不在、
     真内容节点不提前组合」。
- C-4 网络 / AI 等待屏（文章生成、AI 问答、拍照翻译、生图、句法分析）：**不许长得像已有结果**。这一类的等待语义与骨架不同——骨架承诺「马上出现的是这个形状的内容」，而 AI 生成失败率高、时长不可知，画骨架会变成「假内容」，与 `2026-11-06-placeholder-text-is-not-learning-content` 同源。所以 C-4 的交付是**进行中语义**：明确「正在向 AI 请求今日文章（可取消）」+ 不定量的活动指示 + 取消入口，绝不画成「已有 N 条」的假快照，也绝不预生成占位文本。
- C-1 是这些的基建与契约闸；C-5 是真预取；C-6 是门禁 + 截图册 + connected + 报告。

### 决定 4：真预取只做两件本地的事，且必须复用既有装载点与既有形状

1. **闪屏窗口预热本地盘**：新增 `core/storage/StartupLocalDataWarmer`（形状照抄 `StartupPronunciationWarmer`：`suspend fun warm()`、`runCatching`、失败静默、返回值只给日志用不上 UI），由 `EnglishLearningApplication` 用现成的 `warmUpScope` 在 `onCreate` 启动。它做的事只有一件：请求 `AppDatabase` 单例，从而把 `DatabaseStartup.open(context)`（建库 + 迁移）从「闪屏之后」挪进那 1.2 秒。**不得**在此处读取学习数据并塞进任何 VM 状态（那会造出第二套数据权威），也不得预跑备份/导出等重 IO。
2. **tab 预读**：把 `AppScreen` 已有的按 tab 装载点补齐口径——底栏存在的四个 tab 中，只读本地的那几份（词库三段已做的 `vocabulary / wrongWords / favorites`、`readingAccessViewModel.load`）保持既有分派，不新增出站装载点；`aiProfiles`（本地配置的 Profile 列表）这类纯本地读可以预读。**明确排除**：连接探活 `models`、文章生成、云端 TTS 合成、任何 AI 请求。
3. 两条都受同一硬约束：预取不得让任何一次点击「本来不发请求」变成「已经发过请求」。验收方式见 §4 的零出站探针（既有决策 2026-11-04 的台账口径）。

## 4. 验证边界（不写假用例）

契约/单元测试**能**钉的（本批写死）：
- 骨架族唯一性：`ui/components/Skeleton.kt` 之外不得再出现 `SkeletonRow(`/`SkeletonBar(` 定义；`AppScreen` 里 `wordbook_row_skeleton_` 仍恰好 1 处、`WORD_BOOK_PLACEHOLDER_ROWS` 仍被读（改造既有 `LearningSetupLayoutContractTest`，不得先放宽）。
- shimmer 定格结构：光带渐变的**首末相位同值**（静息），即「相位=1 时的 alpha/偏移 == 相位=0」这一条要能被纯函数测出——把渐变 stops 的计算抽成可测函数，不依赖动画时钟。
- 禁裸 hex / 禁时长换算入口：`ShimmerSpecContractTest` 扫生产骨架与 shimmer 源文件，`Color(0x`、`Color(` 十六进制字面量、`animatorDurationScale`、`durationMs` 出现次数为 0。
- 等待屏分类：AI/网络等待屏必须含「进行中」语义锚（可取消 + 说明在请求谁），且不得出现「已生成/已有 N 篇」字样的占位；本地读盘屏不得留裸转圈（调用点计数只减不增，改判据前先跑 §2.5 重算）。
- 预取零出站：`StartupLocalDataWarmer` 只做开库，单测用假 provider 断言它不触网；tab 预读清单与既有装载点逐条对账（新增出站装载点即红）。

只能靠设备/截图、**不假装单测覆盖**的：
- shimmer 的观感与 `scale=0` 定格外观：每条骨架规格各配一张 `animator_duration_scale=0` 截图 + 一张 `scale=1` 中段截图，落 `.git/qa-scratch/`，登记进 `docs/verification/ui-redesign-2026-10.md`。循环动画不能用 `createComposeRule()` 量（批 A 已证测试帧钟替换会让读数失真）。
- 「闪屏那 1.2 秒确实变有用」：只能实测首帧到今日计划可读的耗时，记 `adb shell am start -W` 与目视对照；本批不承诺具体毫秒数（批 B 已定「宽窗口性能阈值不适用竖屏基线」，同一口径适用于此——若不做重采就如实标注无基线）。
- TalkBack 下骨架零播报：connected + 人工走查，单测只能钉「骨架无 Text 节点」。

## 5. 影响

- 正面：全站第一帧不再是空白或转圈；关闭系统动画的用户看到设计内静态骨架；`scale=0` 不需要应用侧降级分支；选词书一屏的两枚既有闸保持绿；`ListSkeleton` 死代码与其假文档一并清掉。
- 成本与风险：骨架是**新增 UI 面**，每铺一屏就多一处要维护的形状（真卡改版时骨架要跟）；契约测试按本仓惯例是「剥注释数锚点」的字符串闸，源码重构时容易假红，缓解是每条锚附一句「为什么是这条锚」的 KDoc（沿用批 B 的做法）。预取动的是启动路径，风险是开库提前带来的启动竞争（与 Kokoro 预热同时在 `Dispatchers.Default`），处置是**先只做开库**、其余按 C-6 的耗时读数决定要不要收缩。
- 明确不做：不引入任何新的网络/AI 出站；不做「预生成今日文章」；不做骨架的逐像素版式复刻（承诺横向起点对齐，纵向不承诺——沿用 `Skeleton.kt` 既有取舍）；不给骨架加文案或假数字；不在 AI 等待屏画假内容。
