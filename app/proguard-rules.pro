# ML Kit (code-barres) : les composants sont découverts par réflexion via les
# <meta-data> du manifeste (MlKitComponentDiscoveryService) et retrouvés par leur
# classe. Sans ces règles, R8 renomme/fusionne ces classes et
# BarcodeScanning.getClient() plante (NullPointerException) à l'ouverture du scanner.
-keep class com.google.mlkit.** { *; }
-keep class com.google.firebase.components.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_barcode_bundled.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_common.** { *; }

# Sauvegarde cloud — SDK Dropbox (dropbox-core-sdk) : aucune règle « keep » nécessaire
# (sérialiseurs générés, pas de réflexion), mais il référence des dépendances optionnelles
# absentes de l'app (OkHttp 2, App Engine, Servlet…). Liste officielle du README du SDK :
# https://github.com/dropbox/dropbox-sdk-java#does-this-sdk-require-any-special-proguard-rules-for-shrink-optimizations
-dontwarn okio.**
-dontwarn okhttp3.**
-dontwarn com.squareup.okhttp.**
-dontwarn com.google.apphosting.**
-dontwarn com.google.appengine.**
-dontwarn com.google.protos.cloud.sql.**
-dontwarn com.google.cloud.sql.**
-dontwarn javax.activation.**
-dontwarn javax.mail.**
-dontwarn javax.servlet.**
-dontwarn org.apache.**
