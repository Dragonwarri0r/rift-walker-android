package com.riftwalker.aidebug.runtime

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.riftwalker.aidebug.protocol.CapabilityListRequest
import com.riftwalker.aidebug.protocol.CapabilityListResponse
import com.riftwalker.aidebug.protocol.NetworkClearRulesRequest
import com.riftwalker.aidebug.protocol.ProtocolJson
import com.riftwalker.aidebug.protocol.RuntimeAuth
import com.riftwalker.aidebug.protocol.RuntimePingResponse
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.Socket

@RunWith(AndroidJUnit4::class)
class RuntimeEndpointInstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun resetRuntimeBeforeTest() {
        AiDebugRuntime.stop()
    }

    @After
    fun stopRuntimeAfterTest() {
        AiDebugRuntime.stop()
    }

    @Test
    fun endpointRequiresSessionTokenAndReadsUtf8Bodies() {
        val port = AiDebugRuntime.start(context, port = 0)
        assertTrue("Runtime endpoint should start on an ephemeral localhost port", port > 0)

        val ping = request(port = port, method = "GET", path = "/runtime/ping")
        assertEquals(200, ping.status)
        val identity = ProtocolJson.json.decodeFromString<RuntimePingResponse>(ping.body)
        assertTrue(identity.debuggable)
        assertTrue(identity.sessionId.isNotBlank())
        assertTrue(identity.sessionToken.isNotBlank())

        val rejected = request(
            port = port,
            method = "POST",
            path = "/capabilities/list",
            body = ProtocolJson.json.encodeToString(CapabilityListRequest()),
        )
        assertEquals(401, rejected.status)

        val unicodeRequest = """{"kind":"all","query":"runtime","note":"会员🚀"}"""
        val accepted = request(
            port = port,
            method = "POST",
            path = "/capabilities/list",
            token = identity.sessionToken,
            body = unicodeRequest,
        )
        assertEquals(200, accepted.status)
        val capabilities = ProtocolJson.json.decodeFromString<CapabilityListResponse>(accepted.body)
        assertTrue(capabilities.capabilities.isNotEmpty())
        assertTrue(capabilities.capabilities.any { it.path == "runtime.ping" })

        val cleared = request(
            port = port,
            method = "POST",
            path = "/network/clearRules",
            token = identity.sessionToken,
            body = ProtocolJson.json.encodeToString(NetworkClearRulesRequest(ruleIds = emptyList())),
        )
        assertEquals(200, cleared.status)
        assertTrue(cleared.body.startsWith("{"))
    }

    private fun request(
        port: Int,
        method: String,
        path: String,
        token: String? = null,
        body: String? = null,
    ): HttpResult {
        val socket = Socket()
        socket.soTimeout = 5_000
        socket.connect(InetSocketAddress("127.0.0.1", port), 2_000)
        socket.use {
            val bodyBytes = body?.toByteArray(Charsets.UTF_8) ?: ByteArray(0)
            val headers = buildString {
                append(method).append(' ').append(path).append(" HTTP/1.1\r\n")
                append("Host: 127.0.0.1:").append(port).append("\r\n")
                append("Accept: application/json\r\n")
                token?.let {
                    append(RuntimeAuth.SESSION_TOKEN_HEADER).append(": ").append(it).append("\r\n")
                }
                if (body != null) {
                    append("Content-Type: application/json; charset=utf-8\r\n")
                    append("Content-Length: ").append(bodyBytes.size).append("\r\n")
                }
                append("Connection: close\r\n")
                append("\r\n")
            }
            it.getOutputStream().write(headers.toByteArray(Charsets.ISO_8859_1))
            if (bodyBytes.isNotEmpty()) {
                it.getOutputStream().write(bodyBytes)
            }
            it.getOutputStream().flush()

            return readResponse(it.getInputStream())
        }
    }

    private fun readResponse(input: InputStream): HttpResult {
        val statusLine = readAsciiLine(input).orEmpty()
        val status = statusLine.split(" ").getOrNull(1)?.toIntOrNull() ?: -1

        var contentLength = 0
        while (true) {
            val line = readAsciiLine(input) ?: break
            if (line.isEmpty()) break
            val separator = line.indexOf(':')
            if (separator > 0 && line.substring(0, separator).equals("content-length", ignoreCase = true)) {
                contentLength = line.substring(separator + 1).trim().toIntOrNull() ?: 0
            }
        }

        val body = if (contentLength > 0) {
            String(readExact(input, contentLength), Charsets.UTF_8)
        } else {
            ""
        }
        return HttpResult(status = status, body = body)
    }

    private fun readAsciiLine(input: InputStream): String? {
        val buffer = ByteArrayOutputStream()
        while (true) {
            val next = input.read()
            if (next == -1) {
                if (buffer.size() == 0) return null
                break
            }
            if (next == '\n'.code) break
            buffer.write(next)
        }

        val bytes = buffer.toByteArray()
        val length = if (bytes.lastOrNull() == '\r'.code.toByte()) bytes.size - 1 else bytes.size
        return String(bytes, 0, length, Charsets.ISO_8859_1)
    }

    private fun readExact(input: InputStream, length: Int): ByteArray {
        val bytes = ByteArray(length)
        var offset = 0
        while (offset < length) {
            val read = input.read(bytes, offset, length - offset)
            check(read != -1) { "Response body ended before content-length bytes were read" }
            offset += read
        }
        return bytes
    }

    private data class HttpResult(
        val status: Int,
        val body: String,
    )
}
