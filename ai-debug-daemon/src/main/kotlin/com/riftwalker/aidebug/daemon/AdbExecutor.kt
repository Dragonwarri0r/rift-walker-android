package com.riftwalker.aidebug.daemon

import java.io.File

class AdbExecutor(
    private val adbPath: String = resolveAdbPath(),
) {
    fun exec(args: List<String>, serial: String? = null): String {
        val command = buildList {
            add(adbPath)
            if (serial != null) {
                add("-s")
                add(serial)
            }
            addAll(args)
        }
        val process = ProcessBuilder(command)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()
        if (exitCode != 0) {
            error("ADB command failed ($exitCode): ${command.joinToString(" ")}\n$output")
        }
        return output
    }

    companion object {
        private fun resolveAdbPath(): String {
            val androidHome = System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT")
            val candidate = androidHome?.let { File(it, "platform-tools/adb") }
            return if (candidate != null && candidate.canExecute()) candidate.absolutePath else "adb"
        }
    }
}
