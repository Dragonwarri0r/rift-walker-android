package com.riftwalker.aidebug.protocol

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
data class RuntimeIdentity(
    val packageName: String,
    val processId: Int,
    val debuggable: Boolean,
    val apiLevel: Int,
    val runtimeVersion: String,
    val sessionId: String? = null,
)

@Serializable
data class RuntimePingResponse(
    val packageName: String,
    val processId: Int,
    val debuggable: Boolean,
    val apiLevel: Int,
    val runtimeVersion: String,
    val sessionId: String,
    val sessionToken: String,
)

@Serializable
data class RuntimeWaitForPingRequest(
    val timeoutMs: Long = 5_000,
    val pollIntervalMs: Long = 100,
)

@Serializable
data class DebugSession(
    val sessionId: String,
    val packageName: String,
    val token: String,
    val startedAtEpochMs: Long,
    val deviceSerial: String? = null,
    val hostPort: Int? = null,
    val devicePort: Int? = null,
)

@Serializable
data class CapabilityDescriptor(
    val path: String,
    val kind: String,
    val schema: JsonObject,
    val mutable: Boolean,
    val restore: String,
    val audit: String,
    val description: String,
    val tags: List<String> = emptyList(),
    val policy: PolicyMetadata = PolicyMetadata(),
)

@Serializable
data class PolicyMetadata(
    val pii: String = "none",
    val sideEffects: String = "none",
    val cleanup: String = "none",
    val risk: String = "low",
)

@Serializable
data class CapabilityListRequest(
    val kind: String = "all",
    val query: String? = null,
)

@Serializable
data class CapabilityListResponse(
    val capabilities: List<CapabilityDescriptor>,
)

@Serializable
data class AuditEvent(
    val id: String,
    val tool: String,
    val target: String? = null,
    val effect: String,
    val restoreToken: String? = null,
    val status: String,
    val timestampEpochMs: Long,
    val argumentsSummary: String? = null,
    val resultSummary: String? = null,
)

@Serializable
data class AuditHistoryRequest(
    val sessionId: String? = null,
    val sinceEpochMs: Long? = null,
)

@Serializable
data class AuditHistoryResponse(
    val events: List<AuditEvent>,
)

@Serializable
data class ErrorBody(
    val code: String,
    val message: String,
    val recoverable: Boolean,
)

@Serializable
data class ErrorResponse(
    val error: ErrorBody,
)

@Serializable
data class ToolEnvelope(
    val tool: String,
    val arguments: JsonElement? = null,
)

object RuntimeAuth {
    const val SESSION_TOKEN_HEADER = "X-Ai-Debug-Token"
}
