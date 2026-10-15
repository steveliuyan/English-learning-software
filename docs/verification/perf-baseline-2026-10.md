# 性能基线 — 2026-10（阶段 5 SP-B Task 5）

口径依据：`docs/decisions/2026-10-15-sp-b-performance-and-reliability-semantics.md` §1——
emulator-5574（API 30，最低支持口径代理设备，minSdk 26 的保守代理）每项 5 轮取中位数，
阈值 = 中位数 × 2 向上取整（<1s 项到 50ms 档，≥1s 项到 500ms 档）。首轮只设生存线
（冷启动 <10s、单项 <5s）防挂死；本文件给出实测后，阈值已回填
`PerfSixMetricsInstrumentedTest`/`SeedData`（Task4→Task5 顺序保证阈值有据）。

## 实测（2026-10-15，emulator-5574 / Android 11 / API 30 / x86_64）

| metric | medianMillis | 阈值（×2 取整） | 测量口径 |
|---|---|---|---|
| cold_start | 2277 | **5000** | `am start -W` TotalTime 中位数 ×5（force-stop 后），脚本侧阈值，记入验收脚本口径 |
| plan_read | 10 | **50** | 400 任务计划快照经 `RoomTodayPlanRepository.find` 读取 |
| card_paging | 4 | **50** | 1000 调度态（半到期）经 `RoomLearningEventRepository.dueCardIds` |
| word_lookup | 8 | **50** | 500 词索引经 `RoomVocabularySearchIndexRepository.search`，前缀命中打满 limit=30 |
| event_append_100 | 324 | **650** | 100 次 `RoomLearningEventRepository.append`（每事件一事务，FSRS 生产路径） |
| backup_validate_500 | 230 | **500** | 500 表项合法包 `BackupPackageValidator.validate` |
| restore_write_500 | 156 | **350** | 500 条 staged 记录经 `RoomRestoreDataWriter.writeReplacement`（含全新库建库+迁移 1..31） |

交叉核验：emulator-5576（同 API 30）同日实测 plan_read 12 / card_paging 4 / word_lookup 7 /
event_append_100 325 / backup_validate_500 233 / restore_write_500 126 / cold_start 2458——
量级一致，阈值对两台设备均留 ≥1.5 倍余量。

## 复测步骤

```bash
GRADLE=<本机 gradle 8.13，见 handoff skill §3>
"$GRADLE" :app:assembleDebug :app:assembleDebugAndroidTest --no-daemon --no-build-cache
adb -s emulator-5574 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5574 install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
bash scripts/perf-baseline.sh emulator-5574
```

instrument 五项同时由 `PerfSixMetricsInstrumentedTest` 断言（超阈值即红）；
冷启动阈值当前由本文件口径人工比对（脚本输出对照 5000ms）。

## 设备覆盖与缺口

- **真机 bf353dda：TODO-USER**——不以模拟器数据冒充真机证据。真机复测走同一脚本
  （`bash scripts/perf-baseline.sh bf353dda`），注意 MIUI 会自动卸载 adb 安装的应用（装完立测）。
- 数据规模为夹具值（500/1000/400/500），代表重度用户而非压测上限；数量级回归由断言兜住。
- 计时设施为 `System.nanoTime` 经 `measureTimeMillis` + 中位数，未引入 androidx.benchmark
  （决策 §1：新依赖需重过锁与许可登记，收益不抵成本）。
