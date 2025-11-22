package com.example.lauzhack

import android.content.Context
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.nio.ByteBuffer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

private const val TAG = "CameraManager"

/**
 * Manages all camera-related operations, including setup and image capture.
 */
class CameraManager(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val onImageCaptured: (ByteArray) -> Unit
) {
    private var imageCapture: ImageCapture? = null
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    init {
        startCamera()
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
            imageCapture = ImageCapture.Builder().build()
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    imageCapture
                )
                Log.d(TAG, "CameraX successfully bound ImageCapture.")
            } catch (exc: Exception) {
                Log.e(TAG, "CameraX use case binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun takePicture() {
        val imageCapture = this.imageCapture ?: run {
            Log.e(TAG, "ImageCapture is not initialized.")
            return
        }

        Log.d(TAG, "Attempting to take picture...")

        imageCapture.takePicture(
            cameraExecutor,
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val bytes = image.planes[0].buffer.toByteArray()
                    image.close()
                    Log.i(TAG, "Picture taken successfully! Size: ${bytes.size} bytes.")
                    onImageCaptured(bytes)
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e(TAG, "Picture capture failed: ${exception.message}", exception)
                }
            }
        )
    }

    fun shutdown() {
        cameraExecutor.shutdown()
    }

    private fun ByteBuffer.toByteArray(): ByteArray {
        rewind()
        val data = ByteArray(remaining())
        get(data)
        return data
    }
}
