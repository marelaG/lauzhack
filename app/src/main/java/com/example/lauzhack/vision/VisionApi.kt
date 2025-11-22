package com.example.lauzhack.vision

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

// --- Data Classes for the Request ---

data class VisionRequest(
    val model: String,
    val messages: List<VisionMessage>
)

data class VisionMessage(
    val role: String,
    val content: List<ContentPart>
)

data class ContentPart(
    val type: String,
    val text: String? = null,
    @SerializedName("image_url") val imageUrl: ImageUrl? = null
)

data class ImageUrl(val url: String)


// --- Data Classes for the Response ---

data class VisionResponse(
    val choices: List<Choice>
)

data class Choice(
    val message: ResponseMessage
)

data class ResponseMessage(
    val content: String
)


// --- Retrofit Service Interface ---

interface VisionService {
    @POST("chat/completions")
    suspend fun analyzeImage(@Body request: VisionRequest): Response<VisionResponse>
}
