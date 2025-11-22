package com.example.lauzhack

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.example.lauzhack.network.RetrofitClient
import com.example.lauzhack.network.TextToSpeechRequest
import com.example.lauzhack.ui.theme.LauzHackTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.IOException

// Define a TAG for the Logcat messages
private const val TAG = "MainActivity"

class MainActivity : ComponentActivity() {
    private var mediaPlayer: MediaPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LauzHackTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    ScreenWithButton(
                        modifier = Modifier.padding(innerPadding),
                        onPlayAudio = {
                            // Start the text-to-speech process with a sample text
                            generateAndPlayAudio("I am now gonna start with a new message to check that the generation is legit.")
                        }
                    )
                }
            }
        }
    }

    private fun generateAndPlayAudio(text: String) {
        lifecycleScope.launch {
            try {
                // Step 1: Start the text-to-speech generation
                Log.d(TAG, "Starting text-to-speech generation...")
                val request = TextToSpeechRequest(text = text)
                val initialResponse = RetrofitClient.instance.startTextToSpeech(request)

                if (!initialResponse.isSuccessful || initialResponse.body() == null) {
                    Log.e(TAG, "Failed to start generation: ${initialResponse.errorBody()?.string()}")
                    return@launch
                }

                val requestId = initialResponse.body()!!.data.requestId
                Log.d(TAG, "Generation started with request ID: $requestId")

                // Step 2: Poll for the result
                while (true) {
                    Log.d(TAG, "Polling for status of request ID: $requestId...")
                    val statusResponse = RetrofitClient.instance.getRequestStatus(requestId)

                    if (!statusResponse.isSuccessful || statusResponse.body() == null) {
                        Log.e(TAG, "Failed to get status: ${statusResponse.errorBody()?.string()}")
                        break // Exit the loop on error
                    }

                    val statusData = statusResponse.body()!!.data
                    Log.d(TAG, "Current status: ${statusData.status}")

                    if (statusData.status == "done") {
                        val resultUrl = statusData.resultUrl
                        if (resultUrl != null) {
                            Log.d(TAG, "Generation complete. Audio URL: $resultUrl")
                            // Step 3: Play the audio
                            playAudioFromUrl(resultUrl)
                        } else {
                            Log.e(TAG, "Generation is done, but result_url is null.")
                        }
                        break // Exit the loop on completion
                    }

                    // Wait for 2 seconds before polling again
                    delay(2000)
                }

            } catch (e: Exception) {
                Log.e(TAG, "An error occurred during the process: ${e.message}", e)
            }
        }
    }

    private fun playAudioFromUrl(url: String) {
        // This function remains the same
        mediaPlayer?.release()
        mediaPlayer = MediaPlayer().apply {
            Log.d(TAG, "Preparing to play audio from URL: $url")
            try {
                val audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
                setAudioAttributes(audioAttributes)
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

    override fun onStop() {
        super.onStop()
        mediaPlayer?.release()
        mediaPlayer = null
    }
}

@Composable
fun ScreenWithButton(modifier: Modifier = Modifier, onPlayAudio: () -> Unit) {
    // This composable remains largely the same
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Button(
            onClick = {
                Log.d(TAG, "Button clicked.")
                onPlayAudio()
            },
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .padding(24.dp)
                .size(width = 300.dp, height = 100.dp)
        ) {
            Text(
                text = "CLICK ME NOW !",
                fontSize = 28.sp
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ScreenWithButtonPreview() {
    LauzHackTheme {
        ScreenWithButton(onPlayAudio = {})
    }
}
