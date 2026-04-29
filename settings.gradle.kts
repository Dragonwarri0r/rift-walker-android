pluginManagement {
    includeBuild("ai-debug-gradle-plugin")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "riftwalker-android"

include(":ai-debug-protocol")
include(":ai-debug-annotations")
include(":ai-debug-runtime")
include(":ai-debug-runtime-noop")
include(":ai-debug-daemon")
include(":sample-app")
