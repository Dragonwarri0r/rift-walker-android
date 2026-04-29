package com.riftwalker.aidebug.daemon

data class AdbDevice(
    val serial: String,
    val state: String,
)
