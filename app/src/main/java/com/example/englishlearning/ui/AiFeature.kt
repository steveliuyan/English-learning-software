package com.example.englishlearning.ui

/**
 * 「AI 学」页上的四个功能入口。
 *
 * 这是**产品目录**，不是能力清单：`implemented` 为 `false` 表示该功能的生成逻辑尚未接通，
 * 页面上必须如实标注，因此它必须与 [status] 的措辞保持一致——这条一致性由 `AiFeatureTest`
 * 双向锁定，任何人接通了 AI 却忘了改文案（或反过来把文案改成「可用」而代码没实现）都会
 * 立刻测试失败。
 *
 * @property key 稳定的短标识，用作 testTag 与路由参数，必须是 kebab-case。
 * @property glyph 列表左侧的单字标记。四个功能各用一字，避免引图标库。
 * @property title 卡片标题。
 * @property summary 一行说明，描述这个功能做什么。
 * @property status 面向用户的一句话当前状态，未实现时必须以「未实现」开头。
 * @property implemented 该功能是否已经真正可用。
 * @property dependencies 上线前还差什么，逐条列出，便于用户和开发者看到真实阻塞项。
 */
enum class AiFeature(
    val key: String,
    val glyph: String,
    val title: String,
    val summary: String,
    val status: String,
    val implemented: Boolean,
    val dependencies: List<String>,
) {
    WORD_PASSAGE(
        key = "word-passage",
        glyph = "文",
        title = "词文串学",
        summary = "根据今日所学单词，匹配一篇外刊短文",
        status = "未实现：文章生成需要先接通 AI 网关，且需要你配置一个可用的模型。",
        implemented = false,
        dependencies = listOf(
            "AI 网关：网络客户端与 INTERNET 权限",
            "AI 配置页：填写 Endpoint、模型与 API Key",
            "文章生成与校验：结构、长度、目标词覆盖校验",
        ),
    ),
    CLOZE(
        key = "cloze",
        glyph = "填",
        title = "AI 短文填词",
        summary = "AI 生成短文，选词填空做练习",
        status = "未实现：出题与判分都依赖 AI 网关，尚未开发。",
        implemented = false,
        dependencies = listOf(
            "AI 网关：网络客户端与 INTERNET 权限",
            "题目生成：把今日词编入短文并挖空",
            "判分与错词回收：把答错的词送回复习队列",
        ),
    ),
    LISTENING(
        key = "listening",
        glyph = "听",
        title = "单词随身听",
        summary = "听写听讲，把词听进耳朵里",
        status = "未实现：需要音频播放与缓存策略，尚未开发。",
        implemented = false,
        dependencies = listOf(
            "发音播放：系统 TTS 优先，需处理英语离线语音包缺失",
            "听写模式：按今日词表顺序播放并等待输入",
            "音频缓存：仅在接入云端语音时才需要",
        ),
    ),
    COACH(
        key = "coach",
        glyph = "讲",
        title = "单词串讲",
        summary = "今日词与常错词逐个讲解，可以追问",
        status = "未实现：讲解与追问需要 AI 网关与多轮对话支持，尚未开发。",
        implemented = false,
        dependencies = listOf(
            "AI 网关：网络客户端与 INTERNET 权限",
            "错词数据：从复习事件里挑出高频遗忘的词",
            "多轮对话：保留上下文并限制每次请求的输入量",
        ),
    ),
}
