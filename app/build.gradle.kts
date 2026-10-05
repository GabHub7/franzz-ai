plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.franzz.orbit"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.franzz.orbit"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }
    buildTypes { release { isMinifyEnabled = false } }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
dependencies { implementation("androidx.core:core-ktx:1.15.0") }

// Upload lewat web GitHub hanya menambah/menimpa berkas, tidak menghapus. Berkas XML lama ini
// bentrok dengan resource baru (ic_launcher_fg) dan membuat build gagal "duplicate resources".
val cleanStaleRes = tasks.register<Delete>("cleanStaleRes") {
    delete(file("src/main/res/drawable/ic_launcher_fg.xml"))
}
tasks.matching { it.name == "preBuild" }.configureEach { dependsOn(cleanStaleRes) }
