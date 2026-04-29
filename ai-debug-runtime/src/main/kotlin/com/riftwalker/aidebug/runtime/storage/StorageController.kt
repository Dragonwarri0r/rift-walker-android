package com.riftwalker.aidebug.runtime.storage

import android.content.Context
import com.riftwalker.aidebug.protocol.DataStorePrefsDeleteRequest
import com.riftwalker.aidebug.protocol.DataStorePrefsGetRequest
import com.riftwalker.aidebug.protocol.DataStorePrefsListRequest
import com.riftwalker.aidebug.protocol.DataStorePrefsSetRequest
import com.riftwalker.aidebug.protocol.PrefsDeleteRequest
import com.riftwalker.aidebug.protocol.PrefsGetRequest
import com.riftwalker.aidebug.protocol.PrefsListRequest
import com.riftwalker.aidebug.protocol.PrefsSetRequest
import com.riftwalker.aidebug.protocol.SqlExecRequest
import com.riftwalker.aidebug.protocol.SqlQueryRequest
import com.riftwalker.aidebug.protocol.StorageRestoreRequest
import com.riftwalker.aidebug.protocol.StorageRestoreResponse
import com.riftwalker.aidebug.protocol.StorageSnapshotRequest
import com.riftwalker.aidebug.protocol.StorageSnapshotResponse
import com.riftwalker.aidebug.runtime.AuditLog
import com.riftwalker.aidebug.runtime.CleanupRegistry
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class StorageController(
    context: Context,
    private val auditLog: AuditLog,
    private val cleanupRegistry: CleanupRegistry,
) {
    private val prefs = SharedPreferencesAdapter(context, auditLog, cleanupRegistry)
    private val sqlite = SqliteAdapter(context, auditLog, cleanupRegistry)
    private val dataStorePreferences = DataStorePreferencesAdapter(context, auditLog, cleanupRegistry)
    private val snapshots = ConcurrentHashMap<String, Snapshot>()

    fun prefsList(request: PrefsListRequest) = prefs.list(request)

    fun prefsGet(request: PrefsGetRequest) = prefs.get(request)

    fun prefsSet(request: PrefsSetRequest) = prefs.set(request)

    fun prefsDelete(request: PrefsDeleteRequest) = prefs.delete(request)

    fun dataStorePrefsList(request: DataStorePrefsListRequest) = dataStorePreferences.list(request)

    fun dataStorePrefsGet(request: DataStorePrefsGetRequest) = dataStorePreferences.get(request)

    fun dataStorePrefsSet(request: DataStorePrefsSetRequest) = dataStorePreferences.set(request)

    fun dataStorePrefsDelete(request: DataStorePrefsDeleteRequest) = dataStorePreferences.delete(request)

    fun sqlQuery(request: SqlQueryRequest) = sqlite.query(request)

    fun sqlExec(request: SqlExecRequest) = sqlite.exec(request)

    fun snapshot(request: StorageSnapshotRequest): StorageSnapshotResponse {
        val snapshot = Snapshot(
            id = "storage_snap_${UUID.randomUUID()}",
            name = request.name,
            prefs = request.prefsFiles.associateWith { prefs.dump(it) },
            databases = request.databaseNames.associateWith { sqlite.captureDatabaseFiles(it) },
            dataStorePreferences = request.dataStorePreferenceNames.associateWith { dataStorePreferences.dump(it) },
            createdAtEpochMs = System.currentTimeMillis(),
        )
        snapshots[snapshot.id] = snapshot
        cleanupRegistry.register("drop storage snapshot ${snapshot.id}") {
            snapshots.remove(snapshot.id)
        }
        auditLog.recordRead("storage.snapshot", snapshot.id, "success", argumentsSummary = request.name)
        return StorageSnapshotResponse(
            snapshotId = snapshot.id,
            name = snapshot.name,
            prefsFiles = snapshot.prefs.keys.sorted(),
            databaseNames = snapshot.databases.keys.sorted(),
            dataStorePreferenceNames = snapshot.dataStorePreferences.keys.sorted(),
            createdAtEpochMs = snapshot.createdAtEpochMs,
        )
    }

    fun restore(request: StorageRestoreRequest): StorageRestoreResponse {
        val snapshot = snapshots[request.snapshotId] ?: error("Unknown storage snapshot: ${request.snapshotId}")
        snapshot.prefs.forEach { (fileName, entries) ->
            prefs.restore(fileName, entries)
        }
        snapshot.databases.forEach { (databaseName, files) ->
            sqlite.restoreDatabaseFiles(databaseName, files)
        }
        snapshot.dataStorePreferences.forEach { (name, entries) ->
            dataStorePreferences.restore(name, entries)
        }
        auditLog.recordMutation("storage.restore", request.snapshotId, restoreToken = null, status = "success")
        return StorageRestoreResponse(
            snapshotId = snapshot.id,
            restoredPrefsFiles = snapshot.prefs.keys.sorted(),
            restoredDatabaseNames = snapshot.databases.keys.sorted(),
            restoredDataStorePreferenceNames = snapshot.dataStorePreferences.keys.sorted(),
        )
    }

    private data class Snapshot(
        val id: String,
        val name: String?,
        val prefs: Map<String, Map<String, com.riftwalker.aidebug.protocol.PrefsEntry>>,
        val databases: Map<String, SqliteAdapter.DatabaseFileSnapshot>,
        val dataStorePreferences: Map<String, Map<String, com.riftwalker.aidebug.protocol.PrefsEntry>>,
        val createdAtEpochMs: Long,
    )
}
