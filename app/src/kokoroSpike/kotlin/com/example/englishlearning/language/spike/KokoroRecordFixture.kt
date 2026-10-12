package com.example.englishlearning.language.spike

import java.security.MessageDigest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

// Pinned, minimal test fixture. Source paths are provenance, never device inputs.
internal object KokoroRecordFixture {
    fun parse(bytes: ByteArray): Map<String, Map<String, String>> {
        require(sha256(bytes) == "8cad6c3d592930156b58b7b62c17b411c03c7e8729709a10dc06d6fe108c78d8") {
            "Record fixture hash mismatch"
        }
        val root = Json.parseToJsonElement(bytes.toString(Charsets.UTF_8)).jsonObject
        require(root.keys == setOf("schema_version", "upstream", "license", "license_path", "license_sha256", "source_files", "lookup_order", "entries"))
        require(root.getValue("schema_version").jsonPrimitive.content == "1")
        require(root.getValue("upstream").jsonPrimitive.content == "IgorSwat/Phonemis@71eb1ce33bd586d38cbac037843b8539d7829c3b")
        require(root.getValue("license").jsonPrimitive.content == "MIT")
        require(root.getValue("license_sha256").jsonPrimitive.content == "f09f9295f76d14fdac54f708716e3d30c712cecb7f7a26a2b23284e56a88231e")
        require(root.getValue("lookup_order").jsonArray.map { it.jsonPrimitive.content } == listOf("record|VB", "record|VERB", "record"))
        val sources = root.getValue("source_files").jsonObject
        val accents = root.getValue("entries").jsonObject
        require(sources.keys == setOf("en-us", "en-gb") && accents.keys == sources.keys)
        val sourceHashes = mapOf(
            "en-us" to "ef0b19a0126455e4216fb08083c8b50f7e85f98f6055738129a89d2095e635d7",
            "en-gb" to "52167ca536a93d56a02e8b1db29f572438cb7103737ef2c3f8dd9dbfc0b35e0a",
        )
        return accents.mapValues { (accent, rows) ->
            val manifest = sources.getValue(accent).jsonObject
            require(manifest.keys == setOf("source_file", "source_path", "bytes", "sha256", "key_hashes", "vb_exact_key_present", "vb_resolved_key"))
            require(manifest.getValue("sha256").jsonPrimitive.content == sourceHashes.getValue(accent))
            require(manifest.getValue("vb_exact_key_present").jsonPrimitive.content == "false")
            require(manifest.getValue("vb_resolved_key").jsonPrimitive.content == "record|VERB")
            val entries = rows.jsonObject.mapValues { (_, value) ->
                require(value.jsonPrimitive.isString)
                value.jsonPrimitive.content
            }
            val hashes = manifest.getValue("key_hashes").jsonObject
            require(entries.keys == setOf("record", "record|VERB") && hashes.keys == entries.keys)
            entries.forEach { (key, value) ->
                require(value.isNotBlank() && sha256(value.toByteArray(Charsets.UTF_8)) == hashes.getValue(key).jsonPrimitive.content)
            }
            entries
        }
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }
}
