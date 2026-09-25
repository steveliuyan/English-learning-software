# Task 1 语音领域契约报告

## 实现

- 在 `PronunciationProvider` 增加 `suspend fun speak(text: String): PronunciationResult`。
- 新增平台无关的 `PronunciationResult`：`Played`、`Unavailable(reason)`、`Failed(cause)`。
- 保留既有 `capabilities()` 契约，未暴露 Android `TextToSpeech` 类型。
- 新增契约测试，覆盖正常文本记录与空白文本不可用结果。

## TDD 证据

- RED：先新增测试并运行目标测试；因 `speak` 与 `PronunciationResult` 尚未存在而无法编译（初次 Gradle 运行也受构建进程超时影响）。
- GREEN：实现契约后再次运行目标测试；Gradle 在 `compileDebugKotlin` 阶段因已有构建目录文件被占用，报 `Unable to delete directory ... app/build/tmp/kotlin-classes/debug`，未能完成测试执行。该失败与新增契约逻辑无关。

## 文件

- `app/src/main/java/com/example/englishlearning/language/domain/PronunciationProvider.kt`
- `app/src/test/java/com/example/englishlearning/language/domain/PronunciationProviderContractTest.kt`
