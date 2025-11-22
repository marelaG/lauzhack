package com.example.lauzhack.network

import com.google.gson.annotations.SerializedName

// The top-level response for the initial POST request
data class TextToSpeechInitialResponse(
    val data: RequestIdData
)

// The nested "data" object containing the request ID
data class RequestIdData(
    @SerializedName("request_id")
    val requestId: String
)
