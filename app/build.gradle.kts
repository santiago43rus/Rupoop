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

base {
    archivesName.set("Rupoop-v1.0.1")
}

android {
    namespace = "com.santiago43rus.rupoop"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.santiago43rus.rupoop"
        minSdk = 29
        targetSdk = 37
        versionCode = 2
        versionName = "1.0.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "GH_CLIENT_ID", "\"${localProperties.getProperty("GH_CLIENT_ID") ?: ""}\"")
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
            isMinifyEnabled = true
            isShrinkResources = true
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
    lint {
        checkReleaseBuilds = false
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
    implementation(libs.retrofit)
    implementation(libs.retrofit2.kotlinx.serialization.converter)
    implementation(libs.okhttp)

    // Video Player (Media3)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.common)
    implementation(libs.androidx.media3.exoplayer.hls)

    // ViewModel
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Icons
    implementation(libs.coil.compose)
    implementation(libs.androidx.compose.material.icons.extended)

    // WorkManager for background sync
    implementation(libs.androidx.work.runtime.ktx)

    // Compose animations
    implementation(libs.androidx.compose.animation)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
