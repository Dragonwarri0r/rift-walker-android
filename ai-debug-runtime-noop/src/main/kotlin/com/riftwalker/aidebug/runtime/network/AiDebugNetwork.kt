package com.riftwalker.aidebug.runtime.network

import okhttp3.Interceptor

object AiDebugNetwork {
    fun interceptor(): Interceptor = Interceptor { chain -> chain.proceed(chain.request()) }
}
