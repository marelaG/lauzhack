package com.example.lauzhack.network

import com.google.gson.annotations.SerializedName

// The top-level response for the status GET request
data class TextToSpeechStatusResponse(
    val data: StatusData
)

// The nested "data" object containing the status and result URL
data class StatusData(
    val status: String,
    @SerializedName("result_url")
    val resultUrl: String?
)
