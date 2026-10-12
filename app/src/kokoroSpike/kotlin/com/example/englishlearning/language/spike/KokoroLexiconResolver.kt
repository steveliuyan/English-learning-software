package com.example.englishlearning.language.spike

import java.util.Locale

data class KokoroWordInput(val text: String, val partOfSpeechTag: String? = null)

data class KokoroLexiconResolution(val phonemes: String, val matchedKey: String)

object KokoroLexiconResolver {
    fun resolve(word: String, accentKey: String, tag: String?, entries: Map<String, String>): KokoroLexiconResolution? {
        require(accentKey == "en-us" || accentKey == "en-gb") { "口音不支持" }
        val normalizedWord = word.trim().lowercase(Locale.ROOT)
        require(normalizedWord.length in 1..20 && Regex("[a-z]+(?:'[a-z]+)*").matches(normalizedWord)) { "单词必须为1至20个英语字母" }
        require(tag == null || Regex("[A-Z]+").matches(tag)) { "词性 tag 必须为非空大写字母" }
        val candidates = buildList {
            if (tag != null) {
                add("$normalizedWord|$tag")
                val family = when {
                    tag.startsWith("VB") -> "VERB"
                    tag.startsWith("NN") -> "NOUN"
                    tag.startsWith("JJ") -> "ADJ"
                    tag.startsWith("RB") -> "ADV"
                    else -> tag
                }
                add("$normalizedWord|$family")
            }
            add(normalizedWord)
        }
        for (key in candidates) {
            val phonemes = entries[key] ?: continue
            require(phonemes.isNotEmpty()) { "词典音素为空" }
            return KokoroLexiconResolution(phonemes, key)
        }
        return null
    }
}
