# 决策：页面转场的挂载权威、方向判据与规格落点（批 D）

日期：2026-11-08　分支：`stage-5-ui-r2-learning-path`　范围：阶段 5 UI 重设计轮 批 D
关联：`docs/decisions/2026-11-06-motion-foundation-and-system-reduced-motion.md`（批 A 口径表与降级判据）、
`docs/decisions/2026-11-06-adaptive-window-layout.md`（批 B 窗口类别只在宿主判一次）、
`docs/decisions/2026-11-07-loading-skeleton-shimmer-prefetch.md`（批 C 骨架与 `scale=0` 定格口径）、
`docs/specs/05-release-quality-and-compliance.md`（本批新增 AC5-07）

用户拍板：方案 1（挂载权威收进纯函数 + 两站转场），覆盖「一级 tab 切换 + 全屏子页进出 + 对话框浮层并表」，
形态为「方向滑动 + 淡入淡出」，按 D-1…D-4 分段交付、每段跑一次门禁。

## 1. 现状（全部现算，命令附后）

| 事实 | 读数 | 出处 |
|---|---|---|
| 全屏子页挂载链 | 35 支（首支 `if` + 34 枚 12 空格缩进的 `} else if (`），跨度 `AppScreen.kt:728`–`2025`，末支 `showSettings` | `grep -cE "^            \} else if \(" app/src/main/java/com/example/englishlearning/ui/AppScreen.kt` → 34 |
| 返回键处理器 | 37 枚 `BackHandler(`，与链分离声明，靠「后声明者优先」分派 | 同文件 `grep -c "BackHandler("` → 37 |
| 链的末段 | `} else {`（`AppScreen.kt:2026`）= 四个一级 tab 的宿主，注释原文即「上面所有分支都是覆盖全屏的二级层，因此天然不显示导航」 | `AppScreen.kt:2027` |
| tab 内容宿主 | **只有一处** `when (selectedTab)`，在 `Scaffold` 的 `contentPadding` 内容盒里（`AppScreen.kt:2061`）；底栏与批 B 侧栏都在它外围，两者写同一个 `selectedTab` | `grep -rn "when (selectedTab)" app/src/main` → 1 hit |
| 今天有没有转场 | 链与 tab 都是**瞬间硬切**；全站唯一有动画的切换腿是对话框浮层 `GlassOverlay` 的 `AnimatedVisibility`（`fadeIn+scaleIn(0.92)` 入场、`fadeOut+scaleOut(0.92)` 退场，四枚 `tween(AppMotion.Normal, …)`） | `ui/components/glass/GlassOverlay.kt:58`–`64` |
| 时长/缓动 token | `AppMotion.Fast=160 / Normal=220 / Slow=300`，`EasingOut=cubic-bezier(.2,.7,.3,1)`、`EasingIn=cubic-bezier(.4,0,.8,.3)`；批 A 已删除换算入口，降级交给平台 | `ui/theme/AppMotion.kt` |
| 批 A 给本批留的账 | 口径表有一行「页面转场 = `Slow` 300ms + 入场 `EasingOut` / 退场 `EasingIn`，批 D 落地」；§4 明写「行级子串匹配看不到 `easing = ` 换行、也看不到把 spec 包进工厂函数的间接传参——由批 D 的转场判据兜」 | 批 A 决策 §3.3 / §4 |

### 1.1 结构性障碍只有一个

`AnimatedContent` 要播**退场**，就必须在状态改变后继续组合离场那一屏；而链的分支是当场由 35 个布尔位决定的——
标志位一变，旧屏立刻从组合里消失，没有任何东西可以淡出/滑出。
所以「当前可见屏」必须成为一个**值**，而且判据只能有**一处**。
这正是上一批 N-1 刚付过学费的形状（同一条 403 规则内联抄六遍、每遍都漏），本批不打算再犯一次。

### 1.2 测试资产现状（现算，本批逐帧断言落点的依据）

```bash
grep -rl "createComposeRule\|createAndroidComposeRule" app/src/test | wc -l        # → 0
grep -rl "createComposeRule\|createAndroidComposeRule" app/src/androidTest | wc -l # → 67
grep -rln "mainClock" app/src/test app/src/androidTest                              # → 1 个文件，且只在注释里
ls app/src/test/java/com/example/englishlearning/ui/*.kt | wc -l                    # → 168（JVM 侧只测纯函数与源码扫描）
```

