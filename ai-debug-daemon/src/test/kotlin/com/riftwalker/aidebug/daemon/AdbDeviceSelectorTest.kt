package com.riftwalker.aidebug.daemon

import kotlin.test.Test
import kotlin.test.assertEquals

class AdbDeviceSelectorTest {
    @Test
    fun parsesDevicesOutput() {
        val output = """
            List of devices attached
            emulator-5554	device
            abc123	offline
        """.trimIndent()

        val devices = output.lines()
            .drop(1)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .map {
                val parts = it.split(Regex("\\s+"))
                AdbDevice(parts[0], parts[1])
            }

        assertEquals(listOf(AdbDevice("emulator-5554", "device"), AdbDevice("abc123", "offline")), devices)
    }
}
