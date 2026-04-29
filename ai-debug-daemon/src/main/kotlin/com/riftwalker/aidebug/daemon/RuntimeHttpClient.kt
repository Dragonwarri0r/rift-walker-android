package com.riftwalker.aidebug.daemon

import com.riftwalker.aidebug.protocol.AuditHistoryRequest
import com.riftwalker.aidebug.protocol.AuditHistoryResponse
import com.riftwalker.aidebug.protocol.CapabilityListRequest
import com.riftwalker.aidebug.protocol.CapabilityListResponse
import com.riftwalker.aidebug.protocol.ActionInvokeRequest
import com.riftwalker.aidebug.protocol.ActionInvokeResponse
import com.riftwalker.aidebug.protocol.ActionListRequest
import com.riftwalker.aidebug.protocol.ActionListResponse
import com.riftwalker.aidebug.protocol.DataStorePrefsDeleteRequest
import com.riftwalker.aidebug.protocol.DataStorePrefsGetRequest
import com.riftwalker.aidebug.protocol.DataStorePrefsListRequest
import com.riftwalker.aidebug.protocol.DataStorePrefsListResponse
import com.riftwalker.aidebug.protocol.DataStorePrefsMutationResponse
import com.riftwalker.aidebug.protocol.DataStorePrefsSetRequest
import com.riftwalker.aidebug.protocol.DataStorePrefsValueResponse
import com.riftwalker.aidebug.protocol.EvalRequest
import com.riftwalker.aidebug.protocol.EvalResponse
import com.riftwalker.aidebug.protocol.HookClearRequest
import com.riftwalker.aidebug.protocol.HookClearResponse
import com.riftwalker.aidebug.protocol.HookOverrideReturnRequest
import com.riftwalker.aidebug.protocol.HookRuleResponse
import com.riftwalker.aidebug.protocol.HookThrowRequest
import com.riftwalker.aidebug.protocol.MediaAudioAssertConsumedRequest
import com.riftwalker.aidebug.protocol.MediaAudioAssertConsumedResponse
import com.riftwalker.aidebug.protocol.MediaAudioClearRequest
import com.riftwalker.aidebug.protocol.MediaAudioHistoryRequest
import com.riftwalker.aidebug.protocol.MediaAudioHistoryResponse
import com.riftwalker.aidebug.protocol.MediaAudioInjectRequest
import com.riftwalker.aidebug.protocol.MediaAudioRuleResponse
import com.riftwalker.aidebug.protocol.MediaCameraAssertConsumedRequest
import com.riftwalker.aidebug.protocol.MediaCameraAssertConsumedResponse
import com.riftwalker.aidebug.protocol.MediaCameraClearRequest
import com.riftwalker.aidebug.protocol.MediaCameraHistoryRequest
import com.riftwalker.aidebug.protocol.MediaCameraHistoryResponse
import com.riftwalker.aidebug.protocol.MediaCameraInjectFramesRequest
import com.riftwalker.aidebug.protocol.MediaCameraRuleResponse
import com.riftwalker.aidebug.protocol.MediaCameraSnapshotRequest
import com.riftwalker.aidebug.protocol.MediaCameraSnapshotResponse
import com.riftwalker.aidebug.protocol.MediaCapabilitiesResponse
import com.riftwalker.aidebug.protocol.MediaClearResponse
import com.riftwalker.aidebug.protocol.MediaFixtureDeleteRequest
import com.riftwalker.aidebug.protocol.MediaFixtureDeleteResponse
import com.riftwalker.aidebug.protocol.MediaFixtureListRequest
import com.riftwalker.aidebug.protocol.MediaFixtureListResponse
import com.riftwalker.aidebug.protocol.MediaFixtureRegisterRequest
import com.riftwalker.aidebug.protocol.MediaFixtureRegisterResponse
import com.riftwalker.aidebug.protocol.MediaTargetListRequest
import com.riftwalker.aidebug.protocol.MediaTargetListResponse
import com.riftwalker.aidebug.protocol.NetworkAssertCalledRequest
import com.riftwalker.aidebug.protocol.NetworkAssertCalledResponse
import com.riftwalker.aidebug.protocol.NetworkClearRulesRequest
import com.riftwalker.aidebug.protocol.NetworkClearRulesResponse
import com.riftwalker.aidebug.protocol.NetworkFailRequest
import com.riftwalker.aidebug.protocol.NetworkHistoryRequest
import com.riftwalker.aidebug.protocol.NetworkHistoryResponse
import com.riftwalker.aidebug.protocol.NetworkMockRequest
import com.riftwalker.aidebug.protocol.NetworkMutateResponseRequest
import com.riftwalker.aidebug.protocol.NetworkRecordToMockRequest
import com.riftwalker.aidebug.protocol.NetworkRecordToMockResponse
import com.riftwalker.aidebug.protocol.NetworkRuleResponse
import com.riftwalker.aidebug.protocol.ObjectSearchRequest
import com.riftwalker.aidebug.protocol.ObjectSearchResponse
import com.riftwalker.aidebug.protocol.OverrideClearRequest
import com.riftwalker.aidebug.protocol.OverrideClearResponse
import com.riftwalker.aidebug.protocol.OverrideGetRequest
import com.riftwalker.aidebug.protocol.OverrideListResponse
import com.riftwalker.aidebug.protocol.OverrideSetRequest
import com.riftwalker.aidebug.protocol.OverrideValueResponse
import com.riftwalker.aidebug.protocol.PrefsDeleteRequest
import com.riftwalker.aidebug.protocol.PrefsGetRequest
import com.riftwalker.aidebug.protocol.PrefsListRequest
import com.riftwalker.aidebug.protocol.PrefsListResponse
import com.riftwalker.aidebug.protocol.PrefsMutationResponse
import com.riftwalker.aidebug.protocol.PrefsSetRequest
import com.riftwalker.aidebug.protocol.PrefsValueResponse
import com.riftwalker.aidebug.protocol.ProtocolJson
import com.riftwalker.aidebug.protocol.ProbeFieldResponse
import com.riftwalker.aidebug.protocol.ProbeGetFieldRequest
import com.riftwalker.aidebug.protocol.ProbeSetFieldRequest
import com.riftwalker.aidebug.protocol.ProbeSetFieldResponse
import com.riftwalker.aidebug.protocol.RuntimeAuth
import com.riftwalker.aidebug.protocol.RuntimePingResponse
import com.riftwalker.aidebug.protocol.SqlExecRequest
import com.riftwalker.aidebug.protocol.SqlExecResponse
import com.riftwalker.aidebug.protocol.SqlQueryRequest
import com.riftwalker.aidebug.protocol.SqlQueryResponse
import com.riftwalker.aidebug.protocol.StateDiffRequest
import com.riftwalker.aidebug.protocol.StateDiffResponse
import com.riftwalker.aidebug.protocol.StateGetRequest
import com.riftwalker.aidebug.protocol.StateListRequest
import com.riftwalker.aidebug.protocol.StateListResponse
import com.riftwalker.aidebug.protocol.StateMutationResponse
import com.riftwalker.aidebug.protocol.StateResetRequest
import com.riftwalker.aidebug.protocol.StateRestoreRequest
import com.riftwalker.aidebug.protocol.StateRestoreResponse
import com.riftwalker.aidebug.protocol.StateSetRequest
import com.riftwalker.aidebug.protocol.StateSnapshotRequest
import com.riftwalker.aidebug.protocol.StateSnapshotResponse
import com.riftwalker.aidebug.protocol.StateValueResponse
import com.riftwalker.aidebug.protocol.StorageRestoreRequest
import com.riftwalker.aidebug.protocol.StorageRestoreResponse
import com.riftwalker.aidebug.protocol.StorageSnapshotRequest
import com.riftwalker.aidebug.protocol.StorageSnapshotResponse
import kotlinx.serialization.encodeToString
import java.net.HttpURLConnection
import java.net.URI