⇒ 本仓的 Compose 语义/行为断言**全部**在 androidTest；JVM 的 168 枚 ui 测试是纯函数与源码契约扫描。
批 D 的两站转场行为判据因此必须写在 connected，不能凭空在 JVM 长出一套 `createComposeRule` 用法。

### 1.3 链边的三处相邻事实（本批不动，但必须先说清，否则会被当成漏改）

1. **`overlayOpen`（`AppScreen.kt:580`–`584`）是第二份「覆盖层」判据，但它的语义不是「挂载了覆盖层」**：
   它把 35 枚标志位**无守卫地**并起来（`showReviewQueue` 亮但 `todayReady == null` 时链上根本没有分支命中、
   屏幕上其实还是 tab 宿主，`overlayOpen` 仍为 true），另外还含两枚**不在链上**的页内浮层位
   （`selectedArticleCard` / `showWordQaCard`，声明在 343 / 365，渲染在文章分支与学习分支内部）。
   它唯一的消费者是 `BackHandler(enabled = !overlayOpen && selectedTab != AppTab.LEARNING)`（:587）。
   ⇒ 本批**不把它换成 `route != null`**：那会在上述守卫形里改变返回键行为，属于夹带。
   收口口径：resolver 与 `overlayOpen` 的语义差由一条 JVM 用例明确钉住（见 §5 D-1），
   「两处判据」在这里是**两个不同问题**，不是同一问题抄两遍，与 N-1 的形状不同。
2. **`ConfirmDestructiveDialog`（`AppScreen.kt:486`，`if (pending != null)`）不是链支**：
   它是 `Ready` 顶层的独立浮层（注释原文：入口已搬进 hub，对话框必须住顶层才在任何子页打开时组合）。
   它自己走窗口层级，与本批的两站转场无交集，链改造时不碰。
3. **`GlassOverlay`（对话框浮层）也不是链支**：它是 `BoxScope` 扩展，渲染在消费者屏内部
   （今天唯一消费者 = `AiProfileSettingsScreen` 音色浮层）。本批对它只做**规格并表**
   （四枚 `tween(` 搬进 `AppTransition`），不改它的挂载点、容器与可见形态。

## 2. API 实测（不凭文档印象设计）

方法按仓内口径：AAR 只有包没有裸 jar，临时目录一律放**仓库外**（Windows 索引进程会把仓库内空目录 lock 成删不掉）。

```bash
unzip -o -q <ui.aar> classes.jar && unzip -o -q classes.jar
grep -rl "invisibleToUser" .          # 唯一命中 androidx/compose/ui/semantics/SemanticsPropertiesKt.class
javap -p androidx/compose/ui/semantics/SemanticsPropertiesKt.class | grep -i invisible
# public static final void invisibleToUser(androidx.compose.ui.semantics.SemanticsPropertyReceiver);
```

- `androidx.compose.animation:animation-android:1.10.0` 里 `AnimatedContentKt` 存在，两站可直接用它。
- **纠正一处直觉**：本仓锁定的 `ui-android:1.10.0` **没有** `Modifier.invisibleToUser()`；
  唯一声明是 `SemanticsPropertyReceiver` 上的扩展。⇒ 生产写法只有一种：
  `Modifier.semantics { invisibleToUser() }`。下一任若照公开文章写 `Modifier.invisibleToUser()`，编译即失败。
- Compose BOM 锁 `2025.12.00`（`gradle/libs.versions.toml:5`）。
- **`AnimatedContent` 的真实形状**（`javap -p androidx.compose.animation.AnimatedContentKt`）：
  `transitionSpec` 的返回类型是 **`ContentTransform`**，不是「`EnterTransition + ExitTransition`」；
  构造它的是顶层 `infix fun EnterTransition.togetherWith(ExitTransition): ContentTransform`
  （字节码里同位还有一枚旧的 `with`，本批一律用 `togetherWith`）。
  参数顺序 `(targetState, modifier, transitionSpec, contentAlignment, label, contentKey, content)`，
  ⇒ 计划里只按名传 `targetState` / `modifier` / `transitionSpec` 加尾随 lambda，不依赖位置。
- **`content` 是 `@Composable AnimatedContentScope.(targetState: S) -> Unit`**：
  lambda 的入参就是**这一腿自己的状态值**，退场腿拿到的是旧 route。
  ⇒ 链必须读这个入参（渲染腿的状态由传入值决定），不能读外层 val——否则退场腿会被渲染成新屏，
  转场看起来像「只有一屏在淡」。这也是 §3.1 把 payload 冻进 route 的直接原因。
