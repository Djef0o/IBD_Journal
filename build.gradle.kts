// Top-level build file. Plugins are declared here and applied in :app.

// KGP >= 2.2.10 est requis par le Kotlin intégré d'AGP 9 ; on force la dernière
// version stable pour lire le métadonnées des bibliothèques récentes (Kotlin 2.4).
buildscript {
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.10")
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}
