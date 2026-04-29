package com.riftwalker.aidebug.runtime.network

import com.riftwalker.aidebug.protocol.ProtocolJson
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URI

object NetworkProtocolInspector {
    fun inspect(
        method: String,
        url: String,
        headers: Map<String, String>,
        body: String?,
    ): NetworkRequestSnapshot {
        val contentType = headers.contentType()
        val graphql = inspectGraphql(contentType, body)
        val grpc = inspectGrpc(contentType, url)
        return NetworkRequestSnapshot(
            method = method,
            url = url,
            headers = headers,
            body = body,
            protocol = graphql?.let { "graphql" } ?: grpc?.let { "grpc" },
            graphqlOperationName = graphql?.operationName,
            graphqlOperationType = graphql?.operationType,
            graphqlQuery = graphql?.query,
            graphqlVariables = graphql?.variables ?: emptyMap(),
            grpcService = grpc?.service,
            grpcMethod = grpc?.method,
        )
    }

    private fun inspectGraphql(contentType: String?, body: String?): GraphqlMetadata? {
        if (body.isNullOrBlank()) return null
        val looksGraphql = contentType?.contains("json", ignoreCase = true) == true ||
            body.contains("operationName") ||
            body.contains("\"query\"")
        if (!looksGraphql) return null

        return runCatching {
            val root = ProtocolJson.json.parseToJsonElement(body).jsonObject
            val query = root["query"]?.jsonPrimitive?.contentOrNull
            val operationName = root["operationName"]?.jsonPrimitive?.contentOrNull
                ?: query?.let(::inferGraphqlOperationName)
            val operationType = query?.let(::inferGraphqlOperationType)
            val variables = root["variables"]
                ?.takeIf { it is JsonObject }
                ?.jsonObject
                ?.toMap()
                ?: emptyMap()
            if (query == null && operationName == null && variables.isEmpty()) {
                null
            } else {
                GraphqlMetadata(operationName, operationType, query, variables)
            }
        }.getOrNull()
    }

    private fun inspectGrpc(contentType: String?, url: String): GrpcMetadata? {
        if (contentType?.contains("grpc", ignoreCase = true) != true) return null
        val path = runCatching { URI(url).rawPath }.getOrNull().orEmpty().trim('/')
        val parts = path.split('/').filter { it.isNotBlank() }
        if (parts.size < 2) return null
        return GrpcMetadata(service = parts[parts.lastIndex - 1], method = parts.last())
    }

    private fun inferGraphqlOperationName(query: String): String? {
        return GRAPHQL_OPERATION_REGEX.find(query)?.groupValues?.getOrNull(2)?.takeIf { it.isNotBlank() }
    }

    private fun inferGraphqlOperationType(query: String): String? {
        return GRAPHQL_OPERATION_REGEX.find(query)?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() }
    }

    private fun Map<String, String>.contentType(): String? {
        return entries.firstOrNull { it.key.equals("content-type", ignoreCase = true) }?.value
    }

    private data class GraphqlMetadata(
        val operationName: String?,
        val operationType: String?,
        val query: String?,
        val variables: Map<String, JsonElement>,
    )

    private data class GrpcMetadata(
        val service: String,
        val method: String,
    )

    private val GRAPHQL_OPERATION_REGEX = Regex("""\b(query|mutation|subscription)\s+([_A-Za-z][_0-9A-Za-z]*)?""")
}
