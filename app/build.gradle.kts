import java.util.Properties

val keystorePropertiesFile = rootProject.file("keystore.properties")
val hasReleaseKeystore = keystorePropertiesFile.exists()
val keystoreProperties = Properties().apply {
    if (hasReleaseKeystore) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "fr.ibdjournal.app"
    compileSdk = 37

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    defaultConfig {
        applicationId = "fr.ibdjournal.app"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            // R8 : supprime le code inutilisé (material-icons-extended surtout) et optimise.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.findByName("release")
        }
    }

    buildFeatures {
        compose = true
    }

    // Déclare au système les langues de l'app (choix de langue par application, Android 13+),
    // à partir des dossiers values-xx présents. Langue des ressources par défaut : res/resources.properties.
    androidResources {
        generateLocaleConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

gradle.taskGraph.whenReady {
    val buildingRelease = allTasks.any { it.name.endsWith("Release") }
    if (buildingRelease && !hasReleaseKeystore) {
        throw GradleException(
            "Missing keystore.properties in ${rootProject.projectDir} — create it with " +
                "storeFile/storePassword/keyAlias/keyPassword entries to sign release builds."
        )
    }
}

dependencies {
    // Core / Lifecycle
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.appcompat)

    // Compose (versions managed by the BOM)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // DI
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.navigation.compose)

    // Persistence
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)

    // Network: OpenFoodFacts API
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.coroutines.android)

    // Camera + barcode scanning (ML Kit)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.mlkit.barcode.scanning)

    // Image loading
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    // WorkManager
    implementation(libs.androidx.work.runtime.ktx)

    // Cloud backup — Google Drive: Google Identity Services AuthorizationClient (OAuth via the
    // Android client registered for package + SHA-1; no client id in code).
    implementation(libs.play.services.auth)
    // Task.await() for the Play services Tasks returned by AuthorizationClient.
    implementation(libs.kotlinx.coroutines.play.services)
    // Cloud backup — Dropbox: official SDK; the Android artifact provides the PKCE AuthActivity
    // (db-<APP_KEY> redirect scheme), the core artifact the API v2 client.
    implementation(libs.dropbox.core.sdk)
    implementation(libs.dropbox.android.sdk)
    // Cloud backup — OneDrive: Custom Tab for the Microsoft sign-in (authorization code + PKCE).
    implementation(libs.androidx.browser)
}

// ---- Cloud backup: per-build OAuth identifiers ------------------------------------------
// Read from the git-ignored local.properties:
//   dropbox.appKey=<"App key" from the Dropbox App Console>
//   onedrive.clientId=<"Application (client) ID" from the Azure app registration>
// A missing/blank value still builds: that provider then shows "not configured" in settings.
val cloudBackupProperties = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
val dropboxAppKey: String = cloudBackupProperties.getProperty("dropbox.appKey").orEmpty().trim()
val oneDriveClientId: String = cloudBackupProperties.getProperty("onedrive.clientId").orEmpty().trim()

android {
    buildFeatures {
        buildConfig = true
    }
    defaultConfig {
        buildConfigField("String", "DROPBOX_APP_KEY", "\"$dropboxAppKey\"")
        buildConfigField("String", "ONEDRIVE_CLIENT_ID", "\"$oneDriveClientId\"")
        // Scheme db-<APP_KEY> of Dropbox's AuthActivity (placeholder keeps the manifest valid
        // when the key is absent; the app never starts the Dropbox flow in that case).
        manifestPlaceholders["dropboxAppKey"] = dropboxAppKey.ifEmpty { "unconfigured" }
    }
}
