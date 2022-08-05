package de.pakab.timelapseftp

import android.content.Context
   import android.view.Surface.ROTATION_0
   import androidx.camera.core.*
   import androidx.camera.lifecycle.ProcessCameraProvider
   import androidx.core.content.ContextCompat
   import androidx.lifecycle.LifecycleOwner
   import java.nio.ByteBuffer
   import java.util.concurrent.Executors

abstract class FTPCamera(context: Context, lifecycleOwner: LifecycleOwner, log: Log) {

    protected abstract fun onCameraStateChanged(cameraInfo: CameraInfo)
    private var imageCapture: ImageCapture? = null
    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private val ftpClient = FTPUpload(log)
    private val context = context
    private val lifecycleOwner = lifecycleOwner
    private val log = log

    private fun ByteBuffer.toByteArray(): ByteArray {
        rewind()    // Rewind the buffer to zero
        val data = ByteArray(remaining())
        get(data)   // Copy the buffer into a byte array
        return data // Return the byte array
    }

    fun capture() {
        imageCapture?.let { imageCapture ->
            imageCapture.takePicture(cameraExecutor, object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val planes = image.planes
                    if (planes.size != 1) {
                        log.log("Expected one plane but got ${planes.size}")
                        return
                    }
                    log.log("Captured image ${image.width}x${image.height}")
                    ftpClient.upload(planes[0].buffer.toByteArray().clone())
                    image.close()
                }

                override fun onError(exception: ImageCaptureException) {
                    log.log("Photo capture failed: ${exception.message}")
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
            log.log("Use case binding failed")
        }
    }
}