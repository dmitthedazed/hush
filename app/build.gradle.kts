plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.occaecat.hush"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.occaecat.hush"
        minSdk = 31
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
    // One line per brand: the shell lists them in DeviceModules.
    implementation(project(":devices:api"))
    implementation(project(":devices:sony"))
    implementation(project(":devices:apple"))
    // The watch companion route (WatchBridge).
    implementation(project(":link"))
    implementation(libs.play.services.wearable)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    implementation(libs.navigation3.runtime)
    implementation(libs.navigation3.ui)
    // Home screen widgets.
    implementation(libs.glance.appwidget)
    implementation(libs.glance.preview)
    implementation(libs.glance.appwidget.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
