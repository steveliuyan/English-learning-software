# 2026-11-07 批 C-5（真预取：闪屏窗口预热开库 + tab 预读对账）工作报告

## 1. 本次目标

批 C 的第 5 子批，口径来自决策册 `docs/decisions/2026-11-07-loading-skeleton-shimmer-prefetch.md` 决定 4 三条：

1. **闪屏窗口预热本地盘**：把 Room 的「建库 + 首次打开」从 MainActivity 的关键路径挪进 `BrandLaunchActivity` 那 1.2 秒，
   只做「请求一次单例」这一件事；明令禁止读学习数据进 VM 状态（会多出第二份数据权威）、禁止预跑备份/导出重 IO。
2. **tab 预读对账**：保留既有分发，把自动触发装载点逐条登记，**不新增任何出站装载点**。
3. **硬约束（用户原话）**：「只用本地、不为预取新增任何网络或 AI 出站」，且「预取不得让任何一次点击『本来不发请求』变成『已经发过请求』」。

验收口径同样来自决策册 §4：预取零出站归 JVM 假 provider 与源码闸；「闪屏那 1.2 秒确实变有用」**只能实测**，
记 `adb shell am start -W` 与目视对照，**本批不承诺具体毫秒数**。

## 2. 完成了什么

**第 2 条实测后判定「零生产改动」**：`AppScreen.kt` 里 15 枚 `LaunchedEffect` 自动装载点抽出来对账，
调用的动词只有 `load` / `refreshHistory` / `setProfile` / `invoke` 四种本地读形状（`photoTranslationViewModel.refreshHistory`
逐层读到 `history.listRecent`，确为纯 Room 读），AI tab 在批 C-2 就已确认可步状态没有异步读。
所以 tab 预读**没有可加的本地读点，也一枚出站点都没加**——本批对它交付的是那把对账闸，不是屏幕改动。

**第 1 条落地**：

- 新增 `core/storage/StartupLocalDataWarmer.kt`（33 行含 KDoc，形状抄 `StartupPronunciationWarmer`，是决策 §2.4 认定的唯一先例）。
  与先例**故意不同的一处**：`CancellationException` 不吞，与 `DatabaseStartup.openWith` 同口径。
- `EnglishLearningApplication` 在**既有** `warmUpScope` 里多踢一脚：`StartupLocalDataWarmer { withContext(Dispatchers.IO) { databaseProvider.get() } }`。
  磁盘 IO 落 `Dispatchers.IO` 而不是与 Kokoro 同挤 `Dispatchers.Default`——那是决策 §5 点名的竞争面。

**新增 11 条 JVM 判据**（两枚闸，见 §3）：`StartupLocalDataWarmerTest` 7 条、`StartupPrefetchLoadPointContractTest` 4 条。

**设备侧读数**（`.git/qa-scratch/c5/`，不入库）：`before` / `after` 各 4 轮冷启对照，脚本 `measure.sh`。
结论三条、边界两条，逐行登记在 `docs/verification/ui-redesign-2026-10.md` 新增的
「2026-11-07 批 C-5：闪屏窗口预热开库的实测对照」一节。

## 3. 验证记录

**门禁（JVM 单测=本仓唯一验收闸）**：`--rerun-tasks --no-daemon --no-build-cache`，`BUILD SUCCESSFUL in 4m 2s`，

```
suites=331 tests=2460 skipped=0 failures=0 errors=0
```

= 批 C-4c 基线 329 类 / 2449 条 + 本批 2 类 11 条，0 失败 0 跳过，**基线只增不减成立**。

**RED→GREEN 实录**：① 编译红 `Unresolved reference 'StartupLocalDataWarmer'`（先写测试后写实现）；
② 测试码自身两处编译错（`assertEquals(listOf, Set)` 类型推断、嵌套 `data class` 取不到外层方法）改绿；
③ **两枚「闸不许空转」失败暴露真缺陷**：抽取器用 `matchEntire` 匹配 `LaunchedEffect(` 头 → 扫出 0 枚 →
「只调已登记装载点」那条**空扫假绿**，是覆盖守卫（`assertEquals(15, effects.size)`）把它揪出来的，改 `.find` 后三种形状都读得到。

