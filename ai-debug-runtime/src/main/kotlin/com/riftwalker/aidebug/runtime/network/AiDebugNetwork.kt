package com.riftwalker.aidebug.runtime.network

import com.riftwalker.aidebug.runtime.AiDebugRuntime
import okhttp3.Interceptor

object AiDebugNetwork {
    fun interceptor(): Interceptor = NetworkControlInterceptor(AiDebugRuntime.networkController())
}