- `AnimatedContentTransitionScope`（`transitionSpec` 的接收者）提供 `initialState` / `targetState`
  （继承自 `Transition.Segment`）与 `slideIntoContainer` / `slideOutOfContainer`；
  本批**不用** `slideIntoContainer`——它按整容器宽度位移，与 §3.3 选定的 ¼ 幅不一致，
  直接用 `slideInHorizontally { it / 4 }` / `slideOutHorizontally { -it / 4 }`（腿本身 `fillMaxSize`，
  `it` 就是全屏宽，¼ 幅口径成立）。

## 3. 决定

### 3.1 挂载权威：`AppOverlayRoute` + `AppOverlayRouteResolver`（判据只搬不译）

- 新建 `ui/AppOverlayRoute.kt`：`sealed interface`，35 支链各对应一枚 route；链末的 tab 宿主用 `null` 表示「无覆盖层」。
- 新建 `ui/AppOverlayRouteResolver.kt`：纯函数 `resolve(flags): AppOverlayRoute?`，
  **判据逐字搬自链、顺序逐字照抄**——「先声明先赢」是语义而不是风格，
  QA3 A1「许可页必须排在关于页之前」那类顺序承诺因此一字不动。
  每枚 route 带一个 `mountIndex`（它在链里的声明序号），tab 宿主 = 序号 35。
- 携带 payload 的分支（`selectedSearchCard` / `selectedRelearnCard` / `selectedLearningRecordCard` /
  `selectedFavoriteCard` / `todayReady` / `selectedFeature` / `selectedFeatureKey` / `readingTarget` / `historyArticle`）
  把值放进 route，而不是让渲染腿回读实时状态。理由见 §1.1：退场腿必须由**冻结的 route 值**渲染。
- 链改成 `AnimatedContent(targetState = route) { r -> when (r) { … } }`；
  分支体内部的次级 `if`（例如词卡详情里 `if (card != null)`、对话框挂载）**原样留在渲染腿**，
  只有链级挂载判据上移。
- `AppScreen` 侧新增一份 `AppMountFlags`（把散在 `rememberSaveable` 里的标志位收成入参），
  每次组合构造一次；不新增状态、不改任何写入点。

**为什么不选另外两条路**：
(a) 在链上「加一个 key 字符串 + 渲染时再 `when (字符串)`」= 同一组判据存在两处（key 计算与链条件），
正是 §1.1 要避免的形状；
(b) 迁 `NavHost` = 重写导航层（35 支链 + 37 枚 `BackHandler` + 状态保持全动），
且 AGENTS.md 要求新子系统先获用户批准——本批不申请。

### 3.2 方向判据：链声明序就是层深，不建第二张表

- 前进/返回由 `mountIndex` 比较得出：新序号 **小于** 旧序号 = 更深 = 前进；大于 = 返回；相等 = 同层。
  链的声明顺序本身就是层级顺序（越靠前越能压住后者），tab 宿主最浅，因此
  「基座 → 子页」「子页 → 更深子页」「子页 → 返回」三种常见腿都落在同一条判据上，不需要独立深度表。
- tab 之间按 `AppTab` 序号（学习 0 / 阅读 1 / AI 助学 2 / 我的 3）定横向：序号增大 = 内容左移、新页从右进。
- 同层互换（当前链上几乎不存在该路径）只做淡入淡出，不假装判得出方向。
- **首次组合不播**（`animateEnterExit` 只作用于状态变化后的腿；挂载初值由 `rememberSaveable` 恢复时同样不播），
  避免冷启动与批 C 的闪屏预热抢同一帧。
- 判据做成纯函数 `ScreenTransitionDirection`，JVM 穷举用例覆盖上述五形。

### 3.3 规格落点：`AppTransition` 是时长/缓动/位移的唯一权威

新建 `ui/theme/AppTransition.kt`，只暴露**具名转场规格**，值全部取 `AppMotion` token：

| 规格（`AppTransition` 上的具名 `val`，标识符按计划 D-3 定稿） | 时长 | 缓动 | 位移 | 透明度 |
|---|---|---|---|---|
| `PageForwardEnter` / `PageBackwardEnter` | `Slow` 300 | `EasingOut` | 入场腿 `+¼w → 0` / `−¼w → 0` | `0 → 1` |
| `PageForwardExit` / `PageBackwardExit` | `Slow` 300 | `EasingIn` | 退场腿 `0 → ∓¼w` | `1 → 0` |
| tab 站复用上面四枚（方向按 `AppTab` 序号，值同规则） | `Slow` 300 | 入 `EasingOut` / 退 `EasingIn` | 同上 | 同上 |
| `CrossfadeEnter` / `CrossfadeExit`（同层互换） | `Slow` 300 | 入 `EasingOut` / 退 `EasingIn` | 无位移 | `0 → 1` / `1 → 0` |
| `DialogEnter` / `DialogExit` | `Normal` 220 | 入 `EasingOut` / 退 `EasingIn` | 无位移 | `fade` + `scale 0.92`（逐字保持今天形态） |

