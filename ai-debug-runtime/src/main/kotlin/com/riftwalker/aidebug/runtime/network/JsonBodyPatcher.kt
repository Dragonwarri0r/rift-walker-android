package com.riftwalker.aidebug.runtime.network

import com.riftwalker.aidebug.protocol.ProtocolJson
import com.riftwalker.aidebug.protocol.ResponsePatch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject

object JsonBodyPatcher {
    fun apply(body: String, patches: List<ResponsePatch>): String {
        if (patches.isEmpty()) return body
        var current = ProtocolJson.json.parseToJsonElement(body)
        patches.forEach { patch ->
            current = applyPatch(current, patch)
        }
        return ProtocolJson.json.encodeToString(JsonElement.serializer(), current)
    }

    private fun applyPatch(root: JsonElement, patch: ResponsePatch): JsonElement {
        val tokens = parsePath(patch.path)
        require(tokens.isNotEmpty()) { "Patch path must not be empty: ${patch.path}" }
        return update(root, tokens, patch)
    }

    private fun update(current: JsonElement, tokens: List<PathToken>, patch: ResponsePatch): JsonElement {
        if (tokens.isEmpty()) {
            return when (patch.op) {
                "replace", "add" -> patch.value ?: JsonNull
                "remove" -> JsonNull
                else -> error("Unsupported patch op: ${patch.op}")
            }
        }

        return when (val token = tokens.first()) {
            is PathToken.Key -> {
                val obj = current as? JsonObject ?: JsonObject(emptyMap())
                if (tokens.size == 1 && patch.op == "remove") {
                    JsonObject(obj.filterKeys { it != token.name })
                } else {
                    JsonObject(
                        obj.toMutableMap().apply {
                            val child = obj[token.name] ?: JsonObject(emptyMap())
                            put(token.name, update(child, tokens.drop(1), patch))
                        },
                    )
                }
            }
            is PathToken.Index -> {
                val arr = current as? JsonArray ?: JsonArray(emptyList())
                val values = arr.toMutableList()
                while (values.size <= token.index) values += JsonNull
                if (tokens.size == 1 && patch.op == "remove") {
                    if (token.index in values.indices) values.removeAt(token.index)
                } else {
                    values[token.index] = update(values[token.index], tokens.drop(1), patch)
                }
                JsonArray(values)
            }
        }
    }

    private fun parsePath(path: String): List<PathToken> {
        require(path.startsWith("$")) { "Only JSONPath values starting with '$' are supported: $path" }
        val tokens = mutableListOf<PathToken>()
        var index = 1
        while (index < path.length) {
            when (path[index]) {
                '.' -> {
                    index += 1
                    val start = index
                    while (index < path.length && path[index] != '.' && path[index] != '[') index += 1
                    val key = path.substring(start, index)
                    require(key.isNotBlank()) { "Blank path segment in $path" }
                    tokens += PathToken.Key(key)
                }
                '[' -> {
                    val end = path.indexOf(']', index)
                    require(end > index) { "Unclosed array index in $path" }
                    val raw = path.substring(index + 1, end)
                    tokens += PathToken.Index(raw.toInt())
                    index = end + 1
                }
                else -> error("Unsupported JSONPath syntax at ${path.substring(index)}")
            }
        }
        return tokens
    }

    private sealed interface PathToken {
        data class Key(val name: String) : PathToken
        data class Index(val index: Int) : PathToken
    }
}
