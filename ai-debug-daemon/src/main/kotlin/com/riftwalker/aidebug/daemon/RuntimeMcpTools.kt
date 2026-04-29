package com.riftwalker.aidebug.daemon

import com.riftwalker.aidebug.protocol.ActionInvokeRequest
import com.riftwalker.aidebug.protocol.ActionListRequest
import com.riftwalker.aidebug.protocol.AuditHistoryRequest
import com.riftwalker.aidebug.protocol.CapabilityListRequest
import com.riftwalker.aidebug.protocol.DataStorePrefsDeleteRequest
import com.riftwalker.aidebug.protocol.DataStorePrefsGetRequest
import com.riftwalker.aidebug.protocol.DataStorePrefsListRequest
import com.riftwalker.aidebug.protocol.DataStorePrefsSetRequest
import com.riftwalker.aidebug.protocol.EvalRequest
import com.riftwalker.aidebug.protocol.HookClearRequest
import com.riftwalker.aidebug.protocol.HookOverrideReturnRequest
import com.riftwalker.aidebug.protocol.HookThrowRequest
import com.riftwalker.aidebug.protocol.NetworkAssertCalledRequest
import com.riftwalker.aidebug.protocol.NetworkClearRulesRequest
import com.riftwalker.aidebug.protocol.NetworkFailRequest
import com.riftwalker.aidebug.protocol.NetworkHistoryRequest
import com.riftwalker.aidebug.protocol.NetworkMockRequest
import com.riftwalker.aidebug.protocol.NetworkMutateResponseRequest
import com.riftwalker.aidebug.protocol.NetworkRecordToMockRequest
import com.riftwalker.aidebug.protocol.ObjectSearchRequest
import com.riftwalker.aidebug.protocol.OverrideClearRequest
import com.riftwalker.aidebug.protocol.OverrideGetRequest
import com.riftwalker.aidebug.protocol.OverrideSetRequest
import com.riftwalker.aidebug.protocol.PrefsDeleteRequest
import com.riftwalker.aidebug.protocol.PrefsGetRequest
import com.riftwalker.aidebug.protocol.PrefsListRequest
import com.riftwalker.aidebug.protocol.PrefsSetRequest
import com.riftwalker.aidebug.protocol.ProtocolJson
import com.riftwalker.aidebug.protocol.ProbeGetFieldRequest
import com.riftwalker.aidebug.protocol.ProbeSetFieldRequest
import com.riftwalker.aidebug.protocol.RuntimeWaitForPingRequest
import com.riftwalker.aidebug.protocol.SqlExecRequest
import com.riftwalker.aidebug.protocol.SqlQueryRequest
import com.riftwalker.aidebug.protocol.StateDiffRequest
import com.riftwalker.aidebug.protocol.StateGetRequest
import com.riftwalker.aidebug.protocol.StateListRequest
import com.riftwalker.aidebug.protocol.StateResetRequest
import com.riftwalker.aidebug.protocol.StateRestoreRequest
import com.riftwalker.aidebug.protocol.StateSetRequest
import com.riftwalker.aidebug.protocol.StateSnapshotRequest
import com.riftwalker.aidebug.protocol.StorageRestoreRequest
import com.riftwalker.aidebug.protocol.StorageSnapshotRequest
import kotlinx.coroutines.delay
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put

