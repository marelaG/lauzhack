package com.example.lauzhack

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Base64
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.lauzhack.network.RetrofitClient
import com.example.lauzhack.network.TextToSpeechRequest
import com.example.lauzhack.vision.ContentPart
import com.example.lauzhack.vision.ImageUrl
import com.example.lauzhack.vision.VisionMessage
import com.example.lauzhack.vision.VisionRequest
import com.example.lauzhack.vision.VisionRetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

private const val TAG = "CameraViewModel"

class CameraViewModel(application: Application) : AndroidViewModel(application) {
    private val _isCapturing = MutableStateFlow(false)
    val isCapturing = _isCapturing.asStateFlow()

    private val _capturedImage = MutableStateFlow<Bitmap?>(null)
    val capturedImage = _capturedImage.asStateFlow()

    private var captureJob: Job? = null
    private var cameraManager: CameraManager? = null
    private var mediaPlayer: MediaPlayer? = null

    fun initializeCameraManager(manager: CameraManager) {
        this.cameraManager = manager
    }

    fun toggleCapture() {
        if (_isCapturing.value) {
            stopCapture()
        } else {
            startCapture()
        }
    }

    private fun startCapture() {
        if (captureJob?.isActive == true) return
        _isCapturing.value = true
        Log.i(TAG, "Picture capture loop STARTED (every 5s).")

        captureJob = viewModelScope.launch {
            while (_isCapturing.value) {
                cameraManager?.takePicture()
                delay(5000L) // Capture every 5 seconds
            }
        }
    }

    private fun stopCapture() {
        captureJob?.cancel()
        captureJob = null
        _isCapturing.value = false
        Log.i(TAG, "Picture capture loop STOPPED.")
    }

    fun onImageCaptured(jpegData: ByteArray) {
        Log.i(TAG, "New image captured. Starting background workflow on IO dispatcher...")
        viewModelScope.launch(Dispatchers.IO) { // Switch to a background thread for the entire workflow
            // Decode bitmap in the background
            val bitmap = BitmapFactory.decodeByteArray(jpegData, 0, jpegData.size)
            withContext(Dispatchers.Main) {
                _capturedImage.value = bitmap // Update UI on the main thread
            }

            // Step 1: Analyze the image
            val description = analyzeImage(jpegData)

            if (description != null) {
                Log.i(TAG, "Vision API analysis complete: '$description'")
                // Step 2: Conditionally vibrate
                if (description.startsWith("WARNING")) {
                    vibratePhone()
                }
                // Step 3: Generate and play audio for the description
                generateAndPlayAudio(description)
            } else {
                Log.e(TAG, "Vision API analysis failed.")
            }
        }
    }

    private suspend fun analyzeImage(imageData: ByteArray): String? {
        val base64Image = Base64.encodeToString(imageData, Base64.NO_WRAP)
        val imageUrl = "data:image/jpeg;base64,$base64Image"

        val request = VisionRequest(
            model = "google/gemma-3n-E4B-it",
            messages = listOf(
                VisionMessage(
                    role = "user",
                    content = listOf(
                        ContentPart(
                            type = "text",
                            text = "You are a helpful crucial assistant helping a visualy impaired person. Please tell this person if there is an obstacle ahead. Give your instructions as shortly as possible. Start with WARNING if there is an obstacle reachable within 3 seconds. Precise what obstacle it is. Don't give unnnecessary warnings if the path ahead is clear enough. For example 'The path is clear for now.', or 'WARNING! Dog ahead at 2 meters'"
                        ),
                        ContentPart(type = "image_url", imageUrl = ImageUrl(url = imageUrl))
                    )
                )
            )
        )

        return try {
            val response = VisionRetrofitClient.instance.analyzeImage(request)
            if (response.isSuccessful) {
                response.body()?.choices?.firstOrNull()?.message?.content
            } else {
                Log.e(TAG, "Vision API error: ${response.errorBody()?.string()}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Vision API request failed: ${e.message}", e)
            null
        }
    }

    private fun vibratePhone() {
        val context = getApplication<Application>().applicationContext
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        if (!vibrator.hasVibrator()) {
            Log.w(TAG, "Device does not have a vibrator.")
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Log.d(TAG, "Triggering vibration with VibrationEffect.")
            vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(200)
        }
    }

    private suspend fun generateAndPlayAudio(text: String) {
        try {
            Log.d(TAG, "Starting text-to-speech generation...")
            val request = TextToSpeechRequest(text = text)
            val initialResponse = RetrofitClient.instance.startTextToSpeech(request)

            if (!initialResponse.isSuccessful || initialResponse.body() == null) {
                Log.e(TAG, "Failed to start generation: ${initialResponse.errorBody()?.string()}")
                return
            }

            val requestId = initialResponse.body()!!.data.requestId
            Log.d(TAG, "Generation started with request ID: $requestId")

            val pollStartTime = System.currentTimeMillis()
            val pollTimeout = 30_000L // 30-second timeout

            while (System.currentTimeMillis() - pollStartTime < pollTimeout) {
                Log.d(TAG, "Polling for status of request ID: $requestId...")
                val statusResponse = RetrofitClient.instance.getRequestStatus(requestId)

                if (statusResponse.isSuccessful && statusResponse.body() != null) {
                    val statusData = statusResponse.body()!!.data
                    Log.d(TAG, "Current status: ${statusData.status}")

                    if (statusData.status == "done") {
                        statusData.resultUrl?.let {
                            Log.d(TAG, "Generation complete. Audio URL: $it")
                            playAudioFromUrl(it)
                        } ?: Log.e(TAG, "Generation is done, but result_url is null.")
                        return // Exit the function on success
                    }
                } else {
                    Log.e(TAG, "Failed to get status: ${statusResponse.errorBody()?.string()}")
                    break // Exit loop on error
                }
                delay(2000)
            }

            Log.e(TAG, "Polling for TTS result timed out after $pollTimeout ms.")

        } catch (e: Exception) {
            Log.e(TAG, "An error occurred during the TTS process: ${e.message}", e)
        }
    }

    private fun playAudioFromUrl(url: String) {
        // This function must be thread-safe for the media player
        viewModelScope.launch(Dispatchers.Main) { // Ensure media player operations are on the main thread
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                Log.d(TAG, "Preparing to play audio from URL: $url")
                try {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    setDataSource(url)
                    setOnPreparedListener {
                        Log.d(TAG, "MediaPlayer prepared, starting playback.")
                        start()
                    }
                    setOnCompletionListener {
                        Log.d(TAG, "MediaPlayer playback completed.")
                        it.release()
                        mediaPlayer = null
                    }
                    setOnErrorListener { mp, what, extra ->
                        Log.e(TAG, "MediaPlayer error - what: $what, extra: $extra")
                        mp.release()
                        mediaPlayer = null
                        true
                    }
                    prepareAsync()
                } catch (e: IOException) {
                    Log.e(TAG, "MediaPlayer IOException: ${e.message}", e)
                    release()
                    mediaPlayer = null
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        cameraManager?.shutdown()
        mediaPlayer?.release()
        mediaPlayer = null
        stopCapture()
        Log.d(TAG, "ViewModel cleared and resources released.")
    }
}
