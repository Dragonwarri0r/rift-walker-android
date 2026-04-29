package com.riftwalker.aidebug.runtime.network

import com.riftwalker.aidebug.protocol.NetworkFailRequest
import com.riftwalker.aidebug.protocol.NetworkFailure
import com.riftwalker.aidebug.protocol.NetworkAssertCalledRequest
import com.riftwalker.aidebug.protocol.NetworkHistoryRequest
import com.riftwalker.aidebug.protocol.NetworkMatcher
import com.riftwalker.aidebug.protocol.NetworkMockRequest
import com.riftwalker.aidebug.protocol.NetworkMockResponse
import com.riftwalker.aidebug.protocol.NetworkMutateResponseRequest
import com.riftwalker.aidebug.protocol.NetworkRecordToMockRequest
import com.riftwalker.aidebug.protocol.ResponsePatch
import com.riftwalker.aidebug.runtime.AuditLog
import com.riftwalker.aidebug.runtime.CleanupRegistry
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.BufferedSink
import java.net.SocketTimeoutException
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NetworkControlInterceptorTest {
    @Test
    fun staticMockShortCircuitsRequest() {
        val controller = newController()
        controller.installMock(
            NetworkMockRequest(
                match = NetworkMatcher(method = "GET", urlRegex = ".*/api/profile"),
                response = NetworkMockResponse(status = 200, bodyText = """{"data":{"isVip":true}}"""),
            ),
        )
        val client = OkHttpClient.Builder()
            .addInterceptor(NetworkControlInterceptor(controller))
            .build()

        val response = client.newCall(
            Request.Builder().url("https://example.com/api/profile").build(),
        ).execute()

        assertEquals(200, response.code)
        assertEquals("""{"data":{"isVip":true}}""", response.body?.string())
        val history = controller.history(NetworkHistoryRequest(includeBodies = true)).records
        assertEquals(1, history.size)
        assertEquals(200, history.first().status)
    }

    @Test
    fun mutatesBufferedJsonResponse() {
        val controller = newController()
        controller.installMutation(
            NetworkMutateResponseRequest(
                match = NetworkMatcher(method = "GET", urlRegex = ".*/api/profile"),
                patch = listOf(ResponsePatch("replace", "$.data.isVip", JsonPrimitive(false))),
            ),
        )
        val client = OkHttpClient.Builder()
            .addInterceptor(NetworkControlInterceptor(controller))
            .build()
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(200).setBody("""{"data":{"isVip":true}}"""))
            server.start()

            val response = client.newCall(
                Request.Builder().url(server.url("/api/profile")).build(),
            ).execute()

            assertEquals("""{"data":{"isVip":false}}""", response.body?.string())
        }
    }

    @Test
    fun ruleTimesAreConsumedDeterministically() {
        val controller = newController()
        controller.installMock(
            NetworkMockRequest(
                match = NetworkMatcher(urlRegex = ".*/api/profile"),
                response = NetworkMockResponse(status = 202, bodyText = """{"mocked":true}"""),
                times = 1,
            ),
        )
        val client = OkHttpClient.Builder()
            .addInterceptor(NetworkControlInterceptor(controller))
            .build()
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(203).setBody("""{"real":true}"""))
            server.start()
            val url = server.url("/api/profile")

            val first = client.newCall(Request.Builder().url(url).build()).execute()
            val second = client.newCall(Request.Builder().url(url).build()).execute()

            assertEquals(202, first.code)
            assertEquals(203, second.code)
        }
    }

    @Test
    fun cleanupHookRemovesInstalledRule() {
        val auditLog = AuditLog()
        val cleanupRegistry = CleanupRegistry(auditLog)
        val controller = NetworkController(auditLog, cleanupRegistry)
        controller.installMock(
            NetworkMockRequest(
                match = NetworkMatcher(urlRegex = ".*/api/profile"),
                response = NetworkMockResponse(status = 202, bodyText = """{"mocked":true}"""),
            ),
        )
        cleanupRegistry.cleanupAll()
        val client = OkHttpClient.Builder()
            .addInterceptor(NetworkControlInterceptor(controller))
            .build()
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(203).setBody("""{"real":true}"""))
            server.start()

            val response = client.newCall(
                Request.Builder().url(server.url("/api/profile")).build(),
            ).execute()

            assertEquals(203, response.code)
        }
    }

    @Test
    fun injectsTimeoutFailure() {
        val controller = newController()
        controller.installFailure(
            NetworkFailRequest(
                match = NetworkMatcher(urlRegex = ".*/api/profile"),
                failure = NetworkFailure(type = "timeout"),
            ),
        )
        val client = OkHttpClient.Builder()
            .addInterceptor(NetworkControlInterceptor(controller))
            .build()

        assertFailsWith<SocketTimeoutException> {
            client.newCall(Request.Builder().url("https://example.com/api/profile").build()).execute()
        }
        assertTrue(controller.history(NetworkHistoryRequest()).records.first().error?.contains("timeout") == true)
    }

    @Test
    fun assertCalledCanWaitForAsyncNetworkRecord() {
        val controller = newController()
        controller.installMock(
            NetworkMockRequest(
                match = NetworkMatcher(method = "GET", urlRegex = ".*/api/profile"),
                response = NetworkMockResponse(status = 200, bodyText = """{"ok":true}"""),
            ),
        )
        val client = OkHttpClient.Builder()
            .addInterceptor(NetworkControlInterceptor(controller))
            .build()

        val worker = thread {
            Thread.sleep(150)
            client.newCall(Request.Builder().url("https://example.com/api/profile").build())
                .execute()
                .close()
        }

        val response = controller.assertCalled(
            NetworkAssertCalledRequest(
                match = NetworkMatcher(method = "GET", urlRegex = ".*/api/profile"),
                minCount = 1,
                timeoutMs = 1_000,
                pollIntervalMs = 25,
            ),
        )

        worker.join()
        assertTrue(response.passed)
        assertEquals(1, response.count)
    }

    @Test
    fun skipsOneShotRequestBodyPreview() {
        val controller = newController()
        controller.installMock(
            NetworkMockRequest(
                match = NetworkMatcher(method = "POST", urlRegex = ".*/api/upload"),
                response = NetworkMockResponse(status = 200, bodyText = """{"ok":true}"""),
            ),
        )
        val client = OkHttpClient.Builder()
            .addInterceptor(NetworkControlInterceptor(controller))
            .build()
        val oneShotBody = object : RequestBody() {
            override fun contentType() = "text/plain".toMediaType()
            override fun isOneShot(): Boolean = true
            override fun writeTo(sink: BufferedSink) {
                error("one-shot body should not be previewed")
            }
        }

        val response = client.newCall(
            Request.Builder()
                .url("https://example.com/api/upload")
                .post(oneShotBody)
                .build(),
        ).execute()

        assertEquals(200, response.code)
        response.close()
        val record = controller.history(NetworkHistoryRequest(includeBodies = true)).records.first()
        assertNull(record.requestBody)
        assertTrue(record.bodyRedacted)
    }

    @Test
    fun redactsSensitiveResponseFieldsInHistory() {
        val controller = newController()
        controller.installMock(
            NetworkMockRequest(
                match = NetworkMatcher(method = "GET", urlRegex = ".*/api/profile"),
                response = NetworkMockResponse(
                    status = 200,
                    bodyText = """{"data":{"name":"Ada","accessToken":"secret-token","email":"ada@example.com"}}""",
                ),
            ),
        )
        val client = OkHttpClient.Builder()
            .addInterceptor(NetworkControlInterceptor(controller))
            .build()

        client.newCall(Request.Builder().url("https://example.com/api/profile").build()).execute().close()

        val record = controller.history(NetworkHistoryRequest(includeBodies = true)).records.first()
        assertTrue(record.bodyRedacted)
        assertTrue(record.responseBody?.contains(""""name":"Ada"""") == true)
        assertTrue(record.responseBody?.contains(""""accessToken":"<redacted>"""") == true)
        assertTrue(record.responseBody?.contains(""""email":"<redacted>"""") == true)
        assertFalse(record.responseBody.orEmpty().contains("secret-token"))
        assertFalse(record.responseBody.orEmpty().contains("ada@example.com"))
    }

    @Test
    fun recordToMockCapturesRealResponseWithoutConsumingAppResponse() {
        val controller = newController()
        val client = OkHttpClient.Builder()
            .addInterceptor(NetworkControlInterceptor(controller))
            .build()
        MockWebServer().use { server ->
            val body = """{"data":{"isVip":true}}"""
            server.enqueue(MockResponse().setResponseCode(201).setBody(body))
            server.enqueue(MockResponse().setResponseCode(500).setBody("""{"real":false}"""))
            server.start()
            val url = server.url("/api/profile")

            val realResponse = client.newCall(Request.Builder().url(url).build()).execute()

            assertEquals(201, realResponse.code)
            assertEquals(body, realResponse.body?.string())
            val record = controller.history(NetworkHistoryRequest(includeBodies = true)).records.first()
            assertEquals(body, record.responseBody)

            val generated = controller.recordToMock(
                NetworkRecordToMockRequest(recordId = record.id, times = 1),
            )
            assertTrue(generated.bodyCaptured)
            assertEquals(201, generated.status)

            val mockedResponse = client.newCall(Request.Builder().url(url).build()).execute()

            assertEquals(201, mockedResponse.code)
            assertEquals(body, mockedResponse.body?.string())
            assertEquals(1, server.requestCount)
        }
    }

    @Test
    fun recordToMockUsesRedactedCapturedBody() {
        val controller = newController()
        val client = OkHttpClient.Builder()
            .addInterceptor(NetworkControlInterceptor(controller))
            .build()
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("content-type", "application/json")
                    .setBody("""{"data":{"name":"Ada","accessToken":"secret-token"}}"""),
            )
            server.enqueue(MockResponse().setResponseCode(500).setBody("""{"real":false}"""))
            server.start()
            val url = server.url("/api/profile")

            val realResponse = client.newCall(Request.Builder().url(url).build()).execute()

            assertTrue(realResponse.body?.string().orEmpty().contains("secret-token"))
            val record = controller.history(NetworkHistoryRequest(includeBodies = true)).records.first()
            assertTrue(record.bodyRedacted)
            val generated = controller.recordToMock(NetworkRecordToMockRequest(recordId = record.id))

            val mockedResponse = client.newCall(Request.Builder().url(url).build()).execute()

            assertTrue(generated.bodyRedacted)
            val mockedBody = mockedResponse.body?.string().orEmpty()
            assertTrue(mockedBody.contains(""""accessToken":"<redacted>""""))
            assertFalse(mockedBody.contains("secret-token"))
        }
    }

    @Test
    fun graphqlOperationNameMatchesStaticMockAndRecordsMetadata() {
        val controller = newController()
        controller.installMock(
            NetworkMockRequest(
                match = NetworkMatcher(
                    method = "POST",
                    urlRegex = ".*/graphql",
                    graphqlOperationName = "GetProfile",
                ),
                response = NetworkMockResponse(status = 200, bodyText = """{"data":{"profile":{"isVip":true}}}"""),
            ),
        )
        val client = OkHttpClient.Builder()
            .addInterceptor(NetworkControlInterceptor(controller))
            .build()
        val requestBody =
            """{"operationName":"GetProfile","query":"query GetProfile { profile { isVip } }","variables":{"userId":"current"}}"""
                .toRequestBody("application/json".toMediaType())

        val response = client.newCall(
            Request.Builder()
                .url("https://example.com/graphql")
                .post(requestBody)
                .build(),
        ).execute()

        assertEquals(200, response.code)
        assertEquals("""{"data":{"profile":{"isVip":true}}}""", response.body?.string())
        val record = controller.history(NetworkHistoryRequest(includeBodies = true)).records.first()
        assertEquals("graphql", record.protocol)
        assertEquals("GetProfile", record.graphqlOperationName)
        assertEquals("query", record.graphqlOperationType)
    }

    @Test
    fun graphqlVariablesMustMatchTopLevelValues() {
        val controller = newController()
        controller.installMock(
            NetworkMockRequest(
                match = NetworkMatcher(
                    method = "POST",
                    urlRegex = ".*/graphql",
                    graphqlOperationName = "GetProfile",
                    graphqlVariables = mapOf("userId" to JsonPrimitive("current")),
                ),
                response = NetworkMockResponse(status = 200, bodyText = """{"mocked":true}"""),
            ),
        )
        val client = OkHttpClient.Builder()
            .addInterceptor(NetworkControlInterceptor(controller))
            .build()
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(200).setBody("""{"real":true}"""))
            server.start()
            val requestBody =
                """{"operationName":"GetProfile","query":"query GetProfile { profile { isVip } }","variables":{"userId":"other"}}"""
                    .toRequestBody("application/json".toMediaType())

            val response = client.newCall(
                Request.Builder()
                    .url(server.url("/graphql"))
                    .post(requestBody)
                    .build(),
            ).execute()

            assertEquals("""{"real":true}""", response.body?.string())
            assertEquals(emptyList(), controller.history(NetworkHistoryRequest()).records.first().matchedRuleIds)
        }
    }

    @Test
    fun graphqlVariablesCanMatchJsonObjects() {
        val controller = newController()
        controller.installMock(
            NetworkMockRequest(
                match = NetworkMatcher(
                    method = "POST",
                    urlRegex = ".*/graphql",
                    graphqlOperationName = "Search",
                    graphqlVariables = mapOf(
                        "filter" to buildJsonObject {
                            put("tier", "vip")
                        },
                    ),
                ),
                response = NetworkMockResponse(status = 200, bodyText = """{"mocked":true}"""),
            ),
        )
        val client = OkHttpClient.Builder()
            .addInterceptor(NetworkControlInterceptor(controller))
            .build()
        val requestBody =
            """{"operationName":"Search","query":"query Search { search { id } }","variables":{"filter":{"tier":"vip"}}}"""
                .toRequestBody("application/json".toMediaType())

        val response = client.newCall(
            Request.Builder()
                .url("https://example.com/graphql")
                .post(requestBody)
                .build(),
        ).execute()

        assertEquals("""{"mocked":true}""", response.body?.string())
    }

    @Test
    fun grpcServiceAndMethodMatchWithoutPreviewingBody() {
        val controller = newController()
        controller.installMock(
            NetworkMockRequest(
                match = NetworkMatcher(
                    grpcService = "com.example.ProfileService",
                    grpcMethod = "GetProfile",
                ),
                response = NetworkMockResponse(
                    status = 200,
                    headers = mapOf("content-type" to "application/grpc"),
                    bodyText = "",
                ),
            ),
        )
        val client = OkHttpClient.Builder()
            .addInterceptor(NetworkControlInterceptor(controller))
            .build()
        val body = object : RequestBody() {
            override fun contentType() = "application/grpc".toMediaType()
            override fun isOneShot(): Boolean = true
            override fun writeTo(sink: BufferedSink) {
                error("gRPC matcher should not preview one-shot bodies")
            }
        }

        val response = client.newCall(
            Request.Builder()
                .url("https://example.com/com.example.ProfileService/GetProfile")
                .post(body)
                .build(),
        ).execute()

        assertEquals(200, response.code)
        val record = controller.history(NetworkHistoryRequest(includeBodies = true)).records.first()
        assertEquals("grpc", record.protocol)
        assertEquals("com.example.ProfileService", record.grpcService)
        assertEquals("GetProfile", record.grpcMethod)
        assertNull(record.requestBody)
        assertTrue(record.bodyRedacted)
    }

    private fun newController(): NetworkController {
        val auditLog = AuditLog()
        return NetworkController(auditLog, CleanupRegistry(auditLog))
    }
}
