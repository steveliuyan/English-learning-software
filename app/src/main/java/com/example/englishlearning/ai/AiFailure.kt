package com.example.englishlearning.ai

/**
 * AI 调用的失败分类。
 *
 * 每个成员都是**不带字段的 `data object`**，用户可见文案只能来自 [AiFailureUiText]。这不是风格
 * 偏好，而是泄露防线：只要没有任何成员能携带自由文本，原始响应体、Endpoint 与 Key 就无法顺着
 * 错误对象流到界面、日志或截图里。要加字段的话 [AiFailureTest] 的文案扫描会立刻失败。
 */
sealed interface AiFailure {
    val uiText: AiFailureUiText

    /** 还没有任何可用的 AI Profile，或 Profile 没有 Key。 */
    data object NotConfigured : AiFailure {
        override val uiText: AiFailureUiText = AiFailureUiText.NotConfigured
    }

    /** Profile 的能力声明不包含本次需要的模态。 */
    data object CapabilityUnsupported : AiFailure {
        override val uiText: AiFailureUiText = AiFailureUiText.CapabilityUnsupported
    }

    data object NetworkUnavailable : AiFailure {
        override val uiText: AiFailureUiText = AiFailureUiText.NetworkUnavailable
    }

    /** 401/403：Key 被拒绝。与「没配置」分开，因为处置方式不同（换密钥 vs 先添加配置）。 */
    data object Unauthorized : AiFailure {
        override val uiText: AiFailureUiText = AiFailureUiText.Unauthorized
    }

    data object RateLimited : AiFailure {
        override val uiText: AiFailureUiText = AiFailureUiText.RateLimited
    }

    data object ServerUnavailable : AiFailure {
        override val uiText: AiFailureUiText = AiFailureUiText.ServerUnavailable
    }

    data object Timeout : AiFailure {
        override val uiText: AiFailureUiText = AiFailureUiText.Timeout
    }

    /** 用户主动取消。必须与网络类失败可区分，否则会把没坏的网络说成坏了。 */
    data object Cancelled : AiFailure {
        override val uiText: AiFailureUiText = AiFailureUiText.Cancelled
    }

    /** 对方返回了东西，但不满足字段、语言、长度或安全校验。 */
    data object InvalidResponse : AiFailure {
        override val uiText: AiFailureUiText = AiFailureUiText.InvalidResponse
    }
}

/** 用户可见文案。全部是常量，没有一处拼接运行时数据。 */
enum class AiFailureUiText(val message: String) {
    NotConfigured("还没有配置 AI 服务，先去「设置 · AI」添加一套。"),
    CapabilityUnsupported("这套配置没开启所需能力，换一个模型或补上能力声明。"),
    NetworkUnavailable("网络不可用，检查网络后重试。"),
    Unauthorized("密钥被拒绝，去「设置 · AI」换一份有效的密钥。"),
    RateLimited("请求太频繁或额度用尽，稍后再试。"),
    ServerUnavailable("对方服务暂时不可用，稍后再试。"),
    Timeout("等待超时，稍后再试。"),
    Cancelled("已取消。"),
    InvalidResponse("返回内容不合格，可以重新生成。"),
}

/** 失败之后用户能做的动作。文案直接当按钮用，所以保持短。 */
enum class UserAction(val label: String) {
    ConfigureProfile("去配置"),
    SwitchModel("更换模型"),
    CheckNetwork("检查网络"),
    RetryLater("稍后重试"),
    RegenerateContent("重新生成"),
    Dismiss("知道了"),
}

/**
 * 把失败翻译成下一步动作。
 *
 * `when` 对 sealed 接口做穷尽检查，所以将来新增失败类型时会**编译失败**，不会悄悄落进
 * 某个 `else` 分支给用户一个错的动作。
 */
fun AiFailure.toUserAction(): UserAction = when (this) {
    AiFailure.NotConfigured -> UserAction.ConfigureProfile
    AiFailure.CapabilityUnsupported -> UserAction.SwitchModel
    AiFailure.NetworkUnavailable -> UserAction.CheckNetwork
    // Key 被拒的处置是回配置里换密钥，不是「重试」——重试同一份无效 Key 永远不会成功。
    AiFailure.Unauthorized -> UserAction.ConfigureProfile
    AiFailure.RateLimited -> UserAction.RetryLater
    AiFailure.ServerUnavailable -> UserAction.RetryLater
    AiFailure.Timeout -> UserAction.RetryLater
    AiFailure.Cancelled -> UserAction.Dismiss
    AiFailure.InvalidResponse -> UserAction.RegenerateContent
}
