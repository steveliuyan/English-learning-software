# 决策：新词取词顺序改为确定性交错序 + 词书选取页渐进发布（2026-10-17）

状态：已批准（用户 2026-10-17 指示「背完 A 才能到 B 不合理」，要求先调研公认有效的背词方法，再按「确定性乱序 + 相似词隔离」落地，并排在 UI 重设计轮之前）。

## 背景

真机使用中暴露两个独立缺陷，都由阶段 1 的实现忠实执行了当时的 Spec 而产生：

**缺陷一：新词严格按词书原序推进。** `StoredPlanCardSource.newCardIds` 取的是
`content.cardIds(wordBookId).filterNot(reviewed).take(limit)`，而内置词书包（`cet4/book.json` 等）本身就是
按 `rank` 排的**字母序**（`cet4:a`、`cet4:abandon`、`cet4:ability`…）。结果是一本字母序单词表：
用户必须先啃完整本 A 才见得到 B。认知机制上这是最不利于长期留存的呈现方式，理由见「依据」。

**缺陷二：词书选取页首帧空白后整列突然蹦出。** `LearningSetupUiState` 没有加载态字段，
`wordBooks` 默认 `emptyList()`；`LearningSetupViewModel.load()` 把「种子登记 → 索引收敛 → 读册列表 →
逐册解析词书包算进度 → 读设置」串成一条链，**只在链尾写一次 state**。其中逐册进度要对每册调
`cards.cardIds(book.id)`，`WordBookPackageCache` 冷缓存时会解析整册 JSON（四级 4308 卡、六级 4634 卡），
所以冷启动后第一次进该屏必然「卡一下、什么都没有、然后全部同时出现」。

两者都不涉及数据正确性：今日计划的不可变快照语义、三档反馈、FSRS 排程、完成与解锁判定全部保持不变。

## 依据（公开研究与工具现状）

1. **检索练习与间隔重复是有效性的主因，我们已具备。** 真实课堂对照实验里，成绩与长期记忆的排序都是
   「分散检索 > 集中检索 > 分散重复 > 集中重复」；等距间隔在 2 天后的留存优于逐步拉长的扩展间隔，
   关键是让第一次检索推迟到略带难度时。本应用的三档「不认识/模糊/认识」强制回忆 + FSRS 排程正好落在这两条上。
2. **交错呈现优于分块呈现，且学习者主观偏好恰好相反。** 分块练习期感觉更顺、自信更高，长期留存更差
   （Kornell & Bjork 2008）；Brunmair & Richter (2019, *Psychological Bulletin* 145(11) 1029–1052) 的元分析
   进一步发现**项目之间越相似，交错收益越明显**。字母序是交错的反面，且把形近义近词（abandon/ability/able/about）
   连排，主动制造前摄干扰——这正是用户抱怨的根源。
3. **词频优先是公认第四条，但本轮不可用。** 高频 1–3 个 1000 词族覆盖一般文本 75–92%，最常用 2000 词族
   约 84% 认读覆盖（Hwang & Nation 1995；Nation 的分类口径）；词频效应本身可测（Forster & Chambers 1973：
   高频词提取快约 71ms）。国内「乱序版」词汇书实际多数是**按词频重排**，乱序只是副作用。
   但我们的词书数据**没有任何词频字段**（卡面字段仅 `cardId/lemma/rank/ipa/senses/example/exampleZh/derived/phrases/synonyms`），
   而可用的频率表有许可障碍：wordfrequency.info 的 COCA/BNC 表前 5000 免费、20000 词学术授权 \$135／商业 \$225，
   网上流传的完整 PDF 属泄露且标注「仅供学习研究、勿用于商业」。按 AGENTS.md
   「未通过许可核验的词书、词典、音频或模型不得打包、下载或发布」，引入频率表必须单独立一批走
   `docs/decisions/` + 第三方台账核验，不能塞进本批。
4. **顺序变更的风险面很小。** 今日计划是生成瞬间的不可变快照（F1-02），历史计划与其任务不被改写；
   复习卡的先后由 FSRS 的 `nextReviewAt` 决定，与本条无关；进度口径是「已评审卡片集合」而非位置
   （`WordBookProgress.calculate`），因此重排取词顺序不会让任何已学词变成未学。

## 候选方案

