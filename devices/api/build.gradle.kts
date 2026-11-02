plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

// The contract between the app shell and a device module (devices/<brand>), plus tools any brand can use (AutoEq).
android {
    namespace = "com.occaecat.hush.devices"
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
    testOptions.unitTests.isReturnDefaultValues = true
}

dependencies {
    api(project(":ui"))
    api(libs.kotlinx.coroutines.android)
    // Insights notes where a device was last seen.
    implementation(libs.play.services.location)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.androidx.core.ktx)
    testImplementation(libs.junit)
}