- **位移取 ¼ 屏宽而不是整幅**：300ms 走完一整屏会读成「甩」，¼ 幅既保住方向感又不让人等；
  与 Material 轴向转场的 ⅛–¼ 惯例一致。这是观感参数，真机目视后要调也只调 `AppTransition` 一处常数。
- 对话框「并表但不变形」：`GlassOverlay` 那四枚 `tween(AppMotion.Normal, …)` 搬进表里，
  可见形态（淡入 + 92% 缩放，无水平位移）逐字不动。浮层不是页面，给它加位移会误导层级。
- 两站之外**不留 per-screen 逃生口**：不提供「某屏自己传 spec」的参数。允许逐屏自定义 = 长出第二份规格，
  与 §3.1 同一个错误。

### 3.4 无障碍与降级

- 退场腿套 `Modifier.semantics { invisibleToUser() }`（形状按 §2 实测），使 300ms 里 TalkBack 不会同时读到两屏；
  入场腿语义零改动，「进屏即朗读」的时机与今天一致。
- 站 2 在 `contentPadding` 内容盒内，**导航条不参与位移**；站 1 是整窗替换，
  所以底栏会随旧屏一起滑走——这是本批唯一明示的可见行为变化，取舍见 §3.4.1。
- `animator_duration_scale=0` 下两腿首帧即落位、无残影（批 A §2.2 实测平台行为），
  按批 C 口径出定格截图；不新增运行时开关，也不新增应用内动效设置行（批 A §3.1）。
- 顶档（字号 3.0）与批 B 三档窗口类别不受影响：位移只走 `graphicsLayer`，不改测量与布局，
  QA3 A2.x 那批版面判据在 settle 后应当逐字复现（作为回归用例钉住）。

#### 3.4.1 底栏跟屏滑走（已明示，用户判）

今天打开子页底栏**立刻消失**；转场后它是旧屏的一部分，随旧屏向左滑出。
想让底栏钉住，必须把 `Scaffold` 上提到链外、把 35 支子页塞进 `contentPadding`，
等于重排全部子页版面（含各自的内边距与骨架形状），不在本批范围。
本批按「整窗滑」实现；若用户目视后判定不可接受，退路是 D-1 只做站 2（tab 切换），站 1 另立一批。

### 3.5 明确不做

`NavHost` / predictive back 迁移；底栏钉住版重排；逐屏自定义转场；应用内动效开关；
把 35 支链拆成多文件（本批只搬判据，不改文件结构）；`when (selectedTab)` 的分支内容任何调整。

## 4. 风险与兜底

1. **转场期间不得多出一次计费出站**（本批最贵的一条）。
   推理是：入场腿今天就立即组合，`LaunchedEffect` 不会因此多跑；退场腿是同一 composition 的延续而非重建。
   但这是推理，不是证据 ⇒ D-3 用 fake provider 钉一枚「从阅读基座进入文章生成页全程出站次数 == 1」，
   并且批 C-4c 已整改的 CTA 重复点击腿同批复跑。
2. **退场腿回读实时状态的已知伪影**：route 的 payload 已冻结，但分支体里 `collectAsState()` 的派生状态仍是活的，
   退场那 300ms 里旧屏内容可能已经变化（例如返回列表被清空）。
   处置：不掩盖、不作为缺陷修——配合 §3.4 的 `invisibleToUser()` 与 300ms 时长，决策上记为已知取舍；
   若真机目视认为刺眼，改法是给退场腿传快照，成本另计。
