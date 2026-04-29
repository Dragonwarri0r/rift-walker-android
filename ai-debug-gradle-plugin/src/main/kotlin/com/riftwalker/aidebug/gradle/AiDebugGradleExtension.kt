package com.riftwalker.aidebug.gradle

import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import javax.inject.Inject

abstract class AiDebugGradleExtension @Inject constructor(
    objects: ObjectFactory,
    project: Project,
) {
    val enabledForBuildTypes: ListProperty<String> = objects.listProperty(String::class.java)
        .convention(listOf("debug"))
    val runtimeDependency: Property<String> = objects.property(String::class.java)
        .convention(":ai-debug-runtime")
    val noopDependency: Property<String> = objects.property(String::class.java)
        .convention(":ai-debug-runtime-noop")
    val annotationsDependency: Property<String> = objects.property(String::class.java)
        .convention(":ai-debug-annotations")

    val exportSchema: Property<Boolean> = objects.property(Boolean::class.java)
        .convention(true)
    val schemaOutput: RegularFileProperty = objects.fileProperty()
        .convention(project.layout.buildDirectory.file("ai-debug/debug-capabilities.json"))
    val symbolIndexOutput: RegularFileProperty = objects.fileProperty()
        .convention(project.layout.buildDirectory.file("ai-debug/probe-symbol-index.json"))

    val probePackages: ListProperty<String> = objects.listProperty(String::class.java)
        .convention(emptyList())
    val exposedFields: ListProperty<String> = objects.listProperty(String::class.java)
        .convention(emptyList())
    val traceMethods: ListProperty<String> = objects.listProperty(String::class.java)
        .convention(emptyList())
    val overrideMethods: ListProperty<String> = objects.listProperty(String::class.java)
        .convention(emptyList())

    val generatedSourceDir = project.layout.buildDirectory.dir("generated/source/aiDebug/main")

    val mediaInputControl: MediaInputControlExtension = objects.newInstance(MediaInputControlExtension::class.java)
    val releaseSafety: ReleaseSafetyExtension = objects.newInstance(ReleaseSafetyExtension::class.java)

    fun probePackage(packageName: String) {
        probePackages.add(packageName)
    }

    fun exposeField(className: String, fieldName: String) {
        exposedFields.add("$className#$fieldName")
    }

    fun traceMethod(methodId: String) {
        traceMethods.add(methodId)
    }

    fun overrideMethod(methodId: String) {
        overrideMethods.add(methodId)
    }

    fun mediaInputControl(action: Action<MediaInputControlExtension>) {
        action.execute(mediaInputControl)
    }

    fun releaseSafety(action: Action<ReleaseSafetyExtension>) {
        action.execute(releaseSafety)
    }
}

abstract class MediaInputControlExtension @Inject constructor(objects: ObjectFactory) {
    val enabledForDebugOnly: Property<Boolean> = objects.property(Boolean::class.java)
        .convention(true)
    val audio: MediaAudioExtension = objects.newInstance(MediaAudioExtension::class.java)
    val camera: MediaCameraExtension = objects.newInstance(MediaCameraExtension::class.java)

    fun audio(action: Action<MediaAudioExtension>) {
        action.execute(audio)
    }

    fun camera(action: Action<MediaCameraExtension>) {
        action.execute(camera)
    }
}

abstract class MediaAudioExtension @Inject constructor(objects: ObjectFactory) {
    val hookAudioRecordReads: Property<Boolean> = objects.property(Boolean::class.java)
        .convention(true)
    val hookAudioRecordLifecycle: Property<Boolean> = objects.property(Boolean::class.java)
        .convention(true)
}

abstract class MediaCameraExtension @Inject constructor(objects: ObjectFactory) {
    val hookCameraXAnalyzers: Property<Boolean> = objects.property(Boolean::class.java)
        .convention(true)
    val hookMlKitInputImageFactories: Property<Boolean> = objects.property(Boolean::class.java)
        .convention(true)
    val customFrameHooks: ListProperty<String> = objects.listProperty(String::class.java)
        .convention(emptyList())

    fun method(owner: String, name: String, desc: String) {
        customFrameHooks.add("$owner#$name$desc")
    }
}

abstract class ReleaseSafetyExtension @Inject constructor(objects: ObjectFactory) {
    val failOnRuntimeLeak: Property<Boolean> = objects.property(Boolean::class.java)
        .convention(true)
    val forbiddenClasses: ListProperty<String> = objects.listProperty(String::class.java)
        .convention(
            listOf(
                "com.riftwalker.aidebug.runtime.RuntimeHttpEndpoint",
                "com.riftwalker.aidebug.runtime.DebugSessionManager",
                "com.riftwalker.aidebug.runtime.network.NetworkControlInterceptor",
                "com.riftwalker.aidebug.runtime.state.StateController",
                "com.riftwalker.aidebug.runtime.storage.StorageController",
                "com.riftwalker.aidebug.runtime.debug.DynamicDebugController",
                "com.riftwalker.aidebug.runtime.AiDebugMediaHookBridge",
                "com.riftwalker.aidebug.runtime.media.MediaController",
            ),
        )
    val forbiddenManifestEntries: ListProperty<String> = objects.listProperty(String::class.java)
        .convention(listOf("com.riftwalker.aidebug.runtime"))
}
