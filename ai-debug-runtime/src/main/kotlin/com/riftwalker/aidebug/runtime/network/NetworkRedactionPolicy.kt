package com.riftwalker.aidebug.runtime.network

import com.riftwalker.aidebug.protocol.ProtocolJson
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject

data class BodyPreview(
    val text: String?,
    val redacted: Boolean,
)

object NetworkRedactionPolicy {
    const val MAX_BODY_PREVIEW = 64 * 1024

    private val sensitiveFragments = listOf(
        "authorization",
        "cookie",
        "token",
        "password",
        "passwd",
        "secret",
        "apikey",
        "api_key",
        "session",
        "credential",
        "email",
        "phone",
        "mobile",
        "address",
        "card",
        "ssn",
    )

    private val jsonPairRegex = Regex(
        pattern = "(?i)(\"(?:access_?token|refresh_?token|token|password|passwd|secret|api_?key|authorization|cookie|session|credential|email|phone|mobile|address|card|ssn)\"\\s*:\\s*)(\"(?:\\\\.|[^\"\\\\])*\"|true|false|null|-?\\d+(?:\\.\\d+)?)",
    )

    private val formPairRegex = Regex(
        pattern = "(?i)(^|&)((?:access_?token|refresh_?token|token|password|passwd|secret|api_?key|authorization|cookie|session|credential|email|phone|mobile|address|card|ssn)=)([^&]*)",
    )

    fun captureBody(body: String?): BodyPreview {
        if (body == null) return BodyPreview(null, redacted = false)
        val truncated = body.length > MAX_BODY_PREVIEW
        val preview = if (truncated) body.take(MAX_BODY_PREVIEW) else body
        val redacted = redactSensitiveValues(preview)
        return BodyPreview(redacted.text, redacted = truncated || redacted.changed)
    }

    private fun redactSensitiveValues(body: String): RedactionResult {
        val jsonRedaction = runCatching {
            val element = ProtocolJson.json.parseToJsonElement(body)
            val redacted = redactJson(element)
            RedactionResult(ProtocolJson.json.encodeToString(JsonElement.serializer(), redacted.element), redacted.changed)
        }.getOrNull()
        if (jsonRedaction != null) return jsonRedaction

        var changed = false
        val jsonLike = jsonPairRegex.replace(body) { match ->
            changed = true
            match.groupValues[1] + "\"<redacted>\""
        }
        val formLike = formPairRegex.replace(jsonLike) { match ->
            changed = true
            match.groupValues[1] + match.groupValues[2] + "<redacted>"
        }
        return RedactionResult(formLike, changed)
    }

    private fun redactJson(element: JsonElement): JsonRedactionResult {
        return when (element) {
            is JsonObject -> {
                var changed = false
                val redacted = buildJsonObject {
                    element.forEach { (key, value) ->
                        if (key.isSensitiveKey()) {
                            changed = true
                            put(key, JsonPrimitive("<redacted>"))
                        } else {
                            val child = redactJson(value)
                            changed = changed || child.changed
                            put(key, child.element)
                        }
                    }
                }
                JsonRedactionResult(redacted, changed)
            }
            is JsonArray -> {
                var changed = false
                val redacted = buildJsonArray {
                    element.forEach { value ->
                        val child = redactJson(value)
                        changed = changed || child.changed
                        add(child.element)
                    }
                }
                JsonRedactionResult(redacted, changed)
            }
            else -> JsonRedactionResult(element, changed = false)
        }
    }

    private fun String.isSensitiveKey(): Boolean {
        val normalized = filter { it.isLetterOrDigit() || it == '_' }.lowercase()
        return sensitiveFragments.any { fragment ->
            normalized == fragment || normalized.contains(fragment)
        }
    }

    private data class RedactionResult(
        val text: String,
        val changed: Boolean,
    )

    private data class JsonRedactionResult(
        val element: JsonElement,
        val changed: Boolean,
    )
}
