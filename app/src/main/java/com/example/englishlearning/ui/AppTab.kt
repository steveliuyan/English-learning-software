package com.example.englishlearning.ui

/**
 * 底部导航的四个一级目的地。
 *
 * 声明顺序就是底导里的渲染顺序，属于对外契约：[AppTabTest] 会锁住它，避免改一次排版
 * 就把导航顺序悄悄换掉。
 */
enum class AppTab(val label: String, val contentDescription: String) {
    LEARNING("学习", "学习"),
    READING("阅读", "阅读"),
    AI("AI 学", "AI 学"),
    SETTINGS("设置", "设置"),
}
