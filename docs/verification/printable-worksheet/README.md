# 可打印单词表 / 默写纸 · 验证记录

设备：`M2102J2SC`（MIUI V816 / Android 13，adb serial `bf353dda`）
分支：`stage-1-f1-05-fsrs-scheduling`
调试 APK：`app/build/outputs/apk/debug/app-debug.apk`，MD5 `c06fdaeca02fe7643212597fccc2b795`

## 一、三份导出模板

用户提供了三份参考导出（`我的词表-今日任务.pdf`、`(1).pdf`、`(2).pdf`）与三张截图，要求导出词表按这些版式实现。据此把导出做成三份模板，每页词数由模板决定，不再使用统一分页：

| 模板 | 版式 | 每页词数 |
| --- | --- | --- |
| `FULL_LIST` 我的词表 | 左右并排两栏，连续编号，左半 1–N/2、右半 N/2+1–N | 40（每栏 20 行） |
| `SPELLING_TEST` 拼写测试 | 左右镜像：左栏印单词+音标、释义留空；右栏单词留空、释义印出 | 20 |
| `EBBINGHAUS_REVIEW` 艾宾浩斯抗遗忘 | 单栏 `No./Word(含音标)/Meaning` + 右侧 `Review` 列（`D1 D2 D4 D7 D15 D30 D60 D90` 打卡格） | 20 |
| 答案页 | 单栏 `No./Word/Meaning`，题号与题目页一致 | 20 |

共同版式：A4 竖版 595×842 pt、圆角青绿外框、深青底白字表头、浅青交替行底色；**每张表都带闭合外框 + 全格网格**，竖线包含左右外边线，读起来是一张连续的表而不是散落的线。标题左上、`Date：` 右上、`Page-N` 右下。四线三格只作用于拼写测试模板的留空单元格。

只有拼写测试模板消费「中译英 / 英译中」方向；完整词表与艾宾浩斯模板都直接印出单词与释义，选择两个方向也不会把词表重复两遍（`WorksheetSettings.requiresDirection()` + 文档构建器只生成一组）。

## 二、渲染证据（真机生成，非设计稿）

以下图片由真机仪器测试 `WorksheetTemplateVisualTest` 用 22 个真实词条渲染 PDF 后，经 `PdfRenderer` 转位图导出，可逐字核对版式：

- `FULL_LIST-page-1.png`：22 词 → 单页，左右各 11 行，编号 1–11 / 12–22
- `SPELLING_TEST-page-1.png` / `-page-2.png`：22 词 → 2 页，编号连续 1–20 / 21–22
- `EBBINGHAUS_REVIEW-page-1.png` / `-page-2.png`：22 词 → 2 页，满页 20 行带 D1–D90 格

`FULL_LIST` 单页与 `SPELLING_TEST`/`EBBINGHAUS` 两页的差异，正是「40 词/页」与「20 词/页」两种模板容量在真机上被断言的结果。

## 三、测试结果

```
:app:testDebugUnitTest        177 tests / 0 failed / 0 skipped
真机 am instrument 全量        92 tests / 0 failed / 0 errors / 0 skipped
```

其中真机定向回归覆盖 7 个类：`WorksheetTemplateVisualTest`、`WorksheetPdfRendererTest`、`WorksheetPreviewRendererTest`、`WorksheetShareLauncherTest`、`WorksheetSettingsScreenTest`、`AppScreenTest`、`TodayPlanScreenTest`（27 项）。

**真机测试跑法（保留用户数据）**：不用 `connectedDebugAndroidTest`（AGP 会卸载重装，可能连带删除 `/data/data/<pkg>`），改为

```bash
./gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest --no-daemon --no-build-cache --console=plain
MSYS_NO_PATHCONV=1 adb install -r -t app/build/outputs/apk/debug/app-debug.apk
MSYS_NO_PATHCONV=1 adb install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
MSYS_NO_PATHCONV=1 adb shell appops set com.example.englishlearning 10021 allow   # MIUI 后台弹出界面，Compose 用例必需
MSYS_NO_PATHCONV=1 adb shell am instrument -w com.example.englishlearning.test/androidx.test.runner.AndroidJUnitRunner
```

跑前 `adb shell run-as <pkg> ls -la /data/data/<pkg>/databases` 记录基线、跑后比对：本轮 `english-learning.db`（176128 B / mtime 2026-09-23 19:39）与 `-wal`、`-shm` 三项时间戳与大小完全未变，确认用户数据未被触碰。

## 四、用户实测四个缺陷的修复与真机复核

