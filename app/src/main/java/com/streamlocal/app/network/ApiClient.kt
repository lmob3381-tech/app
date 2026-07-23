package com.streamlocal.app.network

import android.content.Context
import com.streamlocal.app.data.ServerConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Dibuat ulang setiap kali dibutuhkan (bukan singleton statis) supaya selalu
 * memakai Base URL TERBARU dari Settings. Ini yang membuat aplikasi bisa
 * "pindah" server kapan saja tanpa perlu rebuild — cukup ganti URL di layar
 * Pengaturan, lalu request berikutnya langsung pakai URL itu.
 */
object ApiClient {

    class NoServerConfiguredException : Exception("Base URL server belum diatur")

    private val logging = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BASIC
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .addInterceptor(logging)
        .build()

    /** @throws NoServerConfiguredException kalau base URL belum diisi di Settings */
    fun create(context: Context): StreamLocalApi {
        val baseUrl = ServerConfig.getBaseUrl(context)
            ?: throw NoServerConfiguredException()

        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        return retrofit.create(StreamLocalApi::class.java)
    }
}
