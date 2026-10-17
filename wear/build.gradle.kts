plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    // Same application id and signing key as the phone app: the Wearable Data Layer only routes between those.
    namespace = "com.occaecat.hush.wear"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.occaecat.hush"
        minSdk = 33 // Wear OS 4+
        targetSdk = 36
        versionCode = 17
        versionName = "0.17.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    // Play services pulls in an old Fragment; ActivityResult needs 1.3+ (lint InvalidFragmentVersionForActivityResult).
    implementation("androidx.fragment:fragment:1.8.6")
    implementation(project(":core"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.wear.compose.material3)
    implementation(libs.wear.compose.foundation)
    implementation(libs.wear.compose.navigation3)
    implementation(libs.navigation3.runtime)
    implementation(libs.navigation3.ui)
    implementation(libs.wear)
    implementation(libs.wear.ongoing)
    implementation(libs.wear.remote.interactions)
    implementation(libs.wear.tiles)
    implementation(libs.wear.protolayout)
    implementation(libs.wear.protolayout.material3)
    implementation(libs.wear.protolayout.expression)
    implementation(libs.wear.complications.data.source.ktx)
    implementation(libs.play.services.wearable)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.wear.tooling.preview)
    debugImplementation(libs.wear.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.wear.tiles.renderer)
    debugImplementation(libs.wear.tiles.tooling.preview)
}
