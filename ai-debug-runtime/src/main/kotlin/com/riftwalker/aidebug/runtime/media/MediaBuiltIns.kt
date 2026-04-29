package com.riftwalker.aidebug.runtime.media

import com.riftwalker.aidebug.protocol.CapabilityDescriptor
import com.riftwalker.aidebug.protocol.PolicyMetadata
import com.riftwalker.aidebug.runtime.CapabilityRegistry
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

object MediaBuiltIns {
    fun register(registry: CapabilityRegistry) {
        listOf(
            Tool("media.capabilities", "media", false, "Return media hook support and release guarantees"),
            Tool("media.targets.list", "media", false, "List discovered media input call-sites"),
            Tool("media.fixture.register", "media", true, "Register a staged WAV/PCM/image/NV21 fixture"),
            Tool("media.fixture.list", "media", false, "List registered media fixtures"),
            Tool("media.fixture.delete", "media", true, "Delete a registered media fixture"),
            Tool("media.audio.inject", "media.audio", true, "Inject an audio fixture into an AudioRecord read target"),
            Tool("media.audio.clear", "media.audio", true, "Clear media audio fixture rules"),
            Tool("media.audio.history", "media.audio", false, "Return media audio read history"),
            Tool("media.audio.assertConsumed", "media.audio", false, "Assert an audio fixture was consumed"),
            Tool("media.camera.injectFrames", "media.camera", true, "Inject camera frames into a CameraX or ML Kit target"),
            Tool("media.camera.clear", "media.camera", true, "Clear media camera fixture rules"),
            Tool("media.camera.history", "media.camera", false, "Return camera frame consumption history"),
            Tool("media.camera.snapshot", "media.camera", false, "Return active camera fixture rules and targets"),
            Tool("media.camera.assertConsumed", "media.camera", false, "Assert camera frames were consumed"),
        ).forEach { tool ->
            registry.register(
                CapabilityDescriptor(
                    path = tool.path,
                    kind = tool.kind,
                    schema = buildJsonObject {
                        put("type", "object")
                        put("additionalProperties", true)
                    },
                    mutable = tool.mutable,
                    restore = if (tool.mutable) "cleanup" else "none",
                    audit = if (tool.mutable) "read_write" else "read",
                    description = tool.description,
                    tags = listOf("media", tool.kind).distinct(),
                    policy = PolicyMetadata(
                        sideEffects = if (tool.mutable) "mutates_media_fixture_state" else "none",
                        cleanup = if (tool.mutable) "remove_rule_or_fixture" else "none",
                        risk = if (tool.path.contains("inject")) "medium" else "low",
                    ),
                ),
            )
        }
    }

    private data class Tool(
        val path: String,
        val kind: String,
        val mutable: Boolean,
        val description: String,
    )
}
