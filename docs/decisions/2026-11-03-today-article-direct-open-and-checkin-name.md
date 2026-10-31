# 学习页「今日文章」直达，打卡屏改名「打卡与连续」

- **日期：** 2026-11-03
- **状态：** 已接受（用户在两处答复里选定：本批范围 = 「IA 收尾两件」；命名口径 = 「学习打卡」→「打卡与连续」）
- **上游：** `docs/design/2026-10-31-information-architecture-audit.md` 方案 C 的第 3、4 条（D-5、D-8）
- **前置：** `docs/decisions/2026-11-02-profile-hub-and-four-tabs.md`（四栏 + 我的 hub 已落地，
  本批只收尾它没碰的两条）

## 背景

盘点表里「结构性重排」剩下的两条都在本批收：

1. **D-5「今日文章」行落到阅读首页，而不是文章**（`AppScreen.kt:1980` 现状
   `onOpenReading = { selectedTab = AppTab.READING }`）。学习页两枚入口——未学完那档的四条裸行
   （`TodayPlanScreen.kt:463-476`，tag `today_plan_reading_status`，标题按状态写
   「今日文章 · 未解锁 / 已读 / 可阅读」）与全完成空态里那枚 `today_plan_read_article`
   （「去读今日文章」）——都只把用户送到阅读 tab，还得再点一次今日文章卡才读到文章。
   标签承诺「文章」，落点是「阅读首页」，多一次点击。
2. **D-8「学习打卡」与「学习记录」两个名字都像记录**。打卡屏（`CheckInScreen.kt:65`
   `title = "学习打卡"`）讲的是连续天数与周/月视图，学习记录屏（`LearningRecordsScreen.kt:86`
   `title = "学习记录"`）讲的是逐条词卡与按日历史，两枚入口都从「我的」hub 出发
   （三格「连续天数」→ 打卡屏；行「学习记录」→ 明细屏），名字撞车。

## 决定

### 1. 落点按真值判，不按 tab 判

两枚入口共用一条判定，且**只此一处**：

- 阅读状态是 `ReadingAccessUiState.Ready` **且** `todayArticle != null` ⇒
  调 `ReadingAccessViewModel.openTodayArticle()`，文章以覆盖层挂在当前 tab 之上，
  **不切 tab**；退出文章（页头返回位或系统返回键，走既有 `closeArticle()` 通路）回到学习页。
- 其余一切状态（`Locked` / `Loading` / `Unavailable` / `Ready` 但今天还没生成文章）⇒
  `selectedTab = AppTab.READING`，由阅读首页自己交代原因：锁定态显示 `unlockReason`，
  已解锁但没文章时显示生成入口。

判定本体落成 `ReadingAccessUiState` 上的一枚**纯扩展属性** `canOpenTodayArticleDirectly: Boolean`
（逐态 `when` 穷举、无副作用，与既有 `historyOrEmpty` 并排住在 `ReadingAccessScreen.kt`），
逐态由 JVM 单测钉住；宿主只做「读真值 → 二选一动作」，
不在学习页与阅读页各写一份 `as? Ready`（那会是第二份权威）。
穷举而不是 `this is Ready && todayArticle != null` 的理由与 `historyOrEmpty` 同一条：
将来给状态机加一档时，编译器逼这里表态，而不是让新状态被默认判成「不直达」。

### 2. 阅读取数扩到学习 tab

现状 `AppScreen.kt:408-418` 的加载触发只在 `selectedTab == READING || PROFILE` 时 `load()`。
停留学习 tab 时 `readingState` 恒为 `Loading` ⇒ 判定永远走「切 tab」那一支，直达就成了假话。
因此把条件加上 `selectedTab == AppTab.LEARNING`。

- **代价（如实记）**：学习 tab 首帧多三次本地 Room 读（阅读偏好、历史、今日文章检索），
  全在 ViewModel 协程里，不进 UI 线程，不阻塞学习页组合。
- **收益**：直达不需要「先访问过阅读页」这种隐性前置；学习页那行的文案与真实可点性同一份真值。
- 我的 hub 第三格「文章」数已经吃这份历史（批 1 Task 6 同一理由），本条只是把同一个前置补齐到学习 tab。

