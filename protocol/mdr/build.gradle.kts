plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-test-fixtures`
}

// Sony's MDR protocol (v1 and v2) as plain Kotlin: frames, requests, parsers and the state model. No Android and no
// UI, so it runs and is tested on the JVM; the RFCOMM transport and the session live in :core. The captured
// headsets (test resources) and their profiles (test fixtures) are shared with :core's simulator tests.
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions.jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
}

dependencies {
    testImplementation(libs.junit)
}
