package com.riftwalker.aidebug.protocol

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class ObjectHandle(
    val id: String,
    val label: String? = null,
    val className: String,
    val identityHash: String,
    val registeredAtEpochMs: Long,
)

@Serializable
data class ObjectSearchRequest(
    val query: String,
    val packages: List<String> = emptyList(),
    val includeFields: Boolean = true,
    val limit: Int = 20,
)

@Serializable
data class ObjectSearchResult(
    val handle: String,
    val label: String? = null,
    val className: String,
    val fieldPath: String? = null,
    val valuePreview: String? = null,
    val readable: Boolean,
    val writable: Boolean,
    val redacted: Boolean = false,
    val reason: String? = null,
)

@Serializable
data class ObjectSearchResponse(
    val results: List<ObjectSearchResult>,
)

@Serializable
data class JsonSafeValue(
    val value: JsonElement? = null,
    val valuePreview: String? = null,
    val type: String,
    val redacted: Boolean = false,
    val unsupportedReason: String? = null,
)

@Serializable
data class EvalRequest(
    val language: String = "debug-dsl",
    val code: String,
    val timeoutMs: Long = 2_000,
    val sideEffects: String = "read_only",
)

@Serializable
data class EvalResponse(
    val result: JsonElement? = null,
    val resultType: String,
    val auditEventId: String,
    val cleanupToken: String? = null,
    val sideEffects: String,
    val error: String? = null,
)

@Serializable
data class ProbeDescriptor(
    val id: String,
    val kind: String,
    val target: String,
    val fieldPath: String? = null,
    val methodId: String? = null,
    val readable: Boolean = false,
    val writable: Boolean = false,
    val hookable: Boolean = false,
)

@Serializable
data class ProbeGetFieldRequest(
    val target: String,
    val fieldPath: String,
)

@Serializable
data class ProbeFieldResponse(
    val target: String,
    val fieldPath: String,
    val value: JsonSafeValue,
)

@Serializable
data class ProbeSetFieldRequest(
    val target: String,
    val fieldPath: String,
    val value: JsonElement,
)

@Serializable
data class ProbeSetFieldResponse(
    val target: String,
    val fieldPath: String,
    val value: JsonSafeValue,
    val restoreToken: String? = null,
)

@Serializable
data class HookRule(
    val id: String,
    val methodId: String,
    val whenArgs: List<JsonElement> = emptyList(),
    val action: String,
    val returnValue: JsonElement? = null,
    val throwMessage: String? = null,
    val times: Int? = null,
    val remaining: Int? = null,
    val createdAtEpochMs: Long,
)

@Serializable
data class HookOverrideReturnRequest(
    val methodId: String,
    val whenArgs: List<JsonElement> = emptyList(),
    val returnValue: JsonElement,
    val times: Int? = null,
)

@Serializable
data class HookThrowRequest(
    val methodId: String,
    val whenArgs: List<JsonElement> = emptyList(),
    val message: String = "Injected by ai-debug-runtime",
    val times: Int? = null,
)

@Serializable
data class HookRuleResponse(
    val hookId: String,
    val restoreToken: String,
)

@Serializable
data class HookClearRequest(
    val hookId: String? = null,
    val methodId: String? = null,
)

@Serializable
data class HookClearResponse(
    val cleared: Int,
)
