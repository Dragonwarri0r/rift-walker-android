package com.riftwalker.aidebug.runtime.network

import com.riftwalker.aidebug.protocol.NetworkRecord

data class NetworkRecordBuilder(
    val method: String,
    val url: String,
    val protocol: String?,
    val graphqlOperationName: String?,
    val graphqlOperationType: String?,
    val grpcService: String?,
    val grpcMethod: String?,
    val startedAtEpochMs: Long,
    val requestBody: String?,
    val bodyRedacted: Boolean,
    val matchedRuleIds: List<String>,
) {
    fun finish(
        status: Int?,
        responseBody: String?,
        originalResponseBody: String? = null,
        finalResponseBody: String? = null,
        responseBodyRedacted: Boolean = false,
    ): NetworkRecord {
        val responsePreview = NetworkRedactionPolicy.captureBody(responseBody)
        val originalPreview = NetworkRedactionPolicy.captureBody(originalResponseBody)
        val finalPreview = NetworkRedactionPolicy.captureBody(finalResponseBody)
        return NetworkRecord(
            id = NetworkHistoryStore.nextRecordId(),
            method = method,
            url = url,
            protocol = protocol,
            graphqlOperationName = graphqlOperationName,
            graphqlOperationType = graphqlOperationType,
            grpcService = grpcService,
            grpcMethod = grpcMethod,
            status = status,
            durationMs = System.currentTimeMillis() - startedAtEpochMs,
            matchedRuleIds = matchedRuleIds,
            bodyRedacted = bodyRedacted ||
                responseBodyRedacted ||
                responsePreview.redacted ||
                originalPreview.redacted ||
                finalPreview.redacted,
            requestBody = requestBody,
            responseBody = responsePreview.text,
            originalResponseBody = originalPreview.text,
            finalResponseBody = finalPreview.text,
            timestampEpochMs = startedAtEpochMs,
        )
    }

    fun fail(error: Throwable): NetworkRecord {
        return NetworkRecord(
            id = NetworkHistoryStore.nextRecordId(),
            method = method,
            url = url,
            protocol = protocol,
            graphqlOperationName = graphqlOperationName,
            graphqlOperationType = graphqlOperationType,
            grpcService = grpcService,
            grpcMethod = grpcMethod,
            status = null,
            durationMs = System.currentTimeMillis() - startedAtEpochMs,
            matchedRuleIds = matchedRuleIds,
            bodyRedacted = bodyRedacted,
            requestBody = requestBody,
            error = error.message ?: error::class.java.simpleName,
            timestampEpochMs = startedAtEpochMs,
        )
    }
}
