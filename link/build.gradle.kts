plugins {
    alias(libs.plugins.android.library)
}

// The phone ↔ watch contract: what a headset looks like and what the watch can ask for, in no brand's terms.
// Each brand's engine maps its own state onto it (Sony's in :devices:sony-core).
android {
    namespace = "com.occaecat.hush.link"
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
    testImplementation(libs.junit)
    testImplementation(libs.json)
}
