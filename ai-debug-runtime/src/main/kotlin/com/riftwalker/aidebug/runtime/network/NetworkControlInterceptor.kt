package com.riftwalker.aidebug.runtime.network

import com.riftwalker.aidebug.protocol.NetworkMockResponse
import com.riftwalker.aidebug.protocol.NetworkRule
import okhttp3.Headers.Companion.toHeaders
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import java.io.IOException
import java.net.SocketTimeoutException

class NetworkControlInterceptor(
    private val controller: NetworkController,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val start = System.currentTimeMillis()
        val request = chain.request()
        val headers = request.normalizedHeaders()
        val requestBody = request.body?.previewBody() ?: BodyPreview(null, redacted = false)
        val snapshot = NetworkProtocolInspector.inspect(
            method = request.method,
            url = request.url.toString(),
            headers = headers,
            body = requestBody.text,
        )
        val matchedRules = controller.consumeMatchingRules(snapshot)
        val builder = NetworkRecordBuilder(
            method = snapshot.method,
            url = snapshot.url,
            protocol = snapshot.protocol,
            graphqlOperationName = snapshot.graphqlOperationName,
            graphqlOperationType = snapshot.graphqlOperationType,
            grpcService = snapshot.grpcService,
            grpcMethod = snapshot.grpcMethod,
            startedAtEpochMs = start,
            requestBody = requestBody.text,
            bodyRedacted = requestBody.redacted,
            matchedRuleIds = matchedRules.map { it.id },
        )

        return runCatching {
            matchedRules.firstOrNull { it.action == "fail" }?.let { failRule ->
                applyFailure(failRule)
            }

            matchedRules.firstOrNull { it.action == "mock" }?.let { mockRule ->
                val response = mockRule.response ?: NetworkMockResponse()
                delay(response.delayMs)
                val bodyText = response.bodyText ?: response.body?.toString().orEmpty()
                val mocked = buildMockResponse(chain, response, bodyText, start)
                controller.record(builder.finish(mocked.code, bodyText))
                return mocked
            }

            matchedRules.filter { it.action == "delay" }.forEach {
                delay(it.response?.delayMs ?: it.failure?.delayMs ?: 0)
            }

            val original = chain.proceed(request)
            val mutateRules = matchedRules.filter { it.action == "mutate" }
            if (mutateRules.isEmpty()) {
                val responseBody = original.previewBody()
                controller.record(
                    builder.finish(
                        status = original.code,
                        responseBody = responseBody.text,
                        responseBodyRedacted = responseBody.redacted,
                    ),
                )
                original
            } else {
                val mediaType = original.body?.contentType()
                val originalBody = original.body?.string().orEmpty()
                val finalBody = mutateRules.fold(originalBody) { body, rule ->
                    JsonBodyPatcher.apply(body, rule.patch)
                }
                val mutated = original.newBuilder()
                    .body(finalBody.toResponseBody(mediaType))
                    .build()
                controller.record(
                    builder.finish(
                        status = mutated.code,
                        responseBody = finalBody,
                        originalResponseBody = originalBody,
                        finalResponseBody = finalBody,
                    ),
                )
                mutated
            }
        }.getOrElse { error ->
            controller.record(builder.fail(error))
            throw error
        }
    }

    private fun applyFailure(rule: NetworkRule): Nothing {
        val failure = rule.failure ?: throw IOException("Injected network failure")
        delay(failure.delayMs)
        when (failure.type) {
            "timeout" -> throw SocketTimeoutException("Injected timeout by ${rule.id}")
            "disconnect" -> throw IOException("Injected disconnect by ${rule.id}")
            else -> throw IOException("Injected failure '${failure.type}' by ${rule.id}")
        }
    }

    private fun buildMockResponse(
        chain: Interceptor.Chain,
        response: NetworkMockResponse,
        bodyText: String,
        start: Long,
    ): Response {
        val headers = response.headers.toMutableMap()
        val contentType = headers.entries
            .firstOrNull { it.key.equals("content-type", ignoreCase = true) }
            ?.value
            ?: "application/json; charset=utf-8"
        headers.putIfAbsent("content-type", contentType)

        return Response.Builder()
            .request(chain.request())
            .protocol(Protocol.HTTP_1_1)
            .code(response.status)
            .message("Mocked by ai-debug-runtime")
            .headers(headers.toHeaders())
            .body(bodyText.toResponseBody(contentType.toMediaTypeOrNull()))
            .sentRequestAtMillis(start)
            .receivedResponseAtMillis(System.currentTimeMillis())
            .build()
    }

    private fun delay(delayMs: Long) {
        if (delayMs > 0) Thread.sleep(delayMs)
    }

    private fun okhttp3.Request.normalizedHeaders(): Map<String, String> {
        val headers = headers.names().associateWith { name -> headers[name].orEmpty() }.toMutableMap()
        val bodyContentType = body?.contentType()?.toString()
        if (bodyContentType != null && headers.keys.none { it.equals("content-type", ignoreCase = true) }) {
            headers["content-type"] = bodyContentType
        }
        return headers
    }

    private fun okhttp3.RequestBody.previewBody(): BodyPreview? {
        if (isDuplex() || isOneShot()) return BodyPreview(null, redacted = true)
        val length = runCatching { contentLength() }.getOrDefault(-1L)
        if (length < 0 || length > NetworkRedactionPolicy.MAX_BODY_PREVIEW.toLong()) {
            return BodyPreview(null, redacted = true)
        }
        return runCatching {
            val buffer = Buffer()
            writeTo(buffer)
            NetworkRedactionPolicy.captureBody(buffer.readUtf8())
        }.getOrElse {
            BodyPreview(null, redacted = true)
        }
    }

    private fun Response.previewBody(): BodyPreview {
        val body = body ?: return BodyPreview(null, redacted = false)
        val contentType = body.contentType()?.toString()
        if (!contentType.isTextLikeContentType()) return BodyPreview(null, redacted = true)
        val length = runCatching { body.contentLength() }.getOrDefault(-1L)
        if (length > NetworkRedactionPolicy.MAX_BODY_PREVIEW.toLong()) {
            return BodyPreview(null, redacted = true)
        }
        return runCatching {
            val preview = peekBody(NetworkRedactionPolicy.MAX_BODY_PREVIEW.toLong() + 1).string()
            NetworkRedactionPolicy.captureBody(preview)
        }.getOrElse {
            BodyPreview(null, redacted = true)
        }
    }

    private fun String?.isTextLikeContentType(): Boolean {
        if (this == null) return true
        return contains("json", ignoreCase = true) ||
            contains("text", ignoreCase = true) ||
            contains("xml", ignoreCase = true) ||
            contains("form", ignoreCase = true) ||
            contains("graphql", ignoreCase = true) ||
            contains("javascript", ignoreCase = true)
    }
}
