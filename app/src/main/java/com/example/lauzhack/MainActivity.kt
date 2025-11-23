package com.example.lauzhack

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.lauzhack.ui.theme.LauzHackTheme
import java.util.Locale

private const val TAG = "MainActivity"

// --- App Colors ---
val DarkBackground = Color(0xFF1A1A1A)
val ActiveColor = Color(0xFF00AFFF) // A vibrant, light blue
val ListeningColor = Color(0xFF4CAF50) // A distinct green for listening
val InactiveColor = Color(0xFF4D4D4D)

class MainActivity : ComponentActivity() {
    private val viewModel: CameraViewModel by viewModels()
    private var cameraManager: CameraManager? = null
    private var speechRecognizer: SpeechRecognizer? = null

    // --- PERMISSION LAUNCHERS ---
    private val requestCameraPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            Log.d(TAG, "Camera Permission GRANTED. Initializing CameraManager.")
            initializeCameraManager()
        } else {
            Log.w(TAG, "Camera Permission DENIED.")
            Toast.makeText(this, "Camera permission is required.", Toast.LENGTH_LONG).show()
        }
    }

    private val requestAudioPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            Log.d(TAG, "RECORD_AUDIO Permission GRANTED. Launching speech recognizer.")
            startListening()
        } else {
            Log.w(TAG, "RECORD_AUDIO Permission DENIED.")
            Toast.makeText(this, "Microphone permission is required.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        checkAndRequestCameraPermission()

        setContent {
            LauzHackTheme {
                var isListening by remember { mutableStateOf(false) }

                // Initialize speech recognizer here, tied to the Composable lifecycle
                initializeSpeechRecognizer(onListeningStateChange = { isListening = it })

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = DarkBackground
                ) { innerPadding ->
                    CameraScreen(
                        modifier = Modifier.padding(innerPadding),
                        viewModel = viewModel,
                        isListening = isListening,
                        onLongPress = { handleLongPress() }
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer?.destroy()
    }

    // --- SPEECH-TO-TEXT LOGIC ---
    private fun initializeSpeechRecognizer(onListeningStateChange: (Boolean) -> Unit) {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Log.e(TAG, "Speech recognition is not available on this device.")
            return
        }
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    Log.d(TAG, "Mic is ready for speech.")
                    onListeningStateChange(true)
                }

                override fun onBeginningOfSpeech() {
                    Log.d(TAG, "User started speaking.")
                }

                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    Log.d(TAG, "User stopped speaking.")
                    onListeningStateChange(false)
                }

                override fun onError(error: Int) {
                    Log.e(TAG, "Speech recognizer error: $error")
                    onListeningStateChange(false)
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (matches != null && matches.isNotEmpty()) {
                        Log.d(TAG, "User said: ${matches[0]}")
                    }
                    onListeningStateChange(false)
                }

                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }
    }

    private fun handleLongPress() {
        Log.d(TAG, "Long press detected, starting speech-to-text process...")
        when (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)) {
            PackageManager.PERMISSION_GRANTED -> startListening()
            else -> requestAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startListening() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Listening...")
        }
        speechRecognizer?.startListening(intent)
    }

    // --- CAMERA LOGIC ---
    private fun checkAndRequestCameraPermission() {
        when (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)) {
            PackageManager.PERMISSION_GRANTED -> initializeCameraManager()
            else -> requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun initializeCameraManager() {
        if (cameraManager == null) {
            cameraManager = CameraManager(
                context = this,
                lifecycleOwner = this,
                onImageCaptured = viewModel::onImageCaptured
            ).also { viewModel.initializeCameraManager(it) }
        }
    }
}

// --- COMPOSABLES ---
@Composable
fun CameraScreen(modifier: Modifier = Modifier, viewModel: CameraViewModel, isListening: Boolean, onLongPress: () -> Unit) {
    val isCapturing by viewModel.isCapturing.collectAsState()

    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceAround
    ) {
        Text(
            text = "Pilot",
            fontSize = 48.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White.copy(alpha = 0.8f)
        )

        PulsatingCaptureButton(
            isCapturing = isCapturing,
            isListening = isListening,
            onTap = { viewModel.toggleCapture() },
            onLongPress = onLongPress
        )

        Spacer(modifier = Modifier.height(1.dp))
    }
}

@Composable
fun PulsatingCaptureButton(isCapturing: Boolean, isListening: Boolean, onTap: () -> Unit, onLongPress: () -> Unit) {
    val view = LocalView.current
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")

    val scale by if (isCapturing && !isListening) {
        infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 1.1f,
            animationSpec = infiniteRepeatable(tween(1000), repeatMode = RepeatMode.Reverse),
            label = "scale"
        )
    } else {
        remember { mutableFloatStateOf(1f) }
    }

    val color by animateColorAsState(
        targetValue = when {
            isListening -> ListeningColor
            isCapturing -> ActiveColor
            else -> InactiveColor
        },
        animationSpec = tween(500),
        label = "color"
    )

    val contentDesc = when {
        isListening -> "Listening for voice command. Double-tap to cancel."
        isCapturing -> "Capture button. Currently active. Double-tap to stop."
        else -> "Capture button. Currently inactive. Double-tap to start. Long-press for voice command."
    }

    Box(
        modifier = Modifier
            .size(200.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(color)
            .semantics { contentDescription = contentDesc }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onTap()
                    },
                    onLongPress = {
                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        onLongPress()
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (isListening) Icons.Filled.Mic else Icons.Filled.PowerSettingsNew,
            contentDescription = null, // Description is handled by the parent Box
            tint = DarkBackground,
            modifier = Modifier.size(90.dp)
        )
    }
}

// --- PREVIEWS ---
@Preview(showBackground = true, backgroundColor = 0xFF1A1A1A)
@Composable
fun CameraScreenPreview_Inactive() {
    LauzHackTheme {
        Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceAround) {
            Text("Pilot", fontSize = 48.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.8f))
            PulsatingCaptureButton(isCapturing = false, isListening = false, onTap = {}, onLongPress = {})
            Spacer(modifier = Modifier.height(1.dp))
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF1A1A1A)
@Composable
fun CameraScreenPreview_Active() {
    LauzHackTheme {
        Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceAround) {
            Text("Pilot", fontSize = 48.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.8f))
            PulsatingCaptureButton(isCapturing = true, isListening = false, onTap = {}, onLongPress = {})
            Spacer(modifier = Modifier.height(1.dp))
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF1A1A1A)
@Composable
fun CameraScreenPreview_Listening() {
    LauzHackTheme {
        Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceAround) {
            Text("Pilot", fontSize = 48.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.8f))
            PulsatingCaptureButton(isCapturing = false, isListening = true, onTap = {}, onLongPress = {})
            Spacer(modifier = Modifier.height(1.dp))
        }
    }
}
