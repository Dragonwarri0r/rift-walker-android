package com.riftwalker.aidebug.daemon

import com.riftwalker.aidebug.protocol.ProtocolJson
import com.riftwalker.aidebug.protocol.ReportGenerateRequest
import com.riftwalker.aidebug.protocol.ScenarioRunRequest
import com.riftwalker.aidebug.protocol.ScenarioStep
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.io.path.createTempDirectory
import kotlin.io.path.exists
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ScenarioRunnerTest {
    @Test
    fun runExecutesStepsInOrder() = runBlocking {
        val registry = DaemonToolRegistry()
        val calls = mutableListOf<String>()
        registry.register("state.snapshot", "snapshot") {
            calls += "state.snapshot"
            buildJsonObject { put("snapshotId", "before") }
        }
        registry.register("state.set", "set") {
            calls += "state.set"
            buildJsonObject { put("ok", true) }
        }

        val store = ScenarioRunStore()
        val response = ScenarioRunner(registry, store).run(
            ScenarioRunRequest(
                name = "happy_path",
                steps = listOf(
                    ScenarioStep(id = "snapshot", tool = "state.snapshot"),
                    ScenarioStep(
                        id = "vip",
                        tool = "state.set",
                        arguments = buildJsonObject {
                            put("path", "user.isVip")
                            put("value", true)
                        },
                    ),
                ),
            ),
        )

        assertEquals("passed", response.status)
        assertEquals(listOf("state.snapshot", "state.set"), calls)
        assertEquals(2, response.steps.size)
        assertEquals(response, store.get(response.runId))
    }

    @Test
    fun runStopsOnFirstFailureByDefault() = runBlocking {
        val registry = DaemonToolRegistry()
        val calls = mutableListOf<String>()
        registry.register("fail", "fail") {
            calls += "fail"
            error("boom")
        }
        registry.register("after", "after") {
            calls += "after"
            buildJsonObject { put("ok", true) }
        }

        val response = ScenarioRunner(registry, ScenarioRunStore()).run(
            ScenarioRunRequest(
                name = "failure",
                steps = listOf(
                    ScenarioStep(tool = "fail"),
                    ScenarioStep(tool = "after"),
                ),
            ),
        )

        assertEquals("failed", response.status)
        assertEquals(listOf("fail"), calls)
        assertEquals(2, response.steps.size)
        assertEquals(listOf("failed", "skipped"), response.steps.map { it.status })
    }

    @Test
    fun runContinuesWhenStepOptsIn() = runBlocking {
        val registry = DaemonToolRegistry()
        val calls = mutableListOf<String>()
        registry.register("fail", "fail") {
            calls += "fail"
            error("boom")
        }
        registry.register("after", "after") {
            calls += "after"
            buildJsonObject { put("ok", true) }
        }

        val response = ScenarioRunner(registry, ScenarioRunStore()).run(
            ScenarioRunRequest(
                name = "continue_failure",
                steps = listOf(
                    ScenarioStep(tool = "fail", continueOnError = true),
                    ScenarioStep(tool = "after"),
                ),
            ),
        )

        assertEquals("failed", response.status)
        assertEquals(listOf("fail", "after"), calls)
        assertEquals(listOf("failed", "passed"), response.steps.map { it.status })
    }

    @Test
    fun runRejectsNestedScenarioAsFailedStep() = runBlocking {
        val response = ScenarioRunner(DaemonToolRegistry(), ScenarioRunStore()).run(
            ScenarioRunRequest(
                name = "nested",
                steps = listOf(ScenarioStep(tool = "scenario.run")),
            ),
        )

        assertEquals("failed", response.status)
        assertEquals("failed", response.steps.single().status)
        assertTrue(response.steps.single().error.orEmpty().contains("cannot be nested"))
    }

    @Test
    fun reportGenerateWritesScenarioAndBestEffortArtifacts() = runBlocking {
        val registry = DaemonToolRegistry()
        registry.register("audit.history", "audit") {
            buildJsonObject { put("events", "ok") }
        }
        registry.register("network.history", "network") {
            buildJsonObject { put("records", "ok") }
        }

        val store = ScenarioRunStore()
        val run = ScenarioRunner(registry, store).run(
            ScenarioRunRequest(
                name = "reportable",
                steps = listOf(
                    ScenarioStep(tool = "audit.history"),
                ),
            ),
        )
        val outputRoot = createTempDirectory("ai-debug-report-test")
        val response = ScenarioReportGenerator(registry, store, outputRoot).generate(
            ReportGenerateRequest(runId = run.runId),
        )

        assertTrue(outputRoot.resolve("${response.reportId}.json").exists())
        assertEquals(run.runId, response.report.run?.runId)
        assertNotNull(response.report.audit)
        assertNotNull(response.report.networkHistory)
        assertEquals(emptyList(), response.report.errors)
        assertTrue(ProtocolJson.json.encodeToString(response.report).contains("reportable"))
    }

    @Test
    fun dogfoodScenarioCombinesAppRuntimeNetworkStateAndCleanupTools() {
        val scenario = DogfoodScenarios.sampleProfileBranch(
            serial = "device-1",
            hostPort = 47913,
            devicePort = 37913,
        )

        assertEquals("dogfood_sample_profile_branch", scenario.name)
        assertTrue(scenario.continueOnError)
        val tools = scenario.steps.map { it.tool }
        assertTrue("adb.forward" in tools)
        assertTrue("app.launch" in tools)
        assertTrue("runtime.waitForPing" in tools)
        assertTrue("network.mock" in tools)
        assertTrue("network.assertCalled" in tools)
        assertTrue("state.set" in tools)
        assertTrue("storage.sql.exec" in tools)
        assertTrue("override.set" in tools)
        assertTrue("hook.overrideReturn" in tools)
        assertTrue(scenario.steps.takeLast(5).map { it.id }.containsAll(listOf("cleanup_hook", "cleanup_network")))

        val assertStep = scenario.steps.first { it.id == "assert_profile_called" }
        assertEquals(
            "3000",
            assertStep.arguments!!.jsonObject["timeoutMs"]!!.jsonPrimitive.content,
        )
    }

    @Test
    fun appAndRuntimeWaitToolsAreRegisteredForMcpMode() {
        val registry = DaemonToolRegistry()
        AppMcpTools(AdbExecutor()).registerInto(registry)
        RuntimeMcpTools(RuntimeHttpClient("http://127.0.0.1:1")).registerInto(registry)

        assertTrue("device.list" in registry.names())
        assertTrue("adb.forward" in registry.names())
        assertTrue("app.forceStop" in registry.names())
        assertTrue("app.launch" in registry.names())
        assertTrue("runtime.waitForPing" in registry.names())
    }
}
