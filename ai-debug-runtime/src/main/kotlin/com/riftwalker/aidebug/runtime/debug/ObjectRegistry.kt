package com.riftwalker.aidebug.runtime.debug

import com.riftwalker.aidebug.protocol.ObjectHandle
import com.riftwalker.aidebug.protocol.ObjectSearchRequest
import com.riftwalker.aidebug.protocol.ObjectSearchResponse
import com.riftwalker.aidebug.protocol.ObjectSearchResult
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class ObjectRegistry {
    private val objects = ConcurrentHashMap<String, TrackedObject>()

    fun track(label: String?, value: Any): ObjectHandle {
        val existing = objects.values.firstOrNull { it.value === value }
        if (existing != null) return existing.handle
        val handle = ObjectHandle(
            id = "obj_${UUID.randomUUID()}",
            label = label,
            className = value.javaClass.name,
            identityHash = System.identityHashCode(value).toString(16),
            registeredAtEpochMs = System.currentTimeMillis(),
        )
        objects[handle.id] = TrackedObject(handle, value)
        return handle
    }

    fun requireObject(handle: String): Any {
        return objects[handle]?.value ?: error("Unknown object handle: $handle")
    }

    fun search(request: ObjectSearchRequest): ObjectSearchResponse {
        val query = request.query.lowercase()
        val packages = request.packages
        val results = mutableListOf<ObjectSearchResult>()
        for (tracked in objects.values.sortedBy { it.handle.label ?: it.handle.id }) {
            if (results.size >= request.limit.coerceIn(1, MAX_RESULTS)) break
            val className = tracked.value.javaClass.name
            if (packages.isNotEmpty() && packages.none { className.startsWith(it) }) continue
            val label = tracked.handle.label
            val classOrLabelMatches = className.lowercase().contains(query) ||
                label?.lowercase()?.contains(query) == true
            if (classOrLabelMatches) {
                results += ObjectSearchResult(
                    handle = tracked.handle.id,
                    label = label,
                    className = className,
                    readable = true,
                    writable = false,
                    valuePreview = tracked.value.toString().take(160),
                )
            }
            if (request.includeFields) {
                searchFields(
                    tracked = tracked,
                    query = query,
                    results = results,
                    limit = request.limit.coerceIn(1, MAX_RESULTS),
                )
            }
        }
        return ObjectSearchResponse(results.take(request.limit.coerceIn(1, MAX_RESULTS)))
    }

    fun readField(target: String, fieldPath: String): FieldValue {
        val holder = resolveFieldHolder(requireObject(target), fieldPath)
        val field = holder.field
        val value = runCatching {
            field.isAccessible = true
            field.get(holder.owner)
        }.getOrElse { error ->
            return FieldValue(JsonSafeValueCodec.encode(null), readable = false, writable = false, reason = error.message)
        }
        return FieldValue(
            value = JsonSafeValueCodec.encode(value, field.name),
            readable = true,
            writable = isWritable(field),
        )
    }

    fun writeField(target: String, fieldPath: String, rawValue: kotlinx.serialization.json.JsonElement): FieldWrite {
        val holder = resolveFieldHolder(requireObject(target), fieldPath)
        val field = holder.field
        require(isWritable(field)) { "Field is not writable: $fieldPath" }
        field.isAccessible = true
        val previous = field.get(holder.owner)
        val decoded = JsonSafeValueCodec.decode(rawValue, field.type)
        field.set(holder.owner, decoded)
        return FieldWrite(
            previous = previous,
            current = JsonSafeValueCodec.encode(field.get(holder.owner), field.name),
            restore = {
                field.isAccessible = true
                field.set(holder.owner, previous)
            },
        )
    }

    fun clear() {
        objects.clear()
    }

    private fun searchFields(
        tracked: TrackedObject,
        query: String,
        results: MutableList<ObjectSearchResult>,
        limit: Int,
    ) {
        val queue = ArrayDeque<FieldNode>()
        queue.add(FieldNode(tracked.value, "", depth = 0))
        val seen = hashSetOf<Int>()
        while (queue.isNotEmpty() && results.size < limit) {
            val node = queue.removeFirst()
            val owner = node.owner ?: continue
            val identity = System.identityHashCode(owner)
            if (!seen.add(identity)) continue
            owner.javaClass.allFields().forEach { field ->
                if (results.size >= limit) return
                if (Modifier.isStatic(field.modifiers)) return@forEach
                val path = if (node.path.isBlank()) field.name else "${node.path}.${field.name}"
                val fieldValue = runCatching {
                    field.isAccessible = true
                    field.get(owner)
                }.getOrNull()
                val safeValue = JsonSafeValueCodec.encode(fieldValue, field.name)
                val matches = path.lowercase().contains(query) ||
                    field.type.name.lowercase().contains(query) ||
                    safeValue.valuePreview?.lowercase()?.contains(query) == true
                if (matches) {
                    results += ObjectSearchResult(
                        handle = tracked.handle.id,
                        label = tracked.handle.label,
                        className = tracked.handle.className,
                        fieldPath = path,
                        valuePreview = safeValue.valuePreview,
                        readable = true,
                        writable = isWritable(field),
                        redacted = safeValue.redacted,
                        reason = safeValue.unsupportedReason,
                    )
                }
                if (node.depth < MAX_FIELD_DEPTH && fieldValue != null && fieldValue.isInspectable()) {
                    queue.add(FieldNode(fieldValue, path, node.depth + 1))
                }
            }
        }
    }

    private fun resolveFieldHolder(root: Any, fieldPath: String): FieldHolder {
        val parts = fieldPath.split('.').filter { it.isNotBlank() }
        require(parts.isNotEmpty()) { "fieldPath must not be empty" }
        var owner: Any? = root
        parts.dropLast(1).forEach { name ->
            val current = owner ?: error("Null owner while resolving $fieldPath")
            val field = current.javaClass.findField(name) ?: error("Unknown field '$name' on ${current.javaClass.name}")
            field.isAccessible = true
            owner = field.get(current)
        }
        val finalOwner = owner ?: error("Null owner while resolving $fieldPath")
        val field = finalOwner.javaClass.findField(parts.last())
            ?: error("Unknown field '${parts.last()}' on ${finalOwner.javaClass.name}")
        return FieldHolder(finalOwner, field)
    }

    private fun Class<*>.findField(name: String): Field? {
        var current: Class<*>? = this
        while (current != null && current != Any::class.java) {
            current.declaredFields.firstOrNull { it.name == name }?.let { return it }
            current = current.superclass
        }
        return null
    }

    private fun Class<*>.allFields(): List<Field> {
        val fields = mutableListOf<Field>()
        var current: Class<*>? = this
        while (current != null && current != Any::class.java) {
            fields += current.declaredFields
            current = current.superclass
        }
        return fields
    }

    private fun Any.isInspectable(): Boolean {
        val clazz = javaClass
        return !clazz.isPrimitive &&
            clazz.name.startsWith("com.") &&
            this !is String &&
            this !is Number &&
            this !is Boolean &&
            this !is Enum<*>
    }

    private fun isWritable(field: Field): Boolean {
        return !Modifier.isFinal(field.modifiers) && !Modifier.isStatic(field.modifiers)
    }

    data class FieldValue(
        val value: com.riftwalker.aidebug.protocol.JsonSafeValue,
        val readable: Boolean,
        val writable: Boolean,
        val reason: String? = null,
    )

    data class FieldWrite(
        val previous: Any?,
        val current: com.riftwalker.aidebug.protocol.JsonSafeValue,
        val restore: () -> Unit,
    )

    private data class TrackedObject(
        val handle: ObjectHandle,
        val value: Any,
    )

    private data class FieldNode(
        val owner: Any?,
        val path: String,
        val depth: Int,
    )

    private data class FieldHolder(
        val owner: Any,
        val field: Field,
    )

    private companion object {
        const val MAX_RESULTS = 100
        const val MAX_FIELD_DEPTH = 2
    }
}
