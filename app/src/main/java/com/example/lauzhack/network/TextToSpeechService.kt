package com.example.lauzhack.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface TextToSpeechService {
    // Starts the text-to-speech conversion
    @POST("txt2audio")
    suspend fun startTextToSpeech(@Body request: TextToSpeechRequest): Response<TextToSpeechInitialResponse>

    // Gets the status of a request
    @GET("request-status/{request_id}")
    suspend fun getRequestStatus(@Path("request_id") requestId: String): Response<TextToSpeechStatusResponse>
}
