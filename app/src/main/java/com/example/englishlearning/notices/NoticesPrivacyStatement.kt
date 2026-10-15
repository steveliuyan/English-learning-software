package com.example.englishlearning.notices

/**
 * F5-04：「关于与开源许可」页固定展示的第三方服务隐私说明。
 * 措辞与 Spec 发布要求逐条对应（无账户 / 无自动云备份 / AI 用户自配 / Key 不导出 / 出站逐次确认）；
 * 发布说明文档与本页共用同一事实口径，改动必须同步过 NoticesPrivacyStatementTest。
 */
fun thirdPartyPrivacyStatements(): List<String> = listOf(
    "无账户：学习数据只保存在本机，不上传任何服务器。",
    "无自动云备份：只有你主动导出时才会生成本地文件。",
    "AI 服务由用户自行配置：未配置或不确认时，不发送任何内容。",
    "API Key 只存系统安全区：不进数据库、日志、导出包，也无法在应用内查看。",
    "文本与照片发送第三方 AI 服务前逐次确认：拒绝确认则零出网。",
)
