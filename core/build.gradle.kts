plugins {
    alias(libs.plugins.android.library)
}

// Shared by the phone, watch and TV apps: the Sony MDR protocol, the session controller, the watch link contract
// and the user-facing labels/demo state the phone and TV UIs both use.
android {
    namespace = "com.occaecat.hush.core"
    compileSdk = 36

    defaultConfig {
        minSdk = 31
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    api(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.core.ktx)
    testImplementation(libs.junit)
}