class RuntimeHttpClient(private val baseUrl: String) {
    @Volatile
    private var sessionToken: String? = null

    fun ping(): RuntimePingResponse {
        return get<RuntimePingResponse>("/runtime/ping", authenticate = false).also {
            sessionToken = it.sessionToken
        }
    }

    fun listCapabilities(request: CapabilityListRequest = CapabilityListRequest()): CapabilityListResponse {
        return post("/capabilities/list", ProtocolJson.json.encodeToString(request))
    }

    fun auditHistory(request: AuditHistoryRequest = AuditHistoryRequest()): AuditHistoryResponse {
        return post("/audit/history", ProtocolJson.json.encodeToString(request))
    }

    fun networkHistory(request: NetworkHistoryRequest = NetworkHistoryRequest()): NetworkHistoryResponse {
        return post("/network/history", ProtocolJson.json.encodeToString(request))
    }

    fun mutateResponse(request: NetworkMutateResponseRequest): NetworkRuleResponse {
        return post("/network/mutateResponse", ProtocolJson.json.encodeToString(request))
    }

    fun mockNetwork(request: NetworkMockRequest): NetworkRuleResponse {
        return post("/network/mock", ProtocolJson.json.encodeToString(request))
    }

    fun failNetwork(request: NetworkFailRequest): NetworkRuleResponse {
        return post("/network/fail", ProtocolJson.json.encodeToString(request))
    }

