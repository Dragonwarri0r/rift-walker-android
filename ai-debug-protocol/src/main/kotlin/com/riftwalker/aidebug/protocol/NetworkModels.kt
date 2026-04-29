package com.riftwalker.aidebug.protocol

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class NetworkMatcher(
    val method: String? = null,
    val urlRegex: String? = null,
    val headers: Map<String, String> = emptyMap(),
    val bodyContains: String? = null,
    val contentTypeContains: String? = null,
    val graphqlOperationName: String? = null,
    val graphqlQueryRegex: String? = null,
    val graphqlVariables: Map<String, JsonElement> = emptyMap(),
    val grpcService: String? = null,
    val grpcMethod: String? = null,
    val scenarioScope: String? = null,
)

@Serializable
data class ResponsePatch(
    val op: String,
    val path: String,
    val value: JsonElement? = null,
)

@Serializable
data class NetworkMockResponse(
    val status: Int = 200,
    val headers: Map<String, String> = emptyMap(),
    val body: JsonElement? = null,
    val bodyText: String? = null,
    val delayMs: Long = 0,
)

@Serializable
data class NetworkFailure(
    val type: String,
    val delayMs: Long = 0,
)

@Serializable
data class NetworkRule(
    val id: String,
    val action: String,
    val match: NetworkMatcher,
    val patch: List<ResponsePatch> = emptyList(),
    val response: NetworkMockResponse? = null,
    val failure: NetworkFailure? = null,
    val times: Int? = null,
    val remaining: Int? = null,
    val scenarioScope: String? = null,
    val createdAtEpochMs: Long,
)

@Serializable
data class NetworkRecord(
    val id: String,
    val method: String,
    val url: String,
    val protocol: String? = null,
    val graphqlOperationName: String? = null,
    val graphqlOperationType: String? = null,
    val grpcService: String? = null,
    val grpcMethod: String? = null,
    val status: Int? = null,
    val durationMs: Long,
    val matchedRuleIds: List<String> = emptyList(),
    val bodyRedacted: Boolean = true,
    val requestBody: String? = null,
    val responseBody: String? = null,
    val originalResponseBody: String? = null,
    val finalResponseBody: String? = null,
    val error: String? = null,
    val timestampEpochMs: Long,
)

@Serializable
data class NetworkHistoryRequest(
    val limit: Int = 50,
    val urlRegex: String? = null,
    val includeBodies: Boolean = false,
)

@Serializable
data class NetworkHistoryResponse(
    val records: List<NetworkRecord>,
)

@Serializable
data class NetworkMutateResponseRequest(
    val match: NetworkMatcher,
    val patch: List<ResponsePatch>,
    val times: Int? = null,
    val scenarioScope: String? = null,
)

@Serializable
data class NetworkMockRequest(
    val match: NetworkMatcher,
    val response: NetworkMockResponse,
    val times: Int? = null,
    val scenarioScope: String? = null,
)

@Serializable
data class NetworkFailRequest(
    val match: NetworkMatcher,
    val failure: NetworkFailure,
    val times: Int? = null,
    val scenarioScope: String? = null,
)

@Serializable
data class NetworkRuleResponse(
    val ruleId: String,
    val restoreToken: String,
)

@Serializable
data class NetworkClearRulesRequest(
    val ruleIds: List<String>? = null,
)

@Serializable
data class NetworkClearRulesResponse(
    val cleared: Int,
)

@Serializable
data class NetworkAssertCalledRequest(
    val match: NetworkMatcher,
    val minCount: Int = 1,
    val timeoutMs: Long = 0,
    val pollIntervalMs: Long = 100,
)

@Serializable
data class NetworkAssertCalledResponse(
    val passed: Boolean,
    val count: Int,
    val recordIds: List<String>,
)

@Serializable
data class NetworkRecordToMockRequest(
    val recordId: String? = null,
    val sourceMatch: NetworkMatcher? = null,
    val targetMatch: NetworkMatcher? = null,
    val responseHeaders: Map<String, String> = emptyMap(),
    val times: Int? = null,
    val scenarioScope: String? = null,
)

@Serializable
data class NetworkRecordToMockResponse(
    val ruleId: String,
    val restoreToken: String,
    val sourceRecordId: String,
    val match: NetworkMatcher,
    val status: Int,
    val bodyCaptured: Boolean,
    val bodyRedacted: Boolean,
)
