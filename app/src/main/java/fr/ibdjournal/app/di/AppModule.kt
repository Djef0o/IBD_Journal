package fr.ibdjournal.app.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import fr.ibdjournal.app.data.local.AppDatabase
import fr.ibdjournal.app.data.local.Migrations
import fr.ibdjournal.app.data.local.MealDao
import fr.ibdjournal.app.data.local.ProductDao
import fr.ibdjournal.app.data.local.StoolDao
import fr.ibdjournal.app.data.remote.OpenFoodFactsApi
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json { ignoreUnknownKeys = true }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()

    @Provides
    @Singleton
    fun provideRetrofit(json: Json, client: OkHttpClient): Retrofit =
        Retrofit.Builder()
            .baseUrl("https://world.openfoodfacts.org/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    @Provides
    @Singleton
    fun provideOpenFoodFactsApi(retrofit: Retrofit): OpenFoodFactsApi =
        retrofit.create(OpenFoodFactsApi::class.java)

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME)
            .addMigrations(*Migrations.ALL)
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideMealDao(db: AppDatabase): MealDao = db.mealDao()

    @Provides
    fun provideStoolDao(db: AppDatabase): StoolDao = db.stoolDao()

    @Provides
    fun provideProductDao(db: AppDatabase): ProductDao = db.productDao()
}
