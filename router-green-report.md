# Task 3 GREEN 领域层报告

## 已完成

- `PronunciationRouter` 构造改为接收 `kokoroProvider`、`systemProvider`、`miMoFactory`、`preferences`，移除 OpenAI factory、enum 与路由分支。
- 新增 `speakWord(word, accent)`：始终直达 Kokoro，不读取偏好，也不向 MiMo/System fallback。
- 新增 `speakSentence(sentence, accent, forceRegenerate)`：SystemTts 直达系统 TTS；MiMo 成功返回，非成功结果才 fallback 到系统 TTS。
- 保留 `PronunciationProvider.speak` 三个兼容入口，并将其作为 sentence 路由入口，避免旧 UI 调用立即崩溃。
- `RoomSpeechPreferenceRepository` 读取旧实体 `selectedEngine = "OpenAi"` 时，只有存在非空 MiMo profile 才规范化为 `MiMo`，否则规范化为 `SystemTts`；保存使用当前 enum 名称，不再能写入 OpenAi，同时保留 `openAiProfileId` 字段。
- 将 `PronunciationRouterTest` 收敛为 focused 语义测试，覆盖 Kokoro 双 accent、MiMo→System fallback、Kokoro 无 fallback、System 偏好及旧 OpenAI 数据不进入 Router 等边界。

## 未运行测试

按任务要求仅进行源码编辑，未执行 Gradle 测试或编译命令。DI/UI 尚未修改，当前工作树中仍有下一任务需要处理的旧 OpenAI 调用方，故不在本步骤验证整仓构建。