3. **`MotionSpecContractTest` 的覆盖面锚点：本批实测后把预判改窄（原文预判会红，实测不会）**。
   剥注释后含 `tween(` 的宿主是 **7 枚**（命令：
   `grep -rnE "tween\(" app/src/main/java/com/example/englishlearning/ui --include=*.kt | grep -vE ":[0-9]+: *(//|\*|/\*)" | awk -F: '{print $1}' | sort | uniq -c`
   → SpeechSettingsScreen ×2 / WordCardViewModel ×1 / AppSwitch ×3 / PlanBar ×2 / SegmentedControl ×1 /
   GlassButton ×1 / GlassOverlay ×4；`AppMotion.kt` 与 `Shimmer.kt` 只在注释里出现 `tween(`，不算宿主），
   而锚点是 `size >= MIN_TWEEN_HOST_FILES = 6`（同文件 :214）。
   把 `GlassOverlay` 的四枚 `tween(` 搬进 `theme/AppTransition.kt` 后是 **7 → 7**（GlassOverlay 失去、AppTransition 新增），
   断言**不会**变红 ⇒ 真正过期的只有那条失败信息里写死的「六族」名单，以及「锚点还能不能代表覆盖面」这个问题本身。
   处置：D-3 里先把锚点改成按**规格宿主**计数（`theme/AppTransition.kt` 必须恰好一枚规格宿主 + 全仓 `tween(` 宿主下限），
   改锚点的用例**必须在旧实现上先红一次**（红点：新的「AppTransition 必须含 tween」判据），
   不许直接改数字放行，也不许拿「本来就红不了」当理由跳过 RED。
4. **逐帧断言的落点：本仓 Compose 语义测试只在 androidTest，JVM 一枚都没有**（本批实测，命令见 §1.2）。
   判据若写进 JVM 会静默变成「只测了纯函数」，所以两站转场的行为断言一律放 connected，
   用 `composeRule.mainClock.autoAdvance = false` + `advanceTimeBy` 取中间帧；`gfxinfo` 与截图册同理。
   已知风险是本仓**没有任何逐帧驱动先例**（`mainClock` 全仓只在 `SetupScreenScreenshotTest.kt:34` 以注释出现过一次，
   从未真正拨过时钟），批 C-5 又有「`Displayed` 差 6ms 落在噪声内」的先例 ⇒
   若实测中间帧不可控，退路是只断言「切换后同一帧内两屏都在语义树里 + settle 后只剩一屏」，
   不承诺具体毫秒帧；退路必须如实写进报告，不得拿 settle 断言冒充中间帧断言。
   纯函数侧（resolver / direction / `AppTransition` 规格表）仍在 JVM 穷举，两者互补而非替代。
5. **`AppScreenProductionWiringTest` 的接线闸**：该测试强制 AppScreen 每个可选参数都在 `MainActivity` 接线。
   本批不给 AppScreen 加参数（flags 由内部 `remember` 收），若实现中被迫加参数，须同批接线并复核该闸。

## 5. 验证口径（RED 先行）

- **D-1 RED**：`AppOverlayRouteResolverTest`（多真取先声明、9 处 payload 冻结、守卫形 `showReviewQueue && todayReady != null`
  在 ready 缺失时必须落到浅层、A1 的许可/关于顺序）与 `ScreenTransitionDirectionTest`（前进/返回/同层/tab 左右/首帧不播）
  ——先写用例、看它们红在「类型还不存在」，再搬判据。
- **D-2 等价性判据**：把链的 35 枚挂载条件换成 `when (route)` 是**行为等价重构**，本段不许出现任何动画。
  等价性靠两件事证明：① resolver 的 JVM 用例逐支覆盖（35 支各有「这一支赢」的用例 + 相邻支互斥）；
  ② 既有 connected 全量（`AppScreenTest` 等）在本段前后读数一致——任何一屏在重构后打不开，都是判据搬错了，
  不允许用「改测试适配实现」放行。
- **D-3 行为 RED**（androidTest/connected，依据见 §1.2）：`ScreenTransitionInstrumentedTest` 里断言
  「切 tab / 开子页时两屏同时存在过、离场那屏对无障碍不可见、settle 后只剩一屏」，
  以及 §4.1 的出站次数 == 1。改码前必须红，红点必须在「离场腿根本没被组合」这一句上。
- **契约闸**（`ScreenTransitionContractTest`，源码扫描，仓内四条纪律齐备）：
  ① 转场站点数 == 2（链 + tab 内容盒），且两站都消费 `AppTransition` 具名规格；
  ② 链上不得残留挂载级 `if (show…)` / `else if (selected…)` 判据（防有人把条件抄回链上，重演 N-1）；
  ③ `AppMotion.Slow` 只被 `AppTransition` 读取；
  ④ 合成 must-flag（一段把 `tween(AppMotion.Slow)` 写在屏体里的假源码必须被判出）与 must-not-flag 双边、
  路径常量 + 文件数守卫 + 反空转，扫描交给 `countMatches` 自剥注释、本类不二次剥（既有教训）。
