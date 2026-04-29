package com.riftwalker.aidebug.daemon

import com.riftwalker.aidebug.protocol.ProtocolJson
import com.riftwalker.aidebug.protocol.ReportGenerateRequest
import com.riftwalker.aidebug.protocol.ScenarioRunRequest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put
import java.nio.file.Path

class ScenarioMcpTools(
    private val registry: DaemonToolRegistry,
    reportRoot: Path = Path.of("build", "ai-debug", "reports"),
) {
    private val store = ScenarioRunStore()
    private val runner = ScenarioRunner(registry, store)
    private val reports = ScenarioReportGenerator(registry, store, reportRoot)

    fun registerInto() {
        registry.register(
            name = "scenario.run",
            description = "Run an ordered reproducible scenario made of existing daemon tools",
            inputSchema = objectSchema(),
        ) { args ->
            val request = ProtocolJson.json.decodeFromJsonElement<ScenarioRunRequest>(
                args ?: error("scenario.run requires arguments"),
            )
            ProtocolJson.json.encodeToJsonElement(runner.run(request))
        }
        registry.register(
            name = "report.generate",
            description = "Generate a JSON report artifact with scenario, audit, and network data",
            inputSchema = objectSchema(),
        ) { args ->
            val request = args?.let { ProtocolJson.json.decodeFromJsonElement<ReportGenerateRequest>(it) }
                ?: ReportGenerateRequest()
            ProtocolJson.json.encodeToJsonElement(reports.generate(request))
        }
    }

    private fun objectSchema() = buildJsonObject {
        put("type", "object")
        put("additionalProperties", true)
    }
}
