package com.example.lauzhack

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import com.example.lauzhack.ui.theme.LauzHackTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

private const val TAG = "MainActivity"

class MainActivity : ComponentActivity() {
    // Internal CameraX properties
    internal var imageCapture: ImageCapture? = null
    internal lateinit var cameraExecutor: ExecutorService

    // State property for Compose UI to track the latest image
    var latestImageBytes by mutableStateOf<ByteArray?>(null)

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                Log.d(TAG, "Camera Permission GRANTED. Starting camera setup.")
                startCamera()
            } else {
                Log.d(TAG, "Camera Permission DENIED")
                Toast.makeText(this, "Camera permission is required to take pictures.", Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cameraExecutor = Executors.newSingleThreadExecutor()
        enableEdgeToEdge()

        checkAndRequestCameraPermission()

        setContent {
            LauzHackTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    ScreenWithButton(
                        modifier = Modifier.padding(innerPadding),
                        activity = this
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    // --- Camera Setup & Permissions ---

    internal fun checkAndRequestCameraPermission() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                Log.d(TAG, "Camera Permission already granted. Starting camera setup.")
                startCamera()
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            imageCapture = ImageCapture.Builder().build()
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()

                cameraProvider.bindToLifecycle(
                    this as LifecycleOwner,
                    cameraSelector,
                    imageCapture
                )
                Log.d(TAG, "CameraX successfully bound ImageCapture to back camera.")

            } catch(exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
                Toast.makeText(this, "Camera setup failed: ${exc.message}", Toast.LENGTH_LONG).show()
            }

        }, ContextCompat.getMainExecutor(this))
    }

    // --- Image Data Receiver ---

    fun onPictureTaken(jpegData: ByteArray) {
        Log.i(TAG, "Picture received in new function! Size: ${jpegData.size} bytes. Updating UI.")
        latestImageBytes = jpegData // Update state to trigger Composable recomposition
    }
}

// --- Image Capture Extension Function ---

fun MainActivity.takePictureAndLog() {
    val imageCapture = this.imageCapture ?: run {
        Log.e(TAG, "ImageCapture is not initialized. Did startCamera run?")
        return
    }

    Log.w(TAG, "Attempting to take picture...")

    imageCapture.takePicture(
        this.cameraExecutor,
        object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                val bytes = try {
                    val buffer = image.planes[0].buffer
                    val byteArray = ByteArray(buffer.remaining())
                    buffer.get(byteArray)
                    byteArray
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing captured image.", e)
                    return
                } finally {
                    image.close()
                }

                Log.i(TAG, "A picture was taken successfully! ${bytes.size} bytes.")
                onPictureTaken(bytes)
            }

            override fun onError(exception: ImageCaptureException) {
                Log.e(TAG, "Picture capture failed: ${exception.message}", exception)
            }
        }
    )
}

// --- Composable UI Functions ---

@Composable
fun ScreenWithButton(modifier: Modifier = Modifier, activity: MainActivity) {
    val context = LocalContext.current
    // State is read directly from MainActivity property
    val latestImage = activity.latestImageBytes
    var isCapturing by remember { mutableStateOf(false) }
    var captureJob by remember { mutableStateOf<Job?>(null) }

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // --- Button to Start/Stop Capture ---
        Button(
            onClick = {
                Log.d(TAG, "Big Button clicked!")

                if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(context, "Camera permission not granted. Cannot start capture.", Toast.LENGTH_LONG).show()
                    activity.checkAndRequestCameraPermission()
                    return@Button
                }

                if (isCapturing) {
                    captureJob?.cancel()
                    captureJob = null
                    isCapturing = false
                    Log.i(TAG, "Picture capture loop STOPPED.")
                    Toast.makeText(context, "Capture stopped.", Toast.LENGTH_SHORT).show()
                } else {
                    isCapturing = true
                    Log.i(TAG, "Picture capture loop STARTED (every 5s).")
                    Toast.makeText(context, "Capture started.", Toast.LENGTH_SHORT).show()

                    captureJob = activity.lifecycleScope.launch {
                        while (isCapturing) {
                            activity.takePictureAndLog()
                            delay(5000L)
                        }
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .padding(24.dp)
                .size(width = 300.dp, height = 100.dp)
        ) {
            Text(
                text = if (isCapturing) "STOP CAPTURE" else "START CAPTURE (5s)",
                fontSize = 28.sp
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // --- Image Display Area ---
        latestImage?.let { bytes ->
            val bitmap = remember(bytes) {
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Latest Captured Image",
                modifier = Modifier
                    .size(200.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )
        } ?: Text("No image captured yet.")
    }

}

@Preview(showBackground = true)
@Composable
fun ScreenWithButtonPreview() {
    LauzHackTheme {
        ScreenWithButton(activity = MainActivity())
    }
}