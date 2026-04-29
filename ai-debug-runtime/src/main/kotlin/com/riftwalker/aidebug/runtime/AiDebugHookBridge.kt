package com.riftwalker.aidebug.runtime

object AiDebugHookBridge {
    @JvmStatic
    fun traceEnter(methodId: String) {
        AiDebugRuntime.traceEnter(methodId)
    }

    @JvmStatic
    fun traceExit(methodId: String) {
        AiDebugRuntime.traceExit(methodId)
    }

    @JvmStatic
    fun traceThrow(methodId: String, throwable: Throwable) {
        AiDebugRuntime.traceThrow(methodId, throwable)
    }

    @JvmStatic
    fun hookBoolean(methodId: String): Boolean? {
        return AiDebugRuntime.dynamicDebugController().resolveHookBoolean(methodId)
    }

    @JvmStatic
    fun hookString(methodId: String): String? {
        return AiDebugRuntime.dynamicDebugController().resolveHookString(methodId)
    }
}
