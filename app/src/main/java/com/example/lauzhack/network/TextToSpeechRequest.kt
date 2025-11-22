package com.example.lauzhack.network

import com.google.gson.annotations.SerializedName

// Represents the JSON body for the text-to-speech POST request
data class TextToSpeechRequest(
    val text: String,
    val model: String = "Kokoro",
    val voice: String = "af_alloy",
    val lang: String = "en-us",
    val speed: Int = 1,
    val format: String = "mp3",
    @SerializedName("sample_rate")
    val sampleRate: Int = 24000
)
