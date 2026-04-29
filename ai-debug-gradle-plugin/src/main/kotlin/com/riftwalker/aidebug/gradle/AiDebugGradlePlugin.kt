package com.riftwalker.aidebug.gradle

import com.android.build.api.instrumentation.FramesComputationMode
import com.android.build.api.instrumentation.InstrumentationScope
import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.gradle.BaseExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency

class AiDebugGradlePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create(
            "aiDebug",
            AiDebugGradleExtension::class.java,
            project.objects,
            project,
        )

        project.plugins.withId("com.android.application") {
            configureAndroidProject(project, extension)
            configureAndroidInstrumentation(project, extension)
        }
    }

    private fun configureAndroidProject(project: Project, extension: AiDebugGradleExtension) {
        val generateArtifacts = project.tasks.register(
            "generateAiDebugArtifacts",
            GenerateAiDebugArtifactsTask::class.java,
        ) { task ->
            task.sourceRoots.from(project.file("src/main/java"), project.file("src/main/kotlin"))
            task.probePackages.set(extension.probePackages)
            task.exposedFields.set(extension.exposedFields)
            task.traceMethods.set(extension.traceMethods)
            task.overrideMethods.set(extension.overrideMethods)
            task.exportSchema.set(extension.exportSchema)
            task.schemaOutput.set(extension.schemaOutput)
            task.symbolIndexOutput.set(extension.symbolIndexOutput)
            task.generatedSourceDir.set(extension.generatedSourceDir)
        }

        project.extensions.configure(BaseExtension::class.java) { android ->
            android.sourceSets.getByName("main").java.srcDir(generateArtifacts.map { it.generatedSourceDir.get().asFile })
        }

        project.afterEvaluate {
            val android = project.extensions.getByType(BaseExtension::class.java)
            val enabled = extension.enabledForBuildTypes.get().toSet()
            android.buildTypes.forEach { buildType ->
                val configurationName = "${buildType.name}Implementation"
                val dependency = if (buildType.name in enabled) {
                    extension.runtimeDependency.get()
                } else {
                    extension.noopDependency.get()
                }
                project.dependencies.add(configurationName, project.toDependencyNotation(dependency))
            }
            project.configurations.findByName("implementation")?.let {
                project.dependencies.add(it.name, project.toDependencyNotation(extension.annotationsDependency.get()))
            }
        }

        val releaseSafety = project.tasks.register(
            "checkAiDebugReleaseSafety",
            ReleaseSafetyCheckTask::class.java,
        ) { task ->
            task.reportOutput.set(project.layout.buildDirectory.file("ai-debug/release-safety-report.json"))
            task.failOnRuntimeLeak.set(extension.releaseSafety.failOnRuntimeLeak)
            task.forbiddenClasses.set(extension.releaseSafety.forbiddenClasses)
            task.forbiddenManifestEntries.set(extension.releaseSafety.forbiddenManifestEntries)
            task.scanRoots.from(
                project.layout.buildDirectory.dir("intermediates"),
                project.layout.buildDirectory.dir("outputs"),
                project.layout.buildDirectory.dir("tmp/kotlin-classes"),
            )
            task.dependencyNotations.set(project.provider {
                listOf("releaseImplementation", "releaseRuntimeOnly", "implementation", "runtimeOnly")
                    .flatMap { configurationName ->
                        project.configurations.findByName(configurationName)
                            ?.dependencies
                            ?.map { dependency ->
                                if (dependency is ProjectDependency) {
                                    dependency.path
                                } else {
                                    listOfNotNull(dependency.group, dependency.name, dependency.version).joinToString(":")
                                }
                            }
                            ?: emptyList()
                    }
            })
        }

        project.tasks.matching { it.name == "assembleRelease" }.configureEach { assembleRelease ->
            assembleRelease.finalizedBy(releaseSafety)
        }
        project.tasks.matching { it.name.startsWith("compile") && it.name.endsWith("Kotlin") }.configureEach {
            it.dependsOn(generateArtifacts)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun configureAndroidInstrumentation(project: Project, extension: AiDebugGradleExtension) {
        val androidComponents = project.extensions.getByType(AndroidComponentsExtension::class.java)
            as AndroidComponentsExtension<*, *, *>

        androidComponents.onVariants(androidComponents.selector().all()) { variant ->
            if (variant.name !in extension.enabledForBuildTypes.get()) return@onVariants

            val variantName = variant.name
            val capitalizedVariant = variantName.replaceFirstChar { it.uppercaseChar() }
            variant.instrumentation.transformClassesWith(
                AiDebugAsmClassVisitorFactory::class.java,
                InstrumentationScope.PROJECT,
            ) { params ->
                params.traceMethods.set(extension.traceMethods)
                params.overrideMethods.set(extension.overrideMethods)
                params.hookAudioRecordReads.set(extension.mediaInputControl.audio.hookAudioRecordReads)
                params.hookAudioRecordLifecycle.set(extension.mediaInputControl.audio.hookAudioRecordLifecycle)
                params.hookCameraXAnalyzers.set(extension.mediaInputControl.camera.hookCameraXAnalyzers)
                params.hookMlKitInputImageFactories.set(extension.mediaInputControl.camera.hookMlKitInputImageFactories)
                params.customFrameHooks.set(extension.mediaInputControl.camera.customFrameHooks)
            }
            variant.instrumentation.setAsmFramesComputationMode(FramesComputationMode.COMPUTE_FRAMES_FOR_INSTRUMENTED_METHODS)

            project.tasks.register(
                "verifyAiDebug${capitalizedVariant}Instrumentation",
                VerifyAiDebugInstrumentationTask::class.java,
            ) { task ->
                task.dependsOn("transform${capitalizedVariant}ClassesWithAsm")
                task.buildType.set(variantName)
                task.traceMethods.set(extension.traceMethods)
                task.overrideMethods.set(extension.overrideMethods)
                task.scanRoots.from(
                    project.layout.buildDirectory.dir("intermediates/classes/$variantName/transform${capitalizedVariant}ClassesWithAsm/dirs"),
                    project.layout.buildDirectory.dir("intermediates/classes/$variantName/transform${capitalizedVariant}ClassesWithAsm/jars"),
                )
                task.reportOutput.set(project.layout.buildDirectory.file("ai-debug/instrumentation-$variantName-report.json"))
            }
        }
    }

    private fun Project.toDependencyNotation(raw: String): Any {
        return when {
            raw.startsWith(":") -> dependencies.project(mapOf("path" to raw)) as ProjectDependency
            raw.startsWith("project:") -> dependencies.project(mapOf("path" to raw.removePrefix("project:"))) as ProjectDependency
            else -> raw
        }
    }
}
