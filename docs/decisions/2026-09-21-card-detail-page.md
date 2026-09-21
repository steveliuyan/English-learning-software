# 词卡详情页：设置持久化、详情态归属与说明图方案

- 日期：2026-09-21
- 状态：已接受（实现未开始）
- 相关规格：`docs/specs/01-vocabulary-learning-and-review.md` F1-06、AC1-08~AC1-13
- 相关决策：`2026-09-19-placeholder-word-card-content.md`（占位词内容与许可）、`2026-09-20-closed-database-contract.md`（仓储错误契约）

## 背景

需求：背词时对「模糊」「忘记了」这类没掌握的单词，用户希望能打开当前正在背的这张卡的详情页，看到更完整的解释，并用一张说明图把词义讲清楚。

约束（调研事实）：

- 项目**没有任何应用级设置存储**：无 DataStore、无 SharedPreferences，Room `AppDatabase` 为 version 5、共 10 张表，无 settings 类表。
- 项目**没有位图解码能力与图片库**，`res/` 资源均以矢量方式通过 `painterResource` 使用（`ui/AppScreen.kt:155-159`）。
- `WordCard`（`learning/domain/WordCard.kt:9-18`）字段为：`cardId`、`wordBookId`、`lemma`、`ipa`、`partOfSpeech`、`meaningZh`、`example?`、`inflections`。**无图片、无词根/记忆法、无文章上下文**字段。
- 学习流程由 `ui/AppScreen.kt:93-143` 的 `showSetup` / `showLearning` 两个 `rememberSaveable` 布尔量构成状态机；词卡装配在 `:127-132`。
- 反馈提交在 `ui/WordCardViewModel.kt:83-118`，写成功后在 `:110-113` 把 cardId 计入 `completed` 并 emit；推进/终态 emit 在 `:147-162`。
- 词汇事件表为不可变追加：详情页的进出不得产生学习事件。

## 候选方案

### 1. 设置持久化

1. **Room 新表 `learning_settings`（profileId 主键 + 3 个布尔列），`AppDatabase` 升 version 6 + `MIGRATION_5_6` + 导出 `app/schemas/.../6.json`（采用）**
2. DataStore Preferences
3. SharedPreferences

### 2. 详情态归属

1. **`WordCardViewModel` 暴露详情态，`AppScreen` 在 `showLearning` 分支内叠加渲染（采用）**
2. 把详情页提升为顶层一等路由，与 `showSetup` / `showLearning` 并列

### 3. 说明图资源

1. **`res/drawable` 矢量资源 + `painterResource`，lemma → drawable 封闭映射放在 UI 层纯函数（采用；资源形态于 2026-09-22 修订为彩色卡通位图）**
2. `assets/` 内置位图 + `BitmapFactory` 运行时解码
3. 引入 Coil 等图片库

## 决定

### 设置持久化：Room 新表（方案 1）

- 新表 `learning_settings`：`profileId` 为主键，三列布尔对应「认识」「模糊」「忘记了」是否在提交后打开详情页。以 profileId 为主键天然满足 AC1-13「不同 profile 互不影响」。
- 需同时落 `MIGRATION_5_6` 与 `app/schemas/.../6.json`，并补 v5→v6 迁移测试（既有数据保留）。
- **否决 DataStore**：为 3 个布尔引入新依赖要付依赖版本管理、`gradle.lockfile` 全量重写、第三方 NOTICE 台账三笔成本，而 Room 已有的 schema 导出、迁移与测试基建可零成本复用。
- **否决 SharedPreferences**：无类型化、无迁移机制，且与「Room 为唯一本地持久层」的既有约定冲突。
- 默认值在迁移时以列默认值落地（模糊 / 忘记了 = 1，认识 = 0），不依赖运行时兜底分支，避免"读不到设置就走别的路径"的隐式分歧。

### 详情态归属：留在 `WordCardViewModel`（方案 1）

- 详情页是学习流程内的**附加视图**，不改变"当前在学哪张卡"这一状态，因此不进入顶层路由状态机。`AppScreen` 顶层 `showSetup` / `showLearning` 布尔逻辑（`AppScreen.kt:93-94`、`:115-143`）保持不变，只在 `showLearning` 分支内加一层渲染。
- **否决提升为顶层路由**：那会触碰 `showLearning` 的两处退出路径及其 `load()` 重算逻辑（`AppScreen.kt:109-112`），而这一带正是 2026-09-20「从词卡返回今日计划不重算完成度」事故的发生点。单测全绿、只在真机暴露，代价高。
- 详情态用可空 `StateFlow` 表达：非空即展示，返回时置空。返回详情后必须回到同一张卡，且此时完成度已按提交结果重算。

