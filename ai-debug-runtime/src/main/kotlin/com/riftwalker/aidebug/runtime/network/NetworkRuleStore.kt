package com.riftwalker.aidebug.runtime.network

import com.riftwalker.aidebug.protocol.NetworkMatcher
import com.riftwalker.aidebug.protocol.NetworkRule

class NetworkRuleStore {
    private val lock = Any()
    private val rules = linkedMapOf<String, NetworkRule>()

    fun add(rule: NetworkRule) {
        synchronized(lock) {
            rules[rule.id] = rule
        }
    }

    fun remove(ruleId: String): Boolean {
        return synchronized(lock) {
            rules.remove(ruleId) != null
        }
    }

    fun clearAll(): Int {
        return synchronized(lock) {
            val count = rules.size
            rules.clear()
            count
        }
    }

    fun consumeMatches(request: NetworkRequestSnapshot): List<NetworkRule> {
        return synchronized(lock) {
            val matched = rules.values.filter { it.match.matches(request) }
            matched.forEach { rule ->
                val remaining = rule.remaining
                if (remaining != null) {
                    if (remaining <= 1) {
                        rules.remove(rule.id)
                    } else {
                        rules[rule.id] = rule.copy(remaining = remaining - 1)
                    }
                }
            }
            matched
        }
    }

    private fun NetworkMatcher.matches(request: NetworkRequestSnapshot): Boolean {
        val urlMatches = urlRegex?.toRegex()?.containsMatchIn(request.url) ?: true
        val methodMatches = method?.equals(request.method, ignoreCase = true) ?: true
        val contentTypeMatches = contentTypeContains?.let { expected ->
            request.headers.entries.any {
                it.key.equals("content-type", ignoreCase = true) &&
                    it.value.contains(expected, ignoreCase = true)
            }
        } ?: true
        val headersMatch = headers.all { (key, expected) ->
            request.headers.entries.any { it.key.equals(key, ignoreCase = true) && it.value == expected }
        }
        val bodyMatches = bodyContains?.let { request.body?.contains(it) == true } ?: true
        val graphqlMatches = matchesGraphql(request)
        val grpcMatches = matchesGrpc(request)
        return urlMatches && methodMatches && contentTypeMatches && headersMatch && bodyMatches && graphqlMatches && grpcMatches
    }

    private fun NetworkMatcher.matchesGraphql(request: NetworkRequestSnapshot): Boolean {
        val requiresGraphql = graphqlOperationName != null || graphqlQueryRegex != null || graphqlVariables.isNotEmpty()
        if (!requiresGraphql) return true
        if (request.protocol != "graphql") return false

        val operationMatches = graphqlOperationName?.let { expected ->
            request.graphqlOperationName == expected
        } ?: true
        val queryMatches = graphqlQueryRegex?.let { regex ->
            request.graphqlQuery?.let { regex.toRegex().containsMatchIn(it) } == true
        } ?: true
        val variablesMatch = graphqlVariables.all { (key, expected) ->
            request.graphqlVariables[key] == expected
        }
        return operationMatches && queryMatches && variablesMatch
    }

    private fun NetworkMatcher.matchesGrpc(request: NetworkRequestSnapshot): Boolean {
        val requiresGrpc = grpcService != null || grpcMethod != null
        if (!requiresGrpc) return true
        if (request.protocol != "grpc") return false

        val serviceMatches = grpcService?.let { request.grpcService == it } ?: true
        val methodMatches = grpcMethod?.let { request.grpcMethod == it } ?: true
        return serviceMatches && methodMatches
    }
}
