package com.example.englishlearning.learning

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 词书可见性策略。
 *
 * 界面只展示两类词书：
 * 1. **随包内置**的官方册（id 来自 `assets/wordbooks/metadata.json`）；
 * 2. 用户自己**导入**且包目录确实存在的册。
 *
 * 早期版本写入数据库的占位分组册（id 既不在内置清单、也没有导入包）在此被过滤：
 * 对用户而言等同“已删除”，但学习事件与复习状态**保留**，符合 F1-01
 * 「删除本地词书内容前默认保留学习历史」的约定——不静默销毁任何历史。
 */
object WordBookVisibility {
    fun visible(
        all: List<WordBook>,
        bundledIds: Set<String>,
        importedIds: Set<String>,
    ): List<WordBook> = all.filter { it.id in bundledIds || it.id in importedIds }

    /** 只有用户导入的册可删；内置册永不可删。 */
    fun deletable(
        bookId: String,
        bundledIds: Set<String>,
        importedIds: Set<String>,
    ): Boolean = bookId !in bundledIds && bookId in importedIds
}

/**
 * 从 `metadata.json` 原文解析内置词书 id 集合。
 *
 * 只取 `id` 字段：可见性是**身份**判断，不该被展示文案或字段增减影响，
 * 因此这里比 [SeedWordBooksUseCase] 的入库校验更宽松（单独缺字段的条目直接跳过，
 * 而不是让整份清单解析失败）。
 */
fun bundledWordBookIds(rawMetadataJson: String): Set<String> {
    val elements = try {
        Json.parseToJsonElement(rawMetadataJson).jsonArray
    } catch (_: Exception) {
        return emptySet()
    }
    return elements.mapNotNull { element ->
        try {
            element.jsonObject["id"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }.toSet()
}

/** 内置词书 id 的来源端口，生产实现读 assets，测试可直接给固定集合。 */
fun interface BundledWordBookIdSource {
    fun ids(): Set<String>
}

/** 已导入词书 id 的来源端口（只列已发布目录，不含暂存/备份目录）。 */
fun interface ImportedWordBookIdSource {
    fun ids(): Set<String>
}
