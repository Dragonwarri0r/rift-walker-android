package com.riftwalker.aidebug.runtime

import android.content.Context
import com.riftwalker.aidebug.protocol.ActionInvokeRequest
import com.riftwalker.aidebug.protocol.ActionListRequest
import com.riftwalker.aidebug.protocol.AuditHistoryRequest
import com.riftwalker.aidebug.protocol.CapabilityListRequest
import com.riftwalker.aidebug.protocol.DataStorePrefsDeleteRequest
import com.riftwalker.aidebug.protocol.DataStorePrefsGetRequest
import com.riftwalker.aidebug.protocol.DataStorePrefsListRequest
import com.riftwalker.aidebug.protocol.DataStorePrefsSetRequest
import com.riftwalker.aidebug.protocol.EvalRequest
import com.riftwalker.aidebug.protocol.ErrorBody
import com.riftwalker.aidebug.protocol.ErrorResponse
import com.riftwalker.aidebug.protocol.HookClearRequest
import com.riftwalker.aidebug.protocol.HookOverrideReturnRequest
import com.riftwalker.aidebug.protocol.HookThrowRequest
import com.riftwalker.aidebug.protocol.MediaAudioAssertConsumedRequest
import com.riftwalker.aidebug.protocol.MediaAudioClearRequest
import com.riftwalker.aidebug.protocol.MediaAudioHistoryRequest
import com.riftwalker.aidebug.protocol.MediaAudioInjectRequest
import com.riftwalker.aidebug.protocol.MediaCameraAssertConsumedRequest
import com.riftwalker.aidebug.protocol.MediaCameraClearRequest
import com.riftwalker.aidebug.protocol.MediaCameraHistoryRequest
import com.riftwalker.aidebug.protocol.MediaCameraInjectFramesRequest
import com.riftwalker.aidebug.protocol.MediaCameraSnapshotRequest
import com.riftwalker.aidebug.protocol.MediaFixtureDeleteRequest
import com.riftwalker.aidebug.protocol.MediaFixtureListRequest
import com.riftwalker.aidebug.protocol.MediaFixtureRegisterRequest
import com.riftwalker.aidebug.protocol.MediaTargetListRequest
import com.riftwalker.aidebug.protocol.NetworkAssertCalledRequest
import com.riftwalker.aidebug.protocol.NetworkClearRulesRequest
import com.riftwalker.aidebug.protocol.NetworkFailRequest
import com.riftwalker.aidebug.protocol.NetworkHistoryRequest
import com.riftwalker.aidebug.protocol.NetworkMockRequest
import com.riftwalker.aidebug.protocol.NetworkMutateResponseRequest
import com.riftwalker.aidebug.protocol.NetworkRecordToMockRequest
import com.riftwalker.aidebug.protocol.ObjectSearchRequest
import com.riftwalker.aidebug.protocol.OverrideClearRequest
import com.riftwalker.aidebug.protocol.OverrideGetRequest
import com.riftwalker.aidebug.protocol.OverrideListResponse
import com.riftwalker.aidebug.protocol.OverrideSetRequest
import com.riftwalker.aidebug.protocol.PrefsDeleteRequest
import com.riftwalker.aidebug.protocol.PrefsGetRequest
import com.riftwalker.aidebug.protocol.PrefsListRequest
import com.riftwalker.aidebug.protocol.PrefsSetRequest
import com.riftwalker.aidebug.protocol.ProbeGetFieldRequest
import com.riftwalker.aidebug.protocol.ProbeSetFieldRequest
import com.riftwalker.aidebug.protocol.ProtocolJson
import com.riftwalker.aidebug.protocol.RuntimeAuth
import com.riftwalker.aidebug.protocol.SqlExecRequest
import com.riftwalker.aidebug.protocol.SqlQueryRequest
import com.riftwalker.aidebug.protocol.StateDiffRequest
import com.riftwalker.aidebug.protocol.StateGetRequest
import com.riftwalker.aidebug.protocol.StateListRequest
import com.riftwalker.aidebug.protocol.StateResetRequest
import com.riftwalker.aidebug.protocol.StateRestoreRequest
import com.riftwalker.aidebug.protocol.StateSetRequest
import com.riftwalker.aidebug.protocol.StateSnapshotRequest
import com.riftwalker.aidebug.protocol.StorageRestoreRequest
import com.riftwalker.aidebug.protocol.StorageSnapshotRequest
import kotlinx.serialization.encodeToString
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class RuntimeHttpEndpoint(
    private val context: Context,
    requestedPort: Int,
    private val sessions: DebugSessionManager,
    private val capabilities: CapabilityRegistry,
    private val auditLog: AuditLog,
) {
    private val serverSocket = ServerSocket(requestedPort, 16, InetAddress.getByName("127.0.0.1"))
    private val executor: ExecutorService = Executors.newCachedThreadPool()
    private val running = AtomicBoolean(false)

    val port: Int = serverSocket.localPort

    fun start() {
        if (!running.compareAndSet(false, true)) return
        executor.execute {
            while (running.get()) {
                runCatching {
                    val socket = serverSocket.accept()
                    executor.execute { handle(socket) }
                }
            }
        }
    }

    fun stop() {
        running.set(false)
        runCatching { serverSocket.close() }
        executor.shutdownNow()
    }

    private fun handle(socket: Socket) {
        socket.use {
            val request = readRequest(it)
            val response = route(request)
            it.getOutputStream().write(response.toByteArray(Charsets.UTF_8))
            it.getOutputStream().flush()
        }
    }

    private fun readRequest(socket: Socket): HttpRequest {
        val input = socket.getInputStream()
        val requestLine = readAsciiLine(input).orEmpty()
        val parts = requestLine.split(" ")
        val method = parts.getOrNull(0).orEmpty()
        val path = parts.getOrNull(1).orEmpty().substringBefore("?")

        var contentLength = 0
        val headers = linkedMapOf<String, String>()
        while (true) {
            val line = readAsciiLine(input) ?: break
            if (line.isEmpty()) break
            val separator = line.indexOf(':')
            if (separator > 0) {
                val name = line.substring(0, separator).trim()
                val value = line.substring(separator + 1).trim()
                headers[name.lowercase()] = value
                if (name.equals("content-length", ignoreCase = true)) {
                    contentLength = value.toIntOrNull() ?: 0
                }
            }
        }

        val body = if (contentLength > 0) {
            require(contentLength <= MAX_REQUEST_BODY_BYTES) {
                "Request body exceeds $MAX_REQUEST_BODY_BYTES bytes"
            }
            String(readExact(input, contentLength), Charsets.UTF_8)
        } else {
            ""
        }
        return HttpRequest(method = method, path = path, headers = headers, body = body)
    }

    private fun route(request: HttpRequest): String {
        if (request.path != "/runtime/ping" && !sessions.validateToken(request.header(RuntimeAuth.SESSION_TOKEN_HEADER))) {
            return unauthorized()
        }

        return runCatching {
            when (request.path) {
                "/runtime/ping" -> okJson(ProtocolJson.json.encodeToString(AiDebugRuntime.ping(context)))
                "/capabilities/list" -> {
                    val payload = request.body.takeIf { it.isNotBlank() }
                    val query = payload?.let {
                        ProtocolJson.json.decodeFromString<CapabilityListRequest>(it)
                    } ?: CapabilityListRequest()
                    val response = capabilities.list(query.kind, query.query)
                    auditLog.recordRead(
                        tool = "capabilities.list",
                        target = query.kind,
                        status = "success",
                        argumentsSummary = query.query,
                    )
                    okJson(ProtocolJson.json.encodeToString(com.riftwalker.aidebug.protocol.CapabilityListResponse(response)))
                }
                "/audit/history" -> {
                    val payload = request.body.takeIf { it.isNotBlank() }
                    val query = payload?.let {
                        ProtocolJson.json.decodeFromString<AuditHistoryRequest>(it)
                    } ?: AuditHistoryRequest()
                    auditLog.recordRead(
                        tool = "audit.history",
                        target = query.sessionId,
                        status = "success",
                    )
                    okJson(ProtocolJson.json.encodeToString(com.riftwalker.aidebug.protocol.AuditHistoryResponse(auditLog.history(query.sinceEpochMs))))
                }
                "/session/cleanup" -> {
                    val count = sessions.cleanupCurrent()
                    okJson("""{"cleaned":$count}""")
                }
                "/media/capabilities" -> {
                    okJson(ProtocolJson.json.encodeToString(AiDebugRuntime.mediaController().capabilities()))
                }
                "/media/targets/list" -> {
                    val query = decodeBody(request.body, MediaTargetListRequest())
                    okJson(ProtocolJson.json.encodeToString(AiDebugRuntime.mediaController().targets(query)))
                }
                "/media/fixture/register" -> {
                    val query = ProtocolJson.json.decodeFromString<MediaFixtureRegisterRequest>(request.body)
                    okJson(ProtocolJson.json.encodeToString(AiDebugRuntime.mediaController().registerFixture(query)))
                }
                "/media/fixture/list" -> {
                    val query = decodeBody(request.body, MediaFixtureListRequest())
                    okJson(ProtocolJson.json.encodeToString(AiDebugRuntime.mediaController().fixtures(query)))
                }
                "/media/fixture/delete" -> {
                    val query = ProtocolJson.json.decodeFromString<MediaFixtureDeleteRequest>(request.body)
                    okJson(ProtocolJson.json.encodeToString(AiDebugRuntime.mediaController().deleteFixture(query)))
                }
                "/media/audio/inject" -> {
                    val query = ProtocolJson.json.decodeFromString<MediaAudioInjectRequest>(request.body)
                    okJson(ProtocolJson.json.encodeToString(AiDebugRuntime.mediaController().injectAudio(query)))
                }
                "/media/audio/clear" -> {
                    val query = decodeBody(request.body, MediaAudioClearRequest())
                    okJson(ProtocolJson.json.encodeToString(AiDebugRuntime.mediaController().clearAudio(query)))
                }
                "/media/audio/history" -> {
                    val query = decodeBody(request.body, MediaAudioHistoryRequest())
                    okJson(ProtocolJson.json.encodeToString(AiDebugRuntime.mediaController().audioHistory(query)))
                }
                "/media/audio/assertConsumed" -> {
                    val query = ProtocolJson.json.decodeFromString<MediaAudioAssertConsumedRequest>(request.body)
                    okJson(ProtocolJson.json.encodeToString(AiDebugRuntime.mediaController().assertAudioConsumed(query)))
                }
                "/media/camera/injectFrames" -> {
                    val query = ProtocolJson.json.decodeFromString<MediaCameraInjectFramesRequest>(request.body)
                    okJson(ProtocolJson.json.encodeToString(AiDebugRuntime.mediaController().injectCamera(query)))
                }
                "/media/camera/clear" -> {
                    val query = decodeBody(request.body, MediaCameraClearRequest())
                    okJson(ProtocolJson.json.encodeToString(AiDebugRuntime.mediaController().clearCamera(query)))
                }
                "/media/camera/history" -> {
                    val query = decodeBody(request.body, MediaCameraHistoryRequest())
                    okJson(ProtocolJson.json.encodeToString(AiDebugRuntime.mediaController().cameraHistory(query)))
                }
                "/media/camera/snapshot" -> {
                    val query = decodeBody(request.body, MediaCameraSnapshotRequest())
                    okJson(ProtocolJson.json.encodeToString(AiDebugRuntime.mediaController().cameraSnapshot(query)))
                }
                "/media/camera/assertConsumed" -> {
                    val query = ProtocolJson.json.decodeFromString<MediaCameraAssertConsumedRequest>(request.body)
                    okJson(ProtocolJson.json.encodeToString(AiDebugRuntime.mediaController().assertCameraConsumed(query)))
                }
                "/network/history" -> {
                    val query = decodeBody(request.body, NetworkHistoryRequest())
                    okJson(ProtocolJson.json.encodeToString(AiDebugRuntime.networkController().history(query)))
                }
                "/network/mutateResponse" -> {
                    val query = ProtocolJson.json.decodeFromString<NetworkMutateResponseRequest>(request.body)
                    okJson(ProtocolJson.json.encodeToString(AiDebugRuntime.networkController().installMutation(query)))
                }
                "/network/mock" -> {
                    val query = ProtocolJson.json.decodeFromString<NetworkMockRequest>(request.body)
                    okJson(ProtocolJson.json.encodeToString(AiDebugRuntime.networkController().installMock(query)))
                }
                "/network/fail" -> {
                    val query = ProtocolJson.json.decodeFromString<NetworkFailRequest>(request.body)
                    okJson(ProtocolJson.json.encodeToString(AiDebugRuntime.networkController().installFailure(query)))
                }
                "/network/clearRules" -> {
                    val query = decodeBody(request.body, NetworkClearRulesRequest())
                    okJson(ProtocolJson.json.encodeToString(AiDebugRuntime.networkController().clearRules(query)))
                }
                "/network/assertCalled" -> {
                    val query = ProtocolJson.json.decodeFromString<NetworkAssertCalledRequest>(request.body)
                    okJson(ProtocolJson.json.encodeToString(AiDebugRuntime.networkController().assertCalled(query)))
                }
                "/network/recordToMock" -> {
                    val query = ProtocolJson.json.decodeFromString<NetworkRecordToMockRequest>(request.body)
                    okJson(ProtocolJson.json.encodeToString(AiDebugRuntime.networkController().recordToMock(query)))
                }
                "/state/list" -> {
                    val query = decodeBody(request.body, StateListRequest())
                    okJson(ProtocolJson.json.encodeToString(AiDebugRuntime.stateController().list(query)))
                }
                "/state/get" -> {
                    val query = ProtocolJson.json.decodeFromString<StateGetRequest>(request.body)
                    okJson(ProtocolJson.json.encodeToString(AiDebugRuntime.stateController().get(query)))
                }
                "/state/set" -> {
                    val query = ProtocolJson.json.decodeFromString<StateSetRequest>(request.body)
                    okJson(ProtocolJson.json.encodeToString(AiDebugRuntime.stateController().set(query)))
                }
                "/state/reset" -> {
                    val query = ProtocolJson.json.decodeFromString<StateResetRequest>(request.body)
                    okJson(ProtocolJson.json.encodeToString(AiDebugRuntime.stateController().reset(query)))
                }
                "/state/snapshot" -> {
                    val query = decodeBody(request.body, StateSnapshotRequest())
                    okJson(ProtocolJson.json.encodeToString(AiDebugRuntime.stateController().snapshot(query)))
                }
                "/state/restore" -> {
                    val query = ProtocolJson.json.decodeFromString<StateRestoreRequest>(request.body)
                    okJson(ProtocolJson.json.encodeToString(AiDebugRuntime.stateController().restore(query)))
                }
                "/state/diff" -> {
                    val query = ProtocolJson.json.decodeFromString<StateDiffRequest>(request.body)
                    okJson(ProtocolJson.json.encodeToString(AiDebugRuntime.stateController().diff(query)))
                }
                "/action/list" -> {
                    val query = decodeBody(request.body, ActionListRequest())
                    okJson(ProtocolJson.json.encodeToString(AiDebugRuntime.stateController().listActions(query)))
                }
                "/action/invoke" -> {
                    val query = ProtocolJson.json.decodeFromString<ActionInvokeRequest>(request.body)
                    okJson(ProtocolJson.json.encodeToString(AiDebugRuntime.stateController().invokeAction(query)))
                }
                "/prefs/list" -> {
                    val query = ProtocolJson.json.decodeFromString<PrefsListRequest>(request.body)
                    okJson(ProtocolJson.json.encodeToString(AiDebugRuntime.storageController(context).prefsList(query)))
                }
                "/prefs/get" -> {
                    val query = ProtocolJson.json.decodeFromString<PrefsGetRequest>(request.body)
                    okJson(ProtocolJson.json.encodeToString(AiDebugRuntime.storageController(context).prefsGet(query)))
                }
                "/prefs/set" -> {
                    val query = ProtocolJson.json.decodeFromString<PrefsSetRequest>(request.body)
                    okJson(ProtocolJson.json.encodeToString(AiDebugRuntime.storageController(context).prefsSet(query)))
                }
                "/prefs/delete" -> {
                    val query = ProtocolJson.json.decodeFromString<PrefsDeleteRequest>(request.body)
                    okJson(ProtocolJson.json.encodeToString(AiDebugRuntime.storageController(context).prefsDelete(query)))
                }
                "/datastore/preferences/list" -> {
                    val query = ProtocolJson.json.decodeFromString<DataStorePrefsListRequest>(request.body)
                    okJson(ProtocolJson.json.encodeToString(AiDebugRuntime.storageController(context).dataStorePrefsList(query)))
                }
                "/datastore/preferences/get" -> {
                    val query = ProtocolJson.json.decodeFromString<DataStorePrefsGetRequest>(request.body)
                    okJson(ProtocolJson.json.encodeToString(AiDebugRuntime.storageController(context).dataStorePrefsGet(query)))
                }
                "/datastore/preferences/set" -> {
                    val query = ProtocolJson.json.decodeFromString<DataStorePrefsSetRequest>(request.body)
                    okJson(ProtocolJson.json.encodeToString(AiDebugRuntime.storageController(context).dataStorePrefsSet(query)))
                }
                "/datastore/preferences/delete" -> {
                    val query = ProtocolJson.json.decodeFromString<DataStorePrefsDeleteRequest>(request.body)
                    okJson(ProtocolJson.json.encodeToString(AiDebugRuntime.storageController(context).dataStorePrefsDelete(query)))
                }
                "/storage/sql/query" -> {
                    val query = ProtocolJson.json.decodeFromString<SqlQueryRequest>(request.body)
                    okJson(ProtocolJson.json.encodeToString(AiDebugRuntime.storageController(context).sqlQuery(query)))
                }
                "/storage/sql/exec" -> {
                    val query = ProtocolJson.json.decodeFromString<SqlExecRequest>(request.body)
                    okJson(ProtocolJson.json.encodeToString(AiDebugRuntime.storageController(context).sqlExec(query)))
                }
                "/storage/snapshot" -> {
                    val query = decodeBody(request.body, StorageSnapshotRequest())
                    okJson(ProtocolJson.json.encodeToString(AiDebugRuntime.storageController(context).snapshot(query)))
                }
                "/storage/restore" -> {
                    val query = ProtocolJson.json.decodeFromString<StorageRestoreRequest>(request.body)
                    okJson(ProtocolJson.json.encodeToString(AiDebugRuntime.storageController(context).restore(query)))
                }
                "/override/set" -> {
                    val query = ProtocolJson.json.decodeFromString<OverrideSetRequest>(request.body)
                    okJson(ProtocolJson.json.encodeToString(AiDebugRuntime.overrideStore().set(query)))
                }
                "/override/get" -> {
                    val query = ProtocolJson.json.decodeFromString<OverrideGetRequest>(request.body)
                    okJson(ProtocolJson.json.encodeToString(AiDebugRuntime.overrideStore().get(query)))
                }
                "/override/list" -> {
                    okJson(ProtocolJson.json.encodeToString(OverrideListResponse(AiDebugRuntime.overrideStore().list())))
                }
                "/override/clear" -> {
                    val query = decodeBody(request.body, OverrideClearRequest())
                    okJson(ProtocolJson.json.encodeToString(AiDebugRuntime.overrideStore().clear(query)))
                }
                "/debug/objectSearch" -> {
                    val query = ProtocolJson.json.decodeFromString<ObjectSearchRequest>(request.body)
                    okJson(ProtocolJson.json.encodeToString(AiDebugRuntime.dynamicDebugController().objectSearch(query)))
                }
                "/debug/eval" -> {
                    val query = ProtocolJson.json.decodeFromString<EvalRequest>(request.body)
                    okJson(ProtocolJson.json.encodeToString(AiDebugRuntime.dynamicDebugController().eval(query)))
                }
                "/probe/getField" -> {
                    val query = ProtocolJson.json.decodeFromString<ProbeGetFieldRequest>(request.body)
                    okJson(ProtocolJson.json.encodeToString(AiDebugRuntime.dynamicDebugController().getField(query)))
                }
                "/probe/setField" -> {
                    val query = ProtocolJson.json.decodeFromString<ProbeSetFieldRequest>(request.body)
                    okJson(ProtocolJson.json.encodeToString(AiDebugRuntime.dynamicDebugController().setField(query)))
                }
                "/hook/overrideReturn" -> {
                    val query = ProtocolJson.json.decodeFromString<HookOverrideReturnRequest>(request.body)
                    okJson(ProtocolJson.json.encodeToString(AiDebugRuntime.dynamicDebugController().overrideReturn(query)))
                }
                "/hook/throw" -> {
                    val query = ProtocolJson.json.decodeFromString<HookThrowRequest>(request.body)
                    okJson(ProtocolJson.json.encodeToString(AiDebugRuntime.dynamicDebugController().throwError(query)))
                }
                "/hook/clear" -> {
                    val query = decodeBody(request.body, HookClearRequest())
                    okJson(ProtocolJson.json.encodeToString(AiDebugRuntime.dynamicDebugController().clearHook(query)))
                }
                else -> notFound("Unknown runtime endpoint: ${request.path}")
            }
        }.getOrElse { error ->
            internalError(error.message ?: error::class.java.simpleName)
        }
    }

    private fun okJson(body: String): String = http(200, body)

    private fun notFound(message: String): String = http(
        status = 404,
        body = ProtocolJson.json.encodeToString(
            ErrorResponse(ErrorBody("NOT_FOUND", message, recoverable = true)),
        ),
    )

    private fun unauthorized(): String = http(
        status = 401,
        body = ProtocolJson.json.encodeToString(
            ErrorResponse(ErrorBody("UNAUTHORIZED", "Missing or invalid runtime session token", recoverable = true)),
        ),
    )

    private fun internalError(message: String): String = http(
        status = 500,
        body = ProtocolJson.json.encodeToString(
            ErrorResponse(ErrorBody("RUNTIME_ERROR", message, recoverable = true)),
        ),
    )

    private fun http(status: Int, body: String): String {
        val reason = when (status) {
            200 -> "OK"
            401 -> "Unauthorized"
            404 -> "Not Found"
            else -> "Internal Server Error"
        }
        val bytes = body.toByteArray(Charsets.UTF_8)
        return buildString {
            append("HTTP/1.1 ").append(status).append(' ').append(reason).append("\r\n")
            append("Content-Type: application/json; charset=utf-8\r\n")
            append("Content-Length: ").append(bytes.size).append("\r\n")
            append("Connection: close\r\n")
            append("\r\n")
            append(body)
        }
    }

    private inline fun <reified T> decodeBody(body: String, default: T): T {
        return body.takeIf { it.isNotBlank() }
            ?.let { ProtocolJson.json.decodeFromString<T>(it) }
            ?: default
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
            require(read != -1) { "Request body ended before content-length bytes were read" }
            offset += read
        }
        return bytes
    }

    private data class HttpRequest(
        val method: String,
        val path: String,
        val headers: Map<String, String>,
        val body: String,
    ) {
        fun header(name: String): String? = headers[name.lowercase()]
    }

    private companion object {
        const val MAX_REQUEST_BODY_BYTES = 4 * 1024 * 1024
    }
}
