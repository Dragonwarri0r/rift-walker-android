package com.riftwalker.aidebug.runtime.state

import com.riftwalker.aidebug.protocol.DebugStateDescriptor
import com.riftwalker.aidebug.protocol.StateDiffRequest
import com.riftwalker.aidebug.protocol.StateGetRequest
import com.riftwalker.aidebug.protocol.StateRestoreRequest
import com.riftwalker.aidebug.protocol.StateSetRequest
import com.riftwalker.aidebug.protocol.StateSnapshotRequest
import com.riftwalker.aidebug.runtime.AuditLog
import com.riftwalker.aidebug.runtime.CapabilityRegistry
import com.riftwalker.aidebug.runtime.CleanupRegistry
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StateControllerTest {
    @Test
    fun setsSnapshotsDiffsAndRestoresState() {
        var isVip = false
        val controller = newController()
        controller.registerState(
            StateRegistration(
                descriptor = DebugStateDescriptor(
                    path = "user.isVip",
                    schema = buildJsonObject { put("type", "boolean") },
                    mutable = true,
                    description = "VIP state",
                ),
                read = { JsonPrimitive(isVip) },
                write = { isVip = it.jsonPrimitive.boolean },
                reset = { isVip = false },
            ),
        )

        val snapshot = controller.snapshot(StateSnapshotRequest(name = "before"))
        controller.set(StateSetRequest("user.isVip", JsonPrimitive(true)))

        assertTrue(controller.get(StateGetRequest("user.isVip")).value?.jsonPrimitive?.boolean == true)
        val diff = controller.diff(StateDiffRequest(snapshot.snapshotId)).diffs.single()
        assertEquals("user.isVip", diff.path)
        assertTrue(diff.changed)

        controller.restore(StateRestoreRequest(snapshot.snapshotId))

        assertFalse(isVip)
        assertFalse(controller.get(StateGetRequest("user.isVip")).value?.jsonPrimitive?.boolean == true)
    }

    @Test
    fun resetUsesRegisteredResetCallback() {
        var state = true
        val controller = newController()
        controller.registerState(
            StateRegistration(
                descriptor = DebugStateDescriptor(
                    path = "feature.enabled",
                    schema = buildJsonObject { put("type", "boolean") },
                    mutable = true,
                    description = "Feature flag",
                ),
                read = { JsonPrimitive(state) },
                write = { state = it.jsonPrimitive.boolean },
                reset = { state = false },
            ),
        )

        controller.set(StateSetRequest("feature.enabled", JsonPrimitive(true)))
        controller.reset(com.riftwalker.aidebug.protocol.StateResetRequest("feature.enabled"))

        assertFalse(state)
    }

    private fun newController(): StateController {
        val auditLog = AuditLog()
        return StateController(auditLog, CleanupRegistry(auditLog), CapabilityRegistry())
    }
}