class RuntimeMcpTools(private val client: RuntimeHttpClient) {
    fun registerInto(registry: DaemonToolRegistry) {
        registry.register(
            name = "runtime.ping",
            description = "Return runtime identity for the connected Android debug app",
            inputSchema = objectSchema(),
        ) {
            ProtocolJson.json.encodeToJsonElement(client.ping())
        }
        registry.register(
            name = "runtime.waitForPing",
            description = "Poll runtime.ping until the connected Android debug app is ready",
            inputSchema = objectSchema(),
        ) { args ->
            val request = args?.let { ProtocolJson.json.decodeFromJsonElement<RuntimeWaitForPingRequest>(it) }
                ?: RuntimeWaitForPingRequest()
            val timeoutMs = request.timeoutMs.coerceAtLeast(0)
            val deadline = System.currentTimeMillis() + timeoutMs
            val intervalMs = request.pollIntervalMs.coerceAtLeast(10)
            var lastError: Throwable? = null
            while (true) {
                val ping = runCatching { client.ping() }
                    .onFailure { lastError = it }
                    .getOrNull()
                if (ping != null) {
                    return@register ProtocolJson.json.encodeToJsonElement(ping)
                }
                if (System.currentTimeMillis() >= deadline) {
                    error(
                        "runtime.ping did not succeed within ${timeoutMs}ms" +
                            (lastError?.message?.let { ": $it" } ?: ""),
                    )
                }
                delay(intervalMs)
            }
            @Suppress("UNREACHABLE_CODE")
            ProtocolJson.json.encodeToJsonElement(client.ping())
        }
        registry.register(
            name = "capabilities.list",
            description = "List runtime and app-registered AI debugging capabilities",
            inputSchema = objectSchema(),
        ) { args ->
            val request = args?.let { ProtocolJson.json.decodeFromJsonElement<CapabilityListRequest>(it) }
                ?: CapabilityListRequest()
            ProtocolJson.json.encodeToJsonElement(client.listCapabilities(request))
        }
        registry.register(
            name = "audit.history",
            description = "Return audit events for the connected runtime session",
            inputSchema = objectSchema(),
        ) { args ->
            val request = args?.let { ProtocolJson.json.decodeFromJsonElement<AuditHistoryRequest>(it) }
                ?: AuditHistoryRequest()
            ProtocolJson.json.encodeToJsonElement(client.auditHistory(request))
        }
        registry.register(
            name = "network.history",
            description = "Return captured OkHttp request and response records",
            inputSchema = objectSchema(),
        ) { args ->
            val request = args?.let { ProtocolJson.json.decodeFromJsonElement<NetworkHistoryRequest>(it) }
                ?: NetworkHistoryRequest()
            ProtocolJson.json.encodeToJsonElement(client.networkHistory(request))
        }
        registry.register(
            name = "network.mutateResponse",
            description = "Install a JSON response mutation rule",
            inputSchema = objectSchema(),
        ) { args ->
            val request = ProtocolJson.json.decodeFromJsonElement<NetworkMutateResponseRequest>(
                args ?: error("network.mutateResponse requires arguments"),
            )
            ProtocolJson.json.encodeToJsonElement(client.mutateResponse(request))
        }
        registry.register(
            name = "network.mock",
            description = "Install a static mock response rule",
            inputSchema = objectSchema(),
        ) { args ->
            val request = ProtocolJson.json.decodeFromJsonElement<NetworkMockRequest>(
                args ?: error("network.mock requires arguments"),
            )
            ProtocolJson.json.encodeToJsonElement(client.mockNetwork(request))
        }
        registry.register(
            name = "network.fail",
            description = "Install a timeout or disconnect failure rule",
            inputSchema = objectSchema(),
        ) { args ->
            val request = ProtocolJson.json.decodeFromJsonElement<NetworkFailRequest>(
                args ?: error("network.fail requires arguments"),
            )
            ProtocolJson.json.encodeToJsonElement(client.failNetwork(request))
        }
        registry.register(
            name = "network.clearRules",
            description = "Clear one or more network mock/mutation/failure rules",
            inputSchema = objectSchema(),
        ) { args ->
            val request = args?.let { ProtocolJson.json.decodeFromJsonElement<NetworkClearRulesRequest>(it) }
                ?: NetworkClearRulesRequest()
            ProtocolJson.json.encodeToJsonElement(client.clearNetworkRules(request))
        }
        registry.register(
            name = "network.assertCalled",
            description = "Assert that matching network calls were captured",
            inputSchema = objectSchema(),
        ) { args ->
            val request = ProtocolJson.json.decodeFromJsonElement<NetworkAssertCalledRequest>(
                args ?: error("network.assertCalled requires arguments"),
            )
            ProtocolJson.json.encodeToJsonElement(client.assertNetworkCalled(request))
        }
        registry.register(
            name = "network.recordToMock",
            description = "Convert a captured network record into a static mock rule",
            inputSchema = objectSchema(),
        ) { args ->
            val request = ProtocolJson.json.decodeFromJsonElement<NetworkRecordToMockRequest>(
                args ?: error("network.recordToMock requires arguments"),
            )
            ProtocolJson.json.encodeToJsonElement(client.recordToMock(request))
        }
        registry.register(
            name = "state.list",
            description = "List typed app state registered by the debug runtime",
            inputSchema = objectSchema(),
        ) { args ->
            val request = args?.let { ProtocolJson.json.decodeFromJsonElement<StateListRequest>(it) }
                ?: StateListRequest()
            ProtocolJson.json.encodeToJsonElement(client.stateList(request))
        }
        registry.register(
            name = "state.get",
            description = "Read a typed app state value",
            inputSchema = objectSchema(),
        ) { args ->
            val request = ProtocolJson.json.decodeFromJsonElement<StateGetRequest>(
                args ?: error("state.get requires arguments"),
            )
            ProtocolJson.json.encodeToJsonElement(client.stateGet(request))
        }
        registry.register(
            name = "state.set",
            description = "Set a mutable typed app state value",
            inputSchema = objectSchema(),
        ) { args ->
            val request = ProtocolJson.json.decodeFromJsonElement<StateSetRequest>(
                args ?: error("state.set requires arguments"),
            )
            ProtocolJson.json.encodeToJsonElement(client.stateSet(request))
        }
        registry.register(
            name = "state.reset",
            description = "Reset a mutable typed app state value",
            inputSchema = objectSchema(),
        ) { args ->
            val request = ProtocolJson.json.decodeFromJsonElement<StateResetRequest>(
                args ?: error("state.reset requires arguments"),
            )
            ProtocolJson.json.encodeToJsonElement(client.stateReset(request))
        }
        registry.register(
            name = "state.snapshot",
            description = "Snapshot registered app state values",
            inputSchema = objectSchema(),
        ) { args ->
            val request = args?.let { ProtocolJson.json.decodeFromJsonElement<StateSnapshotRequest>(it) }
                ?: StateSnapshotRequest()
            ProtocolJson.json.encodeToJsonElement(client.stateSnapshot(request))
        }
        registry.register(
            name = "state.restore",
            description = "Restore a state snapshot",
            inputSchema = objectSchema(),
        ) { args ->
            val request = ProtocolJson.json.decodeFromJsonElement<StateRestoreRequest>(
                args ?: error("state.restore requires arguments"),
            )
            ProtocolJson.json.encodeToJsonElement(client.stateRestore(request))
        }
        registry.register(
            name = "state.diff",
            description = "Diff current registered state against a snapshot",
            inputSchema = objectSchema(),
        ) { args ->
            val request = ProtocolJson.json.decodeFromJsonElement<StateDiffRequest>(
                args ?: error("state.diff requires arguments"),
            )
            ProtocolJson.json.encodeToJsonElement(client.stateDiff(request))
        }
        registry.register(
            name = "action.list",
            description = "List app-registered debug actions",
            inputSchema = objectSchema(),
        ) { args ->
            val request = args?.let { ProtocolJson.json.decodeFromJsonElement<ActionListRequest>(it) }
                ?: ActionListRequest()
            ProtocolJson.json.encodeToJsonElement(client.actionList(request))
        }
        registry.register(
            name = "action.invoke",
            description = "Invoke an app-registered debug action",
            inputSchema = objectSchema(),
        ) { args ->
            val request = ProtocolJson.json.decodeFromJsonElement<ActionInvokeRequest>(
                args ?: error("action.invoke requires arguments"),
            )
            ProtocolJson.json.encodeToJsonElement(client.actionInvoke(request))
        }
        registry.register(
            name = "prefs.list",
            description = "List entries in a SharedPreferences file",
            inputSchema = objectSchema(),
        ) { args ->
            val request = ProtocolJson.json.decodeFromJsonElement<PrefsListRequest>(
                args ?: error("prefs.list requires arguments"),
            )
            ProtocolJson.json.encodeToJsonElement(client.prefsList(request))
        }
        registry.register(
            name = "prefs.get",
            description = "Read a SharedPreferences value",
            inputSchema = objectSchema(),
        ) { args ->
            val request = ProtocolJson.json.decodeFromJsonElement<PrefsGetRequest>(
                args ?: error("prefs.get requires arguments"),
            )
            ProtocolJson.json.encodeToJsonElement(client.prefsGet(request))
        }
        registry.register(
            name = "prefs.set",
            description = "Set a SharedPreferences value",
            inputSchema = objectSchema(),
        ) { args ->
            val request = ProtocolJson.json.decodeFromJsonElement<PrefsSetRequest>(
                args ?: error("prefs.set requires arguments"),
            )
            ProtocolJson.json.encodeToJsonElement(client.prefsSet(request))
        }
        registry.register(
            name = "prefs.delete",
            description = "Delete a SharedPreferences value",
            inputSchema = objectSchema(),
        ) { args ->
            val request = ProtocolJson.json.decodeFromJsonElement<PrefsDeleteRequest>(
                args ?: error("prefs.delete requires arguments"),
            )
            ProtocolJson.json.encodeToJsonElement(client.prefsDelete(request))
        }
        registry.register(
            name = "datastore.preferences.list",
            description = "List entries in a Preferences DataStore",
            inputSchema = objectSchema(),
        ) { args ->
            val request = ProtocolJson.json.decodeFromJsonElement<DataStorePrefsListRequest>(
                args ?: error("datastore.preferences.list requires arguments"),
            )
            ProtocolJson.json.encodeToJsonElement(client.dataStorePrefsList(request))
        }
        registry.register(
            name = "datastore.preferences.get",
            description = "Read a Preferences DataStore value",
            inputSchema = objectSchema(),
        ) { args ->
            val request = ProtocolJson.json.decodeFromJsonElement<DataStorePrefsGetRequest>(
                args ?: error("datastore.preferences.get requires arguments"),
            )
            ProtocolJson.json.encodeToJsonElement(client.dataStorePrefsGet(request))
        }
        registry.register(
            name = "datastore.preferences.set",
            description = "Set a Preferences DataStore value",
            inputSchema = objectSchema(),
        ) { args ->
            val request = ProtocolJson.json.decodeFromJsonElement<DataStorePrefsSetRequest>(
                args ?: error("datastore.preferences.set requires arguments"),
            )
            ProtocolJson.json.encodeToJsonElement(client.dataStorePrefsSet(request))
        }
        registry.register(
            name = "datastore.preferences.delete",
            description = "Delete a Preferences DataStore value",
            inputSchema = objectSchema(),
        ) { args ->
            val request = ProtocolJson.json.decodeFromJsonElement<DataStorePrefsDeleteRequest>(
                args ?: error("datastore.preferences.delete requires arguments"),
            )
            ProtocolJson.json.encodeToJsonElement(client.dataStorePrefsDelete(request))
        }
        registry.register(
            name = "storage.sql.query",
            description = "Run a read-only SQL query against an app SQLite/Room database",
            inputSchema = objectSchema(),
        ) { args ->
            val request = ProtocolJson.json.decodeFromJsonElement<SqlQueryRequest>(
                args ?: error("storage.sql.query requires arguments"),
            )
            ProtocolJson.json.encodeToJsonElement(client.sqlQuery(request))
        }
        registry.register(
            name = "storage.sql.exec",
            description = "Run a mutating SQL statement against an app SQLite/Room database",
            inputSchema = objectSchema(),
        ) { args ->
            val request = ProtocolJson.json.decodeFromJsonElement<SqlExecRequest>(
                args ?: error("storage.sql.exec requires arguments"),
            )
            ProtocolJson.json.encodeToJsonElement(client.sqlExec(request))
        }
        registry.register(
            name = "storage.snapshot",
            description = "Snapshot selected SharedPreferences files and SQLite/Room database files",
            inputSchema = objectSchema(),
        ) { args ->
            val request = args?.let { ProtocolJson.json.decodeFromJsonElement<StorageSnapshotRequest>(it) }
                ?: StorageSnapshotRequest()
            ProtocolJson.json.encodeToJsonElement(client.storageSnapshot(request))
        }
        registry.register(
            name = "storage.restore",
            description = "Restore a storage snapshot",
            inputSchema = objectSchema(),
        ) { args ->
            val request = ProtocolJson.json.decodeFromJsonElement<StorageRestoreRequest>(
                args ?: error("storage.restore requires arguments"),
            )
            ProtocolJson.json.encodeToJsonElement(client.storageRestore(request))
        }
        registry.register(
            name = "override.set",
            description = "Set a session-scoped dependency override",
            inputSchema = objectSchema(),
        ) { args ->
            val request = ProtocolJson.json.decodeFromJsonElement<OverrideSetRequest>(
                args ?: error("override.set requires arguments"),
            )
            ProtocolJson.json.encodeToJsonElement(client.overrideSet(request))
        }
        registry.register(
            name = "override.get",
            description = "Read a session-scoped dependency override",
            inputSchema = objectSchema(),
        ) { args ->
            val request = ProtocolJson.json.decodeFromJsonElement<OverrideGetRequest>(
                args ?: error("override.get requires arguments"),
            )
            ProtocolJson.json.encodeToJsonElement(client.overrideGet(request))
        }
        registry.register(
            name = "override.list",
            description = "List active session-scoped dependency overrides",
            inputSchema = objectSchema(),
        ) {
            ProtocolJson.json.encodeToJsonElement(client.overrideList())
        }
        registry.register(
            name = "override.clear",
            description = "Clear one override or all active overrides",
            inputSchema = objectSchema(),
        ) { args ->
            val request = args?.let { ProtocolJson.json.decodeFromJsonElement<OverrideClearRequest>(it) }
                ?: OverrideClearRequest()
            ProtocolJson.json.encodeToJsonElement(client.overrideClear(request))
        }
        registry.register(
            name = "debug.objectSearch",
            description = "Search registry-tracked runtime objects and fields",
            inputSchema = objectSchema(),
        ) { args ->
            val request = ProtocolJson.json.decodeFromJsonElement<ObjectSearchRequest>(
                args ?: error("debug.objectSearch requires arguments"),
            )
            ProtocolJson.json.encodeToJsonElement(client.objectSearch(request))
        }
        registry.register(
            name = "debug.eval",
            description = "Run a constrained app-process debug expression",
            inputSchema = objectSchema(),
        ) { args ->
            val request = ProtocolJson.json.decodeFromJsonElement<EvalRequest>(
                args ?: error("debug.eval requires arguments"),
            )
            ProtocolJson.json.encodeToJsonElement(client.eval(request))
        }
        registry.register(
            name = "probe.getField",
            description = "Read a field from a tracked runtime object handle",
            inputSchema = objectSchema(),
        ) { args ->
            val request = ProtocolJson.json.decodeFromJsonElement<ProbeGetFieldRequest>(
                args ?: error("probe.getField requires arguments"),
            )
            ProtocolJson.json.encodeToJsonElement(client.getField(request))
        }
        registry.register(
            name = "probe.setField",
            description = "Set a field on a tracked runtime object handle",
            inputSchema = objectSchema(),
        ) { args ->
            val request = ProtocolJson.json.decodeFromJsonElement<ProbeSetFieldRequest>(
                args ?: error("probe.setField requires arguments"),
            )
            ProtocolJson.json.encodeToJsonElement(client.setField(request))
        }
        registry.register(
            name = "hook.overrideReturn",
            description = "Install a method return override for registered hook points",
            inputSchema = objectSchema(),
        ) { args ->
            val request = ProtocolJson.json.decodeFromJsonElement<HookOverrideReturnRequest>(
                args ?: error("hook.overrideReturn requires arguments"),
            )
            ProtocolJson.json.encodeToJsonElement(client.overrideReturn(request))
        }
        registry.register(
            name = "hook.throw",
            description = "Install a method throw override for registered hook points",
            inputSchema = objectSchema(),
        ) { args ->
            val request = ProtocolJson.json.decodeFromJsonElement<HookThrowRequest>(
                args ?: error("hook.throw requires arguments"),
            )
            ProtocolJson.json.encodeToJsonElement(client.throwHook(request))
        }
        registry.register(
            name = "hook.clear",
            description = "Clear one hook or hooks for a method id",
            inputSchema = objectSchema(),
        ) { args ->
            val request = args?.let { ProtocolJson.json.decodeFromJsonElement<HookClearRequest>(it) }
                ?: HookClearRequest()
            ProtocolJson.json.encodeToJsonElement(client.clearHook(request))
        }
    }

    fun describeTools(): String {
        val registry = DaemonToolRegistry()
        registerInto(registry)
        return ProtocolJson.json.encodeToString(registry.names())
    }

    private fun objectSchema() = buildJsonObject {
        put("type", "object")
        put("additionalProperties", true)
    }
}
