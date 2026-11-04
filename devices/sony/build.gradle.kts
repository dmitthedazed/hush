plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

// Sony: the WH-1000XM4 over MDR (protocol and session in :core, shared with the watch and TV apps), with its
// pages, widgets, tile, ColorOS card and Xposed module.
android {
    namespace = "com.occaecat.hush.sony"
    compileSdk = 36

    defaultConfig {
        minSdk = 31
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
    implementation(project(":devices:api"))
    implementation(project(":devices:sony-core"))
    implementation(libs.play.services.location)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    compileOnly(libs.libxposed.api)
}
