# 可打印单词表 / 默写纸 · 验证记录

设备：`M2102J2SC`（MIUI V816 / Android 13，adb serial `bf353dda`）
分支：`stage-1-f1-05-fsrs-scheduling`
调试 APK：`app/build/outputs/apk/debug/app-debug.apk`，MD5 `13ff5bbcf0b635e154da4f8b86de26ae`

## 一、三份导出模板

用户提供了三份参考导出（`我的词表-今日任务.pdf`、`(1).pdf`、`(2).pdf`）与三张截图，要求导出词表按这些版式实现。据此把导出做成三份模板，每页词数由模板决定，不再使用统一分页：

| 模板 | 版式 | 每页词数 |
| --- | --- | --- |
| `FULL_LIST` 我的词表 | 左右并排两栏，连续编号，左半 1–N/2、右半 N/2+1–N | 40（每栏 20 行） |
| `SPELLING_TEST` 拼写测试 | 左右镜像：左栏印单词+音标、释义留空；右栏单词留空、释义印出 | 20 |
| `EBBINGHAUS_REVIEW` 艾宾浩斯抗遗忘 | 单栏 `No./Word(含音标)/Meaning` + 右侧 `Review` 列（`D1 D2 D4 D7 D15 D30 D60 D90` 打卡格） | 20 |
| 答案页 | 单栏 `No./Word/Meaning`，题号与题目页一致 | 20 |

共同版式：A4 竖版 595×842 pt、圆角青绿外框、深青底白字表头、浅青交替行底色、浅青单元格网格；标题左上、`Date：` 右上、`Page-N` 右下。四线三格只作用于拼写测试模板的留空单元格。

只有拼写测试模板消费「中译英 / 英译中」方向；完整词表与艾宾浩斯模板都直接印出单词与释义，选择两个方向也不会把词表重复两遍（`WorksheetSettings.requiresDirection()` + 文档构建器只生成一组）。

## 二、渲染证据（真机生成，非设计稿）

以下图片由真机仪器测试 `WorksheetTemplateVisualTest` 用 22 个真实词条渲染 PDF 后，经 `PdfRenderer` 转位图导出，可逐字核对版式：

- `FULL_LIST-page-1.png`：22 词 → 单页，左右各 11 行，编号 1–11 / 12–22
- `SPELLING_TEST-page-1.png` / `-page-2.png`：22 词 → 2 页，编号连续 1–20 / 21–22
- `EBBINGHAUS_REVIEW-page-1.png` / `-page-2.png`：22 词 → 2 页，满页 20 行带 D1–D90 格

`FULL_LIST` 单页与 `SPELLING_TEST`/`EBBINGHAUS` 两页的差异，正是「40 词/页」与「20 词/页」两种模板容量在真机上被断言的结果。

## 三、测试结果

```
:app:testDebugUnitTest         172 tests / 0 failed / 0 skipped
:app:connectedDebugAndroidTest 25 tests / 0 failed / 0 errors / 0 skipped
```

真机定向回归覆盖 7 个类：`WorksheetTemplateVisualTest`、`WorksheetPdfRendererTest`、`WorksheetPreviewRendererTest`、`WorksheetShareLauncherTest`、`WorksheetSettingsScreenTest`、`AppScreenTest`、`TodayPlanScreenTest`。

命令：

```bash
ANDROID_HOME=D:/Android/Sdk GRADLE_USER_HOME=D:/Android/GradleCache \
  ./gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.example.englishlearning.export.WorksheetTemplateVisualTest,com.example.englishlearning.export.WorksheetPdfRendererTest,com.example.englishlearning.export.WorksheetPreviewRendererTest,com.example.englishlearning.export.WorksheetShareLauncherTest,com.example.englishlearning.ui.WorksheetSettingsScreenTest,com.example.englishlearning.ui.AppScreenTest,com.example.englishlearning.ui.TodayPlanScreenTest \
  -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true \
  --continue --no-daemon --no-build-cache --console=plain
```

## 四、一并修复的既有质量门账目

本轮开始前全量单元测试有 4 项失败，与本功能无关，已定位并修复：

1. `LogicalSnapshotSecurityTest`（2 项）与 `ProviderContractTest`（1 项）：禁用词表按**子串**匹配，`ExportProfileRecord` 里的 `Profile` 命中 `file`，把普通导出 DTO 误判成存储材料。改为按词边界（含 camelCase 拆分与相邻词拼接）匹配，`java.io.File`、`filePath`、`files`、`apiKey`、`httpUrl` 仍会被命中，检测能力未削弱。
2. `ThirdPartyNoticesTest`：版本目录中 `androidx-core-splashscreen`、`androidx-lifecycle-viewmodel`、`androidx-hilt-navigation-compose`、`androidx-compose-ui-test-manifest`、`androidx-room-testing` 五个别名缺台账条目，已在 `docs/third-party-notices.md` 补齐。

## 五、边界与未完成项

- PDF 只生成在 `cacheDir/worksheets/`，经 FileProvider 以 `content://` 只读共享；未新增存储或网络权限。
- 词条只来自当前不可变今日计划中已提交反馈的卡片，复习词排在新词前。
- 未做：真实词书内容与全量配图仍缺失（许可未闭合）；`LearningToolsScreen` 里的「错词再练」等仍显示未开放。
- 真机 UI dump 仍受 MIUI `theme_config` 缺失影响，无法产出有效 XML；本轮版式证据改用真机渲染位图，规避该问题。
