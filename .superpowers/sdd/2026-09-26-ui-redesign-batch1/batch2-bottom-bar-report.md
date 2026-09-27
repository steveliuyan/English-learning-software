# 批2第1块：AppBottomBar 按 Tab 使用域色

## 状态
已完成。

## 实现
- `AppTab.LEARNING` 选中项使用 `DomainColors.Learn.base`。
- `AppTab.READING` 选中项使用 `DomainColors.Reading.base`。
- `AppTab.AI` 选中项使用 `DomainColors.AiSpeech.base`。
- `AppTab.SETTINGS` 选中项使用 `DomainColors.Settings.base`。
- 未选中项使用 `AppPalette.TextSecondary`。
- 背景和分割线保持原有 `MintSurface` / `MintOutline` 语义。
- 保留四个 `testTag`、`contentDescription`、点击回调。
- 未引入新依赖，生产代码仅修改 `AppBottomBar.kt`，测试修改 `AppBottomBarTest.kt`。

## TDD 证据
1. 先新增四个领域色断言，生产代码保持未修改。
2. 同时安装主 APK 和 androidTest APK 后运行定向真机测试：新增断言因 `LEARNING` 仍使用 `MintPrimary` 而失败，确认红灯有效。
3. 修改生产代码后重新同时安装主 APK 和 androidTest APK，定向测试转绿。

## 测试摘要
- 定向真机：
  - 命令：`:app:installDebug :app:installDebugAndroidTest`，随后 `:app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.englishlearning.ui.AppBottomBarTest`
  - 设备：`M2102J2SC - 13`
  - 结果：9/9 通过，0 失败。
- JVM 全量：
  - 命令：`:app:testDebugUnitTest`
  - 结果：`BUILD SUCCESSFUL`。
- `git diff --check`：通过。

## Commit
`feat(ui): color bottom navigation by domain`

## 疑虑
- 颜色测试通过节点截图查找精确领域色像素；当前真机测试已验证四个选中项均能捕获对应颜色。
- Gradle 输出存在项目既有的 `android.overridePathCheck=true` experimental warning，以及 JVM 的 classpath sharing warning；不影响测试结果。
- 工作树中原有未跟踪文件（`docs/superpowers/...`、`outputs/`）未修改、未纳入本提交。