    fun clearNetworkRules(request: NetworkClearRulesRequest = NetworkClearRulesRequest()): NetworkClearRulesResponse {
        return post("/network/clearRules", ProtocolJson.json.encodeToString(request))
    }

    fun assertNetworkCalled(request: NetworkAssertCalledRequest): NetworkAssertCalledResponse {
        return post("/network/assertCalled", ProtocolJson.json.encodeToString(request))
    }

    fun recordToMock(request: NetworkRecordToMockRequest): NetworkRecordToMockResponse {
        return post("/network/recordToMock", ProtocolJson.json.encodeToString(request))
    }

    fun stateList(request: StateListRequest = StateListRequest()): StateListResponse {
        return post("/state/list", ProtocolJson.json.encodeToString(request))
    }

    fun stateGet(request: StateGetRequest): StateValueResponse {
        return post("/state/get", ProtocolJson.json.encodeToString(request))
    }

    fun stateSet(request: StateSetRequest): StateMutationResponse {
        return post("/state/set", ProtocolJson.json.encodeToString(request))
    }

    fun stateReset(request: StateResetRequest): StateMutationResponse {
        return post("/state/reset", ProtocolJson.json.encodeToString(request))
    }

    fun stateSnapshot(request: StateSnapshotRequest = StateSnapshotRequest()): StateSnapshotResponse {
        return post("/state/snapshot", ProtocolJson.json.encodeToString(request))
    }

    fun stateRestore(request: StateRestoreRequest): StateRestoreResponse {
        return post("/state/restore", ProtocolJson.json.encodeToString(request))
    }

    fun stateDiff(request: StateDiffRequest): StateDiffResponse {
        return post("/state/diff", ProtocolJson.json.encodeToString(request))
    }

    fun actionList(request: ActionListRequest = ActionListRequest()): ActionListResponse {
        return post("/action/list", ProtocolJson.json.encodeToString(request))
    }

    fun actionInvoke(request: ActionInvokeRequest): ActionInvokeResponse {
        return post("/action/invoke", ProtocolJson.json.encodeToString(request))
    }

    fun prefsList(request: PrefsListRequest): PrefsListResponse {
        return post("/prefs/list", ProtocolJson.json.encodeToString(request))
    }

    fun prefsGet(request: PrefsGetRequest): PrefsValueResponse {
        return post("/prefs/get", ProtocolJson.json.encodeToString(request))
    }

    fun prefsSet(request: PrefsSetRequest): PrefsMutationResponse {
        return post("/prefs/set", ProtocolJson.json.encodeToString(request))
    }

    fun prefsDelete(request: PrefsDeleteRequest): PrefsMutationResponse {
        return post("/prefs/delete", ProtocolJson.json.encodeToString(request))
    }

    fun dataStorePrefsList(request: DataStorePrefsListRequest): DataStorePrefsListResponse {
        return post("/datastore/preferences/list", ProtocolJson.json.encodeToString(request))
    }