**闸有牙的实证（变异探针）**：临时往 `AppScreen` 插一枚未登记的 `wordCardViewModel.load(state.profile.id)` → 闸红并点名
`` `wordCardViewModel.load` ``；随后用**反向 Edit** 摘除（不用 `git checkout --`，那会连带抹掉在途未提交改动），
以 `git diff --stat` 复核 `AppScreen.kt` 零差异。合成双边另有三条：`wordQaViewModel?.ask` 必红、
`favoritesViewModel?.load` 必放、`probeOutbound?.invoke()` 必红（枚名字不算，枚「接收者.方法」才算）。

**connected（`emulator-5554`，`adb install -r` 两枚 APK + `am instrument`）**：

| 类 | 读数 |
|---|---|
| `ui.AppScreenTest` | 首趟 `Tests run: 64, Failures: 1` → 单跑 `OK (1 test)`、整类复跑 `OK (64 tests)` |
| `ui.RecoveryScreenTest` | `OK (5 tests)` |
| `core.storage.MigrationFailureRecoveryTest` | `OK (1 test)` |

首趟那枚红是**本仓在册最久的 flake**（`startLearningOpensTheWordCardAndBackReturnsToTheTodayPlan`，`waitUntil` 5 秒超时），
至少六份文档记过同方法同形状；最早一份（2026-10-15 SP-B）记的是**同族** 8 枚 back/导航用例——
用 `git stash` 回退到分支基线重构建重跑、照样同样红，当场判定设备环境不稳定、归攒批走查，不阻塞门禁。
本批未改实现、未放宽判据。选后两类的理由登记在验证册：预热挂在 `Application.onCreate`，instrumented 进程也会跑它，
所以「进程启动多了一次磁盘 IO」对全部 connected 用例成立，必须看恢复屏与开库失败面有没有被带歪。

**首帧对照读数（要点，全表在验证册）**：

| 侧 | 开库耗时 / 线程 | 开库时刻 vs 闪屏出帧 | `Displayed .MainActivity` 中位 |
|---|---|---|---|
| 未接线 | 51 / 45 / 50 / 48 ms，`main` | 闪屏出帧**后**约 1.3 秒 | 739.5 ms |
| 已接线 | 75 / 96 / 93 / 84 ms，`DefaultDispatcher-worker-2` | 闪屏出帧**前**约 0.9 秒 | 733.5 ms |

主张成立的是**位置**（四轮全部在窗口内跑完，且每进程只开一次）；`Displayed` 只差 6 ms、区间大面积重叠，
**落在噪声里**，故不写「快了 45 ms」这种话——决策册 §4 那句「本批不承诺具体毫秒数」照原文执行。

**测量桩的处置**：为拿读数在 `di/AppModule.provideDatabase` 临时插过两行 `Log.i("C5Timing", …)`，
测完当场摘除，`git diff --stat` 复核工作区只剩 `EnglishLearningApplication.kt` 一处后才提交——桩**没有进任何提交**。

## 4. Git 状态

- 分支 `stage-5-ui-r2-learning-path`（按在册口径**不合 `main`**）。
- 本批两枚代码提交：`f242050`（生产：warmer + Application 接线）→ `2afea7c`（JVM 两枚闸 11 条）；
  本报告与验证册改动随第三枚 docs 提交入库。
- 远端 `origin` = `https://github.com/steveliuyan/English-learning-software.git`，
  `ls-remote` 实测远端 tip `c7271e0`（可达性已恢复：批 C-4c 收口时代理与直连都拒连，本棒现算为可读），
  本地 ahead 6（`git rev-list --count origin/<branch>..HEAD` 现算 = 批 C-4c 的 4 枚 + 本批 2 枚代码提交，**不含本枚 docs 提交**）。
  **未推送**：用户裁决「先不推，等批 C 整批收口」，故本批不推、不宣称备份完成。
