package com.localnet.wifihome.data.network

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {

    /**
     * Buat instance Esp32Api baru mengarah ke [baseUrl], contoh: "http://192.168.1.50/"
     * Base URL wajib diakhiri "/" dan wajib pakai IP lokal ESP32 (bukan domain publik).
     */
    fun createEsp32Api(baseUrl: String): Esp32Api {
        val client = OkHttpClient.Builder()
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()

        val normalizedUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"

        return Retrofit.Builder()
            .baseUrl(normalizedUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(Esp32Api::class.java)
    }
}
