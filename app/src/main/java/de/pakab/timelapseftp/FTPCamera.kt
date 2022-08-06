package de.pakab.timelapseftp

import android.content.Context
import android.util.Log
import android.view.Surface.ROTATION_0
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.nio.ByteBuffer
import java.util.concurrent.Executors

abstract class FTPCamera(context: Context, private val lifecycleOwner: LifecycleOwner) {

    protected abstract fun onCameraStateChanged(cameraInfo: CameraInfo)
    protected abstract fun onCaptureSuccessful()
    protected abstract fun onCaptureError()
    private val TAG = "FTPCamera"
    private var imageCapture: ImageCapture? = null
    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private val ftpClient = FTPUpload()

    private fun ByteBuffer.toByteArray(): ByteArray {
        rewind()    // Rewind the buffer to zero
        val data = ByteArray(remaining())
        get(data)   // Copy the buffer into a byte array
        return data // Return the byte array
    }

    fun capture() {
        Log.i(TAG, "Start capture: $imageCapture")
        imageCapture?.let { imageCapture ->
            imageCapture.takePicture(cameraExecutor, object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val planes = image.planes
                    if (planes.size != 1) {
                        Log.wtf(TAG, "Expected one plane but got ${planes.size}")
                        return
                    }
                    Log.i(TAG, "Captured image ${image.width}x${image.height}")
                    ftpClient.upload(planes[0].buffer.toByteArray().clone())
                    image.close()
                    onCaptureSuccessful()
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exception.message}")
                    onCaptureError()
                }
            })
        }
    }

    init {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            bindCameraUseCases()
        }, ContextCompat.getMainExecutor(context))
    }

    private fun bindCameraUseCases() {
        val cameraProvider = cameraProvider ?: throw IllegalStateException("Camera initialization failed.")
        val cameraSelector = CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_BACK).build()

        imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .setTargetRotation(ROTATION_0)
            .build()

        cameraProvider.unbindAll()

        try {
            camera = cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, imageCapture)
            onCameraStateChanged(camera!!.cameraInfo)
        } catch (exc: Exception){
            Log.wtf(TAG, "Use case binding failed: ", exc)
        }
    }
}