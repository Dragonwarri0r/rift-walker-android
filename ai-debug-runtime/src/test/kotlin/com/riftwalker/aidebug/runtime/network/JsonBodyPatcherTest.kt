package com.riftwalker.aidebug.runtime.network

import com.riftwalker.aidebug.protocol.ResponsePatch
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JsonBodyPatcherTest {
    @Test
    fun replacesNestedObjectValue() {
        val result = JsonBodyPatcher.apply(
            body = """{"data":{"isVip":true,"name":"Ada"}}""",
            patches = listOf(ResponsePatch("replace", "$.data.isVip", JsonPrimitive(false))),
        )

        assertTrue(result.contains(""""isVip":false"""))
        assertTrue(result.contains(""""name":"Ada""""))
    }

    @Test
    fun replacesArrayValue() {
        val result = JsonBodyPatcher.apply(
            body = """{"items":[{"price":10}]}""",
            patches = listOf(ResponsePatch("replace", "$.items[0].price", JsonPrimitive(20))),
        )

        assertEquals("""{"items":[{"price":20}]}""", result)
    }
}
