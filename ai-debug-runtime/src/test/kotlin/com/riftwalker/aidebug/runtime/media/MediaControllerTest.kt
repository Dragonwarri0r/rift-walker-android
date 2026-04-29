package com.riftwalker.aidebug.runtime.media

import com.riftwalker.aidebug.protocol.MediaAudioAssertConsumedRequest
import com.riftwalker.aidebug.protocol.MediaAudioInjectRequest
import com.riftwalker.aidebug.protocol.MediaCameraAssertConsumedRequest
import com.riftwalker.aidebug.protocol.MediaCameraInjectFramesRequest
import com.riftwalker.aidebug.protocol.MediaFixtureRegisterRequest
import com.riftwalker.aidebug.runtime.AuditLog
import com.riftwalker.aidebug.runtime.CleanupRegistry
import kotlinx.serialization.json.JsonPrimitive
import java.io.File
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.Test

class MediaControllerTest {
    @Test
    fun audioInjectionConsumesRegisteredFixtureBytes() {
        withTempFixture(byteArrayOf(1, 2, 3, 4)) { file ->
            val controller = mediaController()
            val target = controller.registerTarget(
                kind = MediaController.KIND_AUDIO_READ,
                callSiteId = "audio:audiorecord:read:Example#loop()V@insn1",
                apiSignature = "AudioRecord.read(byte[], int, int)",
            )
            val fixture = controller.registerFixture(
                MediaFixtureRegisterRequest(
                    fixtureId = "audio-001",
                    devicePath = file.absolutePath,
                    mimeType = "audio/pcm",
                ),
            ).fixture

            controller.injectAudio(MediaAudioInjectRequest(targetId = target.targetId, fixtureId = fixture.fixtureId))

            val read = controller.consumeAudioBytes(
                callSiteId = target.callSiteId,
                apiSignature = "AudioRecord.read(byte[], int, int)",
                overload = "byte[]",
                requestedBytes = 3,
            )

            assertNotNull(read)
            assertContentEquals(byteArrayOf(1, 2, 3), read.bytes)
            val history = controller.audioHistory(com.riftwalker.aidebug.protocol.MediaAudioHistoryRequest(targetId = target.targetId))
            assertEquals(1, history.records.size)
            assertTrue(
                controller.assertAudioConsumed(
                    MediaAudioAssertConsumedRequest(targetId = target.targetId, fixtureId = fixture.fixtureId, minBytes = 3),
                ).passed,
            )
        }
    }

    @Test
    fun cameraInjectionRecordsFrameConsumption() {
        withTempFixture(byteArrayOf(1, 2, 3, 4)) { file ->
            val controller = mediaController()
            val target = controller.registerTarget(
                kind = MediaController.KIND_MLKIT_INPUT_IMAGE,
                callSiteId = "camera:mlkit:inputImage:Example#analyze()V@insn2",
                apiSignature = "InputImage.fromByteArray(byte[], int, int, int, int)",
            )
            val fixture = controller.registerFixture(
                MediaFixtureRegisterRequest(
                    fixtureId = "frame-001",
                    devicePath = file.absolutePath,
                    mimeType = "application/x-nv21",
                    metadata = mapOf(
                        "width" to JsonPrimitive(2),
                        "height" to JsonPrimitive(2),
                        "rotationDegrees" to JsonPrimitive(0),
                    ),
                ),
            ).fixture

            controller.injectCamera(MediaCameraInjectFramesRequest(targetId = target.targetId, fixtureId = fixture.fixtureId))
            val consumed = controller.consumeCameraFrame(
                kind = MediaController.KIND_MLKIT_INPUT_IMAGE,
                callSiteId = target.callSiteId,
                apiSignature = "InputImage.fromByteArray(byte[], int, int, int, int)",
                mode = "mlkit_input_image",
                observed = emptyMap(),
            )

            assertEquals(fixture.fixtureId, consumed?.fixtureId)
            assertTrue(
                controller.assertCameraConsumed(
                    MediaCameraAssertConsumedRequest(targetId = target.targetId, fixtureId = fixture.fixtureId, minFrames = 1),
                ).passed,
            )
        }
    }

    private fun mediaController(): MediaController {
        val auditLog = AuditLog()
        return MediaController(auditLog, CleanupRegistry(auditLog))
    }

    private fun withTempFixture(bytes: ByteArray, block: (File) -> Unit) {
        val file = kotlin.io.path.createTempFile(prefix = "ai-debug-media-", suffix = ".bin").toFile()
        try {
            file.writeBytes(bytes)
            block(file)
        } finally {
            file.delete()
        }
    }
}
