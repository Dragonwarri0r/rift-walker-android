package com.riftwalker.aidebug.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Locale
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

abstract class ReleaseSafetyCheckTask : DefaultTask() {
    @get:Input
    abstract val dependencyNotations: ListProperty<String>

    @get:Internal
    abstract val scanRoots: ConfigurableFileCollection

    @get:Input
    abstract val failOnRuntimeLeak: Property<Boolean>

    @get:Input
    abstract val forbiddenClasses: ListProperty<String>

    @get:Input
    abstract val forbiddenManifestEntries: ListProperty<String>

    @get:OutputFile
    abstract val reportOutput: RegularFileProperty

    @TaskAction
    fun check() {
        val leaks = mutableListOf<String>()
        val scannedFiles = mutableListOf<String>()

        dependencyNotations.get().forEach { dependency ->
            if (dependency.contains("ai-debug-runtime") && !dependency.contains("noop")) {
                leaks += "forbidden-runtime-dependency:$dependency"
            }
            forbiddenClasses.get().forEach { className ->
                if (dependency.contains(className)) {
                    leaks += "forbidden-class-reference:$dependency:$className"
                }
            }
        }

        val forbiddenClassPaths = forbiddenClasses.get().map { it.replace('.', '/') + ".class" }
        val forbiddenBinaryPatterns = forbiddenClasses.get().flatMap { className ->
            val jvmName = className.replace('.', '/')
            listOf(jvmName, "L$jvmName;", className)
        } + forbiddenManifestEntries.get()

        scanRoots.files
            .filter { it.exists() }
            .flatMap { root ->
                if (root.isDirectory) {
                    root.walkTopDown().filter { it.isFile }.toList()
                } else {
                    listOf(root)
                }
            }
            .filter { it.isReleaseCandidate() }
            .forEach { file ->
                scannedFiles += file.invariantPath()
                leaks += scanFile(file, forbiddenClassPaths, forbiddenBinaryPatterns, forbiddenManifestEntries.get())
            }

        val output = reportOutput.get().asFile
        output.parentFile.mkdirs()
        output.writeText(
            """
            {
              "checkedAtEpochMs": ${System.currentTimeMillis()},
              "dependencies": ${dependencyNotations.get().toJsonArray()},
              "scanRoots": ${scanRoots.files.map { it.invariantPath() }.toJsonArray()},
              "scannedFiles": ${scannedFiles.distinct().toJsonArray()},
              "leaks": ${leaks.toJsonArray()},
              "passed": ${leaks.isEmpty()}
            }
            """.trimIndent(),
        )

        if (leaks.isNotEmpty() && failOnRuntimeLeak.get()) {
            error("AI debug runtime leaked into release classpath: ${leaks.joinToString()}")
        }
    }

    private fun scanFile(
        file: File,
        forbiddenClassPaths: List<String>,
        forbiddenBinaryPatterns: List<String>,
        forbiddenManifestEntries: List<String>,
    ): List<String> {
        val extension = file.extension.lowercase(Locale.US)
        return when (extension) {
            "jar", "aar", "apk", "zip" -> scanZipFile(file, forbiddenClassPaths, forbiddenBinaryPatterns, forbiddenManifestEntries)
            "class", "dex" -> scanBinaryFile(file, forbiddenBinaryPatterns)
            "xml", "json", "txt", "properties", "pro", "cfg" -> scanTextFile(file, forbiddenManifestEntries)
            else -> {
                if (forbiddenClassPaths.any { file.invariantPath().endsWith(it) }) {
                    listOf("forbidden-class-file:${file.invariantPath()}")
                } else {
                    emptyList()
                }
            }
        }
    }

    private fun scanZipFile(
        file: File,
        forbiddenClassPaths: List<String>,
        forbiddenBinaryPatterns: List<String>,
        forbiddenManifestEntries: List<String>,
    ): List<String> {
        return runCatching {
            val leaks = mutableListOf<String>()
            ZipFile(file).use { zip ->
                zip.entries().asSequence().forEach { entry ->
                    if (entry.isDirectory) return@forEach

                    val entryName = entry.name
                    forbiddenClassPaths.forEach { classPath ->
                        if (entryName == classPath || entryName.endsWith("/$classPath")) {
                            leaks += "forbidden-class-entry:${file.invariantPath()}!/$entryName"
                        }
                    }

                    val entryExtension = entryName.substringAfterLast('.', "").lowercase(Locale.US)
                    if (entryExtension in ARCHIVE_EXTENSIONS && entry.size in 1..MAX_NESTED_ARCHIVE_BYTES) {
                        val bytes = zip.getInputStream(entry).use { it.readBytes() }
                        leaks += scanNestedZip(
                            owner = "${file.invariantPath()}!/$entryName",
                            bytes = bytes,
                            forbiddenClassPaths = forbiddenClassPaths,
                            forbiddenBinaryPatterns = forbiddenBinaryPatterns,
                            forbiddenManifestEntries = forbiddenManifestEntries,
                        )
                    } else if (entryExtension in BINARY_EXTENSIONS && entry.size in 1..MAX_ENTRY_SCAN_BYTES) {
                        val bytes = zip.getInputStream(entry).use { it.readBytes() }
                        leaks += binaryLeaks("${file.invariantPath()}!/$entryName", bytes, forbiddenBinaryPatterns)
                    } else if (entryExtension in TEXT_EXTENSIONS && entry.size in 1..MAX_ENTRY_SCAN_BYTES) {
                        val text = zip.getInputStream(entry).bufferedReader(Charsets.UTF_8).use { it.readText() }
                        leaks += textLeaks("${file.invariantPath()}!/$entryName", text, forbiddenManifestEntries)
                    }
                }
            }
            leaks
        }.getOrElse { error ->
            listOf("release-safety-scan-error:${file.invariantPath()}:${error.message.orEmpty()}")
        }
    }

