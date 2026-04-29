package com.riftwalker.aidebug.protocol

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class MediaCapabilitiesResponse(
    val fixtureMimeTypes: List<String> = listOf(
        "audio/wav",
        "audio/pcm",
        "image/png",
        "image/jpeg",
        "application/x-nv21",
    ),
    val targetKinds: List<String> = listOf(
        "AUDIO_RECORD_READ",
        "AUDIO_RECORD_LIFECYCLE",
        "CAMERA_X_ANALYZER",
        "MLKIT_INPUT_IMAGE_FACTORY",
        "MLKIT_DETECTOR_PROCESS",
        "CUSTOM_FRAME_METHOD",
    ),
    val audioReadOverloads: List<String> = listOf(
        "AudioRecord.read(byte[], int, int)",
        "AudioRecord.read(byte[], int, int, int)",
        "AudioRecord.read(short[], int, int)",
        "AudioRecord.read(short[], int, int, int)",
        "AudioRecord.read(float[], int, int, int)",
        "AudioRecord.read(ByteBuffer, int)",
        "AudioRecord.read(ByteBuffer, int, int)",
    ),
    val cameraModes: List<String> = listOf(
        "replace_on_real_frame",
        "drive_analyzer",
        "mlkit_input_image",
    ),
    val releaseGuarantees: List<String> = listOf(
        "release variants are not instrumented",
        "release variants depend on ai-debug-runtime-noop",
        "release safety scan rejects media bridge/runtime leaks",
    ),
)

@Serializable
data class MediaTarget(
    val targetId: String,
    val kind: String,
    val callSiteId: String,
    val apiSignature: String? = null,
    val hitCount: Int = 0,
    val firstHitEpochMs: Long? = null,
    val lastHitEpochMs: Long? = null,
    val observed: Map<String, JsonElement> = emptyMap(),
)

@Serializable
data class MediaTargetListRequest(
    val kind: String? = null,
    val query: String? = null,
    val limit: Int = 100,
)

@Serializable
data class MediaTargetListResponse(
    val targets: List<MediaTarget>,
)

@Serializable
data class MediaFixture(
    val fixtureId: String,
    val devicePath: String,
    val sha256: String,
    val mimeType: String,
    val sizeBytes: Long? = null,
    val metadata: Map<String, JsonElement> = emptyMap(),
    val tags: List<String> = emptyList(),
    val registeredAtEpochMs: Long,
)

@Serializable
data class MediaFixtureRegisterRequest(
    val fixtureId: String? = null,
    val hostPath: String? = null,
    val devicePath: String,
    val sha256: String? = null,
    val mimeType: String,
    val metadata: Map<String, JsonElement> = emptyMap(),
    val tags: List<String> = emptyList(),
    val serial: String? = null,
)

@Serializable
data class MediaFixtureRegisterResponse(
    val fixture: MediaFixture,
    val verified: Boolean,
)

@Serializable
data class MediaFixtureListRequest(
    val mimeType: String? = null,
    val query: String? = null,
    val limit: Int = 100,
)

@Serializable
data class MediaFixtureListResponse(
    val fixtures: List<MediaFixture>,
)

@Serializable
data class MediaFixtureDeleteRequest(
    val fixtureId: String,
)

@Serializable
data class MediaFixtureDeleteResponse(
    val deleted: Boolean,
)

@Serializable
data class MediaAudioReadBehavior(
    val blockingMode: String? = null,
    val eof: String = "short_read",
    val shortReadMaxBytes: Int? = null,
    val errorAfterBytes: Long? = null,
    val errorCode: Int? = null,
)

@Serializable
data class MediaAudioInjectRequest(
    val targetId: String,
    val fixtureId: String,
    val loop: Boolean = false,
    val times: Int? = null,
    val behavior: MediaAudioReadBehavior = MediaAudioReadBehavior(),
    val scenarioScope: String? = null,
)

@Serializable
data class MediaAudioRuleResponse(
    val ruleId: String,
    val restoreToken: String,
)

@Serializable
data class MediaAudioClearRequest(
    val targetId: String? = null,
    val ruleIds: List<String>? = null,
)

@Serializable
data class MediaClearResponse(
    val cleared: Int,
)

@Serializable
data class MediaAudioHistoryRequest(
    val targetId: String? = null,
    val fixtureId: String? = null,
    val limit: Int = 100,
)

@Serializable
data class MediaAudioHistoryRecord(
    val id: String,
    val targetId: String,
    val fixtureId: String? = null,
    val callSiteId: String,
    val overload: String,
    val requestedBytes: Int,
    val returnedBytes: Int,
    val consumedBytes: Long,
    val fallback: Boolean,
    val timestampEpochMs: Long,
)

@Serializable
data class MediaAudioHistoryResponse(
    val records: List<MediaAudioHistoryRecord>,
)

@Serializable
data class MediaAudioAssertConsumedRequest(
    val targetId: String,
    val fixtureId: String? = null,
    val minBytes: Long = 1,
    val minReads: Int = 1,
    val timeoutMs: Long = 0,
    val pollIntervalMs: Long = 100,
)

@Serializable
data class MediaAudioAssertConsumedResponse(
    val passed: Boolean,
    val consumedBytes: Long,
    val readCount: Int,
    val recordIds: List<String>,
)

@Serializable
data class MediaCameraInjectFramesRequest(
    val targetId: String,
    val fixtureId: String,
    val mode: String = "replace_on_real_frame",
    val fps: Double? = null,
    val loop: Boolean = false,
    val times: Int? = null,
    val scenarioScope: String? = null,
)

@Serializable
data class MediaCameraRuleResponse(
    val ruleId: String,
    val restoreToken: String,
)

@Serializable
data class MediaCameraClearRequest(
    val targetId: String? = null,
    val ruleIds: List<String>? = null,
)

@Serializable
data class MediaCameraHistoryRequest(
    val targetId: String? = null,
    val fixtureId: String? = null,
    val limit: Int = 100,
)

@Serializable
data class MediaCameraHistoryRecord(
    val id: String,
    val targetId: String,
    val fixtureId: String? = null,
    val callSiteId: String,
    val mode: String,
    val frameIndex: Long,
    val consumed: Boolean,
    val fallback: Boolean,
    val observed: Map<String, JsonElement> = emptyMap(),
    val timestampEpochMs: Long,
)

@Serializable
data class MediaCameraHistoryResponse(
    val records: List<MediaCameraHistoryRecord>,
)

@Serializable
data class MediaCameraSnapshotRequest(
    val targetId: String? = null,
)

@Serializable
data class MediaCameraSnapshotResponse(
    val activeRules: List<MediaCameraRuleSnapshot>,
    val targets: List<MediaTarget>,
)

@Serializable
data class MediaCameraRuleSnapshot(
    val ruleId: String,
    val targetId: String,
    val fixtureId: String,
    val mode: String,
    val consumedFrames: Long,
    val remaining: Int? = null,
)

@Serializable
data class MediaCameraAssertConsumedRequest(
    val targetId: String,
    val fixtureId: String? = null,
    val minFrames: Long = 1,
    val timeoutMs: Long = 0,
    val pollIntervalMs: Long = 100,
)

@Serializable
data class MediaCameraAssertConsumedResponse(
    val passed: Boolean,
    val consumedFrames: Long,
    val recordIds: List<String>,
)
