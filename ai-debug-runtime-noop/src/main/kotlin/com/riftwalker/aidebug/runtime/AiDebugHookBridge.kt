package com.riftwalker.aidebug.runtime

object AiDebugHookBridge {
    @JvmStatic
    fun traceEnter(methodId: String) = Unit

    @JvmStatic
    fun traceExit(methodId: String) = Unit

    @JvmStatic
    fun traceThrow(methodId: String, throwable: Throwable) = Unit

    @JvmStatic
    fun hookBoolean(methodId: String): Boolean? = null

    @JvmStatic
    fun hookString(methodId: String): String? = null
}