    private fun scanNestedZip(
        owner: String,
        bytes: ByteArray,
        forbiddenClassPaths: List<String>,
        forbiddenBinaryPatterns: List<String>,
        forbiddenManifestEntries: List<String>,
    ): List<String> {
        return runCatching {
            val leaks = mutableListOf<String>()
            ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                generateSequence { zip.nextEntry }.forEach { entry ->
                    if (entry.isDirectory) return@forEach

                    val entryName = entry.name
                    forbiddenClassPaths.forEach { classPath ->
                        if (entryName == classPath || entryName.endsWith("/$classPath")) {
                            leaks += "forbidden-class-entry:$owner!/$entryName"
                        }
                    }

                    val entryExtension = entryName.substringAfterLast('.', "").lowercase(Locale.US)
                    if (entryExtension in BINARY_EXTENSIONS) {
                        leaks += binaryLeaks("$owner!/$entryName", zip.readBytesCapped(), forbiddenBinaryPatterns)
                    } else if (entryExtension in TEXT_EXTENSIONS) {
                        leaks += textLeaks("$owner!/$entryName", zip.readBytesCapped().toString(Charsets.UTF_8), forbiddenManifestEntries)
                    }
                }
            }
            leaks
        }.getOrElse { error ->
            listOf("release-safety-scan-error:$owner:${error.message.orEmpty()}")
        }
    }

    private fun scanBinaryFile(file: File, forbiddenBinaryPatterns: List<String>): List<String> {
        if (file.length() > MAX_ENTRY_SCAN_BYTES) return emptyList()
        return binaryLeaks(file.invariantPath(), file.readBytes(), forbiddenBinaryPatterns)
    }

    private fun scanTextFile(file: File, forbiddenManifestEntries: List<String>): List<String> {
        if (file.length() > MAX_ENTRY_SCAN_BYTES) return emptyList()
        return textLeaks(file.invariantPath(), file.readText(Charsets.UTF_8), forbiddenManifestEntries)
    }

    private fun binaryLeaks(owner: String, bytes: ByteArray, patterns: List<String>): List<String> {
        return patterns
            .filter { bytes.containsBytes(it.toByteArray(Charsets.UTF_8)) }
            .map { "forbidden-binary-reference:$owner:$it" }
    }

    private fun textLeaks(owner: String, text: String, patterns: List<String>): List<String> {
        return patterns
            .filter { text.contains(it) }
            .map { "forbidden-manifest-reference:$owner:$it" }
    }

    private fun File.isReleaseCandidate(): Boolean {
        val path = invariantPath().lowercase(Locale.US)
        return path.contains("/release/") ||
            path.contains("-release") ||
            path.contains("release-") ||
            path.contains("release.") ||
            path.endsWith("/release")
    }

    private fun File.invariantPath(): String = absolutePath.replace(File.separatorChar, '/')

    private fun ByteArray.containsBytes(pattern: ByteArray): Boolean {
        if (pattern.isEmpty() || pattern.size > size) return false
        outer@ for (index in 0..(size - pattern.size)) {
            for (offset in pattern.indices) {
                if (this[index + offset] != pattern[offset]) continue@outer
            }
            return true
        }
        return false
    }

    private fun ZipInputStream.readBytesCapped(): ByteArray {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        val output = ByteArrayOutputStream()
        var total = 0
        while (total <= MAX_ENTRY_SCAN_BYTES) {
            val read = read(buffer)
            if (read == -1) break
            output.write(buffer, 0, read)
            total += read
        }
        return output.toByteArray()
    }

    private companion object {
        const val MAX_ENTRY_SCAN_BYTES = 5L * 1024L * 1024L
        const val MAX_NESTED_ARCHIVE_BYTES = 50L * 1024L * 1024L
        val ARCHIVE_EXTENSIONS = setOf("jar", "aar", "apk", "zip")
        val BINARY_EXTENSIONS = setOf("class", "dex")
        val TEXT_EXTENSIONS = setOf("xml", "json", "txt", "properties", "pro", "cfg")
    }
}
