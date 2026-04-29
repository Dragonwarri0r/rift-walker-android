package com.riftwalker.aidebug.runtime

import android.content.Context
import android.os.Build
import android.os.Process
import com.riftwalker.aidebug.protocol.RuntimeIdentity

object RuntimeIdentityProvider {
    fun identity(context: Context, sessionId: String?): RuntimeIdentity {
        return RuntimeIdentity(
            packageName = context.packageName,
            processId = Process.myPid(),
            debuggable = RuntimeGuards.isDebuggable(context),
            apiLevel = Build.VERSION.SDK_INT,
            runtimeVersion = AiDebugRuntime.RUNTIME_VERSION,
            sessionId = sessionId,
        )
    }
}
