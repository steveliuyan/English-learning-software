package com.example.englishlearning.ui

import com.example.englishlearning.ui.components.PayloadKind
import com.example.englishlearning.ui.components.destructiveDialogBody
import com.example.englishlearning.ui.components.mustAskEveryTime
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
     * AGENTS.md 硬要求的纯函数化：**图片每次都问，文本按域名记忆**。
     *
     * 四格真值表全钉，重点是「Image + 已记住域名」这一格——它必须仍然要问。
     * 出站确认是隐私安全面（图片发送每次都需明确确认），UI 轮只能加强不能软化；
     * 把它写成纯函数是为了让「记忆域名」这个便利功能在图片上永远开不了口子
     * （批 3 各屏接 `rememberedForHost` 时读的就是这一格）。
     */
    @Test
    fun image_payload_always_asks_even_when_the_host_is_remembered() {
        assertTrue(
            mustAskEveryTime(PayloadKind.Image, rememberedForHost = true),
            "图片即使域名已记住也必须每次确认（AGENTS.md：图片发送每次都需明确确认）"
        )
        assertTrue(
            mustAskEveryTime(PayloadKind.Image, rememberedForHost = false),
            "图片未记住域名时当然要问"
        )
        assertTrue(
            mustAskEveryTime(PayloadKind.Text, rememberedForHost = false),
            "文本首次发往该域名必须问（AGENTS.md：首次调用第三方 Endpoint 前展示域名并确认）"
        )
        assertFalse(
            mustAskEveryTime(PayloadKind.Text, rememberedForHost = true),
            "文本按域名记忆：已确认过的域名不再逐次打断（AGENTS.md 的「文本按域名记忆」口径）"
        )
    }

    /**
     * 出站确认的正文必须点名三件事：**域名**、**会连密钥一起外发**、**发的是文本还是图片**。
     *
     * 域名逐字出现在文案里（不是「该服务」这种含糊指代），因为 AGENTS.md 要求「首次调用第三方
     * Endpoint 前展示域名」；密钥外发必须说，否则用户以为只发了内容。
     * 图片那一档还得多说一句「每次都问」——它解释了为什么这次确认不能一劳永逸。
     */
    @Test
    fun outbound_body_names_host_key_and_payload_kind() {
        val text = outboundConfirmBody("api.example.com", PayloadKind.Text)
        assertTrue(text.contains("api.example.com"), "文本档必须点名域名：$text")
        assertTrue(text.contains("密钥"), "必须说明密钥会一起外发：$text")
        assertTrue(text.contains("文本"), "必须说明外发的是文本：$text")
        assertFalse(text.contains("图片"), "文本档不得混进图片措辞（两档措辞必须可区分）：$text")

        val image = outboundConfirmBody("vision.example.com", PayloadKind.Image)
        assertTrue(image.contains("vision.example.com"), "图片档必须点名域名：$image")
        assertTrue(image.contains("密钥"), "图片档同样要说明密钥外发：$image")
        assertTrue(image.contains("图片"), "图片档必须说明外发的是图片：$image")
        assertTrue(
            image.contains("每次"),
            "图片档必须当场告知「每次都要确认」（与 mustAskEveryTime 的 Image 格同口径）：$image"
        )
        assertTrue(image != text, "两档措辞不得逐字相同，否则 payloadKind 形参就是装饰")
    }

    /**
     * 反空转（本棒自补）：换域名必须换文案。把域名写死成常量（或漏掉插值）时本条红——
     * 「展示域名」是硬要求，展示别的域名等于没展示。
     */
    @Test
    fun outbound_body_follows_the_actual_host() {
        val a = outboundConfirmBody("one.example.com", PayloadKind.Text)
        val b = outboundConfirmBody("two.example.com", PayloadKind.Text)
        assertTrue(a.contains("one.example.com") && !a.contains("two.example.com"), "域名必须来自入参：$a")
        assertTrue(b.contains("two.example.com") && !b.contains("one.example.com"), "域名必须来自入参：$b")
    }
}