用户报告：① 三份模板预览看起来完全一样、且不是 PDF；② 点导出后文件打不开；③ 点「返回修改」后模板被定死在「拼写测试」、选不了别的；④ 表格不像一张连贯的表。

①③ 同源：`WorksheetViewModel.updateSettings()` 与 `preview()` 都要求状态是 `Ready`，但 `preview()` 把状态改成 `Preview` 后**永不回退**，于是返回后所有设置变更静默失效，再点预览也因提前 `return` 永远沿用第一份分页。改为 `WorksheetPhase{IDLE,LOADING,SETTINGS,PREVIEW,ERROR}` + 单一 `settings`（任何阶段可改）+ `dismissPreview()` 只回退阶段；渲染加自增 `renderGeneration`，过期结果直接删除。

② `WorksheetShareLauncher` 的 authority 改为按 `context.packageName` 推导（等价 `${applicationId}.worksheetfiles`）；分享面板除 `ACTION_SEND` 再经 `EXTRA_INITIAL_INTENTS` 提供 `ACTION_VIEW`；`clipData` 与 `FLAG_GRANT_READ_URI_PERMISSION` 在 chooser 上再声明一次。

① 「不是 PDF」：预览页不再 Compose 自绘文字近似，而是 `produceState(renderedFile) { WorksheetPreviewRenderer(context).render(file, 1000) }`，直接显示刚写出的那份 PDF 页面位图 —— 预览与导出是同一个文件。

④ 版式：见「一、」中的闭合外框 + 全格网格；艾宾浩斯改为整表宽度网格，`No./Word/Meaning` 跨两行合并表头、`D1…D90` 只占表头第二行；中文释义补按字符兜底断行。

真机交互走查（`docs/verification/printable-worksheet/ui/`，adb 驱动，非设计稿）：

| 截图 | 证明的事 |
| --- | --- |
| `01-settings-templates.png` | 三份模板单选卡都在，默认「拼写测试」 |
| `02-settings-preview-action.png` | 选「艾宾浩斯抗遗忘」后开关与预览按钮正常 |
| `03-preview-ebbinghaus.png` | 预览显示真实 PDF 页面图像，标注「第 1 页 · 艾宾浩斯抗遗忘 / 第 2 页 · 答案页」 |
| `04-back-still-editable.png` | **点「返回修改」后模板单选已切到「我的词表」**，说明没被定死 |
| `05-preview-full-list.png` | 同一入口再预览，页面变成双栏完整词表 —— 模板换了预览就跟着换 |
| `06-export-chooser.png` | 系统面板里文件名为 `worksheet-*.pdf`，并出现「Android 系统 打开」入口 |
| `07-opened-in-viewer.png` | PDF 在小米浏览器阅读器中真实渲染出题目页与答案页 |

## 五、一并修复的既有质量门账目

本轮开始前全量单元测试有 4 项失败，与本功能无关，已定位并修复：

1. `LogicalSnapshotSecurityTest`（2 项）与 `ProviderContractTest`（1 项）：禁用词表按**子串**匹配，`ExportProfileRecord` 里的 `Profile` 命中 `file`，把普通导出 DTO 误判成存储材料。改为按词边界（含 camelCase 拆分与相邻词拼接）匹配，`java.io.File`、`filePath`、`files`、`apiKey`、`httpUrl` 仍会被命中，检测能力未削弱。
2. `ThirdPartyNoticesTest`：版本目录中 `androidx-core-splashscreen`、`androidx-lifecycle-viewmodel`、`androidx-hilt-navigation-compose`、`androidx-compose-ui-test-manifest`、`androidx-room-testing` 五个别名缺台账条目，已在 `docs/third-party-notices.md` 补齐。

## 六、边界与未完成项

- PDF 只生成在 `cacheDir/worksheets/`，经 FileProvider 以 `content://` 只读共享；未新增存储或网络权限。
- 词条只来自当前不可变今日计划中已提交反馈的卡片，复习词排在新词前。
- **表格下方留白**：行高按模板容量（每页每栏 20 行）固定，词条少时表格只占页面上半部，不为凑满而画空格子。若希望短列表也铺满整页，需要改的是模板容量而不是行高。
- 未做：真实词书内容与全量配图仍缺失（许可未闭合）；`LearningToolsScreen` 里的「错词再练」等仍显示未开放。
- `uiautomator dump` 在本轮已能正常产出完整 UI XML（含所有 Compose 文本节点），此前记录的「MIUI `theme_config` 缺失导致 dump 失败」并未复现；需要时先用一次 dump 实测再决定取证手段。
