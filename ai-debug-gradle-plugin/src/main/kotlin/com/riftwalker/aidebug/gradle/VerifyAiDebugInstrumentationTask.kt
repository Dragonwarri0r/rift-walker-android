package com.riftwalker.aidebug.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.util.jar.JarFile

abstract class VerifyAiDebugInstrumentationTask : DefaultTask() {
    @get:Input
    abstract val buildType: Property<String>

    @get:Input
    abstract val traceMethods: ListProperty<String>

    @get:Input
    abstract val overrideMethods: ListProperty<String>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val scanRoots: ConfigurableFileCollection

    @get:OutputFile
    abstract val reportOutput: RegularFileProperty

    @TaskAction
    fun verify() {
        val classFiles = ClassFileIndex.from(scanRoots.files)
        val traceResults = traceMethods.get().map { methodId ->
            val bytes = classFiles.requireClassBytes(methodId)
            VerificationResult(
                methodId = methodId,
                className = methodId.targetClassName().orEmpty(),
                verified = bytes.hasUtf8(methodId) &&
                    bytes.hasUtf8("AiDebugHookBridge") &&
                    bytes.hasUtf8("traceEnter") &&
                    bytes.hasUtf8("traceExit"),
                expected = "traceEnter/traceExit bridge calls",
            )
        }
        val overrideResults = overrideMethods.get().map { methodId ->
            val bytes = classFiles.requireClassBytes(methodId)
            VerificationResult(
                methodId = methodId,
                className = methodId.targetClassName().orEmpty(),
                verified = bytes.hasUtf8(methodId) &&
                    bytes.hasUtf8("AiDebugHookBridge") &&
                    (bytes.hasUtf8("hookBoolean") || bytes.hasUtf8("hookString")),
                expected = "hookBoolean or hookString bridge call",
            )
        }

        val failures = (traceResults + overrideResults).filterNot { it.verified }
        writeReport(traceResults, overrideResults)
        if (failures.isNotEmpty()) {
            error(
                failures.joinToString(prefix = "AI debug instrumentation verification failed:\n", separator = "\n") {
                    "- ${it.methodId}: expected ${it.expected}"
                },
            )
        }
    }

    private fun ClassFileIndex.requireClassBytes(methodId: String): ByteArray {
        val className = methodId.targetClassName()
            ?: error("Invalid method id, expected Class#method or Class.method: $methodId")
        return get(className)
            ?: error("Instrumented class not found for $methodId ($className)")
    }

    private fun writeReport(
        traceResults: List<VerificationResult>,
        overrideResults: List<VerificationResult>,
    ) {
        val output = reportOutput.get().asFile
        output.parentFile.mkdirs()
        output.writeText(
            """
            {
              "buildType": ${buildType.get().jsonString()},
              "verifiedAtEpochMs": ${System.currentTimeMillis()},
              "traceMethods": [
            ${traceResults.joinToString(",\n") { it.toJson() }}
              ],
              "overrideMethods": [
            ${overrideResults.joinToString(",\n") { it.toJson() }}
              ]
            }
            """.trimIndent(),
        )
    }

    private data class VerificationResult(
        val methodId: String,
        val className: String,
        val verified: Boolean,
        val expected: String,
    ) {
        fun toJson(): String {
            return """
                {
                  "methodId": ${methodId.jsonString()},
                  "className": ${className.jsonString()},
                  "verified": $verified,
                  "expected": ${expected.jsonString()}
                }
            """.trimIndent()
        }
    }

    private class ClassFileIndex(private val files: Map<String, ByteArray>) {
        fun get(className: String): ByteArray? = files[className.replace('.', '/')]

        companion object {
            fun from(roots: Set<File>): ClassFileIndex {
                val files = linkedMapOf<String, ByteArray>()
                roots.filter { it.exists() }.forEach { root ->
                    when {
                        root.isDirectory -> root.walkTopDown()
                            .filter { it.isFile && it.extension == "class" }
                            .forEach { file ->
                                files[file.relativeTo(root).invariantSeparatorsPath.removeSuffix(".class")] = file.readBytes()
                            }
                        root.isFile && root.extension == "jar" -> JarFile(root).use { jar ->
                            jar.entries().asSequence()
                                .filter { !it.isDirectory && it.name.endsWith(".class") }
                                .forEach { entry ->
                                    jar.getInputStream(entry).use { input ->
                                        files[entry.name.removeSuffix(".class")] = input.readBytes()
                                    }
                                }
                        }
                    }
                }
                return ClassFileIndex(files)
            }
        }
    }
}

private fun ByteArray.hasUtf8(text: String): Boolean {
    val needle = text.toByteArray(Charsets.UTF_8)
    if (needle.isEmpty() || needle.size > size) return false
    for (index in 0..(size - needle.size)) {
        var matches = true
        for (needleIndex in needle.indices) {
            if (this[index + needleIndex] != needle[needleIndex]) {
                matches = false
                break
            }
        }
        if (matches) return true
    }
    return false
}

private fun String.targetClassName(): String? {
    return when {
        contains("#") -> substringBefore("#")
        contains(".") -> substringBeforeLast(".")
        else -> null
    }
}

private fun String.jsonString(): String {
    return buildString {
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
}
