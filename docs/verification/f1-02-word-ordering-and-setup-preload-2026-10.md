# F1-02 新词交错顺序 + F1-01 词书选取页渐进发布验收记录（2026-10）

分支 `stage-1-f1-02-new-word-ordering`，提交范围 `96e868a..2fe4c16`（基线 main `045c316`）。
依据：`docs/specs/01-vocabulary-learning-and-review.md` F1-01／F1-02、
`docs/decisions/2026-10-17-new-word-ordering-and-setup-preload.md`、
`docs/superpowers/plans/2026-10-17-word-ordering-and-setup-preload.md`。

## 0. 结论口径

- 本批解决两件事：**新词不再按词书字母序死啃**（A 组没背完见不到 B），以及
  **打开「选取词书」不再「卡一下然后整列蹦出来」**。
- 全部判定为 JVM 自动化 + 源码契约测试；**首帧观感与真机手感未取证**，见 §5 的 TODO-USER。
- 未新增账户、云同步、订阅、广告、远程代码执行或任意 AI 覆盖能力；未做 Room 迁移
  （不加「切回字母序」开关，见决策「明确不做」）。

## 1. 交错顺序（AC1-23～AC1-25）

契约：`NewWordOrdering.order(cardIds)` 是**词书内容的纯函数**——同前 3 字母的词条位置差
≥ `floor(n / M)`（n=册内词条数，M=最大前缀组），与传入顺序无关、无随机种子、无 per-user 状态；
剔除已学词后剩余词相对次序不变（是完整顺序的子序列）。

- 组内按 `cardId` 的 64 位 FNV-1a 哈希排序，平局再按字典序；展开用「剩余最多的组先出、
  同组冷却 `D-1` 个位置」的确定性贪心。
- 十本内置册实测 `n/M→D`：4308/84→51、4634/95→48、4815/102→47、4581/97→47、5215/96→54、
  7438/147→50、8537/192→44、3214/53→60、1732/15→115、816/8→102（`NewWordOrderingTest#bundled books
  are reordered and keep their spacing guarantee` 逐册解析 `book.json` 断言，不是抽样）。
- **哈希基准已钉死为契约**：`the frozen hash basis pins the sample book path` 写死样例册的 28 元素输出路径。
  改基准值、改前缀截断规则、改平局判序三者任一变动都会让它变红。判别力经变异复验：
  把 `FNV_OFFSET_BASIS` 改一 bit 后**只有**该用例失败（间距/置换类用例按设计不受哈希影响）。
  含义：任何"顺手整理一下哈希"的改动 = 重写所有用户在一册里的剩余路径，必须先写决策记录。

已知局限（决策文档同步登记）：

- 相似性只按前 3 字母判定：`adapt`/`adept`、`effect`/`affect` 不保证隔开。
- **短词条（<3 字母）用整串做前缀**，于是 `go`/`goal`、`be`/`begin`、`in`/`inner` 被当成不同组、
  不保证隔开。这不是理论边角：逐册解析 `book.json` 实测十本内置册各含 6–27 个长度 <3 的词条
  （toefl 6 个、cet4 23 个、senior-high 27 个），且都是最高频虚词。
  反向看，短词各自成组只会让 `M` 偏小、`D` 偏大，即保证更宽松，不会把已成立的承诺算漏。
- 贪心的 `ready` 空档回退（`?: cooling.poll()`）保置换与终止、**让步间距**；当前十册均不触发
  （可行性下界 `(M−1)·D + c ≤ n` 全部远离），代码注释已写明这一代价。

## 2. 词书选取页渐进发布（AC1-26～AC1-27）

- `load()` 后**一帧都不推进**也必须已处于加载态；册列表先发布、进度逐册回填，
  回填期界面显示「进度计算中」，单册读不出来显示「暂时读不到进度」，算出来才显示「已学 N」。
- 三态分开的必要性由 `progressLoading` 承担：「整页还在回填」与「这一本读不出来」都对应
  `progress == null`，合成一态就是说谎式降级。
- 册自带的「共 N 词」绑 `book.totalWords`、**与进度解耦**（终审批 I-1）：进度计算中的册
  不得连词量都念不出来。
- 无进度时**不画进度条、不报「未学」**（与 `TodayPlanScreenTest`、`TodayWorkloadBarTest`
  钉住的「无数据连条子一起不画」同口径）：一条 0% 实心轨道等于把「不知道」说成「一点没学」。
