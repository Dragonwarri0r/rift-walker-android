package com.riftwalker.aidebug.runtime

import com.riftwalker.aidebug.protocol.CapabilityDescriptor
import java.util.concurrent.ConcurrentHashMap

class CapabilityRegistry {
    private val capabilities = ConcurrentHashMap<String, CapabilityDescriptor>()

    fun register(descriptor: CapabilityDescriptor) {
        capabilities[descriptor.path] = descriptor
    }

    fun list(kind: String = "all", query: String? = null): List<CapabilityDescriptor> {
        return capabilities.values
            .asSequence()
            .filter { kind == "all" || it.kind == kind }
            .filter {
                query.isNullOrBlank() ||
                    it.path.contains(query, ignoreCase = true) ||
                    it.description.contains(query, ignoreCase = true) ||
                    it.tags.any { tag -> tag.contains(query, ignoreCase = true) }
            }
            .sortedWith(compareBy<CapabilityDescriptor> { it.kind }.thenBy { it.path })
            .toList()
    }
}
