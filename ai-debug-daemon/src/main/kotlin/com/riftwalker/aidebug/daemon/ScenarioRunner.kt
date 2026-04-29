package com.riftwalker.aidebug.daemon

import com.riftwalker.aidebug.protocol.ScenarioRunRequest
import com.riftwalker.aidebug.protocol.ScenarioRunResponse
import com.riftwalker.aidebug.protocol.ScenarioStep
import com.riftwalker.aidebug.protocol.ScenarioStepResult
import java.util.UUID

class ScenarioRunner(
    private val registry: DaemonToolRegistry,
    private val store: ScenarioRunStore,
) {
    suspend fun run(request: ScenarioRunRequest): ScenarioRunResponse {
        require(request.name.isNotBlank()) { "scenario name must not be blank" }
        require(request.steps.isNotEmpty()) { "scenario must include at least one step" }

        val started = System.currentTimeMillis()
        val results = mutableListOf<ScenarioStepResult>()
        var failed = false

        request.steps.forEachIndexed { index, step ->
            if (failed) {
                results += skippedStep(index, step)
                return@forEachIndexed
            }
            val result = runStep(index, step)
            results += result
            val shouldContinue = step.continueOnError ?: request.continueOnError
            if (result.status == "failed" && !shouldContinue) {
                failed = true
            }
        }

        val finished = System.currentTimeMillis()
        val response = ScenarioRunResponse(
            runId = "run_${UUID.randomUUID()}",
            name = request.name,
            status = if (failed || results.any { it.status == "failed" }) "failed" else "passed",
            startedAtEpochMs = started,
            finishedAtEpochMs = finished,
            durationMs = finished - started,
            steps = results,
        )
        store.put(response)
        return response
    }

    private suspend fun runStep(index: Int, step: ScenarioStep): ScenarioStepResult {
        val started = System.currentTimeMillis()
        return runCatching {
            require(step.tool.isNotBlank()) { "scenario step tool must not be blank" }
            require(step.tool != "scenario.run") { "scenario.run cannot be nested inside a scenario" }

            val result = registry.invoke(step.tool, step.arguments)
            val finished = System.currentTimeMillis()
            ScenarioStepResult(
                index = index,
                id = step.id,
                tool = step.tool,
                status = "passed",
                startedAtEpochMs = started,
                durationMs = finished - started,
                result = result,
            )
        }.getOrElse { error ->
            val finished = System.currentTimeMillis()
            ScenarioStepResult(
                index = index,
                id = step.id,
                tool = step.tool,
                status = "failed",
                startedAtEpochMs = started,
                durationMs = finished - started,
                error = error.message ?: error::class.java.simpleName,
            )
        }
    }

    private fun skippedStep(index: Int, step: ScenarioStep): ScenarioStepResult {
        return ScenarioStepResult(
            index = index,
            id = step.id,
            tool = step.tool,
            status = "skipped",
            startedAtEpochMs = System.currentTimeMillis(),
            durationMs = 0,
            error = "Skipped after a previous step failed",
        )
    }
}