- **A（批准）：确定性交错序。** 以词条**前 3 字母**为相似性单位分组，组内按 `cardId` 的 64 位 FNV-1a 哈希排序，
  再用「剩余最多的组先出、同组必须冷却 `D-1` 个位置」的确定性贪心展开，其中 `D = floor(n / M)`
  （n=册内词条数，M=最大前缀组大小）。性质：**任意两个同前缀词条的位置差 ≥ D**，等价于任意连续 D 张卡前缀互不相同。
  顺序由词书内容唯一决定，与传入顺序无关、无随机种子、无 per-user 状态，跨进程与跨设备逐位可复现。
  可行性来自任务调度下界 `(M−1)·D + c ≤ n`（c=大小为 M 的组数，且 `c·M ≤ n`）。十本内置册逐册实测
  `n/M→D=floor(n/M)`：四级 4308/84→51、六级 4634/95→48、考研 4815/102→47、研究生 4581/97→47、
  雅思 5215/96→54、托福 7438/147→50、博士 8537/192→44、高中 3214/53→60、初中 1732/15→115、小学 816/8→102，
  全部远未触及死锁分支（`NewWordOrderingTest` 逐册解析断言）。
  选前 3 字母而不是前 2：前 2 字母最大组达 196–248 词（D 只有 17–24，且 a-/ad-/ag- 这类同首两字母的词干扰本就更弱），
  前 4 字母最大组虽只有 27–38 词，但 2600+ 个组会把算法退化成"几乎随机"而丢掉"同族词被强制拉开"这条硬保证。
- **B（否决）：纯随机乱序。** 需要持久化种子才能复现，且无法保证相似词隔离——随机序列里 abandon 与 ability
  仍可能相邻，收益只吃到交错的一半。
- **C（否决，本轮）：词频优先 + 乱序。** 研究上最完整，但依赖外部频率表与许可核验（见「依据 3」）。
  留作独立一批。
- **D（否决）：按单元推进（每 100 词为一单元，跨单元交错）。** 保留了"册内进度感"，但单元本身仍是字母序切块，
  A 单元啃完才进 B 单元的问题只推迟、没消除。
- **E（否决）：加设置开关让用户切回字母序。** 见下面「明确不做」。

## 决定

1. **新词取词顺序 = 方案 A。** 落为纯函数 `learning.NewWordOrdering.order(cardIds)`，
   `StoredPlanCardSource.newCardIds` 改为「先对整册求交错序，再剔除已学、截断到 `dailyNewTarget`」。
   这样得到的顺序是完整交错序的**子序列**：剔除任意已学词不改变其余词的相对次序，用户不会看到
   "同一个词今天在第 3 位、明天跳到第 40 位"。
2. **词书选取页 = 渐进发布。** `LearningSetupUiState` 增加 `wordBooksLoading` 与 `progressLoading`；
   `load()` 进入即同步置为加载中（首帧就有占位，不再是空白）；册列表／当前目标／设置读到位即先发布一次
   带列表的 state；随后**逐册**回填进度（每算完一册写一次 state）；索引收敛保持在其后。
   UI 在 `wordBooksLoading` 时显示占位行，进度未到位的册显示"进度计算中"而不是整屏空。
3. **复习链路零改动**：`dueCardIds` 不动，`SubmitCardFeedbackUseCase`、`UnlockPolicy`、
   `GetOrCreateTodayPlanUseCase` 的快照语义不动，Room schema 不动。

## 明确不做（YAGNI 裁决）

- **不加「切回字母序」设置开关，因此不做 Room 迁移。** 字母序的真实需求是"当索引查"，
  而 F1-07「全部单词」已经是按词书原序列出全部词，索引需求已被现有页面满足；把它做成学习顺序开关
  要多付一列 schema + 一次迁移 + 备份实体字段 + 一套双路径测试。代价：若确有用户想按字母序推进学习，
  将来是纯增量改动（加列有 v27／v32 先例）。
- **不做词频分层、不引入外部频率表。** 代价：高频词不会被优先安排；本批只消除"字母序死啃"，
  不承诺"先学最有用的词"。
- **相似词隔离只按前 3 字母判定。** 已知漏网：`adapt`/`adept`、`effect`/`affect` 这类前缀不同而形近或义近的词
  不保证被隔开。做全两两编辑距离检查在 4600 卡上是 O(n²) 且需要词典支撑，V1 不做。
