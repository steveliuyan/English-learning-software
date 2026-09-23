# 「AI 学」入口与四栏底部导航 — 真机验收记录

**日期：** 2026-09-23
**设备：** `bf353dda`（M2102J2SC / MIUI V816 / Android 13）
**设计：** `docs/superpowers/specs/2026-09-23-ai-learning-tab-design.md`
**计划：** `docs/superpowers/plans/2026-09-23-ai-learning-tab.md`

## 1. 交付物

```
app/build/outputs/apk/debug/app-debug.apk          MD5 763c24256baab323aa48aab4c380e6e2
app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
```

## 2. 验证命令

```bash
ANDROID_HOME=D:/Android/Sdk GRADLE_USER_HOME=D:/Android/GradleCache \
  ./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest \
  --no-daemon --no-build-cache --console=plain

adb shell appops set com.example.englishlearning 10021 allow      # MIUI 放行 instrumentation 拉起 Activity
adb shell settings put system accelerometer_rotation 0
adb shell settings put system user_rotation 0                      # 锁竖屏
MSYS_NO_PATHCONV=1 adb install -r -t app/build/outputs/apk/debug/app-debug.apk
MSYS_NO_PATHCONV=1 adb install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
MSYS_NO_PATHCONV=1 adb shell am instrument -w \
  com.example.englishlearning.test/androidx.test.runner.AndroidJUnitRunner
```

**刻意不用 `connectedDebugAndroidTest`**：它会重装应用并删除 `/data/data/<pkg>`，本机有真实学习数据。

## 3. 测试结果

| 层 | 结果 | 对比基线 |
|---|---|---|
| JVM（`testDebugUnitTest` 全量） | **190 tests / 0 failed / 0 skipped** | 179 → +11 |
| 真机 instrumented（全量） | **OK (132 tests)**，78.5 s | 92 → +40 |

真机三轮全量均通过（127 / 130 / 132，逐轮对应文案与无障碍修正后的重跑），最终一轮即上表的 132。

## 4. 数据完整性证据

跑测前先固化基线，再用同一路径取回：`adb exec-out run-as <pkg> cat /data/data/<pkg>/databases/<f>`（必须加 `MSYS_NO_PATHCONV=1`，否则 MSYS 会把 Android 绝对路径改写成 Windows 路径，得到一个 162 字节的假文件）。

| 文件 | 基线 MD5 | 三轮跑测后 MD5 |
|---|---|---|
| `english-learning.db` | `4a39b664490091816b55f19999a21d32` | 完全一致 |
| `english-learning.db-wal` | `72ed682dc549e6efc5ed00a8620fd6b1` | 完全一致 |
| `english-learning.db-shm` | `267cbc2644221328443a6181a2a3d629` | 完全一致 |

设备侧大小/时间戳同样未变：`english-learning.db` 176128 B / 2026-09-24 00:33。**用户数据零改动。**

## 5. 验收标准逐条对照

| 编号 | 标准 | 结果 | 证据 |
|---|---|---|---|
| AC-1 | 底部出现四栏，标签依次为 学习 / 阅读 / AI 学 / 设置 | ✅ | `ui-tab/01-learning.xml`：四栏 `content-desc` 各占 270px 等宽（`[0,2120][270,2206]` … `[810,2120][1080,2206]`）；`01-tab-learning.png` |
| AC-2 | 点击任一栏切换内容，选中态可见 | ✅ | `02-tab-ai.png`（AI 学选中态为薄荷绿加粗）、`08-tab-settings.png`（设置选中态）、`09-tab-reading.png` |
| AC-3 | AI 学页显示渐变头卡、四个功能行、学习导航卡、说明区 | ✅ | `02-tab-ai.png` + `ui-tab/02-ai.xml` 文本；说明区展开见 `11-ai-explainer-expanded.png`、`12-ai-explainer-detail.png` |
| AC-4 | 四个功能行都可点开，页面不得空白或崩溃 | ✅ | `03-feature-word-passage.png`、`04-feature-cloze.png`、`05-feature-listening.png`、`06-feature-coach.png`，四页均有完整骨架 |
| AC-5 | 全屏层出现时底导隐藏；返回后回到原 tab | ✅ | `06-coach.xml` 中四栏 `content-desc` 数量为 0；`10-worksheet-layer.png` 同样无底导；`07-back-to-ai.xml` 返回后底导恢复且仍停在 AI 学；`11-back-from-worksheet.xml` 从默写纸返回后底导恢复且仍在设置栏 |
| AC-6 | 旋转屏幕后停留 tab 不丢失 | ✅ | 横屏 `ui-tab/17-rotated.xml`、转回竖屏 `ui-tab/18-restored.xml`：均停在 AI 学页（以 AI 页独有元素「AI 学习导航」判定），无其他栏内容泄露，底导完整 |
| AC-7 | JVM：`AppTab` 四栏顺序固定；`AiFeature` 状态文案与未实现事实一致 | ✅ | `AppTabTest` 5/5、`AiFeatureTest` 6/6 |
| AC-8 | instrumentation：四栏节点存在、点击回调正确 | ✅ | `AppBottomBarTest` 5 条、`AppScreenTest` 新增 7 条，全绿 |
| AC-9 | 真机全量 0 失败，且应用数据未被清空 | ✅ | 见第 3、4 节 |

