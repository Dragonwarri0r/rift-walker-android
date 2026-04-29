package com.riftwalker.aidebug.runtime.media

import com.riftwalker.aidebug.protocol.MediaAudioAssertConsumedRequest
import com.riftwalker.aidebug.protocol.MediaAudioAssertConsumedResponse
import com.riftwalker.aidebug.protocol.MediaAudioClearRequest
import com.riftwalker.aidebug.protocol.MediaAudioHistoryRecord
import com.riftwalker.aidebug.protocol.MediaAudioHistoryRequest
import com.riftwalker.aidebug.protocol.MediaAudioHistoryResponse
import com.riftwalker.aidebug.protocol.MediaAudioInjectRequest
import com.riftwalker.aidebug.protocol.MediaAudioRuleResponse
import com.riftwalker.aidebug.protocol.MediaCameraAssertConsumedRequest
import com.riftwalker.aidebug.protocol.MediaCameraAssertConsumedResponse
import com.riftwalker.aidebug.protocol.MediaCameraClearRequest
import com.riftwalker.aidebug.protocol.MediaCameraHistoryRecord
import com.riftwalker.aidebug.protocol.MediaCameraHistoryRequest
import com.riftwalker.aidebug.protocol.MediaCameraHistoryResponse
import com.riftwalker.aidebug.protocol.MediaCameraInjectFramesRequest
import com.riftwalker.aidebug.protocol.MediaCameraRuleResponse
import com.riftwalker.aidebug.protocol.MediaCameraRuleSnapshot
import com.riftwalker.aidebug.protocol.MediaCameraSnapshotRequest
import com.riftwalker.aidebug.protocol.MediaCameraSnapshotResponse
import com.riftwalker.aidebug.protocol.MediaCapabilitiesResponse
import com.riftwalker.aidebug.protocol.MediaClearResponse
import com.riftwalker.aidebug.protocol.MediaFixture
import com.riftwalker.aidebug.protocol.MediaFixtureDeleteRequest
import com.riftwalker.aidebug.protocol.MediaFixtureDeleteResponse
import com.riftwalker.aidebug.protocol.MediaFixtureListRequest
import com.riftwalker.aidebug.protocol.MediaFixtureListResponse
import com.riftwalker.aidebug.protocol.MediaFixtureRegisterRequest
import com.riftwalker.aidebug.protocol.MediaFixtureRegisterResponse
import com.riftwalker.aidebug.protocol.MediaTarget
import com.riftwalker.aidebug.protocol.MediaTargetListRequest
import com.riftwalker.aidebug.protocol.MediaTargetListResponse
import com.riftwalker.aidebug.runtime.AuditLog
import com.riftwalker.aidebug.runtime.CleanupRegistry
import kotlinx.serialization.json.JsonElement
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.min

