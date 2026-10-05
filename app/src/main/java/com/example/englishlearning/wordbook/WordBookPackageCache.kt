package com.example.englishlearning.wordbook

import java.io.File

/**
 * 进程内词书包缓存：以包内文件指纹失效，避免每次查词都重新解析并校验整包。
 * 只缓存解析结果，不改变磁盘内容；解析失败不进入缓存。
 */
class WordBookPackageCache<T>(
    private val parse: (File) -> T?,
) {
    private data class Entry<T>(val fingerprint: PackageFingerprint, val value: T)
    private val entries = mutableMapOf<String, Entry<T>>()

    fun get(directory: File): T? {
        val key = directory.canonicalPath
        val next = PackageFingerprint.from(directory)
        val existing = entries[key]
        if (existing?.fingerprint == next) return existing.value
        val parsed = parse(directory) ?: run { entries.remove(key); return null }
        entries[key] = Entry(next, parsed)
        return parsed
    }
}

private data class PackageFingerprint(
    val files: List<FileStamp>,
) {
    companion object {
        fun from(directory: File): PackageFingerprint = PackageFingerprint(
            directory.walkTopDown()
                .filter { it.isFile }
                .map { FileStamp(it.relativeTo(directory).path, it.length(), it.lastModified()) }
                .sortedBy { it.path }
                .toList(),
        )
    }
}

private data class FileStamp(val path: String, val length: Long, val modifiedAt: Long)
