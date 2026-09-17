# 阶段 0 / Task 1 质量阻塞修复报告

## 实现切片

1. 将 `ThirdPartyNotices.kt` 重命名为 `NoticeValidationError.kt`，使文件名与唯一顶级声明一致；使用 ktlint 自动格式化相关 Kotlin 代码。
2. 为 `ThirdPartyNoticesTest` 增加 Version Catalog 覆盖质量门：读取 `gradle/libs.versions.toml` 的全部 `libraries` 与 `plugins` alias，要求其分别映射到台账 ID；插件使用 `plugin-<alias>` ID，避免与 library alias 混淆。
3. 扩展 `docs/third-party-notices.md` 至 27 个条目，覆盖 Version Catalog 的直接 library/plugin alias，包括 AGP、KSP、Compose Compiler、Hilt Gradle plugin/Hilt compiler、core-ktx、activity-compose、Material3、kotlinx-coroutines-test，并保留 Kotlin Test JUnit 5。每个条目均包含名称、版本、许可证、用途、数据流、NOTICE 位置、替代方案和商业分发结论。
4. 使用 Gradle dependency locking 生成并加入 `app/gradle.lockfile` 与 `settings-gradle.lockfile`。

## TDD 证据

- RED：新增 Version Catalog 覆盖测试后运行：
  `GRADLE_USER_HOME=D:/Android/GradleCache ./gradlew :app:testDebugUnitTest --tests "com.example.englishlearning.notices.ThirdPartyNoticesTest"`
  结果：失败，`every version catalog library and plugin alias maps to a notice ledger entry` 在 `ThirdPartyNoticesTest.kt:17` 断言失败，原因是现有台账未覆盖 catalog aliases。
- GREEN：补齐台账与映射后运行同一命令，结果：`BUILD SUCCESSFUL`。

## 测试命令与结果

- `GRADLE_USER_HOME=D:/Android/GradleCache ./gradlew detekt ktlintCheck`
  - 结果：通过，`BUILD SUCCESSFUL`。
- `GRADLE_USER_HOME=D:/Android/GradleCache ./gradlew --stop && GRADLE_USER_HOME=D:/Android/GradleCache ./gradlew :app:assembleDebug :app:testDebugUnitTest --write-locks --no-daemon`
  - 结果：通过；写入 root 与 `:app` dependency lock state。
- 最终验收：
  `GRADLE_USER_HOME=D:/Android/GradleCache ./gradlew verifyThirdPartyNotices :app:testDebugUnitTest --tests "com.example.englishlearning.notices.ThirdPartyNoticesTest" detekt ktlintCheck :app:assembleDebug --no-daemon --no-build-cache`
  - 结果：通过，`BUILD SUCCESSFUL in 58s`；`verifyThirdPartyNotices` 输出 `Validated 27 third-party notice entries.`

## 设计偏差

- 无范围偏差：没有改动 minSdk、namespace、版本策略、Task 1 之外的产品功能，`app/build.gradle.kts` 的 `check` 对根 `verifyThirdPartyNotices`、`detekt`、`ktlintCheck` 的依赖保持不变。
- 执行期间曾因共享 `D:/Android/GradleCache` 的 `.lock` / build cache 文件被拒绝访问而失败；停止 Gradle daemon 后以 `--no-daemon` 重试成功。最终验收在同一纯英文工作树成功。

## 后续建议

- CI 应在 ASCII 路径运行，直至移除当前 `android.overridePathCheck=true` 临时环境配置。
- 后续新增 catalog alias 时，应先补台账条目；该覆盖测试会阻止遗漏。