- **门禁**：每段跑 `:app:assembleDebug` → `:app:testDebugUnitTest --rerun-tasks` → `:app:compileDebugAndroidTestKotlin`，
  JVM 单测是验收线，detekt/ktlint 存量债不当回归；基线只增不减，现算写进报告。
- **收口（D-4）**：双形态 connected（手机竖屏 / 平板宽窗）、`scale=1` 中间帧 + `scale=0` 定格截图册、
  `gfxinfo` 转场前后各 5 次切换的掉帧对照读数、真机 `bf353dda` 里程碑装机由用户目视 TalkBack 与「跟不跟手」，
  按 `docs/handoff/REPORT-TEMPLATE.md` 写报告、分功能域提交、推送请示。

## 6. 影响文件

新增：`ui/AppOverlayRoute.kt`、`ui/AppOverlayRouteResolver.kt`、`ui/ScreenTransitionDirection.kt`、
`ui/theme/AppTransition.kt`、`ui/AppMountFlags.kt`、对应 JVM 测试、`ScreenTransitionContractTest`、
`app/src/androidTest/…/ui/ScreenTransitionInstrumentedTest.kt`。
改动：`ui/AppScreen.kt`（链 → `AnimatedContent` + `when (route)`，tab 内容盒加一站）、
`ui/components/glass/GlassOverlay.kt`（四枚规格并表，形态不动）、
`ui/MotionSpecContractTest`（重算锚点）、`docs/specs/05`（新增 AC5-07）、
`docs/verification/ac-traceability-matrix.md`（AC5-07 行；同批补上批 C 漏登记的 AC5-06 行）、
`docs/superpowers/plans/2026-11-06-motion-and-adaptive-layout.md`（批 D 展开为 D-1…D-4）。

## 2026-11-09 修正（批 D 整批评审后）

日期：2026-11-09　评审范围：`b075060..1a31534`（收口 HEAD `911ad0b`）　裁定：整批「With fixes」（1 Critical + 6 Important + 5 Minor），
逐条裁定在册 `docs/superpowers/plans/2026-11-06-motion-and-adaptive-layout.md` 的「#### D-4 终评补口」（:1312 起）。
本节只做**口径修正**：上面 §1–§6 是 2026-11-08 当天的决定，历史一字不改，被本节推翻的三处以「§x.y 的某句不成立」的形式点名，
Spec 05 F5-06 的对应措辞已随本节改写（本席零代码；`transitionDepth` / `navigationIdentity` / payload 摘除由同批下一席按本节口径实施）。

### 修正 1（Critical）：声明顺序只是**优先级**，层深是同一张表上的**另一列**——§3.2 的公理不成立

**发现**：§3.2 写「链声明序就是层深，不建第二张深度表」，把两件事当成一件。链的臂序承载的是「先声明先赢」的**优先级**
（z 序 + 返回键先后），而挂载判据带 fall-through 守卫，**更深的那一屏因此常常声明在更后**——序号于是同时背了两个含义，
方向判据就在带守卫的那几支上读反。

**证据**（HEAD `911ad0b` 现算，命令与读数）：

```bash
grep -n "override val mountIndex" app/src/main/java/com/example/englishlearning/ui/AppOverlayRoute.kt
# :49 ReadingHistory=29 / :50 ReadingFeed=30 / :51 ArticleImport=31 / :55 ArticleReading=32
# :27 LearningSetup=7 / :28 LearningFlow=8
grep -n "showReadingHistory\|showReadingFeed\|showArticleImport\|readingTarget != null" \
  app/src/main/java/com/example/englishlearning/ui/AppOverlayRouteResolver.kt
# :45 `showReadingHistory && historyArticle == null` / :46 `showReadingFeed && readingTarget == null && historyArticle == null`
# :47 `showArticleImport` / :48 `readingTarget != null || historyArticle != null` —— 守卫把列表层让给更深的文章层
grep -n "onOpenArticle = { historyArticle = it }" app/src/main/java/com/example/englishlearning/ui/AppScreen.kt   # :1865
```

`AppScreen.kt:1865` 的 `onOpenArticle` 在 `showReadingHistory` 仍为真的时候把 route 从 29 换成 32；
`ScreenTransitionDirection.kt:17`（`mountIndex(to) > mountIndex(from) → Backward`）因此判成返回；
`AppTransition.kt:35` 的 `PageBackwardEnter` 是新屏**从左侧进**。用户点开的是一篇文章，动画说的是「你返回了」。
同理 LearningSetup(7) → LearningFlow(8)、ReadingFeed(30)/ArticleImport(31) → ArticleReading(32)。
这条不是实现跑偏，是「判据只搬不译」（§3.1）的必然后果：搬过来的顺序保住了 z 序与返回键优先级，
也把守卫造成的「后声明反而更深」一起搬进了方向判据。