    fun dataStorePrefsGet(request: DataStorePrefsGetRequest): DataStorePrefsValueResponse {
        return post("/datastore/preferences/get", ProtocolJson.json.encodeToString(request))
    }

    fun dataStorePrefsSet(request: DataStorePrefsSetRequest): DataStorePrefsMutationResponse {
        return post("/datastore/preferences/set", ProtocolJson.json.encodeToString(request))
    }

    fun dataStorePrefsDelete(request: DataStorePrefsDeleteRequest): DataStorePrefsMutationResponse {
        return post("/datastore/preferences/delete", ProtocolJson.json.encodeToString(request))
    }

    fun sqlQuery(request: SqlQueryRequest): SqlQueryResponse {
        return post("/storage/sql/query", ProtocolJson.json.encodeToString(request))
    }

    fun sqlExec(request: SqlExecRequest): SqlExecResponse {
        return post("/storage/sql/exec", ProtocolJson.json.encodeToString(request))
    }

    fun storageSnapshot(request: StorageSnapshotRequest = StorageSnapshotRequest()): StorageSnapshotResponse {
        return post("/storage/snapshot", ProtocolJson.json.encodeToString(request))
    }

    fun storageRestore(request: StorageRestoreRequest): StorageRestoreResponse {
        return post("/storage/restore", ProtocolJson.json.encodeToString(request))
    }

    fun overrideSet(request: OverrideSetRequest): OverrideValueResponse {
        return post("/override/set", ProtocolJson.json.encodeToString(request))
    }

    fun overrideGet(request: OverrideGetRequest): OverrideValueResponse {
        return post("/override/get", ProtocolJson.json.encodeToString(request))
    }

    fun overrideList(): OverrideListResponse {
        return post("/override/list", "{}")
    }

    fun overrideClear(request: OverrideClearRequest = OverrideClearRequest()): OverrideClearResponse {
        return post("/override/clear", ProtocolJson.json.encodeToString(request))
    }

    fun objectSearch(request: ObjectSearchRequest): ObjectSearchResponse {
        return post("/debug/objectSearch", ProtocolJson.json.encodeToString(request))
    }

    fun eval(request: EvalRequest): EvalResponse {
        return post("/debug/eval", ProtocolJson.json.encodeToString(request))
    }

    fun getField(request: ProbeGetFieldRequest): ProbeFieldResponse {
        return post("/probe/getField", ProtocolJson.json.encodeToString(request))
    }

    fun setField(request: ProbeSetFieldRequest): ProbeSetFieldResponse {
        return post("/probe/setField", ProtocolJson.json.encodeToString(request))
    }

    fun overrideReturn(request: HookOverrideReturnRequest): HookRuleResponse {
        return post("/hook/overrideReturn", ProtocolJson.json.encodeToString(request))
    }

    fun throwHook(request: HookThrowRequest): HookRuleResponse {
        return post("/hook/throw", ProtocolJson.json.encodeToString(request))
    }

    fun clearHook(request: HookClearRequest = HookClearRequest()): HookClearResponse {
        return post("/hook/clear", ProtocolJson.json.encodeToString(request))
    }

    fun mediaCapabilities(): MediaCapabilitiesResponse {
        return post("/media/capabilities", "{}")
    }

    fun mediaTargets(request: MediaTargetListRequest = MediaTargetListRequest()): MediaTargetListResponse {
        return post("/media/targets/list", ProtocolJson.json.encodeToString(request))
    }

    fun mediaFixtureRegister(request: MediaFixtureRegisterRequest): MediaFixtureRegisterResponse {
        return post("/media/fixture/register", ProtocolJson.json.encodeToString(request))
    }

    fun mediaFixtureList(request: MediaFixtureListRequest = MediaFixtureListRequest()): MediaFixtureListResponse {
        return post("/media/fixture/list", ProtocolJson.json.encodeToString(request))
    }

    fun mediaFixtureDelete(request: MediaFixtureDeleteRequest): MediaFixtureDeleteResponse {
        return post("/media/fixture/delete", ProtocolJson.json.encodeToString(request))
    }

