import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Release signing: env vars (CI) take precedence over the git-ignored root keystore.properties
// (local builds). Without either, release builds are left unsigned.
val keystoreProperties = Properties().apply {
    providers.fileContents(layout.settingsDirectory.file("keystore.properties"))
        .asText.orNull?.let { load(it.reader()) }
}

fun signingValue(envVar: String, property: String): String? =
    providers.environmentVariable(envVar).orNull?.takeIf { it.isNotEmpty() }
        ?: keystoreProperties.getProperty(property)

val releaseKeystore = signingValue("HOMESCREENER_KEYSTORE", "storeFile")

android {
    namespace = "ankhsquirrel.homescreener"
    compileSdk = 37

    defaultConfig {
        applicationId = "ankhsquirrel.homescreener"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
    }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = layout.settingsDirectory.file(releaseKeystore).asFile
                storePassword = signingValue("HOMESCREENER_KEYSTORE_PASSWORD", "storePassword")
                keyAlias = signingValue("HOMESCREENER_KEY_ALIAS", "keyAlias")
                keyPassword = signingValue("HOMESCREENER_KEY_PASSWORD", "keyPassword")
            }
        }
    }

    buildTypes {
        release {
            signingConfigs.findByName("release")?.let { signingConfig = it }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    // Don't embed the Google-signed dependency metadata blob (F-Droid / reproducible builds).
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.kotlinx.serialization.json)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
}
