package com.riftwalker.aidebug.daemon

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
import com.riftwalker.aidebug.protocol.ProtocolJson
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put
import java.io.File
import java.security.MessageDigest

class MediaMcpTools(
    private val client: RuntimeHttpClient,
    private val adb: AdbExecutor = AdbExecutor(),
) {
    private val devices = AdbDeviceSelector(adb)

    fun registerInto(registry: DaemonToolRegistry) {
        registry.register(
            name = "media.capabilities",
            description = "Return supported business-transparent media hook capabilities",
            inputSchema = objectSchema(),
        ) {
            ProtocolJson.json.encodeToJsonElement(client.mediaCapabilities())
        }
        registry.register(
            name = "media.targets.list",
            description = "List media call-site targets discovered by runtime hooks",
            inputSchema = objectSchema(),
        ) { args ->
            val request = args?.let { ProtocolJson.json.decodeFromJsonElement<MediaTargetListRequest>(it) }
                ?: MediaTargetListRequest()
            ProtocolJson.json.encodeToJsonElement(client.mediaTargets(request))
        }
        registry.register(
            name = "media.fixture.register",
            description = "Stage and register a WAV/PCM/PNG/JPEG/NV21 fixture for media injection",
            inputSchema = objectSchema(),
        ) { args ->
            val request = ProtocolJson.json.decodeFromJsonElement<MediaFixtureRegisterRequest>(
                args ?: error("media.fixture.register requires arguments"),
            )
            ProtocolJson.json.encodeToJsonElement(client.mediaFixtureRegister(stageFixtureIfNeeded(request)))
        }
        registry.register(
            name = "media.fixture.list",
            description = "List registered media fixtures",
            inputSchema = objectSchema(),
        ) { args ->
            val request = args?.let { ProtocolJson.json.decodeFromJsonElement<MediaFixtureListRequest>(it) }
                ?: MediaFixtureListRequest()
            ProtocolJson.json.encodeToJsonElement(client.mediaFixtureList(request))
        }
        registry.register(
            name = "media.fixture.delete",
            description = "Delete a registered media fixture",
            inputSchema = objectSchema(),
        ) { args ->
            val request = ProtocolJson.json.decodeFromJsonElement<MediaFixtureDeleteRequest>(
                args ?: error("media.fixture.delete requires arguments"),
            )
            ProtocolJson.json.encodeToJsonElement(client.mediaFixtureDelete(request))
        }
        registry.register(
            name = "media.audio.inject",
            description = "Inject a fixture into an AudioRecord target",
            inputSchema = objectSchema(),
        ) { args ->
            val request = ProtocolJson.json.decodeFromJsonElement<MediaAudioInjectRequest>(
                args ?: error("media.audio.inject requires arguments"),
            )
            ProtocolJson.json.encodeToJsonElement(client.mediaAudioInject(request))
        }
        registry.register(
            name = "media.audio.clear",
            description = "Clear active AudioRecord fixture injections",
            inputSchema = objectSchema(),
        ) { args ->
            val request = args?.let { ProtocolJson.json.decodeFromJsonElement<MediaAudioClearRequest>(it) }
                ?: MediaAudioClearRequest()
            ProtocolJson.json.encodeToJsonElement(client.mediaAudioClear(request))
        }
        registry.register(
            name = "media.audio.history",
            description = "Return AudioRecord fixture read history",
            inputSchema = objectSchema(),
        ) { args ->
            val request = args?.let { ProtocolJson.json.decodeFromJsonElement<MediaAudioHistoryRequest>(it) }
                ?: MediaAudioHistoryRequest()
            ProtocolJson.json.encodeToJsonElement(client.mediaAudioHistory(request))
        }
        registry.register(
            name = "media.audio.assertConsumed",
            description = "Assert that an audio fixture was consumed by the target",
            inputSchema = objectSchema(),
        ) { args ->
            val request = ProtocolJson.json.decodeFromJsonElement<MediaAudioAssertConsumedRequest>(
                args ?: error("media.audio.assertConsumed requires arguments"),
            )
            ProtocolJson.json.encodeToJsonElement(client.mediaAudioAssertConsumed(request))
        }
        registry.register(
            name = "media.camera.injectFrames",
            description = "Inject image/NV21 frames into a CameraX or ML Kit target",
            inputSchema = objectSchema(),
        ) { args ->
            val request = ProtocolJson.json.decodeFromJsonElement<MediaCameraInjectFramesRequest>(
                args ?: error("media.camera.injectFrames requires arguments"),
            )
            ProtocolJson.json.encodeToJsonElement(client.mediaCameraInjectFrames(request))
        }
        registry.register(
            name = "media.camera.clear",
            description = "Clear active camera fixture injections",
            inputSchema = objectSchema(),
        ) { args ->
            val request = args?.let { ProtocolJson.json.decodeFromJsonElement<MediaCameraClearRequest>(it) }
                ?: MediaCameraClearRequest()
            ProtocolJson.json.encodeToJsonElement(client.mediaCameraClear(request))
        }
        registry.register(
            name = "media.camera.history",
            description = "Return camera frame fixture history",
            inputSchema = objectSchema(),
        ) { args ->
            val request = args?.let { ProtocolJson.json.decodeFromJsonElement<MediaCameraHistoryRequest>(it) }
                ?: MediaCameraHistoryRequest()
            ProtocolJson.json.encodeToJsonElement(client.mediaCameraHistory(request))
        }
        registry.register(
            name = "media.camera.snapshot",
            description = "Return active camera fixture rules and discovered targets",
            inputSchema = objectSchema(),
        ) { args ->
            val request = args?.let { ProtocolJson.json.decodeFromJsonElement<MediaCameraSnapshotRequest>(it) }
                ?: MediaCameraSnapshotRequest()
            ProtocolJson.json.encodeToJsonElement(client.mediaCameraSnapshot(request))
        }
        registry.register(
            name = "media.camera.assertConsumed",
            description = "Assert that camera fixture frames were consumed by the target",
            inputSchema = objectSchema(),
        ) { args ->
            val request = ProtocolJson.json.decodeFromJsonElement<MediaCameraAssertConsumedRequest>(
                args ?: error("media.camera.assertConsumed requires arguments"),
            )
            ProtocolJson.json.encodeToJsonElement(client.mediaCameraAssertConsumed(request))
        }
    }

    private fun stageFixtureIfNeeded(request: MediaFixtureRegisterRequest): MediaFixtureRegisterRequest {
        val hostPath = request.hostPath?.takeIf { it.isNotBlank() } ?: return request
        val hostFile = File(hostPath)
        require(hostFile.isFile) { "media.fixture.register hostPath is not a file: $hostPath" }
        val selected = devices.select(request.serial)
        adb.exec(listOf("push", hostFile.absolutePath, request.devicePath), serial = selected.serial)
        adb.exec(listOf("shell", "chmod", "644", request.devicePath), serial = selected.serial)
        return request.copy(sha256 = request.sha256 ?: sha256(hostFile))
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun objectSchema() = buildJsonObject {
        put("type", "object")
        put("additionalProperties", true)
    }
}
