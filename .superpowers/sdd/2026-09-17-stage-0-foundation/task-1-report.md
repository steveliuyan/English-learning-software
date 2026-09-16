# 阶段 0 Task 1 交付报告

## 实现切片

- 创建单模块 `:app` Android Gradle 工程，锁定 Java 17、`minSdk 26`、`compileSdk/targetSdk 36`、Compose、Room、Hilt、AndroidX Security 与测试工具版本。
- 使用 Gradle Wrapper 8.13；未依赖系统安装的 Gradle。
- 增加 `ThirdPartyNoticesTest`，定义并实现 `verifyThirdPartyNotices(markdown)`：每个二级标题台账项必须具有名称、版本、许可证、用途、数据流、NOTICE 位置、替代方案、商业分发结论。
- 根任务 `verifyThirdPartyNotices` 已接入根 `check`，同时 `:app:check` 依赖该质量门。
- 建立 14 项初始许可台账：Kotlin、Compose、Room、Hilt、AndroidX Security、JUnit、AndroidX Test、Robolectric、MockK、Turbine、detekt、ktlint、数据集、模型。数据集和模型明确为未引入/不打包。
- 提供独立 Kotlin 脚本 `tools/verify-third-party-notices.main.kts`，内容与 Gradle 质量门使用同一必填字段契约。

## 改动文件

- `settings.gradle.kts`
- `build.gradle.kts`
- `gradle/libs.versions.toml`
- `gradle/wrapper/gradle-wrapper.jar`
- `gradle/wrapper/gradle-wrapper.properties`
- `gradlew`、`gradlew.bat`
- `gradle.properties`
- `app/build.gradle.kts`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/com/example/englishlearning/notices/ThirdPartyNotices.kt`
- `app/src/test/java/com/example/englishlearning/notices/ThirdPartyNoticesTest.kt`
- `docs/third-party-notices.md`
- `tools/verify-third-party-notices.main.kts`

## RED 证明

1. 测试先于生产实现创建：`ThirdPartyNoticesTest.kt`。
2. 首次运行 `./gradlew :app:testDebugUnitTest --tests '*ThirdPartyNoticesTest'` 在工程/Wrapper尚未存在时按预期失败：`gradlew: No such file or directory`。
3. 工程创建后，测试执行再次被环境阻断：初次无 Android SDK；该问题不是测试断言失败，无法作为功能 RED 证明。
4. 质量门的行为 RED 已实际验证：临时删除 `Kotlin` 条目中的 `数据流` 字段后运行 `./gradlew verifyThirdPartyNotices`，任务失败并输出：`Kotlin: 缺少必填字段“数据流”`。随后恢复台账。

## 测试命令与结果

| 命令 | 结果 |
|---|---|
| `./gradlew verifyThirdPartyNotices` | 通过：`Validated 14 third-party notice entries.` |
| `./gradlew verifyThirdPartyNotices`（临时删除数据流字段） | 预期失败：`Kotlin: 缺少必填字段“数据流”` |
| `./gradlew :app:testDebugUnitTest --tests '*ThirdPartyNoticesTest'` | 未完成：环境没有配置 `ANDROID_HOME` / Android SDK，AGP 报 `SDK location not found` |
| `./gradlew :app:assembleDebug` | 未执行；同样受缺少 Android SDK 阻断 |

## 设计偏差

- `android.overridePathCheck=true` 是为该受控工作树路径含中文字符而加入的 AGP 临时兼容开关；AGP 输出其为 experimental warning。项目发布前应迁移至 ASCII 路径并移除此开关。
- `detekt` 和 `ktlint` 按计划在初始台账登记为“未引入（计划质量工具）”，本 Task 1 未启用对应插件，避免超出计划最小切片。
- Gradle 质量门在构建脚本中实现以保证可由 `check` 调用；`tools/verify-third-party-notices.main.kts` 作为可独立审查/执行的等价校验脚本保留。后续可提取共享逻辑以消除重复。

## 后续建议

1. 在有 Android SDK（至少 API 36 platform）的环境配置 `ANDROID_HOME` 或工作树 `local.properties` 后，优先运行：`./gradlew verifyThirdPartyNotices :app:testDebugUnitTest :app:assembleDebug`。
2. 确认 `ThirdPartyNoticesTest` 的断言实际通过后再合入后续任务。
3. Task 7 启用 detekt 与 ktlint 插件并将其加入 CI 和最终质量门。
