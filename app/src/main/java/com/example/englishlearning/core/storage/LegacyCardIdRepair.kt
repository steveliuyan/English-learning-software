package com.example.englishlearning.core.storage

/**
 * 早期版本写下的 `placeholder:<册>:<词>` 卡片 id 与现在真实词书的 `<册>:<词>` 之间的对齐规则。
 *
 * 为什么需要它：`PlaceholderWordCardSource` 已经不再被 DI 绑定，真实词书一律交付 `<册>:<词>`；
 * 而进度、到期队列、新词去重全都按 cardId 取交集，前缀不同就等于交集恒为空。三处可见后果：
 * 首页「已学」永远是 0、到期复习队列拿到解析不出卡片的 id、已经学过正在复习的词被当成新词重排进当天计划。
 *
 * 这个对象只有纯逻辑（不碰 Android、不碰 SQL），为的是让「复习优先」「同类保留新格式」
 * 这类取舍能在 JVM 单元测试里被钉死——否则每条断言都要开一轮 instrumentation 才能验证。
 */
internal object LegacyCardIdRepair {

    /** 旧格式的前缀。真实册 id 的正则不允许出现冒号，所以这个前缀不可能属于某个真实册名。 */
    const val PLACEHOLDER_PREFIX = "placeholder:"

    /** 计划任务里「到期复习」的 kind。 */
    const val DUE_TASK_KIND = "DUE"

    /** 计划任务里「今日新学」的 kind。 */
    const val NEW_TASK_KIND = "NEW"

    /** 是否是旧格式 id。 */
    fun isLegacy(cardId: String): Boolean = cardId.startsWith(PLACEHOLDER_PREFIX)

    /**
     * 去掉旧格式前缀。只改前缀，**不碰「哪一册的哪个词」**——`<册>:<词>` 正是 `WordCardSource`
     * 现在期望的形式（册 id 由 `cardId.substringBefore(':')` 反解）。
     *
     * 非旧格式的原样返回，所以对同一行重复调用是幂等的。
     */
    fun stripLegacyPrefix(cardId: String): String =
        if (isLegacy(cardId)) cardId.removePrefix(PLACEHOLDER_PREFIX) else cardId

    /**
     * 同一计划里两行改写后会撞 `(planId, cardId)` 主键时，该删掉哪一行。
     *
     * 取舍是**复习优先**：旧行是到期复习、新行是今日新学时，删掉新学那一行。这个词有复习状态、
     * 今天本来就该被复习，让它回去当新词重学一遍等于把「已学」抹掉。
     * 其余情况（同为 DUE、同为 NEW）都是纯重复，保留已经是新格式的那一行。
     *
     * @return 应当被删除的 cardId（两行之一）。
     */
    fun collisionVictim(
        legacyCardId: String,
        legacyTaskKind: String,
        modernCardId: String,
        modernTaskKind: String,
    ): String {
        val legacyWins = legacyTaskKind == DUE_TASK_KIND && modernTaskKind != DUE_TASK_KIND
        return if (legacyWins) modernCardId else legacyCardId
    }
}
