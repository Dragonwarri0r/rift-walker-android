package com.riftwalker.aidebug.runtime.network

import com.riftwalker.aidebug.protocol.NetworkAssertCalledRequest
import com.riftwalker.aidebug.protocol.NetworkAssertCalledResponse
import com.riftwalker.aidebug.protocol.NetworkClearRulesRequest
import com.riftwalker.aidebug.protocol.NetworkClearRulesResponse
import com.riftwalker.aidebug.protocol.NetworkFailRequest
import com.riftwalker.aidebug.protocol.NetworkHistoryRequest
import com.riftwalker.aidebug.protocol.NetworkHistoryResponse
import com.riftwalker.aidebug.protocol.NetworkMockRequest
import com.riftwalker.aidebug.protocol.NetworkMutateResponseRequest
import com.riftwalker.aidebug.protocol.NetworkRecord
import com.riftwalker.aidebug.protocol.NetworkRecordToMockRequest
import com.riftwalker.aidebug.protocol.NetworkRecordToMockResponse
import com.riftwalker.aidebug.protocol.NetworkRule
import com.riftwalker.aidebug.protocol.NetworkRuleResponse
import com.riftwalker.aidebug.runtime.AuditLog
import com.riftwalker.aidebug.runtime.CleanupRegistry
import java.util.UUID

