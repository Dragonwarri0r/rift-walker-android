import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("com.riftwalker.ai-debug")
    kotlin("android")
}

android {
    namespace = "com.riftwalker.sample"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.riftwalker.sample"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}

aiDebug {
    enabledForBuildTypes.set(listOf("debug"))
    probePackage("com.riftwalker.sample")
    exposeField("com.riftwalker.sample.SampleSession", "isVip")
    overrideMethod("com.riftwalker.sample.MainActivity#isNewCheckoutEnabled()")
    traceMethod("com.riftwalker.sample.MainActivity#renderLocalState()")
    mediaInputControl {
        audio {
            hookAudioRecordReads.set(true)
            hookAudioRecordLifecycle.set(true)
        }
        camera {
            hookCameraXAnalyzers.set(true)
            hookMlKitInputImageFactories.set(true)
        }
    }
}
