plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.occaecat.hush"
    compileSdk = 36

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
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    // Home screen widgets.
    implementation(libs.glance.appwidget)
    implementation(libs.glance.preview)
    implementation(libs.glance.appwidget.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
