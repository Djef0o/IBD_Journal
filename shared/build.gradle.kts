// Multiplatform-structured core of the app: models, Room database, calculations.
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

    sourceSets {
        commonMain.dependencies {
            api(libs.androidx.room.runtime)
            api(libs.kotlinx.datetime)
            api(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

// Room generates its code for the Android target.
dependencies {
    add("kspAndroid", libs.androidx.room.compiler)
}
