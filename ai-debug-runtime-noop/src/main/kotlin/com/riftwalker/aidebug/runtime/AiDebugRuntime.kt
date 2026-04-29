package com.riftwalker.aidebug.runtime

import android.content.Context

object AiDebugRuntime {
    const val DEFAULT_PORT: Int = 37913
    const val RUNTIME_VERSION: String = "0.1.0-noop"

    fun start(context: Context, port: Int = DEFAULT_PORT): Int = port

    fun stop() = Unit
}
