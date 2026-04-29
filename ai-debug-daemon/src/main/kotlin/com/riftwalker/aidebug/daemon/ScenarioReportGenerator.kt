package com.riftwalker.aidebug.daemon

import com.riftwalker.aidebug.protocol.NetworkHistoryRequest
import com.riftwalker.aidebug.protocol.ProtocolJson
import com.riftwalker.aidebug.protocol.ReportGenerateRequest
import com.riftwalker.aidebug.protocol.ReportGenerateResponse
import com.riftwalker.aidebug.protocol.ScenarioReport
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.encodeToJsonElement
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

class ScenarioReportGenerator(
    private val registry: DaemonToolRegistry,
    private val store: ScenarioRunStore,
    private val reportRoot: Path,
) {
    suspend fun generate(request: ReportGenerateRequest): ReportGenerateResponse {
        Files.createDirectories(reportRoot)

        val reportId = "report_${UUID.randomUUID()}"
        val run = request.runId?.let { store.get(it) ?: error("Unknown scenario run id: $it") }
            ?: store.latest()
        val errors = mutableListOf<String>()

        val audit = if (request.includeAudit) {
            collect("audit.history", null, errors)
        } else {
            null
        }
        val network = if (request.includeNetworkHistory) {
            collect(
                "network.history",
                ProtocolJson.json.encodeToJsonElement(NetworkHistoryRequest(includeBodies = request.includeBodies)),
                errors,
            )
        } else {
            null
        }

        val report = ScenarioReport(
            reportId = reportId,
            generatedAtEpochMs = System.currentTimeMillis(),
            name = request.name ?: run?.name ?: reportId,
            run = run,
            audit = audit,
            networkHistory = network,
            errors = errors,
        )
        val path = reportRoot.resolve("$reportId.json").toAbsolutePath().normalize()
        Files.writeString(path, ProtocolJson.json.encodeToString(report))
        return ReportGenerateResponse(
            reportId = reportId,
            path = path.toString(),
            report = report,
        )
    }

    private suspend fun collect(
        tool: String,
        arguments: JsonElement?,
        errors: MutableList<String>,
    ): JsonElement? {
        return runCatching {
            registry.invoke(tool, arguments)
        }.getOrElse { error ->
            errors += "$tool: ${error.message ?: error::class.java.simpleName}"
            null
        }
    }
}
