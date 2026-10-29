plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Optional side-by-side hardware test build; the source namespace and normal release id stay unchanged.
val hushApplicationId = providers.gradleProperty("hushTestApplicationId").orElse("com.occaecat.hush").get()
require(hushApplicationId.matches(Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+"))) {
    "hushTestApplicationId must be a valid Android application id"
}
val hushIsolatedTestBuild = hushApplicationId != "com.occaecat.hush"

android {
    namespace = "com.occaecat.hush"
    compileSdk = 36

    defaultConfig {
        applicationId = hushApplicationId
        minSdk = 31
        targetSdk = 36
        versionCode = 17
        versionName = "0.17.0"
        resValue("string", "hush_application_id", hushApplicationId)
        resValue("string", "hush_app_label", if (hushIsolatedTestBuild) "Hush Jabra Test" else "Hush")
        buildConfigField("boolean", "ISOLATED_TEST_BUILD", hushIsolatedTestBuild.toString())
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
        resValues = true
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
    testImplementation(libs.junit)
    testImplementation("org.json:json:20240303")
}
