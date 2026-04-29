package com.riftwalker.aidebug.runtime

import android.content.Context
import com.riftwalker.aidebug.protocol.AuditHistoryResponse
import com.riftwalker.aidebug.protocol.CapabilityListRequest
import com.riftwalker.aidebug.protocol.CapabilityListResponse
import com.riftwalker.aidebug.protocol.RuntimePingResponse
import com.riftwalker.aidebug.runtime.debug.DynamicDebugBuiltIns
import com.riftwalker.aidebug.runtime.debug.DynamicDebugController
import com.riftwalker.aidebug.runtime.media.MediaBuiltIns
import com.riftwalker.aidebug.runtime.media.MediaController
import com.riftwalker.aidebug.runtime.network.NetworkBuiltIns
import com.riftwalker.aidebug.runtime.network.NetworkController
import com.riftwalker.aidebug.runtime.override.DebugOverrideStore
import com.riftwalker.aidebug.runtime.override.OverrideBuiltIns
import com.riftwalker.aidebug.runtime.state.StateBuiltIns
import com.riftwalker.aidebug.runtime.state.StateController
import com.riftwalker.aidebug.runtime.storage.StorageBuiltIns
import com.riftwalker.aidebug.runtime.storage.StorageController

object AiDebugRuntime {
    const val DEFAULT_PORT: Int = 37913
    const val RUNTIME_VERSION: String = "0.1.0"

    private val auditLog = AuditLog()
    private val cleanupRegistry = CleanupRegistry(auditLog)
    private val sessions = DebugSessionManager(cleanupRegistry)
    private val capabilities = CapabilityRegistry()
    private val network = NetworkController(auditLog, cleanupRegistry)
    private val state = StateController(auditLog, cleanupRegistry, capabilities)
    private val overrideStore = DebugOverrideStore(auditLog, cleanupRegistry)
    private val dynamicDebug = DynamicDebugController(auditLog, cleanupRegistry, state, network, overrideStore)
    private val media = MediaController(auditLog, cleanupRegistry)

    @Volatile
    private var endpoint: RuntimeHttpEndpoint? = null

    @Volatile
    private var storage: StorageController? = null

    @Volatile
    private var generatedRegistryLoaded: Boolean = false

    init {
        RuntimeBuiltIns.register(capabilities)
        NetworkBuiltIns.register(capabilities)
        StateBuiltIns.register(capabilities)
        StorageBuiltIns.register(capabilities)
        OverrideBuiltIns.register(capabilities)
        DynamicDebugBuiltIns.register(capabilities)
        MediaBuiltIns.register(capabilities)
    }

    fun start(context: Context, port: Int = DEFAULT_PORT): Int {
        if (!RuntimeGuards.isDebuggable(context)) {
            return -1
        }

        loadGeneratedRegistry()
        val existing = endpoint
        if (existing != null) return existing.port

        val appContext = context.applicationContext
        val created = RuntimeHttpEndpoint(
            context = appContext,
            requestedPort = port,
            sessions = sessions,
            capabilities = capabilities,
            auditLog = auditLog,
        )
        created.start()
        endpoint = created
        return created.port
    }

    fun stop() {
        endpoint?.stop()
        endpoint = null
    }

    fun ping(context: Context): RuntimePingResponse {
        val session = sessions.currentOrCreate(context.packageName)
        val identity = RuntimeIdentityProvider.identity(context, session.sessionId)
        auditLog.recordRead(tool = "runtime.ping", target = context.packageName, status = "success")
        return RuntimePingResponse(
            packageName = identity.packageName,
            processId = identity.processId,
            debuggable = identity.debuggable,
            apiLevel = identity.apiLevel,
            runtimeVersion = identity.runtimeVersion,
            sessionId = session.sessionId,
            sessionToken = session.token,
        )
    }

    fun listCapabilities(request: CapabilityListRequest = CapabilityListRequest()): CapabilityListResponse {
        auditLog.recordRead(
            tool = "capabilities.list",
            target = request.kind,
            status = "success",
            argumentsSummary = request.query,
        )
        return CapabilityListResponse(capabilities.list(request.kind, request.query))
    }

    fun auditHistory(): AuditHistoryResponse = AuditHistoryResponse(auditLog.history())

    fun registerCapability(registration: CapabilityRegistration) {
        capabilities.register(registration.descriptor)
    }

    fun registerCleanup(description: String, cleanup: () -> Unit): String {
        return cleanupRegistry.register(description, cleanup)
    }

    fun networkController(): NetworkController = network

    fun stateController(): StateController = state

    fun overrideStore(): DebugOverrideStore = overrideStore

    fun dynamicDebugController(): DynamicDebugController = dynamicDebug

    fun mediaController(): MediaController = media

    fun traceEnter(methodId: String) {
        auditLog.recordRead("trace.enter", methodId, "success")
    }

    fun traceExit(methodId: String) {
        auditLog.recordRead("trace.exit", methodId, "success")
    }

    fun traceThrow(methodId: String, throwable: Throwable) {
        auditLog.recordRead(
            tool = "trace.throw",
            target = methodId,
            status = "success",
            resultSummary = throwable.message ?: throwable::class.java.name,
        )
    }

    fun storageController(context: Context): StorageController {
        val existing = storage
        if (existing != null) return existing
        return synchronized(this) {
            storage ?: StorageController(context.applicationContext, auditLog, cleanupRegistry)
                .also { storage = it }
        }
    }

    fun cleanupSession(): Int {
        return cleanupRegistry.cleanupAll()
    }

    private fun loadGeneratedRegistry() {
        if (generatedRegistryLoaded) return
        synchronized(this) {
            if (generatedRegistryLoaded) return
            runCatching {
                Class.forName("com.riftwalker.aidebug.generated.AiDebugGeneratedRegistry")
                    .getMethod("register")
                    .invoke(null)
            }
            generatedRegistryLoaded = true
        }
    }
}