**裁定**：**不重排链、不改守卫**（顺序就是产品承诺，QA3 A1 的许可/关于顺序依赖它）。改为在同一张 route 表上增设
`transitionDepth: Int`，默认 `= mountIndex`，**只在点名且有点位可核的 from→to 边证据的支上覆写**；
`ScreenTransitionDirectionPolicy.overlay` 改比 `transitionDepth`，`mountIndex` 继续只承担挂载与返回键语义。
配套纪律：每条覆写在 `ScreenTransitionDirectionTest` 里必须有一条**命名边**的方向用例，
`ScreenTransitionContractTest` 钉「覆写清单 == 用例清单」（多一条覆写或少一条用例即红），
并钉「未覆写支的 `transitionDepth` 等于 `mountIndex`、覆写只出现在清单里」。
待覆写边以实测为准（至少 29→32、30→32、31→32、7→8 四对，逐对找到 `:1865` 那样的发起点行号才进表）。
**仍然是同一张表的两列，不是第二张手工表**——§3.2「不建第二张表」的初衷（同一份层级事实不许存在两处）不变，
被推翻的只是「序号本身就是层深」这句。

### 修正 2（Important）：route 值变了 ≠ 用户换了一屏——站 1 必须按**稳定身份**取键

**发现**：route 变体是 data class，payload 里冻的是**实时派生数据**，于是「同一屏的数据变了」被 `AnimatedContent` 读成「换了一屏」。

**证据**：`AppOverlayRoute.kt:31/32` 的 `ReviewQueue(plan)` / `TodayWordList(plan)` 冻的是 `TodayPlanUiState.Ready`
（来源 `AppScreen.kt:397` `val todayReady = todayState as? TodayPlanUiState.Ready`），内含 `newDone` / `dueDone` / 卡面；
词卡类 payload（`:20/22/23/24`）同理。⇒ **每提交一次三档反馈**都造出一枚不等的 route ⇒ 起一轮新转场（同序号 ⇒ Crossfade）
⇒ 该腿重新组合、屏内 `remember` 态清零、首帧效果重跑。截图册旁证已在册：`docs/verification/ui-redesign-2026-10.md:2064-2066`
记的「异步加载的屏在 300ms 里以骨架形态退场」正是这条的可见形状。

**裁定**：给 route 增设 `navigationIdentity`（每支的**稳定身份**，剥掉易变进度字段），站 1 用
`AnimatedContent(contentKey = { it?.navigationIdentity })`，把「用户换了一屏」与「同一屏的数据变了」分开。
**先红后绿**：失败用例 = 挂着 `ReviewQueue` 推一份只改 `newDone` 的 `Ready`，断言转场腿数仍为 1 且屏内 `remember` 态存活。
若实测「同 key 换值」时 Compose 不把新值送进 content lambda，回退方案 = payload 只装身份、渲染腿按现行口径读实时 VM，
并把这一取舍追加在本节下面——**不许悄悄改成「什么都不做」**。

**口径连带**：§3.3 表格里的 `CrossfadeEnter/CrossfadeExit`（同层互换）与 Spec 05 那句「同层互换只做淡入淡出」，
在新口径下只对**身份不同**的同支互换生效；同身份换值是就地更新，不进入任何转场规格。
红线保护：一次数据刷新既不得造出第二条转场腿，也不得重放「组合即发起」的出站——这是 §4.1 的出站红线（Spec 05 F5-06 末条）
与本条的交点，红线本身按原文成立，不作 weakening。

### 修正 3（Important）：冻结义务的**实际范围**——挂载判据 + 该腿确实读到的 payload

**发现**：§3.1 的理由句与 Spec 05 原句「屏渲染只认解析结果携带的值……退场那一屏必须由冻结值渲染」把话说到了渲染半边；
批 D 实际兑现的是**挂载**只认解析结果。

