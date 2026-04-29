package com.riftwalker.aidebug.daemon

import com.riftwalker.aidebug.protocol.ScenarioRunResponse
import java.util.concurrent.ConcurrentHashMap

class ScenarioRunStore {
    private val runs = ConcurrentHashMap<String, ScenarioRunResponse>()

    fun put(run: ScenarioRunResponse) {
        runs[run.runId] = run
    }

    fun get(runId: String): ScenarioRunResponse? = runs[runId]

    fun latest(): ScenarioRunResponse? = runs.values.maxByOrNull { it.finishedAtEpochMs }
}
