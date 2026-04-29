package com.riftwalker.aidebug.runtime.state

import com.riftwalker.aidebug.protocol.DebugActionDescriptor
import com.riftwalker.aidebug.protocol.DebugStateDescriptor
import kotlinx.serialization.json.JsonElement

data class StateRegistration(
    val descriptor: DebugStateDescriptor,
    val read: () -> JsonElement?,
    val write: ((JsonElement) -> Unit)? = null,
    val reset: (() -> Unit)? = null,
    val initialValue: JsonElement? = runCatching { read() }.getOrNull(),
)

data class ActionRegistration(
    val descriptor: DebugActionDescriptor,
    val invoke: (JsonElement?) -> JsonElement?,
)