    fun mediaAudioInject(request: MediaAudioInjectRequest): MediaAudioRuleResponse {
        return post("/media/audio/inject", ProtocolJson.json.encodeToString(request))
    }

    fun mediaAudioClear(request: MediaAudioClearRequest = MediaAudioClearRequest()): MediaClearResponse {
        return post("/media/audio/clear", ProtocolJson.json.encodeToString(request))
    }

    fun mediaAudioHistory(request: MediaAudioHistoryRequest = MediaAudioHistoryRequest()): MediaAudioHistoryResponse {
        return post("/media/audio/history", ProtocolJson.json.encodeToString(request))
    }

    fun mediaAudioAssertConsumed(request: MediaAudioAssertConsumedRequest): MediaAudioAssertConsumedResponse {
        return post("/media/audio/assertConsumed", ProtocolJson.json.encodeToString(request))
    }

    fun mediaCameraInjectFrames(request: MediaCameraInjectFramesRequest): MediaCameraRuleResponse {
        return post("/media/camera/injectFrames", ProtocolJson.json.encodeToString(request))
    }

    fun mediaCameraClear(request: MediaCameraClearRequest = MediaCameraClearRequest()): MediaClearResponse {
        return post("/media/camera/clear", ProtocolJson.json.encodeToString(request))
    }

    fun mediaCameraHistory(request: MediaCameraHistoryRequest = MediaCameraHistoryRequest()): MediaCameraHistoryResponse {
        return post("/media/camera/history", ProtocolJson.json.encodeToString(request))
    }

    fun mediaCameraSnapshot(request: MediaCameraSnapshotRequest = MediaCameraSnapshotRequest()): MediaCameraSnapshotResponse {
        return post("/media/camera/snapshot", ProtocolJson.json.encodeToString(request))
    }

    fun mediaCameraAssertConsumed(request: MediaCameraAssertConsumedRequest): MediaCameraAssertConsumedResponse {
        return post("/media/camera/assertConsumed", ProtocolJson.json.encodeToString(request))
    }

    private inline fun <reified T> get(path: String, authenticate: Boolean = true): T {
        if (authenticate) ensureSession()
        return request(path = path, method = "GET", body = null, authenticate = authenticate)
    }

    private inline fun <reified T> post(path: String, body: String): T {
        ensureSession()
        return request(path = path, method = "POST", body = body, authenticate = true)
    }

    private fun ensureSession() {
        if (sessionToken == null) {
            ping()
        }
    }

    private inline fun <reified T> request(
        path: String,
        method: String,
        body: String?,
        authenticate: Boolean,
    ): T {
        var result = execute(path, method, body, authenticate)
        if (authenticate && result.status == 401) {
            sessionToken = null
            ensureSession()
            result = execute(path, method, body, authenticate = true)
        }
        return decode(result)
    }

    private fun execute(path: String, method: String, body: String?, authenticate: Boolean): HttpResult {
        val connection = open(path, method, authenticate)
        return try {
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val status = connection.responseCode
            val responseBody = if (status in 200..299) {
                connection.inputStream.bufferedReader().readText()
            } else {
                connection.errorStream?.bufferedReader()?.readText().orEmpty()
            }
            HttpResult(status, responseBody)
        } finally {
            connection.disconnect()
        }
    }

    private fun open(path: String, method: String, authenticate: Boolean): HttpURLConnection {
        val url = URI("$baseUrl$path").toURL()
        return (url.openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 2_000
            readTimeout = 5_000
            if (authenticate) {
                setRequestProperty(
                    RuntimeAuth.SESSION_TOKEN_HEADER,
                    sessionToken ?: error("Runtime session token is not initialized"),
                )
            }
        }
    }

    private inline fun <reified T> decode(result: HttpResult): T {
        if (result.status !in 200..299) {
            error("Runtime request failed (${result.status}): ${result.body}")
        }
        return ProtocolJson.json.decodeFromString(result.body)
    }

    private data class HttpResult(val status: Int, val body: String)
}