### 触发时序：落在 `submit` 成功分支

- 位置：`WordCardViewModel.kt:110-113` 写入成功之后，读三开关 → 按该档决定是否 emit 详情态。
- **否决在 `FeedbackButton.onClick`（`WordCardScreen.kt:199-226`）里判断**：UI 会先于写库结果决策，写失败时会出现"已经进去了但没提交"的假象，破坏"报成功 ⇒ 数据可读回"的不变量表述。
- 写失败分支不打开详情页，只按既有错误路径返回。
- 一次提交仍只有一次推进/终态 emit（`:147-162`）；详情态是叠加在其后的纯查看动作，**不写入学习事件表**。

### 说明图：`res/drawable` + `painterResource`（方案 1）

- lemma → drawable 的映射为 UI 层纯函数，无对应资源时返回 null → 隐藏图片区域。
- 零解码代码、零新依赖，离线天然可用，与项目既有矢量资源用法一致。
- **否决 `assets` + `BitmapFactory`**：需要自建解码、采样与内存管理，V1 属过度设计。
- **否决引入图片库**：新依赖 + 锁文件 + 台账成本，收益为零。

> **2026-09-22 修订（资源形态）**：以上「机制」判断保持不变——映射仍是 UI 层纯函数、无匹配返回 null 即隐藏、零新依赖、零自写解码代码、不引图片库。被修订的只有**资源形态**：单色薄荷绿矢量图真机查看后读起来像图标、看不出词义，已替换为 **AI 生成的彩色卡通插画**，落 `res/drawable-nodpi/illus_<lemma>.webp`（512×512 无损 WebP，共 12 张，约 1.14 MB）。位图同样经 `painterResource` 渲染（`minSdk` 26 原生支持 WebP），因此「无位图解码能力」这一调研事实并不构成阻碍，「否决 `assets` + `BitmapFactory`」与「否决图片库」的结论也不受影响。原因、流水线与质量评估见 `2026-09-22-card-illustration-assets.md`。

### 内容模块缺失：整体隐藏

- 任一模块无数据时整块不渲染：不留空标题、不显示"暂无"。
- **V1 实际可见模块**为 `WordCard` 已有字段 + 说明图：中文释义与词性、音标、例句、词形变化、说明图。
- 「词根 / 记忆法」与「文章上下文例句」在 V1 **无数据源**（前者 `WordCard` 无字段，后者依赖阶段 2 的文章数据），按上述规则自然隐藏。**V1 不为它们新增领域字段**——若后期要真正提供词根/记忆法，需单独决策（扩展 `WordCard` 或扩充占位词源），不夹带进本切片。
- 说明图当前只可能覆盖内置占位词（12 自撰词 × 6 书），真实词书词条与配套图片的许可尚未闭合，配图资源的规模化生产不在本切片。

## 实现边界（分切片）

1. **切片 1 — 设置持久化**：`learning_settings` 表 + `MIGRATION_5_6` + `schema/6.json` + DAO/仓储/UseCase + v5→v6 迁移测试。仓储沿用 `CancellationException` 行为判别式（见 `2026-09-20-closed-database-contract.md`）。
2. **切片 2 — 详情页静态版**：`CardDetailScreen` + `res/drawable` 说明图目录 + lemma → drawable 纯函数；模块为空即不渲染。
3. **切片 3 — 状态与接线**：`WordCardViewModel` 详情态 + `submit` 成功分支时序；`AppScreen` 在 `showLearning` 分支接线；设置页加三个开关。
4. **切片 4 — 收口**：独立质量审查 + 真机流程验收（截图取证）+ 文档同步。

不改动：学习事件模型、FSRS 调度、解锁判定、`showSetup`/`showLearning` 顶层状态机语义。

## 验收证据

实现尚未开始，本文件**不填写任何验收证据**。分层证据将在切片 4 落盘：

- JVM：设置读写、默认值、迁移、模块隐藏规则。
- 真机：AC1-09/AC1-10/AC1-11 的完整流程截图（提交 → 详情页 → 返回 → 完成度一致）。
- 引用日志时只引用日志中真实存在的文本（文件名不可作为结论依据）。

## 未纳入

- 详情页模块的逐项配置开关（后期设置项，V1 全部提供）。
- 发音按钮与实际音频（阶段 3）。
- 文章上下文例句（阶段 2 数据到位后自然解锁）。
- 词根 / 记忆法数据字段扩展（需单独决策）。
- 真实词书词条与配套说明图的许可闭合（外部依赖，见 `2026-09-19-placeholder-word-card-content.md`）。