- **3 字母前缀对短词条不成立，且这个漏网在内置词书上真实存在。** `take(3)` 使长度 <3 的词条用**整串**做前缀，
  于是 `go`（前缀 `go`）与 `goal`（前缀 `goa`）、`be` 与 `begin`、`in` 与 `inner` 被当成不同组，不保证隔开。
  逐册实测（解析 `book.json` 的 `cardId`，取分隔符后词条）：十本内置册各含 6–27 个长度 <3 的词条
  （cet4 23 个：`a` `as` `at` `be` `by` `do` `go` `he`…；toefl 最少 6 个：`as` `at` `go` `in` `ox` `so`；
  junior-high 25 个、senior-high 27 个最多），且这些短词全是最高频的虚词，被包含的长词
  （`go`/`goal`、`be`/`because`、`in`/`interesting`）在每册都有多个。
  反过来看代价面：短词各自成组，只会让 `M` 偏小、`D` 偏大，即间距保证更宽松，不会算漏成假承诺——
  文档与测试里的「同前缀至少隔 D」始终按同一口径成立，破的只是「形近义词都拉开」这个产品意图。
  不改的理由：改成「按较短串判定包含关系」要把分组换成前缀树，`floor(n/M)` 的可证明性需整段重做，
  而这一类相邻干扰远弱于 abandon/ability/able 那种同族长词串烧；V1 记录为已知局限。

## 影响

- Spec：`docs/specs/01-vocabulary-learning-and-review.md` F1-01（首帧与渐进发布）、F1-02（交错序），
  新增 AC1-23～AC1-27。
- 测试：`NewWordOrderingTest`（置换性/确定性/反字母序/前缀间距/子序列稳定性/截断与短册退化）、
  `StoredPlanCardSourceTest`（原按字母序断言的用例改为断言交错序的子序列）、
  `LearningSetupViewModelTest`（首帧加载态、列表先于进度、逐册回填、失败文案）。
- **既有 3 条 `StoredPlanCardSourceTest` 用例的精确顺序断言必须改写**——它们锁的是旧契约，不是回归。
- 门禁：JVM 单测基线只增不减；`verifyAcTraceabilityMatrix` 需要同批补 AC1-23～AC1-27 证据行。
- 真机/QA：本批属"可手工感知"的改动，装机后由用户试用确认顺序观感与词书页首帧；
  交错序对 TalkBack 无新增语义要求（卡片内容与朗读链路未变）。
- 设备取证缺口：交错序的间距性质在真机演示数据上未被自动化覆盖，属 JVM 层保证；
  QA 第三轮可加一条"连续两天新词不出现同前缀扎堆"的手工观察。

## 证据链接

- [间隔效应和检索效应对初中英语学习者词汇学习的影响（真实课堂四组对照）](https://mall.cnki.net/magazine/Article/CMFD/1021660727.htm)
- [Expanding retrieval practice promotes short-term retention, but equally spaced retrieval enhances long-term retention](https://xueshu.baidu.com/usercenter/paper/show?paperid=e9eff1fcaf132ffe803e8ad38c4d191a&site=xueshu_se)
- [Robert Bjork: A Teacher's Guide to Desirable Difficulties（interleaving 感觉更难但留存更好）](https://www.structural-learning.com/post/robert-bjork-teachers-guide-desirable)
- [Exploring Whether Making Second-Language Vocabulary Learning Effortful…（含 Brunmair & Richter 2019 交错元分析）](https://www.mdpi.com/2076-328X/15/5/692)
- [Mixing Grammar Exercises Facilitates Long-Term Retention: Effects of Blocking, Interleaving and Increasing Practice](https://www.researchgate.net/publication/333385481_Mixing_Grammar_Exercises_Facilitates_Long-Term_Retention_Effects_of_Blocking_Interleaving_and_Increasing_Practice)
- [3.1 词频效应（Forster & Chambers 1973：高频词提取快约 71ms）](https://www.daowen.com/book/content/239823826/8797263.html)
- [词表学习中频次和间隔效应研究](https://m.zhangqiaokeyan.com/academic-journal-cn_detail_thesis/02012163295141.html)
- [为什么推荐按词频来记忆单词？（字母序 / 随机序 / 词频序对比）](https://mbd.baidu.com/newspage/data/dtlandingsuper?from=2001k&nid=dt_4734543962460168951)
- [wordfrequency.info 词频表购买页（COCA/BNC：20000 词学术 \$135／商业 \$225）](https://www.wordfrequency.info/purchase.asp)
- [两个 COCA 词频文件比较（前 5000 免费，其余多为泄露，禁止商用）](https://www.cnblogs.com/hhdom/p/18346277)
