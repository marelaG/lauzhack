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
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.lauzhack.network.RetrofitClient
import com.example.lauzhack.network.TextToSpeechRequest
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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

    // This is the integration point
    fun onImageCaptured(jpegData: ByteArray) {
        // Update the UI with the new image
        val bitmap = BitmapFactory.decodeByteArray(jpegData, 0, jpegData.size)
        _capturedImage.value = bitmap
        Log.i(TAG, "ViewModel updated with new image. Now triggering audio feedback.")

        // Trigger vibration and audio feedback
        vibratePhone()
        generateAndPlayAudio("Watch out! There is a car coming from your right!")
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

    private fun generateAndPlayAudio(text: String) {
        viewModelScope.launch {
            try {
                Log.d(TAG, "Starting text-to-speech generation...")
                val request = TextToSpeechRequest(text = text)
                val initialResponse = RetrofitClient.instance.startTextToSpeech(request)

                if (!initialResponse.isSuccessful || initialResponse.body() == null) {
                    Log.e(TAG, "Failed to start generation: ${initialResponse.errorBody()?.string()}")
                    return@launch
                }

                val requestId = initialResponse.body()!!.data.requestId
                Log.d(TAG, "Generation started with request ID: $requestId")

                while (true) {
                    Log.d(TAG, "Polling for status of request ID: $requestId...")
                    val statusResponse = RetrofitClient.instance.getRequestStatus(requestId)

                    if (!statusResponse.isSuccessful || statusResponse.body() == null) {
                        Log.e(TAG, "Failed to get status: ${statusResponse.errorBody()?.string()}")
                        break
                    }

                    val statusData = statusResponse.body()!!.data
                    Log.d(TAG, "Current status: ${statusData.status}")

                    if (statusData.status == "done") {
                        statusData.resultUrl?.let {
                            Log.d(TAG, "Generation complete. Audio URL: $it")
                            playAudioFromUrl(it)
                        } ?: Log.e(TAG, "Generation is done, but result_url is null.")
                        break
                    }
                    delay(2000)
                }
            } catch (e: Exception) {
                Log.e(TAG, "An error occurred during the process: ${e.message}", e)
            }
        }
    }

    private fun playAudioFromUrl(url: String) {
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

    override fun onCleared() {
        super.onCleared()
        cameraManager?.shutdown()
        mediaPlayer?.release()
        mediaPlayer = null
        stopCapture()
        Log.d(TAG, "ViewModel cleared and resources released.")
    }
}
