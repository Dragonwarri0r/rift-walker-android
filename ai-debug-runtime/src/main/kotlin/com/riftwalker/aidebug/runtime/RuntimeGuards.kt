package com.riftwalker.aidebug.runtime

import android.content.Context
import android.content.pm.ApplicationInfo

object RuntimeGuards {
    fun isDebuggable(context: Context): Boolean {
        val appInfo = context.applicationInfo
        return (appInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }
}
