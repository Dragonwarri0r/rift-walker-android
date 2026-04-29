package com.riftwalker.aidebug.runtime.debug

import com.riftwalker.aidebug.protocol.JsonSafeValue
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

object JsonSafeValueCodec {
    private const val MAX_STRING = 512
    private const val MAX_COLLECTION = 20

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

    fun encode(value: Any?, fieldName: String? = null): JsonSafeValue {
        if (fieldName != null && fieldName.isSensitiveName()) {
            return JsonSafeValue(
                value = JsonPrimitive("<redacted>"),
                valuePreview = "<redacted>",
                type = value?.javaClass?.name ?: "null",
                redacted = true,
            )
        }

        return when (value) {
            null -> JsonSafeValue(JsonNull, "null", "null")
            is JsonElement -> JsonSafeValue(value, preview(value.toString()), "JsonElement")
            is Boolean -> JsonSafeValue(JsonPrimitive(value), value.toString(), "boolean")
            is Int -> JsonSafeValue(JsonPrimitive(value), value.toString(), "int")
            is Long -> JsonSafeValue(JsonPrimitive(value), value.toString(), "long")
            is Float -> JsonSafeValue(JsonPrimitive(value), value.toString(), "float")
            is Double -> JsonSafeValue(JsonPrimitive(value), value.toString(), "double")
            is Number -> JsonSafeValue(JsonPrimitive(value.toDouble()), value.toString(), value.javaClass.name)
            is CharSequence -> {
                val text = value.toString()
                val truncated = preview(text)
                JsonSafeValue(JsonPrimitive(truncated), truncated, "string", redacted = truncated.length < text.length)
            }
            is Enum<*> -> JsonSafeValue(JsonPrimitive(value.name), value.name, value.javaClass.name)
            is Iterable<*> -> {
                val values = value.take(MAX_COLLECTION).map { encode(it).value ?: JsonNull }
                val truncated = value.drop(MAX_COLLECTION).any()
                JsonSafeValue(JsonArray(values), "Iterable(size>=${values.size})", value.javaClass.name, redacted = truncated)
            }
            is Array<*> -> {
                val values = value.take(MAX_COLLECTION).map { encode(it).value ?: JsonNull }
                JsonSafeValue(JsonArray(values), "Array(size=${value.size})", value.javaClass.name, redacted = value.size > MAX_COLLECTION)
            }
            is Map<*, *> -> JsonSafeValue(
                value = JsonPrimitive(value.toString().take(MAX_STRING)),
                valuePreview = "Map(size=${value.size})",
                type = value.javaClass.name,
                unsupportedReason = "map serialization is summarized",
            )
            else -> JsonSafeValue(
                value = JsonPrimitive("${value.javaClass.name}@${System.identityHashCode(value).toString(16)}"),
                valuePreview = value.toString().take(MAX_STRING),
                type = value.javaClass.name,
                unsupportedReason = "object value summarized",
            )
        }
    }

    fun decode(value: JsonElement, targetType: Class<*>): Any? {
        if (value is JsonNull) return null
        val primitive = value as? JsonPrimitive
            ?: error("Only primitive JSON values are supported for field writes")
        val raw = primitive.content
        return when {
            targetType == java.lang.Boolean.TYPE || targetType == java.lang.Boolean::class.java -> raw.toBooleanStrictOrNull()
                ?: error("Expected boolean value")
            targetType == java.lang.Integer.TYPE || targetType == java.lang.Integer::class.java -> raw.toInt()
            targetType == java.lang.Long.TYPE || targetType == java.lang.Long::class.java -> raw.toLong()
            targetType == java.lang.Float.TYPE || targetType == java.lang.Float::class.java -> raw.toFloat()
            targetType == java.lang.Double.TYPE || targetType == java.lang.Double::class.java -> raw.toDouble()
            targetType == java.lang.String::class.java || CharSequence::class.java.isAssignableFrom(targetType) -> raw
            targetType.isEnum -> targetType.enumConstants?.firstOrNull { (it as Enum<*>).name == raw }
                ?: error("Unknown enum constant '$raw' for ${targetType.name}")
            else -> error("Unsupported field write type: ${targetType.name}")
        }
    }

    private fun String.isSensitiveName(): Boolean {
        val normalized = filter { it.isLetterOrDigit() || it == '_' }.lowercase()
        return sensitiveFragments.any { normalized == it || normalized.contains(it) }
    }

    private fun preview(text: String): String {
        return if (text.length > MAX_STRING) text.take(MAX_STRING) else text
    }
}
