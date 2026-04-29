package com.riftwalker.aidebug.runtime.network

import com.riftwalker.aidebug.protocol.NetworkHistoryRequest
import com.riftwalker.aidebug.protocol.NetworkMatcher
import com.riftwalker.aidebug.protocol.NetworkRecord
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

class NetworkHistoryStore {
    private val records = CopyOnWriteArrayList<NetworkRecord>()

    fun add(record: NetworkRecord) {
        records += record
        while (records.size > MAX_RECORDS) {
            records.removeAt(0)
        }
    }

    fun list(request: NetworkHistoryRequest): List<NetworkRecord> {
        val regex = request.urlRegex?.toRegex()
        return records
            .asReversed()
            .asSequence()
            .filter { regex == null || regex.containsMatchIn(it.url) }
            .take(request.limit.coerceIn(1, MAX_RECORDS))
            .map { if (request.includeBodies) it else it.copy(requestBody = null, responseBody = null, originalResponseBody = null, finalResponseBody = null) }
            .toList()
    }

    fun recordsMatching(matcher: NetworkMatcher): List<NetworkRecord> {
        val regex = matcher.urlRegex?.toRegex()
        return records.filter { record ->
            (matcher.method == null || matcher.method.equals(record.method, ignoreCase = true)) &&
                (regex == null || regex.containsMatchIn(record.url)) &&
                (matcher.graphqlOperationName == null || matcher.graphqlOperationName == record.graphqlOperationName) &&
                (matcher.grpcService == null || matcher.grpcService == record.grpcService) &&
                (matcher.grpcMethod == null || matcher.grpcMethod == record.grpcMethod)
        }
    }

    fun find(recordId: String): NetworkRecord? {
        return records.firstOrNull { it.id == recordId }
    }

    fun latestMatching(matcher: NetworkMatcher): NetworkRecord? {
        val regex = matcher.urlRegex?.toRegex()
        return records.asReversed().firstOrNull { record ->
            (matcher.method == null || matcher.method.equals(record.method, ignoreCase = true)) &&
                (regex == null || regex.containsMatchIn(record.url)) &&
                (matcher.graphqlOperationName == null || matcher.graphqlOperationName == record.graphqlOperationName) &&
                (matcher.grpcService == null || matcher.grpcService == record.grpcService) &&
                (matcher.grpcMethod == null || matcher.grpcMethod == record.grpcMethod)
        }
    }

    companion object {
        fun nextRecordId(): String = "net_${UUID.randomUUID()}"
        private const val MAX_RECORDS = 500
    }
}
