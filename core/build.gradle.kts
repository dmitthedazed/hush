plugins {
    alias(libs.plugins.android.library)
}

// Shared by the phone, watch and TV apps: the Sony MDR transport (on :protocol:mdr), the session controller, the watch link contract
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

    // The headset simulator runs HeadphonesController on the JVM: Android stubs answer with defaults.
    testOptions.unitTests.isReturnDefaultValues = true
    // The captured headsets live with the protocol; read them as files, not from a jar.
    sourceSets { getByName("test") { resources.directories.add("../protocol/mdr/src/test/resources") } }
}

dependencies {
    api(project(":protocol:mdr"))
    api(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.core.ktx)
    testImplementation(libs.junit)
    testImplementation(testFixtures(project(":protocol:mdr")))
    testImplementation(libs.kotlinx.coroutines.test)
}
