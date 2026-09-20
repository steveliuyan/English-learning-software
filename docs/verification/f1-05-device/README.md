# F1-05 真机验证证据

真机：`bf353dda`（Xiaomi M2102J2SC / MIUI，Android 13）
被测包名：`com.example.englishlearning`

## 1. 学习闭环手工验证（2026-09-20 21:36–21:49）

用 `adb` 安装 `app-debug.apk` 后手工走了一遍完整闭环：创建资料 → 今日计划 → 词卡 → 提交反馈 → 杀进程重启。

| 截图 | 内容 |
|---|---|
| `dev-01-launch.png` | 首次启动，创建资料页 |
| `dev-02-after-create.png` | 创建资料完成，进入今日计划 |
| `dev-02-today-plan.png` | 今日计划：0/10 未解锁 |
| `dev-03-word-card.png` | 词卡页，三档反馈可点 |
| `dev-04-plan-after-feedback.png` | 提交反馈后回到今日计划 |
| `dev-05-after-restart.png` | 杀进程重启后今日计划：10/10 已解锁 |

设备库核对：`learning_events` 10 行，全部带 `algorithmVersion = fsrs-v1`、`paramsVersion = fsrs-v1-default`。

## 2. AppScreenTest 真机运行（2026-09-20 23:51）

### 背景

第 1 节手工验证时发现缺陷：从词卡返回今日计划**不重算完成度**——词卡内显示「共完成 10/10」，返回后今日计划仍显示「0/10 未解锁」，必须杀进程重启才变成「10/10 已解锁」。根因是 `AppScreen.kt` 的 `LaunchedEffect` 只以 `profileId` 为 key，两条退出学习流的路径都没有调用 `todayPlanViewModel.load(...)`。

修复提交：`fcf1126`（`fix(ui): recompute today plan when leaving the learning flow`）。
测试调整：`3837386`（消除 androidTest 与 main 的同名 FQN 冲突）、`f657feb`（两个用例改用系统返回，覆盖 `BackHandler` 路径）。

### 运行方式

```
ANDROID_HOME=D:/Android/Sdk GRADLE_USER_HOME=D:/Android/GradleCache \
./gradlew.bat :app:connectedDebugAndroidTest \
  -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true \
  "-Pandroid.testInstrumentationRunnerArguments.class=com.example.englishlearning.ui.AppScreenTest" \
  --no-daemon --no-build-cache --console=plain
```

日志：`verification-logs/02-appscreen-device.log`

**必须带 `leaveApksInstalledAfterRun=true`**：该任务默认在跑完后卸载被测应用，会连带清空 `/data/data/<pkg>` 且不可恢复。运行前后各拉取一次设备库比对，确认 10 条学习事件与 1 条今日计划完好。

### 结果

```
Starting 10 tests on M2102J2SC - 13
Finished 10 tests on M2102J2SC - 13
BUILD SUCCESSFUL
```

`appscreen-test-result.xml`：`tests="10" failures="0" errors="0" skipped="0"`。

修复前同一命令的结果是 `tests="10" failures="2"`，两个失败都是断言行不存在节点 `ContentDescription = '返回今日计划'`——因为处于 `Ready`（正在看词卡）状态时词卡不渲染该按钮。

### 关键断言

`returningFromLearningReloadsTodayPlan` 用计数夹具验证刷新确实发生：

- 资料变为 `Ready` 后立即断言 `invocations == 1`（初次加载 1 次）；
- 进入词卡、再用系统返回退出，断言 `invocations == 2`。

该用例通过，即实测值为 1 → 2，**「离开学习流必须重算今日计划」在真机上得到确认**。

### 遗留

- 本轮只跑了 `AppScreenTest` 一个类。其余 androidTest 类（`RoomTodayPlanRepositoryTest`、`RoomLearningEventRepositoryTest`、`FsrsFeedbackFlowTest`、`TodayPlanScreenTest`、`WordCardScreenTest`、`SetupScreenScreenshotTest`）尚未在本轮真机复跑。
- AC1-07 的跨日/重启/时区变更覆盖见后续补充。