**证据**：站 1 腿体内 `collectAsState()` 实测 20 处
（`grep -n "collectAsState()" app/src/main/java/com/example/englishlearning/ui/AppScreen.kt | awk -F: '$1>=776 && $1<=2100' | wc -l` → 20），
另有 `AppScreen.kt:269`–`510` 在链外统一 collect 的派生值由各支腿直接消费；整批评审读数为 35 支里 27 支从实时 VM 渲染。
最尖锐的一处：`ArticleReading` 腿渲染读 `articleReadingViewModel?.uiState`（`AppScreen.kt:1911-1917`），
而 `route.historyArticle` 没有生产读者，被 `ScreenTransitionContractTest.kt:788` 的
`KNOWN_UNREAD_PAYLOAD_FIELDS = setOf("featureKey", "historyArticle")` 登记成零读者。

**裁定**：本批**不**把 35 支的渲染输入全数搬进 payload（范围与风险都超出批 D，§4.2 的「退场腿回读实时状态」继续按已知伪影记）。
但「冻了却没人读」不接受，二选一：`ArticleReading` 腿的正文改读 `route.historyArticle`，
或把 `historyArticle` 从 payload 摘掉、`fromHistory` 判据由 `readingTarget == null`（`AppScreen.kt:1914`）单独承担并删掉登记项。
冻结义务自此在规格里写实为「**挂载判据 + 已点名携带且被渲染读取的 payload 字段**」，零读者登记项只许变短。
**不弱化**的部分：「禁止在链上再留任何挂载级判据」照旧（契约闸判据 2 钉着），挂载冻结一句也不收回。

### 修正 4（Minor，随批一句）：Wide 侧栏与底栏同进同出

§3.4 与 §3.4.1 只登记了底栏随旧屏滑走。实测 `AppScreen.kt:2104-2115`：`Scaffold` 的 `bottomBar`（`:2107` `AppBottomBar`）
与 `usesSideNavigation` 分支下的 `AppSideNav`（`:2115`）**都在站 1 的 tab 宿主腿之内**，
所以 Wide 档从子页退回 tab 宿主时，侧栏与底栏是同一支退场腿的一部分、同样随旧屏滑出。
Spec 05 line 58 已补一句：这与 §3.4.1 是**同一条**明示的可见行为变化、同一份责任，不是新增的第二个变化，也不算缺陷。

### 与本节有关、但不改口径的两条（留给实施席）

- Spec 05 line 57 的 `invisibleToUser` 是**纯语义属性**（不拦触摸，实测旁证 `docs/verification/ui-redesign-2026-10.md:2070`），
  且当前「离场屏不可见」的 connected 断言只覆盖站 2（`TabLeg` 带状态 tag，`OverlayLeg` 不区分离场腿）——
  覆盖面的补强是 D-4 第 5 条的代码活，本节不动该句规格。
- 矩阵 AC5-07 行的两处超证据措辞（「挂载层唯一性结构断言」「顶档 3.0 字号在动画结束后逐字复现」）由 D-4 第 6 条处理，
  本节不动矩阵。

### 因本节而变陈旧的复述点（实施席逐处核，勿漏）

- `docs/decisions/…-page-transition-routing-and-motion.md` §3.2 标题与正文（:116-122）、§3.3 表格的 crossfade 行（:136）、
  §3.1 的理由句（:103）——**保留为历史**，以本节为准，不重写。
- `docs/superpowers/plans/2026-11-06-motion-and-adaptive-layout.md`：:89「链声明序 = `mountIndex`」、
  :310 与 :561 的 KDoc 草稿「链声明序就是层深，不建第二张深度表」、:382-384 `AppOverlayRoute` KDoc 草稿、
  :567-571 `ScreenTransitionDirectionPolicy` 实现草稿、:640 契约闸判据描述。
- `docs/verification/ac-traceability-matrix.md:82`（AC5-07 行）：「方向由声明序号比较得出」「每枚 route 字段有生产读者」
  「挂载层唯一性结构断言」「顶档 3.0 字号…逐字复现」四处。
- `docs/handoff/2026-11-09-batch-d-page-transition-report.md`：:23「方向由声明序号比较得出」、:31「退场腿读冻结的旧 route」、
  :255「同层互换只淡」、:256「屏渲染只认解析结果携带的冻结值」。
- `app/` 侧 KDoc：`AppOverlayRoute.kt:9-11`「序号即层级…不需要第二张深度表」、`:13-15` 与 `:53`（其中 `:53` 引的
  `AppScreen.kt:1847` 已漂到 `:1914`）、`ScreenTransitionDirection.kt:7-9`、`AppTransition.kt:40` 的「同层互换」注释、
  `AppOverlayRouteResolver.kt:6-9`；测试侧 `ScreenTransitionDirectionTest.kt:8`、`ScreenTransitionContractTest.kt:25`。
