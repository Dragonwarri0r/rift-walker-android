package com.riftwalker.aidebug.runtime.network

data class NetworkRequestSnapshot(
    val method: String,
    val url: String,
    val headers: Map<String, String>,
    val body: String?,
    val protocol: String? = null,
    val graphqlOperationName: String? = null,
    val graphqlOperationType: String? = null,
    val graphqlQuery: String? = null,
    val graphqlVariables: Map<String, kotlinx.serialization.json.JsonElement> = emptyMap(),
    val grpcService: String? = null,
    val grpcMethod: String? = null,
)
