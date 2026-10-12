# Kokoro Android 显式动词 Spike 设计

## 状态

已获用户批准进行隔离 Android Spike；此批准不代表允许接入生产 Provider、修改生产路由或发布 Kokoro 资产。本文规格完成后需由用户复核，实施计划另行编写。

## 目标

在 Android 真机验证 `record` 的默认名词读音与显式动词 `VB` 读音能否通过词典优先路径合成、播放并导出供人工听感评审，同时保持正式 APK 与生产发音行为不变。

## 非目标

- 不改 `PronunciationRouter`、Hilt 绑定、词卡 UI、MiMo/OpenAI 路由、缓存键或正式 Provider。
- 不实现自动词性标注、完整词典加载或通用词典优先产品能力。
- 不将完整 Phonemis lexicon、模型、音色或 ORT 测试依赖加入正式 APK。
- 不使用 `connectedDebugAndroidTest`，不卸载应用，不清理用户数据，不提交或推送。
- 不因单词测试通过而改变 `NO_GO_KEEP_SYSTEM_TTS`。

## 现状与约束

Android `KokoroAndroidEngine.synthesize(text, accent)` 没有词性参数。其现有实现固定走 grapheme → Phonemis frontend → CTC → Kokoro vocab，因而不能把当前 `record` 输出称作显式动词。桌面隔离探针已有 `select_lexicon(word, accent, lexicons, tag)`：按 `word|tag`、POS 家族键、裸词的优先顺序查找；`record|VB` 对照无 tag 时的默认词典项，并已在英美两种口音生成样本。

本项只需验证 Android 对“显式 tag 命中精确词典项”的接线，不需要把数 MB 的完整 lexicon 放进测试包。使用从既有固定 Phonemis 词典条目核验取得的最小测试 fixture；fixture 记录来源 revision、许可证、原始文件 SHA-256、抽取键和值的校验摘要，并只含验收所需的 `record|VB` 词条。原始 tag 音素必须从已审计词典中提取，不得凭记忆或人工猜写。若桌面固定输入集只通过显式 `VB` 查询到裸词键，而没有 `record|VB` 精确键，则 Android 规格必须忠实复现该已验证语义：断言实际命中的键和 tag 家族回退规则；不得假设精确 tagged 条目存在。

## 方案

在共享 Spike 层增加显式前处理输入类型，例如 `KokoroWordInput(text, partOfSpeechTag)`；既有 `synthesize(text, accent)` 保持行为兼容并转调 tag 为空的新入口。将词典查找抽成可由 JVM 单测验证的窄接口：先查 `normalizedWord|tag`，再按现有桌面规则查 tag 家族，最后查裸词；若有命中，跳过 neural frontend；没有命中时保持当前神经回退。测试夹具只包含本项需要的 record 动词条目，且默认名词基线应使用从相同固定词典审计取得的无 tag 条目，或者明确只比较显式动词命中与既有默认输出，不能伪称覆盖完整词典。

Android instrumentation 对 US/GB 分别生成：无 tag 的 `record`、显式 `VB` 的 `record`。断言包括有效 WAV、播放器返回 `Played`、fixture 命中时 frontend 调用次数为零、词典音素映射与期望一致、tagged/default 摘要按固定预期不同；导出文件使用独立目录和不可覆盖命名。测试若无法证明选中了精确 `record|VB` 项，必须失败，不能退化为 frontend fallback 后仍通过。

## 文件边界

