package com.example.englishlearning.core.storage

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 迁移里「撞主键时删哪一行」的取舍守卫。
 *
 * 迁移本身只能在真机上跑，但这条取舍是纯决策。删错了的后果都很隐蔽：少掉一条复习任务，
 * 或者把一个已经学过的词降级回「今日新学」——两者在界面上都只是「数字差一」，肉眼几乎发现不了。
 */
class LegacyCardIdRepairTest {

    @Test
    fun `stripping removes only the prefix and keeps the book and the word`() {
        assertEquals("cet4:ability", LegacyCardIdRepair.stripLegacyPrefix("placeholder:cet4:ability"))
        assertEquals("ngsl-core-10:a", LegacyCardIdRepair.stripLegacyPrefix("placeholder:ngsl-core-10:a"))
    }

    @Test
    fun `stripping is idempotent and never touches a modern id`() {
        val modern = "cet4:ability"
        assertEquals(modern, LegacyCardIdRepair.stripLegacyPrefix(modern))
        assertEquals(
            modern,
            LegacyCardIdRepair.stripLegacyPrefix(LegacyCardIdRepair.stripLegacyPrefix("placeholder:$modern")),
        )
    }

    @Test
    fun `only ids carrying the legacy prefix are treated as legacy`() {
        assertTrue(LegacyCardIdRepair.isLegacy("placeholder:cet4:ability"))
        assertFalse(LegacyCardIdRepair.isLegacy("cet4:ability"))
        assertFalse(LegacyCardIdRepair.isLegacy("cet4:placeholder:ability"))
    }

    /** 复习优先：旧行是 DUE、新格式行是 NEW 时删掉 NEW，让这个学过的词回到复习队列。 */
    @Test
    fun `a due task wins over a new task`() {
        val victim = LegacyCardIdRepair.collisionVictim(
            legacyCardId = "placeholder:cet4:ability",
            legacyTaskKind = LegacyCardIdRepair.DUE_TASK_KIND,
            modernCardId = "cet4:ability",
            modernTaskKind = LegacyCardIdRepair.NEW_TASK_KIND,
        )
        assertEquals("cet4:ability", victim)
    }

    /** 反向也要成立：复习不能被降级成新学，哪怕复习那一行是旧格式。 */
    @Test
    fun `a due task wins even when the due row is the legacy one`() {
        val victim = LegacyCardIdRepair.collisionVictim(
            legacyCardId = "placeholder:cet4:ability",
            legacyTaskKind = LegacyCardIdRepair.NEW_TASK_KIND,
            modernCardId = "cet4:ability",
            modernTaskKind = LegacyCardIdRepair.DUE_TASK_KIND,
        )
        assertEquals("placeholder:cet4:ability", victim)
    }

    /** 同类相撞就是纯重复，保留已经是新格式的那一行。 */
    @Test
    fun `duplicate tasks of the same kind keep the already migrated row`() {
        listOf(LegacyCardIdRepair.DUE_TASK_KIND, LegacyCardIdRepair.NEW_TASK_KIND).forEach { kind ->
            val victim = LegacyCardIdRepair.collisionVictim("placeholder:cet4:a", kind, "cet4:a", kind)
            assertEquals("placeholder:cet4:a", victim, "同为 $kind 时该删的是旧格式那行")
        }
    }
}
