package com.example.englishlearning.ui

/**
 * 底部导航的四个一级目的地。
 *
 * 声明顺序就是底导里的渲染顺序，属于对外契约：[AppTabTest] 会锁住它，避免改一次排版
 * 就把导航顺序悄悄换掉。
 *
 * 第三格的中文名由用户 2026-11-02 定名：**「AI 助学」**。最初那句口语需求是「在应用底下也加个 ai 学」，
 * 定名后「AI 学」三字退役，标签与无障碍播报同步跟到定名，不做一半。
 *
 * 2026-11-02 用户裁定回到四栏（`docs/decisions/2026-11-02-profile-hub-and-four-tabs.md`）：
 * 「我的」和「设置」并排做两个一级目的地是重复的——设置本来就在「我的」里面。
 * 底导只保留四个真正并列的目的地；「设置」降为 [PROFILE] 里的子页，只放外观与全局开关，
 * 其余调整入口（学习计划、词书、提醒与发音、AI 服务、数据与备份、关于与许可）都住在 hub 的分组里。
 * 上一版 `2026-10-31-settings-as-fifth-tab.md` 的五栏方案因此废止，`SETTINGS` 不再是枚举成员。
 */
enum class AppTab(val label: String, val contentDescription: String) {
    LEARNING("学习", "学习"),
    READING("阅读", "阅读"),
    AI("AI 助学", "AI 助学"),
    PROFILE("我的", "我的"),
}
