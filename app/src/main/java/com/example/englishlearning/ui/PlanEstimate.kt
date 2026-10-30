package com.example.englishlearning.ui

/**
 * 批 1 Task 12（学习设置屏 = 原型 `#/plan-edit` + `#/card-settings` 的合并屏）的两句**会算错的话**。
 *
 * 这两句都带数字或流程承诺，所以从屏侧拆成纯函数：布局契约只认字面量出现没出现，判不出
 * 「4308 词还要 756 天」这类算错的数；算式归算式，排版归排版，两边各自可测。
 * 判据见 `PlanEstimateTest`。
 *
 * 原型给的是一句写死的样例（`按 4 个/天算，四级 4308 词大约还要 756 天；按 20 个/天算约 151 天`），
 * 而它自己就不自洽（4308 ÷ 4 = 1077，不是 756；后半句 4308 ÷ 20 = 215.4，也不是 151）。
 * 本仓按「样例指标一律绑真数据」的既有裁定（2026-10-29）改成现算：词量读不到就整句不显示，
 * 不退化成样例数字——AGENTS.md 与本阶段口径都禁止编造指标。
 */

/**
 * 「每日新增」那节的预估句（原型 `.caption`）。
 *
 * 只取档位集合的**最低与最高**两档各说一遍（原型也是 `按 4 个/天 …` 与 `按 20 个/天 …` 两档），
 * 中间档全列出来会把一行 caption 撑成三行，2× 字号下直接挤破这节。
 * 天数**向上取整**：不足一天按一天说，说「215 天」会少排一天。
 *
 * @param bookName 当前所选册的显示名；缺失时宁可不显示这句（没有主语的数字无法核对）。
 * @param remainingWords 该册**尚未学过**的词量（来自 `WordBookProgress.unlearned`），不是册的总词数；
 *   0 / 负数 / null 都视为读不到。
 * @param options 每日新增档位集合，默认与本屏 chip 行同一份 `DAILY_TARGET_OPTIONS`
 *   （声明在 `AppScreen.kt`，同包可见——预估句与 chip 行共用一份档位，不在这里另抄一遍 3/5/10/20）。
 */
internal fun planEstimateText(
    bookName: String?,
    remainingWords: Int?,
    options: List<Int> = DAILY_TARGET_OPTIONS,
): String? {
    val name = bookName?.takeIf { it.isNotBlank() } ?: return null
    val remaining = remainingWords?.takeIf { it > 0 } ?: return null
    val rates = options.filter { it > 0 }.distinct().sorted()
    if (rates.isEmpty()) return null
    val lowest = rates.first()
    val highest = rates.last()
    val lowestDays = ceilDays(remaining, lowest)
    val gap = latinToCjkSpace(name)
    return if (lowest == highest) {
        "按 $lowest 个/天算，$name${gap}剩下 $remaining 词大约还要 $lowestDays 天。"
    } else {
        val highestDays = ceilDays(remaining, highest)
        "按 $lowest 个/天算，$name${gap}剩下 $remaining 词大约还要 $lowestDays 天；按 $highest 个/天算约 $highestDays 天。"
    }
}

/**
 * 词书名与后随的「剩下」之间要不要留那一格，按原型自己的排版口径：
 * 原型那句是 `按 4 个/天算，四级 4308 词大约还要 756 天`——拉丁/数字与汉字相邻时空一格，汉字与汉字之间不留。
 * 一律留格会把「大学英语四级 剩下」拆出原型没有的那一拍；一律不留会把「A剩下」挤成一坨。
 */
private fun latinToCjkSpace(name: String): String =
    if (name.last().isLetterOrDigit() && name.last().code < 128) " " else ""

private fun ceilDays(remainingWords: Int, perDay: Int): Int =
    (remainingWords + perDay - 1) / perDay

/**
 * 「评分之后怎么走」那节的走向句（原型 `card-settings` 的 `.caption` 前半句）。
 *
 * 原型写的是「当前的走向：评「认识」+（先展开释义，点「下一个词」继续。| 直接翻到下一个词，评分回执用轻提示带过。）」，
 * 本仓照抄有两处会对不上真机：
 *  1. 开关打开时用户落在**词义详情页**，那页主按钮的可见文案是「下一个单词」（`CardDetailScreen`），
 *     原型那枚「下一个词」在它自己的预览卡里；所以开态点名「下一个单词」，关态点名回执上的「下一个词」
 *     （`WordCardScreen` 回执主按钮），两种走向各指各自屏幕上真存在的那枚键。
 *  2. 关态原型说「用轻提示带过」，本仓关态给的是**回执卡**（批 1 Task 10 的 `.rate-receipt`），
 *     不是 snackbar，所以这里说「不展开释义，回执上点…」。
 *
 * 后半句（`评「模糊」或「忘记」一定会展开释义与例句，标记「忘记」的词会在本轮末尾再出现一次。`）
 * **整条不搬**：本仓三档各有各的真开关，「一定会展开」是假话；而「本轮末尾再出现一次」与 AGENTS.md
 * 「每张卡提交一次有效反馈即记为该计划项完成、今日计划是不可变快照」对冲——当天不会再出现，
 * FSRS 的再次排期落在之后的日期。术语按词卡屏口径叫「不认识」，不叫「忘记」。
 */
internal fun revealCaptionText(openDetailOnKnown: Boolean): String =
    if (openDetailOnKnown) {
        "当前的走向：评「认识」先展开释义，点「下一个单词」继续。"
    } else {
        "当前的走向：评「认识」不展开释义，回执上点「下一个词」继续。"
    }
