# ML Kit (code-barres) : les composants sont découverts par réflexion via les
# <meta-data> du manifeste (MlKitComponentDiscoveryService) et retrouvés par leur
# classe. Sans ces règles, R8 renomme/fusionne ces classes et
# BarcodeScanning.getClient() plante (NullPointerException) à l'ouverture du scanner.
-keep class com.google.mlkit.** { *; }
-keep class com.google.firebase.components.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_barcode_bundled.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_common.** { *; }