### 3. 命名：只改撞车的那一枚

- `CheckInScreen` 页名 **「学习打卡」→「打卡与连续」**：屏内讲的就是连续天数与周/月视图，
  带「连续」二字就不会和「记录」混。
- **「学习记录」保持原名**（它就是逐条完成事件与按日历史，语义准确，改名反而丢搜索线索）。
- hub 三格入口名 **「连续天数」不动**：那是数字格，改名会与页头「打卡与连续」脱钩成两套说法。
- **「阅读历史」不动**：与上面两枚不构成混淆，盘点表也只把它列作低优先。

### 4. 明确不做

- AI 助学页的配置入口：**本批仍不加**（用户 2026-11-02 裁定「暂不加」，盘点表方案 C 第 2 条继续挂账）。
- 学习、阅读两个一级 tab 的内容版式；解锁规则与 FSRS；文章生成本身；任何新增采集或网络调用。

## 为什么是需求变化，不是放宽

- 判据从「点行 = 切 tab」改成「点行 = 按真值直达或切 tab」，是**新增一条更严的落点要求**：
  JVM 逐态钉判定，connected 钉两条端到端路径（已解锁直达文章、未解锁落阅读首页并见原因）。
- 改名同步修改 connected 断言的期望串，而不是删断言：`CheckInScreenTest` 继续按文字定位页名。
- 既有「学习页只读宿主传入真值、不自己读仓库」的分工不变（`TodayPlanNumberAuthorityTest` 继续承重）。

## 影响

- 代码：`ReadingAccessScreen.kt`（判定属性与状态机同住）、`AppScreen.kt`（学习页两枚入口共用的
  lambda + 加载触发条件）、`CheckInScreen.kt`（页名）。`TodayPlanScreen.kt` **无行为改动**，
  只给 `onOpenReading` 补一句「落点由宿主按真值判」的 KDoc：入口文案与 tag 都不动，落点差异收在宿主一处。
- 测试：新增 `TodayArticleLandingTest`（JVM 逐态 4 枚 + 接线形状 3 枚）+ `AppScreenTest` 四条 connected
  （直达、空态 CTA 直达、锁定落首页、Ready 无文章落首页）；阅读夹具加一枚 `withTodayArticle` 开关；
  改 `CheckInScreenTest`（2 处文字锚）与 `CheckInScreenHeaderShapeContractTest`
  （4 处样本串 + 一枚跨文件旧名归零闸）；
  `docs/verification/` 与 handoff 里的旧页名描述按新名追加说明，不覆写历史记录。
- 文档：本决策 + 批计划 `docs/superpowers/plans/2026-11-03-ia-direct-article-open-and-naming.md`。

## 风险

1. **直达后返回落点**：文章覆盖层退出必须回到学习页并清掉 `readingTarget`，否则回到学习页却顶着一份
   已消费的文章状态。既有 `closeArticle()` 是唯一出口（`AppScreen.kt:651`），connected 补断言。
   **实施时实测到的形状（2026-11-03 connected）**：`ArticleReadingScreen.kt:200-202` 的 F3-01B
   覆盖词面板在文章首进时自动展开，它是 `ModalBottomSheet` 独立窗，**系统返回先收面板、收不到文章**。
   所以直达后的真实退法是「返回一次收面板 → 再返回一次才回学习页」；connected 用例显式点
   `coverage_popup_close` 后只留一条返回腿给文章，避免把断言写成面板的返回优先级。
   这条不是本批引入的：从阅读 tab 打开文章本来就是同一形状，只是直达把它带到学习页出口上，
   用户如果嫌两次返回麻烦，是 F3-01B 面板策略的事，另开账。
2. **学习 tab 提前取数**：若某处隐含假设「没进过阅读页时 `readingState` 是 `Loading`」，扩触发会暴露它；
   实施时以实跑门控为准，不靠推断放行。
3. **改名辐射面**：页名串散在 connected 用例、契约样本与既有报告里。只改代码与本批文档，
   历史报告作为当时事实保留。
