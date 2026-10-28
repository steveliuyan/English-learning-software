package com.example.englishlearning.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.example.englishlearning.R

/**
 * 本地衬线英文字族：原型 `--serif: 'Newsreader', …`。
 *
 * 原型提供可变字重 woff2（wght 200–800，opsz 6–72）；Android 资源字体不支持 woff2，
 * 也不支持可变轴，故批 0 用 `fonttools` 从原型字源实例化五档静态 TTF（opsz 固定 16pt），
 * 覆盖 400 / 500 / 550 / 600 / 650。字体许可 SIL OFL 1.1，登记在
 * `docs/third-party-notices.md`；不得联网取字或替换字族。
 *
 * 未覆盖字形（中文、IPA、罕见拉丁扩展）由系统字体回退，原型同样回退到 `Songti SC` / 系统 sans。
 */
object AppFont {
    val Serif: FontFamily = FontFamily(
        Font(R.font.newsreader_regular, FontWeight.Normal),
        Font(R.font.newsreader_medium, FontWeight.Medium),
        Font(R.font.newsreader_book, FontWeight(550)),
        Font(R.font.newsreader_semibold, FontWeight.SemiBold),
        Font(R.font.newsreader_bold, FontWeight(650)),
    )
}
