package com.example.lauzhack

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.lauzhack.ui.theme.LauzHackTheme

private const val TAG = "MainActivity"

class MainActivity : ComponentActivity() {
    private val viewModel: CameraViewModel by viewModels()
    private var cameraManager: CameraManager? = null

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                Log.d(TAG, "Camera Permission GRANTED. Initializing CameraManager.")
                initializeCameraManager()
            } else {
                Log.w(TAG, "Camera Permission DENIED.")
                Toast.makeText(this, "Camera permission is required.", Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        checkAndRequestCameraPermission()

        setContent {
            LauzHackTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    CameraScreen(
                        modifier = Modifier.padding(innerPadding),
                        viewModel = viewModel
                    )
                }
            }
        }
    }

    private fun checkAndRequestCameraPermission() {
        when (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)) {
            PackageManager.PERMISSION_GRANTED -> {
                Log.d(TAG, "Permission already granted. Initializing CameraManager.")
                initializeCameraManager()
            }
            else -> {
                Log.d(TAG, "Permission not granted. Launching request.")
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    private fun initializeCameraManager() {
        if (cameraManager == null) {
            cameraManager = CameraManager(
                context = this,
                lifecycleOwner = this,
                onImageCaptured = viewModel::onImageCaptured
            ).also {
                viewModel.initializeCameraManager(it)
                Log.d(TAG, "CameraManager initialized and passed to ViewModel.")
            }
        }
    }
}

@Composable
fun CameraScreen(modifier: Modifier = Modifier, viewModel: CameraViewModel) {
    val isCapturing by viewModel.isCapturing.collectAsState()

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Button(
            onClick = { viewModel.toggleCapture() },
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
    }
}

@Preview(showBackground = true)
@Composable
fun CameraScreenPreview() {
    LauzHackTheme {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Button(onClick = {}, modifier = Modifier.size(width = 300.dp, height = 100.dp)) {
                Text("START CAPTURE (5s)", fontSize = 28.sp)
            }
        }
    }
}
