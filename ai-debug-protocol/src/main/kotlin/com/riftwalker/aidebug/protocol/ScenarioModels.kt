package com.riftwalker.aidebug.protocol

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class ScenarioRunRequest(
    val name: String,
    val steps: List<ScenarioStep>,
    val continueOnError: Boolean = false,
)

@Serializable
data class ScenarioStep(
    val id: String? = null,
    val tool: String,
    val arguments: JsonElement? = null,
    val continueOnError: Boolean? = null,
)

@Serializable
data class ScenarioRunResponse(
    val runId: String,
    val name: String,
    val status: String,
    val startedAtEpochMs: Long,
    val finishedAtEpochMs: Long,
    val durationMs: Long,
    val steps: List<ScenarioStepResult>,
)

@Serializable
data class ScenarioStepResult(
    val index: Int,
    val id: String? = null,
    val tool: String,
    val status: String,
    val startedAtEpochMs: Long,
    val durationMs: Long,
    val result: JsonElement? = null,
    val error: String? = null,
)

@Serializable
data class ReportGenerateRequest(
    val runId: String? = null,
    val name: String? = null,
    val includeAudit: Boolean = true,
    val includeNetworkHistory: Boolean = true,
    val includeBodies: Boolean = false,
)

@Serializable
data class ReportGenerateResponse(
    val reportId: String,
    val path: String,
    val report: ScenarioReport,
)

@Serializable
data class ScenarioReport(
    val reportId: String,
    val generatedAtEpochMs: Long,
    val name: String,
    val run: ScenarioRunResponse? = null,
    val audit: JsonElement? = null,
    val networkHistory: JsonElement? = null,
    val errors: List<String> = emptyList(),
)