class MediaController(
    private val auditLog: AuditLog,
    private val cleanupRegistry: CleanupRegistry,
) {
    private val fixtures = ConcurrentHashMap<String, MediaFixture>()
    private val targets = ConcurrentHashMap<String, TargetState>()
    private val audioRules = ConcurrentHashMap<String, AudioRuleState>()
    private val cameraRules = ConcurrentHashMap<String, CameraRuleState>()
    private val audioHistory = CopyOnWriteArrayList<MediaAudioHistoryRecord>()
    private val cameraHistory = CopyOnWriteArrayList<MediaCameraHistoryRecord>()

    fun capabilities(): MediaCapabilitiesResponse {
        auditLog.recordRead("media.capabilities", target = null, status = "success")
        return MediaCapabilitiesResponse()
    }

    fun registerTarget(
        kind: String,
        callSiteId: String,
        apiSignature: String? = null,
        observed: Map<String, JsonElement> = emptyMap(),
    ): MediaTarget {
        val now = System.currentTimeMillis()
        val targetId = targetId(kind, callSiteId)
        val state = targets.compute(targetId) { _, existing ->
            if (existing == null) {
                TargetState(
                    targetId = targetId,
                    kind = kind,
                    callSiteId = callSiteId,
                    apiSignature = apiSignature,
                    hitCount = 1,
                    firstHitEpochMs = now,
                    lastHitEpochMs = now,
                    observed = observed,
                )
            } else {
                existing.copy(
                    apiSignature = apiSignature ?: existing.apiSignature,
                    hitCount = existing.hitCount + 1,
                    lastHitEpochMs = now,
                    observed = if (observed.isEmpty()) existing.observed else existing.observed + observed,
                )
            }
        } ?: error("Failed to register media target: $targetId")
        return state.toProtocol()
    }

    fun targets(request: MediaTargetListRequest): MediaTargetListResponse {
        auditLog.recordRead(
            tool = "media.targets.list",
            target = request.kind,
            status = "success",
            argumentsSummary = request.query,
        )
        val query = request.query?.lowercase()
        val listed = targets.values
            .asSequence()
            .filter { request.kind == null || it.kind == request.kind }
            .filter {
                query == null ||
                    it.targetId.lowercase().contains(query) ||
                    it.callSiteId.lowercase().contains(query) ||
                    it.apiSignature?.lowercase()?.contains(query) == true
            }
            .sortedWith(compareByDescending<TargetState> { it.lastHitEpochMs ?: 0L }.thenBy { it.targetId })
            .take(request.limit.coerceAtLeast(0))
            .map { it.toProtocol() }
            .toList()
        return MediaTargetListResponse(listed)
    }

    fun registerFixture(request: MediaFixtureRegisterRequest): MediaFixtureRegisterResponse {
        require(request.mimeType.isNotBlank()) { "media.fixture.register requires mimeType" }
        require(request.devicePath.isNotBlank()) { "media.fixture.register requires devicePath" }
        val file = File(request.devicePath)
        require(file.exists()) { "Media fixture file does not exist on device/runtime path: ${request.devicePath}" }
        val computedSha256 = sha256(file)
        request.sha256?.let { expected ->
            require(expected.equals(computedSha256, ignoreCase = true)) {
                "Media fixture sha256 mismatch: expected=$expected actual=$computedSha256"
            }
        }
        val fixture = MediaFixture(
            fixtureId = request.fixtureId?.takeIf { it.isNotBlank() } ?: "fixture_${UUID.randomUUID()}",
            devicePath = file.absolutePath,
            sha256 = computedSha256,
            mimeType = request.mimeType,
            sizeBytes = file.length(),
            metadata = request.metadata,
            tags = request.tags,
            registeredAtEpochMs = System.currentTimeMillis(),
        )
        fixtures[fixture.fixtureId] = fixture
        auditLog.recordMutation(
            tool = "media.fixture.register",
            target = fixture.fixtureId,
            restoreToken = null,
            status = "success",
            argumentsSummary = fixture.mimeType,
            resultSummary = "sizeBytes=${fixture.sizeBytes}",
        )
        return MediaFixtureRegisterResponse(fixture = fixture, verified = true)
    }

    fun fixtures(request: MediaFixtureListRequest): MediaFixtureListResponse {
        auditLog.recordRead("media.fixture.list", request.mimeType, "success", request.query)
        val query = request.query?.lowercase()
        val listed = fixtures.values
            .asSequence()
            .filter { request.mimeType == null || it.mimeType == request.mimeType }
            .filter {
                query == null ||
                    it.fixtureId.lowercase().contains(query) ||
                    it.devicePath.lowercase().contains(query) ||
                    it.tags.any { tag -> tag.lowercase().contains(query) }
            }
            .sortedByDescending { it.registeredAtEpochMs }
            .take(request.limit.coerceAtLeast(0))
            .toList()
        return MediaFixtureListResponse(listed)
    }

    fun deleteFixture(request: MediaFixtureDeleteRequest): MediaFixtureDeleteResponse {
        val deleted = fixtures.remove(request.fixtureId) != null
        if (deleted) {
            audioRules.values.removeIf { it.fixture.fixtureId == request.fixtureId }
            cameraRules.values.removeIf { it.fixture.fixtureId == request.fixtureId }
        }
        auditLog.recordMutation(
            tool = "media.fixture.delete",
            target = request.fixtureId,
            restoreToken = null,
            status = "success",
            resultSummary = "deleted=$deleted",
        )
        return MediaFixtureDeleteResponse(deleted)
    }

    fun injectAudio(request: MediaAudioInjectRequest): MediaAudioRuleResponse {
        requireTarget(request.targetId)
        val fixture = fixtures[request.fixtureId]
            ?: error("Media fixture not found: ${request.fixtureId}")
        val bytes = loadAudioPayload(fixture)
        val rule = AudioRuleState(
            ruleId = "audio_rule_${UUID.randomUUID()}",
            targetId = request.targetId,
            fixture = fixture,
            bytes = bytes,
            loop = request.loop,
            remainingReads = request.times,
            behavior = request.behavior,
        )
        audioRules[rule.ruleId] = rule
        val cleanupToken = cleanupRegistry.register("remove media audio rule ${rule.ruleId}") {
            audioRules.remove(rule.ruleId)
        }
        auditLog.recordMutation(
            tool = "media.audio.inject",
            target = request.targetId,
            restoreToken = cleanupToken,
            status = "success",
            argumentsSummary = request.fixtureId,
        )
        return MediaAudioRuleResponse(ruleId = rule.ruleId, restoreToken = cleanupToken)
    }

    fun clearAudio(request: MediaAudioClearRequest): MediaClearResponse {
        val requestedRuleIds = request.ruleIds
        val toRemove = audioRules.values
            .filter { rule ->
                (request.targetId == null || rule.targetId == request.targetId) &&
                    (requestedRuleIds == null || rule.ruleId in requestedRuleIds)
            }
            .map { it.ruleId }
        toRemove.forEach { audioRules.remove(it) }
        auditLog.recordMutation(
            tool = "media.audio.clear",
            target = request.targetId ?: "all",
            restoreToken = null,
            status = "success",
            resultSummary = "cleared=${toRemove.size}",
        )
        return MediaClearResponse(cleared = toRemove.size)
    }

    fun audioHistory(request: MediaAudioHistoryRequest): MediaAudioHistoryResponse {
        auditLog.recordRead("media.audio.history", request.targetId, "success")
        val records = audioHistory
            .asSequence()
            .filter { request.targetId == null || it.targetId == request.targetId }
            .filter { request.fixtureId == null || it.fixtureId == request.fixtureId }
            .sortedByDescending { it.timestampEpochMs }
            .take(request.limit.coerceAtLeast(0))
            .toList()
        return MediaAudioHistoryResponse(records)
    }

    fun assertAudioConsumed(request: MediaAudioAssertConsumedRequest): MediaAudioAssertConsumedResponse {
        val deadline = System.currentTimeMillis() + request.timeoutMs.coerceAtLeast(0)
        val intervalMs = request.pollIntervalMs.coerceAtLeast(10)
        var response = audioConsumedResponse(request)
        while (!response.passed && System.currentTimeMillis() < deadline) {
            Thread.sleep(min(intervalMs, (deadline - System.currentTimeMillis()).coerceAtLeast(1)))
            response = audioConsumedResponse(request)
        }
        auditLog.recordRead(
            tool = "media.audio.assertConsumed",
            target = request.targetId,
            status = if (response.passed) "success" else "failure",
            resultSummary = "bytes=${response.consumedBytes}, reads=${response.readCount}",
        )
        return response
    }

    fun consumeAudioBytes(
        callSiteId: String,
        apiSignature: String,
        overload: String,
        requestedBytes: Int,
    ): AudioFixtureRead? {
        val target = registerTarget(
            kind = KIND_AUDIO_READ,
            callSiteId = callSiteId,
            apiSignature = apiSignature,
        )
        val rule = audioRules.values.firstOrNull { it.targetId == target.targetId } ?: return null
        val bytes = synchronized(rule) {
            rule.read(requestedBytes.coerceAtLeast(0))
        }
        if (bytes == null) {
            if (rule.isComplete()) {
                audioRules.remove(rule.ruleId)
            }
            return null
        }
        val record = recordAudioRead(
            targetId = target.targetId,
            fixtureId = rule.fixture.fixtureId,
            callSiteId = callSiteId,
            overload = overload,
            requestedBytes = requestedBytes,
            returnedBytes = bytes.size,
            consumedBytes = rule.consumedBytes,
            fallback = false,
        )
        if (rule.isComplete()) {
            audioRules.remove(rule.ruleId)
        }
        return AudioFixtureRead(bytes = bytes, recordId = record.id)
    }

    fun recordAudioFallback(
        callSiteId: String,
        apiSignature: String,
        overload: String,
        requestedBytes: Int,
        returnedBytes: Int,
    ) {
        val target = registerTarget(
            kind = KIND_AUDIO_READ,
            callSiteId = callSiteId,
            apiSignature = apiSignature,
        )
        recordAudioRead(
            targetId = target.targetId,
            fixtureId = null,
            callSiteId = callSiteId,
            overload = overload,
            requestedBytes = requestedBytes,
            returnedBytes = returnedBytes.coerceAtLeast(0),
            consumedBytes = 0,
            fallback = true,
        )
    }

    fun recordAudioLifecycle(callSiteId: String, apiSignature: String, event: String) {
        val target = registerTarget(
            kind = KIND_AUDIO_LIFECYCLE,
            callSiteId = callSiteId,
            apiSignature = apiSignature,
        )
        auditLog.recordRead(
            tool = "media.audio.lifecycle",
            target = target.targetId,
            status = "success",
            resultSummary = event,
        )
    }

    fun injectCamera(request: MediaCameraInjectFramesRequest): MediaCameraRuleResponse {
        requireTarget(request.targetId)
        val fixture = fixtures[request.fixtureId]
            ?: error("Media fixture not found: ${request.fixtureId}")
        val rule = CameraRuleState(
            ruleId = "camera_rule_${UUID.randomUUID()}",
            targetId = request.targetId,
            fixture = fixture,
            mode = request.mode,
            loop = request.loop,
            remainingFrames = request.times,
        )
        cameraRules[rule.ruleId] = rule
        val cleanupToken = cleanupRegistry.register("remove media camera rule ${rule.ruleId}") {
            cameraRules.remove(rule.ruleId)
        }
        auditLog.recordMutation(
            tool = "media.camera.injectFrames",
            target = request.targetId,
            restoreToken = cleanupToken,
            status = "success",
            argumentsSummary = "${request.fixtureId}:${request.mode}",
        )
        return MediaCameraRuleResponse(ruleId = rule.ruleId, restoreToken = cleanupToken)
    }

    fun clearCamera(request: MediaCameraClearRequest): MediaClearResponse {
        val requestedRuleIds = request.ruleIds
        val toRemove = cameraRules.values
            .filter { rule ->
                (request.targetId == null || rule.targetId == request.targetId) &&
                    (requestedRuleIds == null || rule.ruleId in requestedRuleIds)
            }
            .map { it.ruleId }
        toRemove.forEach { cameraRules.remove(it) }
        auditLog.recordMutation(
            tool = "media.camera.clear",
            target = request.targetId ?: "all",
            restoreToken = null,
            status = "success",
            resultSummary = "cleared=${toRemove.size}",
        )
        return MediaClearResponse(cleared = toRemove.size)
    }

    fun cameraHistory(request: MediaCameraHistoryRequest): MediaCameraHistoryResponse {
        auditLog.recordRead("media.camera.history", request.targetId, "success")
        val records = cameraHistory
            .asSequence()
            .filter { request.targetId == null || it.targetId == request.targetId }
            .filter { request.fixtureId == null || it.fixtureId == request.fixtureId }
            .sortedByDescending { it.timestampEpochMs }
            .take(request.limit.coerceAtLeast(0))
            .toList()
        return MediaCameraHistoryResponse(records)
    }

    fun cameraSnapshot(request: MediaCameraSnapshotRequest): MediaCameraSnapshotResponse {
        auditLog.recordRead("media.camera.snapshot", request.targetId, "success")
        return MediaCameraSnapshotResponse(
            activeRules = cameraRules.values
                .filter { request.targetId == null || it.targetId == request.targetId }
                .map {
                    MediaCameraRuleSnapshot(
                        ruleId = it.ruleId,
                        targetId = it.targetId,
                        fixtureId = it.fixture.fixtureId,
                        mode = it.mode,
                        consumedFrames = it.consumedFrames,
                        remaining = it.remainingFrames,
                    )
                }
                .sortedBy { it.ruleId },
            targets = targets(MediaTargetListRequest(kind = null, query = request.targetId, limit = 100)).targets,
        )
    }

    fun assertCameraConsumed(request: MediaCameraAssertConsumedRequest): MediaCameraAssertConsumedResponse {
        val deadline = System.currentTimeMillis() + request.timeoutMs.coerceAtLeast(0)
        val intervalMs = request.pollIntervalMs.coerceAtLeast(10)
        var response = cameraConsumedResponse(request)
        while (!response.passed && System.currentTimeMillis() < deadline) {
            Thread.sleep(min(intervalMs, (deadline - System.currentTimeMillis()).coerceAtLeast(1)))
            response = cameraConsumedResponse(request)
        }
        auditLog.recordRead(
            tool = "media.camera.assertConsumed",
            target = request.targetId,
            status = if (response.passed) "success" else "failure",
            resultSummary = "frames=${response.consumedFrames}",
        )
        return response
    }

    fun consumeCameraFrame(
        kind: String,
        callSiteId: String,
        apiSignature: String,
        mode: String,
        observed: Map<String, JsonElement>,
    ): MediaFixture? {
        val target = registerTarget(kind = kind, callSiteId = callSiteId, apiSignature = apiSignature, observed = observed)
        val rule = cameraRules.values.firstOrNull { it.targetId == target.targetId } ?: run {
            recordCameraFrame(
                targetId = target.targetId,
                fixtureId = null,
                callSiteId = callSiteId,
                mode = mode,
                frameIndex = 0,
                consumed = false,
                fallback = true,
                observed = observed,
            )
            return null
        }
        val frameIndex = synchronized(rule) {
            rule.consume()
        } ?: return null
        val record = recordCameraFrame(
            targetId = target.targetId,
            fixtureId = rule.fixture.fixtureId,
            callSiteId = callSiteId,
            mode = rule.mode,
            frameIndex = frameIndex,
            consumed = true,
            fallback = false,
            observed = observed + rule.fixture.metadata,
        )
        if (rule.isComplete()) {
            cameraRules.remove(rule.ruleId)
        }
        return rule.fixture.takeIf { record.consumed }
    }

    fun fixtureBytes(fixture: MediaFixture): ByteArray = File(fixture.devicePath).readBytes()

    private fun requireTarget(targetId: String) {
        require(targets.containsKey(targetId)) {
            "Media target not found: $targetId. Run media.targets.list after the app hits the call-site."
        }
    }

    private fun recordAudioRead(
        targetId: String,
        fixtureId: String?,
        callSiteId: String,
        overload: String,
        requestedBytes: Int,
        returnedBytes: Int,
        consumedBytes: Long,
        fallback: Boolean,
    ): MediaAudioHistoryRecord {
        val record = MediaAudioHistoryRecord(
            id = "audio_read_${UUID.randomUUID()}",
            targetId = targetId,
            fixtureId = fixtureId,
            callSiteId = callSiteId,
            overload = overload,
            requestedBytes = requestedBytes,
            returnedBytes = returnedBytes,
            consumedBytes = consumedBytes,
            fallback = fallback,
            timestampEpochMs = System.currentTimeMillis(),
        )
        audioHistory += record
        return record
    }

    private fun recordCameraFrame(
        targetId: String,
        fixtureId: String?,
        callSiteId: String,
        mode: String,
        frameIndex: Long,
        consumed: Boolean,
        fallback: Boolean,
        observed: Map<String, JsonElement>,
    ): MediaCameraHistoryRecord {
        val record = MediaCameraHistoryRecord(
            id = "camera_frame_${UUID.randomUUID()}",
            targetId = targetId,
            fixtureId = fixtureId,
            callSiteId = callSiteId,
            mode = mode,
            frameIndex = frameIndex,
            consumed = consumed,
            fallback = fallback,
            observed = observed,
            timestampEpochMs = System.currentTimeMillis(),
        )
        cameraHistory += record
        return record
    }

    private fun audioConsumedResponse(request: MediaAudioAssertConsumedRequest): MediaAudioAssertConsumedResponse {
        val records = audioHistory
            .filter {
                it.targetId == request.targetId &&
                    !it.fallback &&
                    (request.fixtureId == null || it.fixtureId == request.fixtureId)
            }
        val consumed = records.sumOf { it.returnedBytes.toLong() }
        return MediaAudioAssertConsumedResponse(
            passed = consumed >= request.minBytes && records.size >= request.minReads,
            consumedBytes = consumed,
            readCount = records.size,
            recordIds = records.map { it.id },
        )
    }

    private fun cameraConsumedResponse(request: MediaCameraAssertConsumedRequest): MediaCameraAssertConsumedResponse {
        val records = cameraHistory
            .filter {
                it.targetId == request.targetId &&
                    it.consumed &&
                    (request.fixtureId == null || it.fixtureId == request.fixtureId)
            }
        return MediaCameraAssertConsumedResponse(
            passed = records.size.toLong() >= request.minFrames,
            consumedFrames = records.size.toLong(),
            recordIds = records.map { it.id },
        )
    }

    private fun loadAudioPayload(fixture: MediaFixture): ByteArray {
        val bytes = fixtureBytes(fixture)
        if (fixture.mimeType != "audio/wav" || bytes.size < 44) return bytes
        val dataOffset = findWavDataOffset(bytes) ?: return bytes
        return bytes.copyOfRange(dataOffset, bytes.size)
    }

    private fun findWavDataOffset(bytes: ByteArray): Int? {
        var index = 12
        while (index + 8 <= bytes.size) {
            val chunkId = String(bytes, index, 4, Charsets.US_ASCII)
            val chunkSize = littleEndianInt(bytes, index + 4)
            val dataStart = index + 8
            if (chunkId == "data") return dataStart.coerceAtMost(bytes.size)
            index = dataStart + chunkSize + (chunkSize and 1)
        }
        return null
    }

    private fun littleEndianInt(bytes: ByteArray, offset: Int): Int {
        return (bytes[offset].toInt() and 0xff) or
            ((bytes[offset + 1].toInt() and 0xff) shl 8) or
            ((bytes[offset + 2].toInt() and 0xff) shl 16) or
            ((bytes[offset + 3].toInt() and 0xff) shl 24)
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

    private fun targetId(kind: String, callSiteId: String): String {
        return "${kind.lowercase()}:$callSiteId"
    }

    private data class TargetState(
        val targetId: String,
        val kind: String,
        val callSiteId: String,
        val apiSignature: String?,
        val hitCount: Int,
        val firstHitEpochMs: Long,
        val lastHitEpochMs: Long,
        val observed: Map<String, JsonElement>,
    ) {
        fun toProtocol(): MediaTarget {
            return MediaTarget(
                targetId = targetId,
                kind = kind,
                callSiteId = callSiteId,
                apiSignature = apiSignature,
                hitCount = hitCount,
                firstHitEpochMs = firstHitEpochMs,
                lastHitEpochMs = lastHitEpochMs,
                observed = observed,
            )
        }
    }

    private data class AudioRuleState(
        val ruleId: String,
        val targetId: String,
        val fixture: MediaFixture,
        val bytes: ByteArray,
        val loop: Boolean,
        var remainingReads: Int?,
        val behavior: com.riftwalker.aidebug.protocol.MediaAudioReadBehavior,
        var offset: Int = 0,
        var consumedBytes: Long = 0,
    ) {
        fun read(requestedBytes: Int): ByteArray? {
            if (remainingReads != null && remainingReads!! <= 0) return null
            val maxBytes = behavior.shortReadMaxBytes?.let { min(requestedBytes, it.coerceAtLeast(0)) } ?: requestedBytes
            if (maxBytes <= 0 || bytes.isEmpty()) return ByteArray(0)
            if (offset >= bytes.size) {
                if (!loop) {
                    return if (behavior.eof == "fallback") null else ByteArray(0)
                }
                offset = 0
            }
            val available = bytes.size - offset
            val readBytes = min(maxBytes, available)
            val out = bytes.copyOfRange(offset, offset + readBytes)
            offset += readBytes
            consumedBytes += readBytes
            remainingReads = remainingReads?.minus(1)
            return out
        }

        fun isComplete(): Boolean {
            return (remainingReads != null && remainingReads!! <= 0) || (!loop && offset >= bytes.size)
        }
    }

    private data class CameraRuleState(
        val ruleId: String,
        val targetId: String,
        val fixture: MediaFixture,
        val mode: String,
        val loop: Boolean,
        var remainingFrames: Int?,
        var consumedFrames: Long = 0,
    ) {
        fun consume(): Long? {
            if (remainingFrames != null && remainingFrames!! <= 0) return null
            consumedFrames += 1
            remainingFrames = remainingFrames?.minus(1)
            return consumedFrames - 1
        }

        fun isComplete(): Boolean {
            return remainingFrames != null && remainingFrames!! <= 0
        }
    }

    data class AudioFixtureRead(
        val bytes: ByteArray,
        val recordId: String,
    )

    companion object {
        const val KIND_AUDIO_READ = "AUDIO_RECORD_READ"
        const val KIND_AUDIO_LIFECYCLE = "AUDIO_RECORD_LIFECYCLE"
        const val KIND_CAMERA_ANALYZER = "CAMERA_X_ANALYZER"
        const val KIND_MLKIT_INPUT_IMAGE = "MLKIT_INPUT_IMAGE_FACTORY"
    }
}
