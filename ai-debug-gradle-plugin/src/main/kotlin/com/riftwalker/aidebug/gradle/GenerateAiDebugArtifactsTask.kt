package com.riftwalker.aidebug.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

abstract class GenerateAiDebugArtifactsTask : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceRoots: ConfigurableFileCollection

    @get:Input
    abstract val probePackages: ListProperty<String>

    @get:Input
    abstract val exposedFields: ListProperty<String>

    @get:Input
    abstract val traceMethods: ListProperty<String>

    @get:Input
    abstract val overrideMethods: ListProperty<String>

    @get:Input
    abstract val exportSchema: Property<Boolean>

    @get:OutputFile
    abstract val schemaOutput: RegularFileProperty

    @get:OutputFile
    abstract val symbolIndexOutput: RegularFileProperty

    @get:OutputDirectory
    abstract val generatedSourceDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val sources = sourceRoots.files
            .filter { it.exists() }
            .flatMap { root -> root.walkTopDown().filter { it.isFile && (it.extension == "kt" || it.extension == "java") }.toList() }
        val annotations = sources.flatMap(::scanSource)

        writeGeneratedRegistry(annotations)
        writeSchema(annotations)
        writeSymbolIndex(annotations)
    }

    private fun scanSource(file: java.io.File): List<AnnotationEntry> {
        val text = file.readText()
        val packageName = PACKAGE_REGEX.find(text)?.groupValues?.get(1).orEmpty()
        val stateEntries = AI_STATE_REGEX.findAll(text).mapNotNull { match ->
            val args = parseArgs(match.groupValues[1])
            val declaration = match.groupValues[2]
            val name = STATE_DECLARATION_REGEX.find(declaration)?.groupValues?.get(2) ?: return@mapNotNull null
            val owner = findOwnerBefore(text, match.range.first, packageName)
            AnnotationEntry(
                kind = "state",
                path = args["path"] ?: args["value"] ?: name,
                type = args["type"] ?: "string",
                description = args["description"] ?: "Generated @AiState $name",
                owner = owner?.fqName,
                ownerKind = owner?.kind,
                member = name,
                mutable = declaration.trimStart().startsWith("var"),
            )
        }
        val actionEntries = AI_ACTION_REGEX.findAll(text).mapNotNull { match ->
            val args = parseArgs(match.groupValues[1])
            val name = FUNCTION_DECLARATION_REGEX.find(match.groupValues[2])?.groupValues?.get(1) ?: return@mapNotNull null
            val owner = findOwnerBefore(text, match.range.first, packageName)
            AnnotationEntry(
                kind = "action",
                path = args["path"] ?: args["value"] ?: name,
                type = "object",
                description = args["description"] ?: "Generated @AiAction $name",
                owner = owner?.fqName,
                ownerKind = owner?.kind,
                member = name,
                mutable = true,
            )
        }
        val probeEntries = AI_PROBE_REGEX.findAll(text).mapNotNull { match ->
            val args = parseArgs(match.groupValues[1])
            val declaration = match.groupValues[2]
            val name = STATE_DECLARATION_REGEX.find(declaration)?.groupValues?.get(2)
                ?: FUNCTION_DECLARATION_REGEX.find(declaration)?.groupValues?.get(1)
                ?: return@mapNotNull null
            val owner = findOwnerBefore(text, match.range.first, packageName)
            AnnotationEntry(
                kind = "probe",
                path = args["id"] ?: args["value"] ?: name,
                type = "probe",
                description = args["description"] ?: "Generated @AiProbe $name",
                owner = owner?.fqName,
                ownerKind = owner?.kind,
                member = name,
                mutable = true,
            )
        }
        return (stateEntries + actionEntries + probeEntries).toList()
    }

    private fun writeGeneratedRegistry(entries: List<AnnotationEntry>) {
        val outputDir = generatedSourceDir.get().asFile.resolve("com/riftwalker/aidebug/generated")
        outputDir.mkdirs()
        val output = outputDir.resolve("AiDebugGeneratedRegistry.kt")
        val stateRegistrations = entries.filter { it.kind == "state" && it.owner != null && it.ownerKind == "object" }
            .joinToString("\n") { entry ->
                val function = when (entry.type) {
                    "boolean" -> "booleanState"
                    "int", "integer" -> "intState"
                    "long" -> "longState"
                    "double", "number" -> "doubleState"
                    else -> "stringState"
                }
                val writer = if (entry.mutable) {
                    "write = { value -> ${entry.owner}.${entry.member} = value },"
                } else {
                    ""
                }
                """
        AiDebug.$function(
            path = ${entry.path.kotlinString()},
            description = ${entry.description.kotlinString()},
            tags = listOf("generated", "annotation"),
            read = { ${entry.owner}.${entry.member} },
            $writer
        )
                """.trimEnd()
            }
        val actionRegistrations = entries.filter { it.kind == "action" && it.owner != null && it.ownerKind == "object" }
            .joinToString("\n") { entry ->
                """
        AiDebug.action(
            path = ${entry.path.kotlinString()},
            description = ${entry.description.kotlinString()},
            tags = listOf("generated", "annotation"),
        ) {
            ${entry.owner}.${entry.member}()
            null
        }
                """.trimEnd()
            }
        output.writeText(
            """
            package com.riftwalker.aidebug.generated

            import com.riftwalker.aidebug.runtime.AiDebug

            object AiDebugGeneratedRegistry {
                @JvmStatic
                fun register() {
$stateRegistrations
$actionRegistrations
                }
            }
            """.trimIndent(),
        )
    }

    private fun writeSchema(entries: List<AnnotationEntry>) {
        val output = schemaOutput.get().asFile
        output.parentFile.mkdirs()
        val body = entries.joinToString(",\n") { entry ->
            """
              {
                "kind": "${entry.kind}",
                "path": ${entry.path.jsonString()},
                "type": ${entry.type.jsonString()},
                "description": ${entry.description.jsonString()},
                "owner": ${entry.owner?.jsonString() ?: "null"},
                "ownerKind": ${entry.ownerKind?.jsonString() ?: "null"},
                "member": ${entry.member.jsonString()},
                "mutable": ${entry.mutable}
              }
            """.trimIndent()
        }
        output.writeText(
            """
            {
              "generatedAtEpochMs": ${System.currentTimeMillis()},
              "exported": ${exportSchema.get()},
              "capabilities": [
            $body
              ]
            }
            """.trimIndent(),
        )
    }

    private fun writeSymbolIndex(entries: List<AnnotationEntry>) {
        val output = symbolIndexOutput.get().asFile
        output.parentFile.mkdirs()
        val annotated = entries.joinToString(",\n") { entry ->
            """
              {
                "kind": ${entry.kind.jsonString()},
                "path": ${entry.path.jsonString()},
                "owner": ${entry.owner?.jsonString() ?: "null"},
                "ownerKind": ${entry.ownerKind?.jsonString() ?: "null"},
                "member": ${entry.member.jsonString()}
              }
            """.trimIndent()
        }
        output.writeText(
            """
            {
              "probePackages": ${probePackages.get().toJsonArray()},
              "exposedFields": ${exposedFields.get().toJsonArray()},
              "traceMethods": ${traceMethods.get().toJsonArray()},
              "overrideMethods": ${overrideMethods.get().toJsonArray()},
              "annotated": [
            $annotated
              ]
            }
            """.trimIndent(),
        )
    }

    private fun parseArgs(raw: String): Map<String, String> {
        if (raw.isBlank()) return emptyMap()
        return raw.split(',')
            .map { it.trim() }
            .mapNotNull { arg ->
                val parts = arg.split('=', limit = 2)
                if (parts.size == 1) {
                    "value" to parts[0].trim().trim('"')
                } else {
                    parts[0].trim() to parts[1].trim().trim('"')
                }
            }
            .toMap()
    }

    private fun findOwnerBefore(text: String, index: Int, packageName: String): Owner? {
        val match = OWNER_REGEX.findAll(text)
            .filter { it.range.first < index }
            .lastOrNull()
            ?: return null
        return Owner(
            kind = match.groupValues[1],
            fqName = listOf(packageName, match.groupValues[2])
                .filter { it.isNotBlank() }
                .joinToString("."),
        )
    }

    private data class AnnotationEntry(
        val kind: String,
        val path: String,
        val type: String,
        val description: String,
        val owner: String?,
        val ownerKind: String?,
        val member: String,
        val mutable: Boolean,
    )

    private data class Owner(
        val kind: String,
        val fqName: String,
    )

    private companion object {
        val PACKAGE_REGEX = Regex("""(?m)^\s*package\s+([A-Za-z0-9_.]+)""")
        val OWNER_REGEX = Regex("""\b(object|class)\s+([A-Za-z0-9_]+)""")
        val AI_STATE_REGEX = Regex("""@AiState(?:\(([^)]*)\))?\s*((?:@[\w.]+(?:\([^)]*\))?\s*)*(?:var|val)\s+\w+[^\n]*)""")
        val AI_ACTION_REGEX = Regex("""@AiAction(?:\(([^)]*)\))?\s*((?:@[\w.]+(?:\([^)]*\))?\s*)*fun\s+\w+[^\n]*)""")
        val AI_PROBE_REGEX = Regex("""@AiProbe(?:\(([^)]*)\))?\s*((?:@[\w.]+(?:\([^)]*\))?\s*)*(?:(?:var|val)\s+\w+[^\n]*|fun\s+\w+[^\n]*))""")
        val STATE_DECLARATION_REGEX = Regex("""\b(var|val)\s+([A-Za-z0-9_]+)""")
        val FUNCTION_DECLARATION_REGEX = Regex("""\bfun\s+([A-Za-z0-9_]+)""")
    }
}

private fun String.kotlinString(): String = jsonString()

private fun String.jsonString(): String = buildString {
    append('"')
    this@jsonString.forEach { char ->
        when (char) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> append(char)
        }
    }
    append('"')
}

internal fun List<String>.toJsonArray(): String = joinToString(prefix = "[", postfix = "]") { it.jsonString() }
