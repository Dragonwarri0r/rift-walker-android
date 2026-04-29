package com.riftwalker.aidebug.daemon

import com.riftwalker.aidebug.protocol.NetworkHistoryRequest
import com.riftwalker.aidebug.protocol.ProtocolJson
import com.riftwalker.aidebug.protocol.ReportGenerateRequest
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import java.nio.file.Path
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    runCatching {
        runDaemonCommand(args)
    }.onFailure { error ->
        System.err.println("ai-debug-daemon: ${error.message ?: error::class.java.simpleName}")
        exitProcess(1)
    }
}

private fun runDaemonCommand(args: Array<String>) {
    val hostPort = args.firstOrNull { it.startsWith("--host-port=") }
        ?.substringAfter("=")
        ?.toIntOrNull()
        ?: 37913

    val client = RuntimeHttpClient("http://127.0.0.1:$hostPort")
    val command = args.firstOrNull() ?: "ping"
    val commandArgs = args.drop(1)

    when (command) {
        "mcp" -> {
            val registry = DaemonToolRegistry()
            AppMcpTools().registerInto(registry)
            RuntimeMcpTools(client).registerInto(registry)
            MediaMcpTools(client).registerInto(registry)
            ScenarioMcpTools(registry).registerInto()
            McpStdioServer(registry).run()
        }
        "ping" -> println(ProtocolJson.json.encodeToString(client.ping()))
        "capabilities" -> println(ProtocolJson.json.encodeToString(client.listCapabilities()))
        "audit" -> println(ProtocolJson.json.encodeToString(client.auditHistory()))
        "network-history" -> println(ProtocolJson.json.encodeToString(client.networkHistory(NetworkHistoryRequest(includeBodies = true))))
        "dogfood-sample" -> runDogfoodSample(commandArgs, client, hostPort)
        else -> error("Unknown command '$command'. Use mcp, ping, capabilities, audit, network-history, or dogfood-sample.")
    }
}

private fun runDogfoodSample(args: List<String>, client: RuntimeHttpClient, hostPort: Int) = runBlocking {
    val serial = option(args, "serial") ?: System.getenv("DEVICE_SERIAL")?.takeIf { it.isNotBlank() }
    val devicePort = option(args, "device-port")?.toIntOrNull() ?: hostPort
    val packageName = option(args, "package") ?: "com.riftwalker.sample"
    val activity = option(args, "activity") ?: "$packageName/.MainActivity"
    val reportRoot = option(args, "report-root") ?: "build/ai-debug/dogfood-reports"

    val registry = DaemonToolRegistry()
    AppMcpTools().registerInto(registry)
    RuntimeMcpTools(client).registerInto(registry)
    MediaMcpTools(client).registerInto(registry)

    val store = ScenarioRunStore()
    val runner = ScenarioRunner(registry, store)
    val reports = ScenarioReportGenerator(registry, store, Path.of(reportRoot))
    val scenario = DogfoodScenarios.sampleProfileBranch(
        serial = serial,
        hostPort = hostPort,
        devicePort = devicePort,
        packageName = packageName,
        activity = activity,
    )
    val run = runner.run(scenario)
    val report = reports.generate(
        ReportGenerateRequest(
            runId = run.runId,
            name = scenario.name,
            includeAudit = true,
            includeNetworkHistory = true,
            includeBodies = false,
        ),
    )
    println(ProtocolJson.json.encodeToString(report))
    check(run.status == "passed") {
        "Dogfood scenario failed: ${run.runId}; report=${report.path}"
    }
}

private fun option(args: List<String>, name: String): String? {
    val prefix = "--$name="
    return args.firstOrNull { it.startsWith(prefix) }?.substringAfter("=")
}
