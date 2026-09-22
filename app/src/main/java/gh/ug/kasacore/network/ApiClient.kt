package gh.ug.kasacore.network

import gh.ug.kasacore.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Points at whatever ngrok prints on Selorm's laptop (00_START_HERE.md §4.2).
 * Override per build with -PkasaServerUrl=https://xxxx.ngrok-free.app/ (see app/build.gradle.kts).
 * 10.0.2.2 is the emulator's alias for the host machine's localhost; a real
 * phone needs the ngrok URL.
 */
object ApiClient {
    val kasaApi: KasaApi by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC
            else HttpLoggingInterceptor.Level.NONE
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)   // ASR can be slow on a laptop CPU
            .build()

        Retrofit.Builder()
            .baseUrl(BuildConfig.SERVER_BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(KasaApi::class.java)
    }
}
