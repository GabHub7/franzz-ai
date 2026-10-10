plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "id.franzz.orbit"
    compileSdk = 34

    defaultConfig {
        applicationId = "id.franzz.orbit"
        minSdk = 26
        targetSdk = 34
        versionCode = 3
        versionName = "1.1.1"
        val server = (project.findProperty("ORBIT_SERVER") as String?)?.trim().orEmpty().replace("\\", "").replace("\"", "")
        buildConfigField("String", "DEFAULT_SERVER", "\"$server\"")
    }
    buildFeatures { buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildTypes { release { isMinifyEnabled = false } }
}