- `app/src/kokoroSpike/kotlin/.../KokoroAndroidEngine.kt`：定义显式输入/tag 契约，并保持旧入口兼容。
- `app/src/kokoroSpike/kotlin/...` 新增的小型 lexicon resolver：实现 tag key 优先级与回退策略，不承载 IO 或 Android 依赖。
- `app/src/test/java/.../language/spike/`：新增 resolver 单测，覆盖 exact tag、POS family、bare-word fallback、miss、无效/空 tag 及不应调用 frontend 的边界。
- `app/src/androidTest/assets/`：只放有来源/许可/哈希记录的精简 record fixture；不放完整 lexicon。
- `app/src/androidTest/java/.../language/spike/KokoroEngineAvailabilityTest.kt`：添加显式名词/动词对照、英美生成/播放/导出断言；既有固定词、未知词、内存采样测试继续保留。
- `outputs/kokoro-spike-20261006/` 与 `.superpowers/sdd/2026-10-07-kokoro-android-spike/`：保存本轮日志、资产审计、试听 WAV、报告与进度记录；这些忽略目录证据不等同于 Git 提交备份。
- `docs/decisions/2026-10-06-kokoro-word-pronunciation.md`：只在真机验收与资产边界核查完成后追加结果，继续标记 `NO_GO_KEEP_SYSTEM_TTS`。

## 测试与验收顺序

1. 固定来源审计：确认 Phonemis 词典 revision、许可、原始 lexicon 文件 SHA-256；独立提取 US/GB 的 `record|VB`（以及如可确认的无 tag 名词项）并保存 fixture manifest。抽取值必须经脚本从源词典读取，不手写音素。
2. JVM RED：先写 resolver 与 Engine 接口测试；运行目标用例，确认失败来自缺少 tag-aware lookup/Engine 行为，而非编译器无关错误。
3. 最小 GREEN：实现精确 tag → POS family → bare fallback 和显式输入适配；验证词典命中时 frontend 不被调用。
4. 独立变异：分别改变 tag key 优先级、绕过 lexicon 强制 frontend、错误映射到默认裸词项；每个变异必须使对应行为断言失败，再还原并复测。
5. 真机构建：运行 Kokoro focused JVM 回归、`compileDebugAndroidTestKotlin`、`assembleDebug` 与 `assembleDebugAndroidTest`。检查正式 debug APK ZIP/DEX/native 项目不含 fixture、ORT、Spike 实现或 Kokoro 资产；测试包边界不符即停止。
6. 设备验收：确认设备 `bf353dda` 与数据库文件存在；测试前拉取主库、SHM、WAL 并记录 MD5；只使用 `adb install -r -t` 原地更新测试 APK；仅用显式 `am instrument` 执行目标类，不运行 connected task。
7. 对每个口音验证默认与显式 VB 两条路径的词典来源、WAV 结构、非空数据、摘要差异和 `Played`；记录准备/合成/播放耗时及周期内存观测。导出到独立新目录，不覆盖现有六段或 workbuddy 样本。
8. 测试后重新拉取主库、SHM、WAL，逐字节比较；任一差异都阻止本项通过并调查归因。
9. 独立回读 WAV，核验 RIFF/WAVE、24 kHz mono PCM16、文件大小、SHA-256 与设备日志一致；用户人工评审新动词样本后单独记录听感结论。

## 失败与安全要求

- 词典 fixture 来源、许可、原始摘要或目标项无法证实时停止，不得手写替代读音。
- 显式 tag 未命中时，本项动词验收必须失败；生产中未来是否允许回退是独立产品决策。
- ONNX/前处理/播放异常需释放临时资源；播放后清零音频缓冲；不触碰 Room、SecretStore、网络或用户配置。
- fixture 仅限 test/androidTest source set；正式 APK 新增任何 Kokoro/Phonemis/ORT 条目均为阻断。
- 不清理工作树已有未提交/未跟踪内容，不使用 destructive Git 操作。

## 通过后的结论

本 Spike 最多证明指定词 `record` 在固定词典条目和指定设备上的显式动词路径能运行并供人工试听；它不证明自动词性标注、任意词准确率、全词典分发许可或生产回退可靠性。全部技术证据完成后，只能进入正式接入评审，不能自动改为 GO；`NO_GO_KEEP_SYSTEM_TTS` 保持有效，直到另行批准生产集成。