class NetworkController(
    private val auditLog: AuditLog,
    private val cleanupRegistry: CleanupRegistry,
) {
    private val rules = NetworkRuleStore()
    private val history = NetworkHistoryStore()

    fun installMutation(request: NetworkMutateResponseRequest): NetworkRuleResponse {
        val rule = NetworkRule(
            id = nextRuleId(),
            action = "mutate",
            match = request.match.copy(scenarioScope = request.scenarioScope ?: request.match.scenarioScope),
            patch = request.patch,
            times = request.times,
            remaining = request.times,
            scenarioScope = request.scenarioScope,
            createdAtEpochMs = System.currentTimeMillis(),
        )
        return install(rule, "network.mutateResponse")
    }

    fun installMock(request: NetworkMockRequest): NetworkRuleResponse {
        val rule = NetworkRule(
            id = nextRuleId(),
            action = "mock",
            match = request.match.copy(scenarioScope = request.scenarioScope ?: request.match.scenarioScope),
            response = request.response,
            times = request.times,
            remaining = request.times,
            scenarioScope = request.scenarioScope,
            createdAtEpochMs = System.currentTimeMillis(),
        )
        return install(rule, "network.mock")
    }

    fun installFailure(request: NetworkFailRequest): NetworkRuleResponse {
        val rule = NetworkRule(
            id = nextRuleId(),
            action = "fail",
            match = request.match.copy(scenarioScope = request.scenarioScope ?: request.match.scenarioScope),
            failure = request.failure,
            times = request.times,
            remaining = request.times,
            scenarioScope = request.scenarioScope,
            createdAtEpochMs = System.currentTimeMillis(),
        )
        return install(rule, "network.fail")
    }

    fun clearRules(request: NetworkClearRulesRequest): NetworkClearRulesResponse {
        val ruleIds = request.ruleIds
        val cleared = if (ruleIds == null) {
            rules.clearAll()
        } else {
            ruleIds.sumOf { ruleId -> if (rules.remove(ruleId)) 1 else 0 }
        }
        auditLog.recordMutation(
            tool = "network.clearRules",
            target = request.ruleIds?.joinToString(",") ?: "all",
            restoreToken = null,
            status = "success",
            resultSummary = "cleared=$cleared",
        )
        return NetworkClearRulesResponse(cleared)
    }

    fun history(request: NetworkHistoryRequest): NetworkHistoryResponse {
        auditLog.recordRead(
            tool = "network.history",
            target = request.urlRegex,
            status = "success",
            argumentsSummary = "limit=${request.limit}, includeBodies=${request.includeBodies}",
        )
        return NetworkHistoryResponse(history.list(request))
    }

    fun assertCalled(request: NetworkAssertCalledRequest): NetworkAssertCalledResponse {
        val matches = waitForMatches(request)
        val response = NetworkAssertCalledResponse(
            passed = matches.size >= request.minCount,
            count = matches.size,
            recordIds = matches.map { it.id },
        )
        auditLog.recordRead(
            tool = "network.assertCalled",
            target = request.match.urlRegex,
            status = if (response.passed) "success" else "failure",
            resultSummary = "count=${response.count}, min=${request.minCount}, timeoutMs=${request.timeoutMs}",
        )
        return response
    }

    fun recordToMock(request: NetworkRecordToMockRequest): NetworkRecordToMockResponse {
        val record = selectRecord(request)
        val status = record.status
            ?: error("Cannot convert failed network record to mock: ${record.id}")
        val body = record.finalResponseBody ?: record.responseBody
        val match = request.targetMatch ?: record.toMatcher()
        val rule = NetworkRule(
            id = nextRuleId(),
            action = "mock",
            match = match.copy(scenarioScope = request.scenarioScope ?: match.scenarioScope),
            response = com.riftwalker.aidebug.protocol.NetworkMockResponse(
                status = status,
                headers = request.responseHeaders,
                bodyText = body.orEmpty(),
            ),
            times = request.times,
            remaining = request.times,
            scenarioScope = request.scenarioScope,
            createdAtEpochMs = System.currentTimeMillis(),
        )
        val installed = install(rule, "network.recordToMock")
        return NetworkRecordToMockResponse(
            ruleId = installed.ruleId,
            restoreToken = installed.restoreToken,
            sourceRecordId = record.id,
            match = match,
            status = status,
            bodyCaptured = body != null,
            bodyRedacted = record.bodyRedacted,
        )
    }

    internal fun consumeMatchingRules(request: NetworkRequestSnapshot): List<NetworkRule> {
        return rules.consumeMatches(request)
    }

    internal fun record(record: NetworkRecord) {
        history.add(record)
    }

    private fun install(rule: NetworkRule, tool: String): NetworkRuleResponse {
        rules.add(rule)
        val cleanupToken = cleanupRegistry.register("remove network rule ${rule.id}") {
            rules.remove(rule.id)
        }
        auditLog.recordMutation(
            tool = tool,
            target = rule.id,
            restoreToken = cleanupToken,
            status = "success",
            argumentsSummary = rule.match.urlRegex,
        )
        return NetworkRuleResponse(ruleId = rule.id, restoreToken = cleanupToken)
    }

    private fun selectRecord(request: NetworkRecordToMockRequest): NetworkRecord {
        request.recordId?.let { recordId ->
            return history.find(recordId)
                ?: error("Network record not found: $recordId")
        }
        val sourceMatch = request.sourceMatch
            ?: error("network.recordToMock requires recordId or sourceMatch")
        return history.latestMatching(sourceMatch)
            ?: error("No network record matched sourceMatch")
    }

    private fun NetworkRecord.toMatcher(): com.riftwalker.aidebug.protocol.NetworkMatcher {
        return com.riftwalker.aidebug.protocol.NetworkMatcher(
            method = method,
            urlRegex = Regex.escape(url),
            graphqlOperationName = graphqlOperationName,
            grpcService = grpcService,
            grpcMethod = grpcMethod,
        )
    }

    private fun nextRuleId(): String = "rule_${UUID.randomUUID()}"

    private fun waitForMatches(request: NetworkAssertCalledRequest): List<NetworkRecord> {
        val timeoutMs = request.timeoutMs.coerceAtLeast(0)
        val intervalMs = request.pollIntervalMs.coerceAtLeast(10)
        val deadline = System.currentTimeMillis() + timeoutMs
        var matches = history.recordsMatching(request.match)
        while (matches.size < request.minCount && System.currentTimeMillis() < deadline) {
            Thread.sleep(intervalMs.coerceAtMost((deadline - System.currentTimeMillis()).coerceAtLeast(1)))
            matches = history.recordsMatching(request.match)
        }
        return matches
    }
}
