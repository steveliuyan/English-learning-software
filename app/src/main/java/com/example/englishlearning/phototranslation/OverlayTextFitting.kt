package com.example.englishlearning.phototranslation

/** 文字度量能力：渲染时由 Paint 实现，单元测试用确定性假件（宽度 = 字号 × 系数）。 */
interface OverlayGlyphMetrics {
    fun widthOf(text: String, textSizePx: Float): Float

    fun lineHeightOf(textSizePx: Float): Float
}

/** 一块待覆盖的文字：OCR 包围盒 + 模型给的译文；[eligible] 为 false 的块（低置信）默认不覆盖。 */
data class OverlayBlock(
    val id: Int,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val translation: String,
    val eligible: Boolean = true,
)

data class OverlayRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
}

sealed interface OverlayBlockLayout {
    data class Drawn(
        val id: Int,
        val lines: List<String>,
        val textSizePx: Float,
        val area: OverlayRect,
        val truncated: Boolean,
    ) : OverlayBlockLayout

    data class Skipped(
        val id: Int,
        val reason: OverlaySkipReason,
    ) : OverlayBlockLayout
}

enum class OverlaySkipReason {
    /** 低置信块：默认不覆盖，用户可逐块打开。 */
    NotEligible,

    /** 该块这一轮没有译文（缺 id 或译文空白）：保留原图。 */
    MissingTranslation,

    /** 包围盒几何退化（宽或高不为正）。 */
    DegenerateBlock,

    /** 可读下限字号也放不下：保留原图并标「空间不足」，不压扁。 */
    SpaceInsufficient,
}

/**
 * 原位覆盖的排版求解（F3-04 / AC3-10）。纯计算：不碰 Bitmap、不碰 Canvas，因此可被单元测试逐值钉住。
 *
 * 顺序是固定的：先按块高推字号并钳制 → 从推得字号往下试，直到放下（行数 × 行高 ≤ 可用高）
 * → 到可读下限仍放不下就按可用行数截断加省略号 → 连一行都放不下才放弃该块。
 */
object OverlayTextFitting {

    data class Config(
        /** 可读下限（11sp 等效像素，由调用方按当前 density 换算）。低于它宁可放弃覆盖。 */
        val minTextSizePx: Float,
        /** 字号上限：块再高也不无限放大。 */
        val maxTextSizePx: Float = 72f,
        /** 推得字号的起点比例（相对块高）。 */
        val idealSizeRatio: Float = 0.75f,
        /** 行高 = 字号 × 该系数。 */
        val lineSpacingMultiplier: Float = 1.2f,
        /** 允许向上下各借的空隙（调用方按相邻块间隙的一半传入）。 */
        val expansionPerSidePx: Float = 0f,
        val sizeStepPx: Float = 1f,
    )

    fun fit(
        block: OverlayBlock,
        metrics: OverlayGlyphMetrics,
        config: Config,
    ): OverlayBlockLayout {
        if (!block.eligible) return OverlayBlockLayout.Skipped(block.id, OverlaySkipReason.NotEligible)
        val text = block.translation.trim()
        if (text.isEmpty()) return OverlayBlockLayout.Skipped(block.id, OverlaySkipReason.MissingTranslation)
        if (block.right - block.left <= 0f || block.bottom - block.top <= 0f) {
            return OverlayBlockLayout.Skipped(block.id, OverlaySkipReason.DegenerateBlock)
        }

        val area = OverlayRect(
            left = block.left,
            top = block.top - config.expansionPerSidePx,
            right = block.right,
            bottom = block.bottom + config.expansionPerSidePx,
        )
        val availableHeight = area.height

        val ideal = (block.bottom - block.top) * config.idealSizeRatio
        var size = ideal.coerceIn(config.minTextSizePx, config.maxTextSizePx)
        while (size >= config.minTextSizePx) {
            val lines = wrap(text, area.width, size, metrics)
            if (lines.size * metrics.lineHeightOf(size) <= availableHeight) {
                return OverlayBlockLayout.Drawn(block.id, lines, size, area, truncated = false)
            }
            size -= config.sizeStepPx
        }

        val floorSize = config.minTextSizePx
        val maxLines = (availableHeight / metrics.lineHeightOf(floorSize)).toInt()
        if (maxLines < 1) return OverlayBlockLayout.Skipped(block.id, OverlaySkipReason.SpaceInsufficient)

        val wrapped = wrap(text, area.width, floorSize, metrics)
        val kept = wrapped.take(maxLines).toMutableList()
        val truncated = wrapped.size > maxLines
        if (truncated) {
            kept[kept.lastIndex] = truncateToWidth(kept.last(), area.width, floorSize, metrics)
        }
        return OverlayBlockLayout.Drawn(block.id, kept, floorSize, area, truncated = truncated)
    }

    /** 贪心折行：中文按字断，西文按词断，单行放不下的西文长词再按字符断。 */
    fun wrap(
        text: String,
        maxWidth: Float,
        textSizePx: Float,
        metrics: OverlayGlyphMetrics,
    ): List<String> {
        val lines = ArrayList<String>()
        val current = StringBuilder()
        fun flush() {
            val line = current.toString().trimEnd()
            current.clear()
            if (line.isNotEmpty()) lines += line
        }
        for (token in tokenize(text)) {
            if (token == " ") {
                if (current.isNotEmpty()) current.append(' ')
                continue
            }
            if (metrics.widthOf(token, textSizePx) > maxWidth) {
                // 单个西文长词连整行都放不下：先收尾，再按字符硬断，避免整行溢出。
                flush()
                for (ch in token) {
                    if (current.isNotEmpty() && metrics.widthOf(current.toString() + ch, textSizePx) > maxWidth) {
                        flush()
                    }
                    current.append(ch)
                }
                continue
            }
            if (current.isNotEmpty() && metrics.widthOf(current.toString() + token, textSizePx) > maxWidth) {
                flush()
            }
            current.append(token)
        }
        flush()
        if (lines.isEmpty()) lines += ""
        return lines
    }

    /** 末行放不下时逐字符回退加省略号；连一个字符都放不下就只留省略号。 */
    private fun truncateToWidth(
        line: String,
        maxWidth: Float,
        textSizePx: Float,
        metrics: OverlayGlyphMetrics,
    ): String {
        // 只要进了截断分支，末行就一定带省略号——哪怕这一行本身放得下，内容也已经被切掉了。
        var end = line.length
        while (end > 0) {
            val candidate = line.substring(0, end).trimEnd() + "…"
            if (metrics.widthOf(candidate, textSizePx) <= maxWidth) return candidate
            end--
        }
        return "…"
    }

    private fun tokenize(text: String): List<String> {
        val tokens = ArrayList<String>()
        val word = StringBuilder()
        fun flushWord() {
            if (word.isNotEmpty()) {
                tokens += word.toString()
                word.clear()
            }
        }
        for (ch in text) {
            when {
                ch.isWhitespace() -> {
                    flushWord()
                    tokens += " "
                }
                isCjk(ch) -> {
                    flushWord()
                    tokens += ch.toString()
                }
                else -> word.append(ch)
            }
        }
        flushWord()
        return tokens
    }

    private fun isCjk(ch: Char): Boolean {
        val code = ch.code
        return code in 0x2E80..0x9FFF || code in 0xF900..0xFAFF || code in 0xFE30..0xFE4F || code in 0xFF00..0xFFEF
    }
}
