import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    id("org.jetbrains.kotlin.plugin.serialization")
}

val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use { load(it) }
    }
}

android {
    namespace = "com.santiago43rus.rupoop"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.rupoop"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "GH_CLIENT_ID", "\"${localProperties.getProperty("GH_CLIENT_ID") ?: ""}\"")
        buildConfigField("String", "GH_CLIENT_SECRET", "\"${localProperties.getProperty("GH_CLIENT_SECRET") ?: ""}\"")
        buildConfigField("String", "PROXY_URL", "\"${localProperties.getProperty("PROXY_URL") ?: ""}\"")
        buildConfigField("String", "DONATE_URL", "\"${localProperties.getProperty("DONATE_URL") ?: ""}\"")
        manifestPlaceholders["appAuthRedirectScheme"] = "rupoop"
    }

    signingConfigs {
        create("release") {
            val keystoreFile = file("keystore.jks")
            if (keystoreFile.exists()) {
                storeFile = keystoreFile
                storePassword = System.getenv("KEYSTORE_PASSWORD") ?: localProperties.getProperty("KEYSTORE_PASSWORD") ?: ""
                keyAlias = System.getenv("KEY_ALIAS") ?: localProperties.getProperty("KEY_ALIAS") ?: ""
                keyPassword = System.getenv("KEY_PASSWORD") ?: localProperties.getProperty("KEY_PASSWORD") ?: ""
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            val keystoreFile = file("keystore.jks")
            if (keystoreFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    // Security
    implementation(libs.androidx.security.crypto)

    // Auth
    implementation(libs.appauth)

    // JSON Serialization
    implementation(libs.kotlinx.serialization.json)

    // Network & API
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.jakewharton.retrofit:retrofit2-kotlinx-serialization-converter:1.0.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Video Player (Media3)
    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-ui:1.5.1")
    implementation("androidx.media3:media3-common:1.5.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.5.1")

    // ViewModel
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.6.1")

    // Icons
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("androidx.compose.material:material-icons-extended:1.7.7")

    // WorkManager for background sync
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // Compose animations
    implementation("androidx.compose.animation:animation:1.7.7")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
