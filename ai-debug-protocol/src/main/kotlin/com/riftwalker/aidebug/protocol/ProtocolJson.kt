package com.riftwalker.aidebug.protocol

import kotlinx.serialization.json.Json

object ProtocolJson {
    val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        prettyPrint = false
        encodeDefaults = true
    }
}
