plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "app.araddia"
    compileSdk = 35
    defaultConfig {
        applicationId = "app.araddia"
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        versionName = "2.1"
        ndk { abiFilters += "arm64-v8a" }
    }
    buildTypes { release { isMinifyEnabled = false } }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}
dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    // Motor local (Gemma 3n / Gemma 4 em .litertlm/.task). Se o Gradle não achar a versão, use a mais recente.
    implementation("com.google.mediapipe:tasks-genai:0.10.26")
    // Vídeo de fundo
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")
    // OCR (offline) e PDF
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")
}
