package com.riftwalker.aidebug.runtime.debug

import com.riftwalker.aidebug.protocol.DebugStateDescriptor
import com.riftwalker.aidebug.protocol.EvalRequest
import com.riftwalker.aidebug.protocol.HookClearRequest
import com.riftwalker.aidebug.protocol.HookOverrideReturnRequest
import com.riftwalker.aidebug.protocol.HookThrowRequest
import com.riftwalker.aidebug.protocol.ObjectSearchRequest
import com.riftwalker.aidebug.protocol.ProbeGetFieldRequest
import com.riftwalker.aidebug.protocol.ProbeSetFieldRequest
import com.riftwalker.aidebug.protocol.StateSetRequest
import com.riftwalker.aidebug.runtime.AiDebugHookBridge
import com.riftwalker.aidebug.runtime.AiDebugRuntime
import com.riftwalker.aidebug.runtime.AuditLog
import com.riftwalker.aidebug.runtime.CapabilityRegistry
import com.riftwalker.aidebug.runtime.CleanupRegistry
import com.riftwalker.aidebug.runtime.network.NetworkController
import com.riftwalker.aidebug.runtime.override.DebugOverrideStore
import com.riftwalker.aidebug.runtime.state.StateController
import com.riftwalker.aidebug.runtime.state.StateRegistration
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DynamicDebugControllerTest {
    @Test
    fun searchesTrackedObjectAndReadsField() {
        val controller = newFixture().debug
        val session = SessionFixture()
        val handle = controller.track("sample.session", session)

        val result = controller.objectSearch(ObjectSearchRequest(query = "vip")).results.firstOrNull()

        assertNotNull(result)
        assertEquals(handle.id, result.handle)
        assertEquals("isVip", result.fieldPath)
        val field = controller.getField(ProbeGetFieldRequest(handle.id, "isVip"))
        assertFalse(field.value.value?.jsonPrimitive?.boolean == true)
    }

    @Test
    fun setsFieldAndRestoresWithCleanupToken() {
        val fixture = newFixture()
        val session = SessionFixture()
        val handle = fixture.debug.track("sample.session", session)

        val response = fixture.debug.setField(ProbeSetFieldRequest(handle.id, "isVip", JsonPrimitive(true)))

        assertTrue(session.isVip)
        assertNotNull(response.restoreToken)
        fixture.cleanup.cleanupAll()
        assertFalse(session.isVip)
    }

    @Test
    fun evalCanReadAndMutateStateDsl() {
        val fixture = newFixture()
        var isVip = false
        fixture.state.registerState(
            StateRegistration(
                descriptor = DebugStateDescriptor(
                    path = "user.isVip",
                    schema = buildJsonObject { put("type", "boolean") },
                    mutable = true,
                    description = "VIP",
                ),
                read = { JsonPrimitive(isVip) },
                write = { isVip = it.jsonPrimitive.boolean },
                reset = { isVip = false },
            ),
        )

        val set = fixture.debug.eval(
            EvalRequest(
                language = "debug-dsl",
                code = """env.state.set("user.isVip", true)""",
                sideEffects = "may_mutate",
            ),
        )
        val get = fixture.debug.eval(
            EvalRequest(
                language = "debug-dsl",
                code = """env.state.get("user.isVip")""",
            ),
        )

        assertTrue(isVip)
        assertNotNull(set.cleanupToken)
        assertTrue(get.result?.jsonPrimitive?.boolean == true)
    }

    @Test
    fun hookOverrideReturnIsConsumedByManualHookPoint() {
        val fixture = newFixture()
        fixture.debug.overrideReturn(
            HookOverrideReturnRequest(
                methodId = "FeatureFlags#isEnabled(java.lang.String)",
                whenArgs = listOf(JsonPrimitive("newCheckout")),
                returnValue = JsonPrimitive(true),
                times = 1,
            ),
        )

        val first = fixture.debug.hookBoolean(
            methodId = "FeatureFlags#isEnabled(java.lang.String)",
            args = listOf("newCheckout"),
        ) { false }
        val second = fixture.debug.hookBoolean(
            methodId = "FeatureFlags#isEnabled(java.lang.String)",
            args = listOf("newCheckout"),
        ) { false }

        assertTrue(first)
        assertFalse(second)
    }

    @Test
    fun hookBridgeConsumesRuntimeReturnAndThrowRules() {
        val methodId = "Spec09#featureEnabled()"
        AiDebugRuntime.dynamicDebugController().clearHook(HookClearRequest(methodId = methodId))
        AiDebugRuntime.dynamicDebugController().overrideReturn(
            HookOverrideReturnRequest(
                methodId = methodId,
                returnValue = JsonPrimitive(true),
                times = 1,
            ),
        )

        assertTrue(AiDebugHookBridge.hookBoolean(methodId) == true)
        assertEquals(null, AiDebugHookBridge.hookBoolean(methodId))

        AiDebugRuntime.dynamicDebugController().throwError(
            HookThrowRequest(
                methodId = methodId,
                message = "forced by spec09",
            ),
        )

        assertFailsWith<IllegalStateException> {
            AiDebugHookBridge.hookBoolean(methodId)
        }
        AiDebugRuntime.dynamicDebugController().clearHook(HookClearRequest(methodId = methodId))
    }

    private fun newFixture(): Fixture {
        val auditLog = AuditLog()
        val cleanup = CleanupRegistry(auditLog)
        val state = StateController(auditLog, cleanup, CapabilityRegistry())
        val network = NetworkController(auditLog, cleanup)
        val overrides = DebugOverrideStore(auditLog, cleanup)
        return Fixture(
            cleanup = cleanup,
            state = state,
            debug = DynamicDebugController(auditLog, cleanup, state, network, overrides),
        )
    }

    private data class Fixture(
        val cleanup: CleanupRegistry,
        val state: StateController,
        val debug: DynamicDebugController,
    )

    private class SessionFixture {
        var isVip: Boolean = false
    }
}
