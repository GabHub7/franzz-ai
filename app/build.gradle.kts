plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val defaultServer = "https://www.franzzorbit.my.id"
val serverValue = (project.findProperty("ORBIT_SERVER") as String?)
    ?.trim()?.trimEnd('/')?.ifBlank { defaultServer } ?: defaultServer
val escapedServer = serverValue.replace("\\", "\\\\").replace("\"", "\\\"")

android {
    namespace = "id.franzz.orbit"
    compileSdk = 35

    defaultConfig {
        applicationId = "id.franzz.orbit"
        minSdk = 26
        targetSdk = 34
        versionCode = 4
        versionName = "1.2.0"
        buildConfigField("String", "DEFAULT_SERVER", "\"$escapedServer\"")
    }

    buildFeatures { buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildTypes { release { isMinifyEnabled = false } }
}
