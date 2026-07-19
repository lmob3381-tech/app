package com.lmob.repomanager.network

import com.google.gson.GsonBuilder
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object ApiClient {

    private const val BASE_URL = "https://api.github.com/"

    fun create(tokenProvider: () -> String?): GitHubApi {
        val authInterceptor = Interceptor { chain ->
            val token = tokenProvider()
            val requestBuilder = chain.request().newBuilder()
                .addHeader("Accept", "application/vnd.github+json")
                .addHeader("X-GitHub-Api-Version", "2022-11-28")
            if (!token.isNullOrBlank()) {
                requestBuilder.addHeader("Authorization", "Bearer $token")
            }
            chain.proceed(requestBuilder.build())
        }

        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(logging)
            .build()

        // PENTING: Gson() default menyertakan field bernilai null di JSON output,
        // misalnya GhDeleteTreeItem(sha = null) tetap terkirim sebagai "sha": null.
        // GitHub Git Trees API menolak ini dengan 422 karena "sha": null dianggap
        // field yang ADA tapi kosong, bukan field yang tidak dikirim sama sekali.
        // GsonBuilder().create() (tanpa memanggil .serializeNulls()) akan MEN-SKIP
        // field null saat serialize -- inilah yang kita butuhkan di sini.
        val gson = GsonBuilder().create()

        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(GitHubApi::class.java)
    }
}
