package com.riftwalker.aidebug.runtime.override

import com.riftwalker.aidebug.protocol.OverrideClearRequest
import com.riftwalker.aidebug.protocol.OverrideGetRequest
import com.riftwalker.aidebug.protocol.OverrideSetRequest
import com.riftwalker.aidebug.runtime.AuditLog
import com.riftwalker.aidebug.runtime.CleanupRegistry
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DebugOverrideStoreTest {
    @Test
    fun storesTypedOverridesAndClearsThem() {
        val store = newStore()

        store.set(OverrideSetRequest("feature.newCheckout", JsonPrimitive(true)))

        assertTrue(store.getBoolean("feature.newCheckout") == true)
        assertTrue(store.get(OverrideGetRequest("feature.newCheckout")).exists)

        val cleared = store.clear(OverrideClearRequest("feature.newCheckout"))

        assertEquals(1, cleared.cleared)
        assertFalse(store.get(OverrideGetRequest("feature.newCheckout")).exists)
    }

    @Test
    fun expiresOverridesByTtl() {
        val store = newStore()

        store.set(OverrideSetRequest("clock.now", JsonPrimitive("soon"), ttlMs = 1))
        Thread.sleep(5)

        assertEquals(null, store.getString("clock.now"))
        assertFalse(store.get(OverrideGetRequest("clock.now")).exists)
    }

    private fun newStore(): DebugOverrideStore {
        val auditLog = AuditLog()
        return DebugOverrideStore(auditLog, CleanupRegistry(auditLog))
    }
}
