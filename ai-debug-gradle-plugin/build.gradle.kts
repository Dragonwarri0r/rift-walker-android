plugins {
    kotlin("jvm") version "2.2.0"
    `java-gradle-plugin`
}

group = "com.riftwalker"
version = "0.1.0"

dependencies {
    compileOnly("com.android.tools.build:gradle:8.13.2")
    compileOnly("com.android.tools.build:gradle-api:8.13.2")
}

gradlePlugin {
    plugins {
        create("aiDebug") {
            id = "com.riftwalker.ai-debug"
            implementationClass = "com.riftwalker.aidebug.gradle.AiDebugGradlePlugin"
        }
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}
