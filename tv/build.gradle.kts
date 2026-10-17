plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Android TV / Google TV build: Compose for TV (tv-material), talking to the headset directly over the TV's
// Bluetooth, like the phone app does.
android {
    namespace = "com.occaecat.hush.tv"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.occaecat.hush"
        minSdk = 31 // Android TV 12+, same floor as :core
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
    implementation(project(":core"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.tv.material)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