- 工作区除本批文档外无遗留；`.git/qa-scratch/c5/`（`before.txt`、`after.txt`、`measure.sh`、三份构建/门禁日志）不入库。

## 5. 未完成 / 遗留问题

- **「首帧 → 今日计划可读」这段骨架时长未量**：要量它得在 `TodayPlanViewModel` 的 `Ready` 落点再插一枚临时桩，
  本批没有为一条读数再走一轮双构建（两侧各重建 + 重装约 5 分钟）。被搬走的成本上界就是那 45–51 ms 主线程开库，
  已按「不承诺毫秒」口径登记为遗留，并入待目视清单。
- **开库自身变慢（48 → 85 ms 量级）是决策 §5 那条风险的实测回读**：预热与闪屏组合、Kokoro 资产物化同处启动最挤的一段。
  是否要为它加「等闪屏结束再开库」的延迟，按决策册原文归 **批 C-6 的耗时读数**裁定，本批不自作主张收缩。
- 批 C-6 尚未开工：整批门禁复跑、`scale=0` / `scale=1` 截图册收口、connected 全量、批 C 报告与**整批推送**请示。
- #14 真机目视、QA 第三轮整轮、以及 C-4b/C-4c 结转的目视四项仍未销（取消 pill 像浮白盒、18dp/2dp 静帧像小弧、
  按张计费句不在生图等待面、批 B 四项）。

## 6. 下一任接手须知

- 本批只动了启动路径的一枚副作用调用，**没碰任何屏幕**；`AppScreen.kt` 与 `di/AppModule.kt` 相对 `HEAD` 零差异，可直接核对。
- 读预取相关的两枚闸之前先读它们各自的「闸判的是装载点名字」限制：若将来有人把出站包进一枚**已登记**的 `load()`，
  源码闸读不到——那是刻意的口径边界，不是漏判，真要收紧得改成按 VM 类型判定，代价是闸开始依赖类型解析。
- 复跑读数：`bash .git/qa-scratch/c5/measure.sh <标签> 4`（脚本内含 `force-stop` → `logcat -c` → `am start -W` → 抓 `Displayed`）。
  装包后**首轮必弃**（含 dexopt，实测虚高到 2241 / 5685 ms）。
- 门禁三条老规矩一条没变：`--rerun-tasks` 才算真跑；统计只在 `BUILD SUCCESSFUL` 之后做（本棒中途读到过变异探针那趟留下的
  陈旧 `TEST-*.xml` 报 1 失败，差点误报——这是 Seat D M-4 那条「陈旧 XML 让五元组失真」的失败数一侧）；
  `; exit ${PIPESTATUS[0]}` 不能省。
- 本机 Gradle：`/c/Users/steveliuyan/.gradle/wrapper/dists/gradle-8.13-bin/5xuhj0ry160q40clulazy9h7d/gradle-8.13/bin/gradle.bat`
  （skill 里那条发现命令少了一层哈希目录，实测取不到）。
- connected 首趟单红先按在册 flake 处理：单跑 + 整类复跑都绿才算过，别急着自己发明第三种判据。

## 7. 用户确认过的决策

- 「先不推，等批 C 整批收口」——本批不推送，§4 只登记远端读数。
- 「直接进 C-5 真预取」——开工授权，本批按决策册决定 4 三条执行，未扩项。
- 「只用本地、不为预取新增任何网络或 AI 出站」「预取不得让任何一次点击『本来不发请求』变成『已经发过请求』」——
  落地为两枚闸的白名单口径（第 2 节）。
- 「闪屏窗口 + tab 预取」这一对范围由用户在批 C 立项时选定；决策册 §4「本批不承诺具体毫秒数」按原文执行，
  读数只登记位置证据与噪声区间。
- 沿用在册：分支不合 `main`、推送逐批请示、门禁=JVM 单测（detekt/ktlint 存量债非回归）、
  破坏性 git/设备动作先请示、真机只手工试用、`instrumented` 只在 `emulator-5554`、证据落 `.git/qa-scratch/` 不入库。
