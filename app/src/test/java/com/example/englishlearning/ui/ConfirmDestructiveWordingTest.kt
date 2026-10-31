package com.example.englishlearning.ui

import com.example.englishlearning.ai.AiPayloadKind
import com.example.englishlearning.ui.components.destructiveDialogBody
import com.example.englishlearning.ui.components.outboundConfirmBody
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 批 1 Task 11 Step 1：两枚确认对话框的**措辞**必须是纯函数，不许各屏各写一遍。
 *
 * 为什么措辞也要进 JVM 契约：`ConfirmDestructiveDialog` 要收的是 4 份 `AlertDialog` + 1 份行内
 * （spec §3 表），`OutboundConfirmDialog` 要收的是 5 份出站确认（`SentenceAnalysisScreen.kt:171-206`
 * 一族）——这 10 处的现状就是「级联条数、保留面、不可撤销、域名与密钥会外发」四件事各屏各写，
 * 少写哪一件没人发现。措辞收进纯函数后：① 少一件当场红（本类逐要素断言）；② 业务硬规则不会被
 * UI 轮软化（AGENTS.md「删除级联必须明说不可撤销」「图片发送每次都需明确确认」）。
 *
 * connected 侧读不到「文案为什么这么写」，只能断言字符串在树上；所以四要素齐备、
 * 图片每次确认这两条规则的唯一证据面就是本文件（与 `InlineNoticeKindTest` 钉墨色同理）。
 */
class ConfirmDestructiveWordingTest {

    /** brief Step 1 原文用例：级联条数 + 保留面 + 不可撤销，三件事一次说全。 */
    @Test
    fun body_states_cascade_count_kept_surfaces_and_irreversibility() {
        val body = destructiveDialogBody(
            listOf("会同时删除 12 条复习记录"),
            listOf("收藏与查询历史不受影响"),
            irreversible = true,
        )
        assertTrue(body.any { it.contains("12 条复习记录") }, "级联条数必须原样出现在正文里：$body")
        assertTrue(body.any { it.contains("收藏") }, "不受影响的保留面必须说出来：$body")
        assertTrue(body.any { it.contains("不可撤销") }, "不可撤销必须明说：$body")
    }

    /**
     * brief Step 1 原文用例：`irreversible = true` 时该字样必须出现——这是 AGENTS.md 的业务规则
     * （删除级联确认必须明说不可撤销），不是可以为了「文案好看」省略的修饰。
     */
    @Test
    fun irreversible_flag_cannot_be_forgotten_to_be_nice() {
        val body = destructiveDialogBody(listOf("删除词书《核心 1000》"), emptyList(), irreversible = true)
        assertTrue(body.any { it.contains("不可撤销") }, "级联删除必须明说不可撤销：$body")
    }

    /**
     * 反空转（本棒自补）：`irreversible = false` 时那一行必须真的消失。
     * 没有这条，上一条形同虚设——把「不可撤销」硬写进正文（无视旗标）也能让它恒绿。
     * 现网确有可逆的破坏性入口（例如「重建今日计划」这类可再生成的操作），旗标为假时不该谎称不可撤销。
     */
    @Test
    fun reversible_confirmation_does_not_claim_irreversibility() {
        val body = destructiveDialogBody(listOf("会重排今天的新词队列"), emptyList(), irreversible = false)
        assertFalse(
            body.any { it.contains("不可撤销") },
            "旗标为假时不得声称不可撤销（谎称会让用户放弃本可挽回的操作）：$body"
        )
        assertEquals(listOf("会重排今天的新词队列"), body, "可逆确认的正文只剩调用方给的级联行：$body")
    }

    /**
     * 调用方文案**逐字**透传、顺序固定（级联 → 保留面 → 不可撤销）。
     *
     * 为什么钉顺序：这条对话框的读法是「先看我毁掉什么，再看我保住什么，最后看能不能反悔」；
     * 把「不可撤销」排到最前会让 TalkBack 用户先听到结论再听到条数，级联条数（最具体的信息）
     * 反而被挤到最后一条。也钉「不改写调用方文案」：组件不猜条数、不猜面，具体数字由调用方给。
     */
    @Test
    fun body_keeps_caller_lines_verbatim_and_in_reading_order() {
        val cascade = listOf("会同时删除 12 条复习记录", "会删除 3 张卡片配图")
        val keep = listOf("其他词书不受影响", "导出过的备份不受影响")
        val body = destructiveDialogBody(cascade, keep, irreversible = true)
        assertEquals(cascade + keep + body.last(), body, "级联行在前、保留面居中、不可撤销收尾：$body")
        assertEquals(cascade + keep, body.dropLast(1), "调用方的每一行都必须逐字保留（组件不改写、不合并、不排序）")
        assertTrue(body.last().contains("不可撤销"), "收尾那一行就是不可撤销声明：$body")
    }

    /**
     * 「图片每次都问，文本按域名记忆」这条真值表**不在本类钉了**（U-3，决策 2026-11-03 整合 §1）。
     *
     * 原来这里用 UI 侧的 `mustAskEveryTime(kind, rememberedForHost)` 四格全钉。那枚函数在生产代码里
     * **零调用**——显隐实际由域层 `ai/AiOutboundConfirmation.kt` 的 `requiredConfirmation(profile, kind,
     * confirmedTextHost)` 决定，于是同一规则有两份权威：一份被测试钉得很牢，一份没人调。
     * 四格真值表已经在 `ai/AiRequestPolicyTest` 逐格钉住（首问 / 同域名复用 / 换域名重新问 /
     * Image 即使已记住仍 `Required`），删掉这份 UI 侧副本不减少任何覆盖，
     * 少一份权威才是这次整合要的东西。
     *
     * 本类继续负责的是**措辞**：三件事齐不齐、两档可不可区分、域名是否来自入参。
     */
    @Test
    fun outbound_body_names_host_key_and_payload_kind() {
        val text = outboundConfirmBody("api.example.com", AiPayloadKind.Text)
        assertTrue(text.contains("api.example.com"), "文本档必须点名域名：$text")
        assertTrue(text.contains("密钥"), "必须说明密钥会一起外发：$text")
        assertTrue(text.contains("文本"), "必须说明外发的是文本：$text")
        assertFalse(text.contains("图片"), "文本档不得混进图片措辞（两档措辞必须可区分）：$text")

        val image = outboundConfirmBody("vision.example.com", AiPayloadKind.Image)
        assertTrue(image.contains("vision.example.com"), "图片档必须点名域名：$image")
        assertTrue(image.contains("密钥"), "图片档同样要说明密钥外发：$image")
        assertTrue(image.contains("图片"), "图片档必须说明外发的是图片：$image")
        assertTrue(
            image.contains("每次"),
            "图片档必须当场告知「每次都要确认」（与域层 `requiredConfirmation` 的 Image 格同口径）：$image"
        )
        assertTrue(image != text, "两档措辞不得逐字相同，否则 payloadKind 形参就是装饰")
    }

    /**
     * 反空转（本棒自补）：换域名必须换文案。把域名写死成常量（或漏掉插值）时本条红——
     * 「展示域名」是硬要求，展示别的域名等于没展示。
     */
    @Test
    fun outbound_body_follows_the_actual_host() {
        val a = outboundConfirmBody("one.example.com", AiPayloadKind.Text)
        val b = outboundConfirmBody("two.example.com", AiPayloadKind.Text)
        assertTrue(a.contains("one.example.com") && !a.contains("two.example.com"), "域名必须来自入参：$a")
        assertTrue(b.contains("two.example.com") && !b.contains("one.example.com"), "域名必须来自入参：$b")
    }
}