- 重进该页保留已算出的进度（只保留仍在列表里的册），回填途中不把进度表抹空；
  某册这次读不出来则从表里摘掉，旧数字不得替「现在读不到」说话。
- 过期加载任务零落笔：`loadJob?.cancel()` 挡不住真实盘上不可中断的拷贝，
  因此整份发布前、逐册发布前、`finally` 收尾处各有一道显式 `coroutineContext.isActive` 检查，
  三处各有用例钉住（`LearningSetupViewModelTest` 的三条 stale/superseded 用例）。

## 3. 契约测试匹配面的局限（承 F5-03 债 M-B1／M-B2）

`WordBookCardProgressContractTest` 与 F5-03 的静态禁令契约同形状：剥注释后按**精确书写形状**
匹配源码，先断锚点存在再判 offender。已知局限照旧登记：

- 行注释剥离正则会把字符串字面量里的 `//` 之后整行抹掉；块注释正则可能被字面量里的
  星号紧跟斜杠那对字符吞掉一段真实代码。
- 规则锁的是形状：换空格、换变量名会触发契约。重排版时要**有意识地一起改**，
  而不是事后无声放宽规则。
- 扫描目录/文件缺失时立即失败（`scanUiSources` 断言目录存在且 `.kt` 非空，`sourceOf` 断言
  恰好一个 `AppScreen.kt`），不允许对着隐形的文件空转通过。

## 4. 门禁原文与结果

```
gradle.bat --offline --no-daemon --no-build-cache \
  :app:assembleDebug :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin \
  verifyAcTraceabilityMatrix verifyThirdPartyNotices
→ BUILD SUCCESSFUL
  AC matrix covered 56 ids with evidence.
  Validated 73 third-party notice entries.
```

按 `app/build/test-results/testDebugUnitTest/*.xml` 现算：**206 个 suite / 1499 例，
0 失败 / 0 错误 / 0 跳过**（`newest_age_s≈40`，非 UP-TO-DATE 取证）。
对照 main 基线 1471：本批 +28（Task 2 的 9、Task 3 的 4、评审轮 1 的 2、轮 2 的 2、
终审批的 11 = 契约 7 + ViewModel 3 + 金值 1），只增不减。

RED 取证（各条均为先红后绿，原始报错已归档在 SDD 工作区 `task-3-report.md`）：
过期整份发布 `expected: <30> but was: <10>`；过期进度回填 `expected: <[kaoyan]> but was: <[cet4, kaoyan]>`；
抹空进度 `expected: <[cet4, cet6]> but was: <[]>`；词量契约首跑即失败于
`word count comes from the book and never from progress()`。

评审链：Task 2 首轮 8 项 → 复评 2 轮关闭；Task 3 首轮 3 项 + 复审批 4 项 → `190d223` 窄复评全部
ADDRESSED；整分支终审无 Critical，I-1／I-2／I-3 + Minor A–D 于 `2fe4c16` 一次关闭，
窄复评 7/7 ADDRESSED 且无新增 Critical/Important（复评方用 python 独立复刻了 FNV-1a 贪心逐位复现金值、
并重新解析十册 `book.json` 核对文档里的每个 `n/M/D` 与短词计数）。

## 5. TODO-USER（真机装机后人工确认，归 UI 重设计轮与 QA 第三轮）

- 打开「选取词书」是否还卡：期望是列表先出现、进度条一本本长出来，而不是一片骨架后整列蹦出。
- 新词顺序观感：连续几张不应是同一前缀家族（`abandon`/`ability`/`able` 应被拉开）。
- 骨架行（3 行、约 74dp）与真卡（带进度约 130dp）高度不同，回填后卡片会长高——
  **这是刻意取舍**（不预留按册高度），是否要做视觉稳定属 UI 重设计轮裁决。
- 大字号（fontScale）与 TalkBack 下的三态播报未做设备侧走查。

## 6. 交下来的账（不 silently drop）

- `AppScreen.kt` 仍有两处裸 `Color(0xFFDCEAE1)`／`Color(0xFFCDEDD9)` 字面量（本次未触碰），
  与 F5-03 的颜色 token 化口径待并；归 UI 重设计轮（颜色 token + 深色模式一起做）。
- 按册预留骨架高度、字母 Rail 真 48dp、喇叭药片放大等 F5-03 移交债，全部留给 UI 重设计轮。
- 交错顺序在真机演示数据上无自动化覆盖（间距性质属 JVM 层保证），设备侧只走 §5 的人工观感。
- 若将来要「按词频优先」分层，需要外部频率表与许可核验（决策「候选方案 C」已否决留批）。
