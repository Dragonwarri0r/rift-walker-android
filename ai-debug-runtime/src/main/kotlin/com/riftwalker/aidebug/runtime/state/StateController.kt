package com.riftwalker.aidebug.runtime.state

import com.riftwalker.aidebug.protocol.ActionInvokeRequest
import com.riftwalker.aidebug.protocol.ActionInvokeResponse
import com.riftwalker.aidebug.protocol.ActionListRequest
import com.riftwalker.aidebug.protocol.ActionListResponse
import com.riftwalker.aidebug.protocol.DebugActionDescriptor
import com.riftwalker.aidebug.protocol.DebugStateDescriptor
import com.riftwalker.aidebug.protocol.StateDiffEntry
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
import com.riftwalker.aidebug.runtime.AuditLog
import com.riftwalker.aidebug.runtime.CapabilityRegistry
import com.riftwalker.aidebug.runtime.CleanupRegistry
import kotlinx.serialization.json.JsonElement
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class StateController(
    private val auditLog: AuditLog,
    private val cleanupRegistry: CleanupRegistry,
    private val capabilities: CapabilityRegistry,
) {
    private val states = ConcurrentHashMap<String, StateRegistration>()
    private val actions = ConcurrentHashMap<String, ActionRegistration>()
    private val snapshots = ConcurrentHashMap<String, Snapshot>()

    fun registerState(registration: StateRegistration) {
        states[registration.descriptor.path] = registration
        capabilities.register(registration.descriptor.toCapabilityDescriptor())
    }

    fun registerAction(registration: ActionRegistration) {
        actions[registration.descriptor.path] = registration
        capabilities.register(registration.descriptor.toCapabilityDescriptor())
    }

    fun list(request: StateListRequest): StateListResponse {
        val query = request.query
        val tag = request.tag
        val descriptors = states.values
            .asSequence()
            .map { it.descriptor }
            .filter { descriptor ->
                query.isNullOrBlank() ||
                    descriptor.path.contains(query, ignoreCase = true) ||
                    descriptor.description.contains(query, ignoreCase = true) ||
                    descriptor.tags.any { it.contains(query, ignoreCase = true) }
            }
            .filter { descriptor -> tag == null || descriptor.tags.contains(tag) }
            .sortedBy { it.path }
            .toList()
        auditLog.recordRead("state.list", query, "success")
        return StateListResponse(descriptors)
    }

    fun get(request: StateGetRequest): StateValueResponse {
        val registration = states[request.path] ?: error("Unknown state path: ${request.path}")
        val value = registration.read()
        auditLog.recordRead("state.get", request.path, "success")
        return StateValueResponse(path = request.path, value = value, mutable = registration.descriptor.mutable)
    }

    fun set(request: StateSetRequest): StateMutationResponse {
        val registration = states[request.path] ?: error("Unknown state path: ${request.path}")
        require(registration.descriptor.mutable) { "State is immutable: ${request.path}" }
        val write = registration.write ?: error("State has no setter: ${request.path}")
        val previous = registration.read()
        write(request.value)
        val restoreToken = previous?.let { previousValue ->
            cleanupRegistry.register("restore state ${request.path}") {
                write(previousValue)
            }
        }
        auditLog.recordMutation(
            tool = "state.set",
            target = request.path,
            restoreToken = restoreToken,
            status = "success",
            argumentsSummary = request.value.toString(),
        )
        return StateMutationResponse(path = request.path, restoreToken = restoreToken)
    }

    fun reset(request: StateResetRequest): StateMutationResponse {
        val registration = states[request.path] ?: error("Unknown state path: ${request.path}")
        require(registration.descriptor.mutable) { "State is immutable: ${request.path}" }
        val previous = registration.read()
        when {
            registration.reset != null -> registration.reset.invoke()
            registration.write != null && registration.initialValue != null -> registration.write.invoke(registration.initialValue)
            else -> error("State has no reset behavior: ${request.path}")
        }
        val restoreToken = previous?.let { previousValue ->
            registration.write?.let { write ->
                cleanupRegistry.register("restore state ${request.path}") {
                    write(previousValue)
                }
            }
        }
        auditLog.recordMutation("state.reset", request.path, restoreToken, "success")
        return StateMutationResponse(path = request.path, restoreToken = restoreToken)
    }

    fun snapshot(request: StateSnapshotRequest): StateSnapshotResponse {
        val selected = request.paths?.toSet()
        val values = states.values
            .asSequence()
            .filter { selected == null || it.descriptor.path in selected }
            .associate { it.descriptor.path to it.read() }
        val snapshot = Snapshot(
            id = "state_snap_${UUID.randomUUID()}",
            name = request.name,
            values = values,
            createdAtEpochMs = System.currentTimeMillis(),
        )
        snapshots[snapshot.id] = snapshot
        cleanupRegistry.register("drop state snapshot ${snapshot.id}") {
            snapshots.remove(snapshot.id)
        }
        auditLog.recordRead("state.snapshot", snapshot.id, "success", argumentsSummary = request.name)
        return StateSnapshotResponse(
            snapshotId = snapshot.id,
            name = snapshot.name,
            paths = values.keys.sorted(),
            createdAtEpochMs = snapshot.createdAtEpochMs,
        )
    }

    fun restore(request: StateRestoreRequest): StateRestoreResponse {
        val snapshot = snapshots[request.snapshotId] ?: error("Unknown state snapshot: ${request.snapshotId}")
        val restored = mutableListOf<String>()
        val skipped = mutableListOf<String>()
        snapshot.values.forEach { (path, value) ->
            val registration = states[path]
            val write = registration?.write
            if (registration?.descriptor?.mutable == true && write != null && value != null) {
                write(value)
                restored += path
            } else {
                skipped += path
            }
        }
        auditLog.recordMutation("state.restore", request.snapshotId, restoreToken = null, status = "success")
        return StateRestoreResponse(request.snapshotId, restored.sorted(), skipped.sorted())
    }

    fun diff(request: StateDiffRequest): StateDiffResponse {
        val snapshot = snapshots[request.snapshotId] ?: error("Unknown state snapshot: ${request.snapshotId}")
        val diffs = snapshot.values.keys
            .sorted()
            .map { path ->
                val before = snapshot.values[path]
                val after = states[path]?.read()
                StateDiffEntry(path = path, before = before, after = after, changed = before != after)
            }
        auditLog.recordRead("state.diff", request.snapshotId, "success")
        return StateDiffResponse(request.snapshotId, diffs)
    }

    fun listActions(request: ActionListRequest): ActionListResponse {
        val query = request.query
        val tag = request.tag
        val descriptors = actions.values
            .asSequence()
            .map { it.descriptor }
            .filter { descriptor ->
                query.isNullOrBlank() ||
                    descriptor.path.contains(query, ignoreCase = true) ||
                    descriptor.description.contains(query, ignoreCase = true) ||
                    descriptor.tags.any { it.contains(query, ignoreCase = true) }
            }
            .filter { descriptor -> tag == null || descriptor.tags.contains(tag) }
            .sortedBy { it.path }
            .toList()
        auditLog.recordRead("action.list", query, "success")
        return ActionListResponse(descriptors)
    }

    fun invokeAction(request: ActionInvokeRequest): ActionInvokeResponse {
        val registration = actions[request.path] ?: error("Unknown action path: ${request.path}")
        val result = registration.invoke(request.input)
        auditLog.record(
            tool = "action.invoke",
            target = request.path,
            effect = "action",
            restoreToken = null,
            status = "success",
            argumentsSummary = request.input?.toString(),
            resultSummary = result?.toString(),
        )
        return ActionInvokeResponse(path = request.path, result = result)
    }

    private data class Snapshot(
        val id: String,
        val name: String?,
        val values: Map<String, JsonElement?>,
        val createdAtEpochMs: Long,
    )
}
