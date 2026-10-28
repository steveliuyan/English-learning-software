package com.example.englishlearning.ui

/**
 * 底部导航的四个一级目的地。
 *
 * 声明顺序就是底导里的渲染顺序，属于对外契约：[AppTabTest] 会锁住它，避免改一次排版
 * 就把导航顺序悄悄换掉。
 *
 * 第三格的中文名由用户 2026-11-02 定名：**「AI 助学」**。最初那句口语需求是「在应用底下也加个 ai 学」，
 * 定名后「AI 学」三字退役，标签与无障碍播报同步跟到定名，不做一半。
 */
enum class AppTab(val label: String, val contentDescription: String) {
    LEARNING("学习", "学习"),
    READING("阅读", "阅读"),
    AI("AI 助学", "AI 助学"),
    SETTINGS("我的", "我的"),
}
