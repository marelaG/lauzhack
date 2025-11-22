package com.example.lauzhack.network

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {
    private const val BASE_URL = "https://api.deapi.ai/api/v1/client/"

    // IMPORTANT: Replace with your actual API key
    private const val API_KEY = ""

    private val httpClient by lazy {
        // Create a logging interceptor to see request and response logs
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        OkHttpClient.Builder()
            .addInterceptor { chain ->
                // Add authorization and content type headers to every request
                val request = chain.request().newBuilder()
                    .addHeader("Authorization", "Bearer $API_KEY")
                    .addHeader("accept", "application/json")
                    .build()
                chain.proceed(request)
            }
            .addInterceptor(logging) // Add the logging interceptor
            .build()
    }

    val instance: TextToSpeechService by lazy {
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(httpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        retrofit.create(TextToSpeechService::class.java)
    }
}
