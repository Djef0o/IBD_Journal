// Multiplatform core of the app: models, Room database, calculations. Android uses it right now;
// the iOS targets prepare the future iPhone app (compiled here, linked on macOS).
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

kotlin {
    android {
        namespace = "fr.ibdjournal.shared"
        compileSdk = 37
        minSdk = 26
        withHostTest {}
    }

    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            api(libs.androidx.room.runtime)
            api(libs.kotlinx.datetime)
            api(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
        }
        iosMain.dependencies {
            implementation(libs.androidx.sqlite.bundled)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

// Room generates its code for each target.
dependencies {
    add("kspAndroid", libs.androidx.room.compiler)
    add("kspIosArm64", libs.androidx.room.compiler)
    add("kspIosSimulatorArm64", libs.androidx.room.compiler)
}
