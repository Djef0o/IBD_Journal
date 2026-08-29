package fr.ibdjournal.app

import android.app.Application
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class IBDJournalApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // ImageLoader par défaut pour Coil 3 : chargement local + réseau (Open Food Facts).
        SingletonImageLoader.setSafe { context ->
            ImageLoader.Builder(context)
                .components { add(OkHttpNetworkFetcherFactory()) }
                .crossfade(true)
                .build()
        }
    }
}
