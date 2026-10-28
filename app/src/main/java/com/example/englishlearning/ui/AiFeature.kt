package com.example.englishlearning.ui

import com.example.englishlearning.ui.components.AppIcon

/**
 * 「AI 助学」页上的功能入口。
 *
 * 这是**产品目录**，不是能力清单：`implemented` 为 `false` 表示该功能的生成逻辑尚未接通，
 * 页面上必须如实标注，因此它必须与 [status] 的措辞保持一致——这条一致性由 `AiFeatureTest`
 * 双向锁定，任何人接通了 AI 却忘了改文案（或反过来把文案改成「可用」而代码没实现）都会
 * 立刻测试失败。
 *
 * @property key 稳定的短标识，用作 testTag 与路由参数，必须是 kebab-case。
 * @property glyph 列表左侧的单字标记。各功能各用一字，避免引图标库。
 *   **当前 UI 已不读这一栏**（单字当图标在 spec §4.2 里被否，见 [icon]）；字段保留是因为
 *   `AiFeatureTest` 的「各功能各用一字」契约仍在，删栏要连契约一起裁，不在图标去重这条里顺手做。
 * @property icon 列表左侧与详情页头的字形，**字形的唯一出处**。
 *
 *   2026-11-02 用户评审：「AI 助学」页七枚入口全从同一枚 `AppIcon.Spark` 出，扫列表时只剩标题可读，
 *   分不出哪一行是拍照翻译、哪一行是生图。改用七枚各指一件具体的物（报纸/清单/喇叭/话筒/文档/
 *   相机/画面）。域锚点由渲染处的 `AppDomain.AiSpeech` 配色与页头的品牌渐变承担，字形不再重复
 *   承担「这一屏属于 AI」——那正是七行读成一行时被浪费掉的一整栏语义。
 *
 *   七枚必须两两不同、且逐枚钉在 `AiFeatureIconContractTest` 里：新增功能时不许再借一枚现成字形
 *   顶上（会重新长出「两行一样」），也不许回到屏侧写死字面量（目录里的字形一行都不会生效）。
 * @property title 卡片标题。
 * @property summary 一行说明，描述这个功能做什么。
 * @property status 面向用户的一句话当前状态，未实现时必须以「未实现」开头。
 * @property implemented 该功能是否已经真正可用。
 * @property dependencies 上线前还差什么，逐条列出，便于用户和开发者看到真实阻塞项。
 */
enum class AiFeature(
    val key: String,
    val glyph: String,
    val icon: AppIcon,
    val title: String,
    val summary: String,
    val status: String,
    val implemented: Boolean,
    val dependencies: List<String>,
) {
    WORD_PASSAGE(
        key = "word-passage",
        glyph = "文",
        icon = AppIcon.Newspaper,
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
        icon = AppIcon.List,
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
        icon = AppIcon.Speaker,
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
        icon = AppIcon.Mic,
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
    SENTENCE_ANALYSIS(
        key = "sentence-analysis",
        glyph = "析",
        icon = AppIcon.Document,
        title = "长难句分析",
        summary = "把一句英文拆成主句、从句和短语，逐段给中文解释",
        status = "已可用：去「设置 · AI」选好默认文本服务，回这里输入一句英文即可分析。",
        implemented = true,
        dependencies = listOf(
            "默认文本 AI 服务：在设置里选好一套带密钥的模型",
            "出站确认：按域名确认一次，与文章生成同语义",
        ),
    ),
    PHOTO_TRANSLATION(
        key = "photo-translation",
        glyph = "拍",
        icon = AppIcon.Camera,
        title = "拍照翻译",
        summary = "拍一页英文或从相册选图，识别文字校对后再译成中文",
        status = "已可用：拍照或从相册选图，识别文字可直接编辑，确认后交给 AI 翻译。",
        implemented = true,
        dependencies = listOf(
            "相机权限：首次拍照时请求；拒绝后可改用相册选图",
            "端侧识别：ML Kit 主路径，可安装约 4.1 MB 的 Tesseract 离线备用模型",
            "文本 AI 服务：翻译走默认 Profile，图片不出设备，文本每次发送前逐次确认",
        ),
    ),
    IMAGE_STUDIO(
        key = "image-studio",
        glyph = "画",
        icon = AppIcon.Image,
        title = "AI 生图",
        summary = "用文字 AI 生成画面描述，再交给生图服务出图",
        status = "已可用：去「设置 · AI」添加一套生图服务并设为生图默认，回这里输入主题即可出图。",
        implemented = true,
        dependencies = listOf(
            "生图配置：独立 Profile 指向 OpenAI 兼容生图接口",
            "两段式确认：提示词按域名一次，出图每次都要确认",
            "成本提示：按张计费，通常是文本调用的数十倍",
        ),
    ),
}

/**
 * 状态徽章的唯一产出（批 3 Task 23 / 裁决④）：面向用户回答的是「**现在能不能用**」，
 * 不是「代码里有没有这个枚举」。
 *
 * 徽章面与目录面是**两面不同的话**，本函数只管徽章面：
 *  - 徽章面「现在能用」/「未上线」——用户在列表里扫一眼就知道该点哪一行；
 *  - 目录面 [AiFeature.status] 仍说「已可用：…」/「未实现：…」——那是**一句话进度实话**，
 *    措辞契约（未实现必须以「未实现」开头）由 `AiFeatureTest` 双向锁定，本批一字未改（R-23.4）。
 *
 * 放在与目录同一个文件里（R-23.9）：徽章词是目录属性 `implemented` 的投影，另开组件文件会让
 * 「一份目录两处措辞」重新长出来，也会牵动组件份数棘轮那张未授权的名单。
 *
 * **本函数必须留在文件末尾**：`AiFeatureTest` 里那条徽章契约按「`fun featureStatusBadge(` 之后
 * 即函数本体」取样，一旦把它挪到目录之前，那条用例会先炸在取样前提上，而不是悄悄读到整份目录。
 */
fun featureStatusBadge(implemented: Boolean): String = if (implemented) "现在能用" else "未上线"
