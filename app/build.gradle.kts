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
        versionCode = 16
        versionName = "0.16.0"
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
    implementation(project(":core"))
    implementation(libs.play.services.wearable)
    implementation(libs.play.services.location)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.adaptive)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.glance.appwidget)
    implementation(libs.glance.preview)
    implementation(libs.glance.appwidget.preview)
    compileOnly(libs.libxposed.api)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