## 6. 导航行为走查（adb 全程驱动）

| 动作 | 期望 | 实测 |
|---|---|---|
| 底导点「AI 学」 | 进 AI 学页 | ✅ `02-ai.xml` 首屏为「AI 学 / 用你学过的词…/ AI 尚未接通」 |
| 底导点「阅读」 | 进阅读页，**无**自带返回按钮 | ✅ `09-reading.xml` 有 `每日阅读`，无 `reading_access_back` |
| 底导点「设置」 | 进设置页 | ✅ `08-settings.xml` |
| 设置页点「生成默写纸」 | 进默写纸设置（全屏层，底导隐藏） | ✅ `10-worksheet.xml` 首行为「← 返回设置」，无底导 |
| 默写纸按返回键 | 回设置栏 | ✅ `11-back-from-worksheet.xml` |
| 今日计划点「学习工具与设置」 | 切到设置栏 | ✅ `12-tools-to-settings.xml` |
| 今日计划点「阅读文章」 | 切到阅读栏 | ✅ `13-reading-entry.xml` |
| 阅读栏按返回键 | 回学习栏 | ✅ `14-back-to-learning-tab.xml` |
| AI 功能页按返回键 | 回 AI 学栏 | ✅ `07-back-to-ai.xml` |

## 7. 真机走查抓到的两处「文案说谎」（已修并复验）

这两处组件级测试抓不到：`assertExists` 只关心节点在不在，不关心写的话对不对。

### 7.1 设置页说「每日新增 0 词」

- **症状**：`08-tab-settings.png`（修复前）资料卡显示「大学英语四级 · 每日新增 0 词」。
- **真相**：该词书新词已学完，当日计划的 `newTarget` 因此为 0（PRD FR-01 第 8 条：剩余新词少于目标时以实际剩余为准），而用户配置的每日目标并非 0。把计划数说成配置目标是错的。
- **修复**：`SettingsScreen` 的参数由 `dailyNewTarget` 改为 `todayNewTarget` / `todayDueTarget`，文案改为「今日计划：新增 X · 复习 Y」；新增回归测试 `profile_card_never_claims_the_plan_count_is_the_configured_daily_target`。
- **复验**：`08-tab-settings.png`（修复后）与 `ui-tab/08-settings.xml` 显示「ly / 大学英语四级 / 今日计划：新增 0 · 复习 9」。

### 7.2 默写纸设置页说「← 返回学习工具」

- **症状**：`10-worksheet-layer.png`（修复前）返回按钮写着「← 返回学习工具」。
- **真相**：「学习工具」页已在本轮并入「设置」栏并删除，文案指向了一个不存在的页面。
- **修复**：改为「← 返回设置」；`WorksheetSettingsScreenTest` 新增 `backEntryNamesTheSettingsTabItActuallyReturnsTo`，同时断言旧文案不再存在。
- **复验**：`10-worksheet-layer.png`（修复后）与 `ui-tab/10-worksheet.xml` 首行即「← 返回设置」，旧文案检索结果为 `False`。

## 8. 截图清单

`docs/verification/ai-learning-tab/`

| 文件 | 内容 |
|---|---|
| `01-tab-learning.png` | 学习栏（今日计划）+ 四栏底导 |
| `02-tab-ai.png` | **AI 学页**：渐变头卡 + 「AI 尚未接通」徽章 + 四个功能行 + 学习导航卡 |
| `03-feature-word-passage.png` | 词文串学功能页骨架 |
| `04-feature-cloze.png` | AI 短文填词功能页骨架 |
| `05-feature-listening.png` | 单词随身听功能页骨架 |
| `06-feature-coach.png` | 单词串讲功能页骨架（底导已隐藏） |
| `08-tab-settings.png` | 设置栏（修复后文案） |
| `09-tab-reading.png` | 阅读栏（无自带返回按钮） |
| `10-worksheet-layer.png` | 从设置进默写纸（全屏层，底导隐藏，「← 返回设置」） |
| `11-ai-explainer-expanded.png` | AI 学说明区展开后 |
| `12-ai-explainer-detail.png` | 说明区四段完整内容 |

原始 `uiautomator` XML 与数据库取证在 `verification-logs/ui-tab/`、`verification-logs/db-*/`（`.gitignore` 忽略，不进仓库）。

## 9. 本轮明确未做

- AI 网络客户端、`INTERNET` 权限、Endpoint 真实连通
- AI Profile 管理界面（新增 / 编辑 / 测试连接 / `GET /models` 模型列表）
- 四个功能的真实生成逻辑（文章、填词、串讲、AI 问答）
- 音频播放与缓存（单词随身听）
- 截图里的「课程」「发现」「我的」等栏位——项目当前无对应能力，不建空页面

页面现状如实反映以上边界：四个功能页的「当前进度」都写着「未实现」并列出依赖，头卡徽章恒显示「AI 尚未接通」。
