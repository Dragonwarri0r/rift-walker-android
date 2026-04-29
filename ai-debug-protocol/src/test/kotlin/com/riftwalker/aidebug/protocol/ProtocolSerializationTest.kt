package com.riftwalker.aidebug.protocol

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProtocolSerializationTest {
    @Test
    fun serializesCapabilityDescriptor() {
        val descriptor = CapabilityDescriptor(
            path = "runtime.ping",
            kind = "runtime",
            schema = buildJsonObject { put("type", "object") },
            mutable = false,
            restore = "none",
            audit = "read",
            description = "Return runtime identity",
        )

        val encoded = ProtocolJson.json.encodeToString(descriptor)
        assertTrue(encoded.contains("runtime.ping"))

        val decoded = ProtocolJson.json.decodeFromString<CapabilityDescriptor>(encoded)
        assertEquals(descriptor, decoded)
    }

    @Test
    fun serializesErrorResponseShape() {
        val encoded = ProtocolJson.json.encodeToString(
            ErrorResponse(
                error = ErrorBody(
                    code = "APP_NOT_DEBUGGABLE",
                    message = "Mutable runtime tools are disabled",
                    recoverable = false,
                ),
            ),
        )

        assertTrue(encoded.contains("APP_NOT_DEBUGGABLE"))
        val decoded = ProtocolJson.json.decodeFromString<ErrorResponse>(encoded)
        assertEquals("APP_NOT_DEBUGGABLE", decoded.error.code)
    }

    @Test
    fun serializesStateSetRequest() {
        val encoded = ProtocolJson.json.encodeToString(
            StateSetRequest(
                path = "user.isVip",
                value = kotlinx.serialization.json.JsonPrimitive(true),
            ),
        )

        val decoded = ProtocolJson.json.decodeFromString<StateSetRequest>(encoded)
        assertEquals("user.isVip", decoded.path)
        assertTrue(encoded.contains("true"))
    }

    @Test
    fun serializesMediaFixtureAndInjectionRequests() {
        val fixture = MediaFixtureRegisterRequest(
            fixtureId = "vip-card-001",
            devicePath = "/data/local/tmp/ai-debug/fixtures/vip-card-001.nv21",
            sha256 = "abc123",
            mimeType = "application/x-nv21",
            metadata = mapOf(
                "width" to kotlinx.serialization.json.JsonPrimitive(1280),
                "height" to kotlinx.serialization.json.JsonPrimitive(720),
                "rotationDegrees" to kotlinx.serialization.json.JsonPrimitive(90),
            ),
        )
        val encodedFixture = ProtocolJson.json.encodeToString(fixture)
        val decodedFixture = ProtocolJson.json.decodeFromString<MediaFixtureRegisterRequest>(encodedFixture)
        assertEquals("vip-card-001", decodedFixture.fixtureId)
        assertEquals("application/x-nv21", decodedFixture.mimeType)

        val inject = MediaAudioInjectRequest(
            targetId = "audio_record_read:com.example.Capture#loop()V@insn4",
            fixtureId = "wake-word",
            loop = true,
        )
        val encodedInject = ProtocolJson.json.encodeToString(inject)
        val decodedInject = ProtocolJson.json.decodeFromString<MediaAudioInjectRequest>(encodedInject)
        assertEquals("wake-word", decodedInject.fixtureId)
        assertTrue(decodedInject.loop)
    }
}
