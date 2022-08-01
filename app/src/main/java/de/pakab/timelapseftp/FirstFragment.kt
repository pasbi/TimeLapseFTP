package de.pakab.timelapseftp

import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.Surface.ROTATION_0
import android.view.View
import android.view.ViewGroup
import de.pakab.timelapseftp.databinding.FragmentFirstBinding
import android.widget.Toast
import android.widget.Toast.LENGTH_SHORT
import androidx.annotation.RequiresApi
import androidx.camera.core.*
import androidx.camera.core.ImageCapture.OnImageCapturedCallback
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPConnectionClosedException
import java.io.ByteArrayInputStream
import java.net.SocketException
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import java.nio.ByteBuffer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.IllegalStateException

/**
 * A simple [Fragment] subclass as the default destination in the navigation.
 */
class FirstFragment : Fragment() {

    private val TAG = "FirstFragment"
    private var _binding: FragmentFirstBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    private val serverAddress = ""
    private val userName = ""
    private val password = ""
    private val networkThread = HandlerThread("NetworkThread")
    private val ftpClient = FTPClient()

    private var imageCapture: ImageCapture? = null
    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var preview: Preview? = null
    private lateinit var cameraExecutor: ExecutorService

    @RequiresApi(Build.VERSION_CODES.O)
    private fun upload(byteArray: ByteArray) {
        if (!networkThread.isAlive) {
            networkThread.start()
        }
        val filename = DateTimeFormatter
            .ofPattern("yyyy-MM-dd_HH-mm-ss-SSSSSS")
            .withZone(ZoneOffset.UTC)
            .format(Instant.now()) + ".jpg"
        Handler(networkThread.looper).post {
            if (!ftpClient.isAvailable) {
                Log.i(TAG, "FTPClient is not available. Attempt to connect ...")
                ftpClient.connect(serverAddress)
                ftpClient.login(userName, password)
                ftpClient.setFileType(FTPClient.BINARY_FILE_TYPE)
            }
            if (ftpClient.isAvailable) {
                try {
                    val inputStream = ByteArrayInputStream(byteArray)
                    Log.i(TAG, "uploading $filename ...")
                    if (ftpClient.appendFile(filename, inputStream)) {
                        Log.i(TAG, "Successfully uploaded image '$filename'")
                    } else {
                        Log.e(TAG, "Failed to upload image '$filename'")
                    }
                } catch (e: FTPConnectionClosedException) {
                    Log.w(TAG, "FTP Connection closed: ${e.message}")
                } catch (e: SocketException) {
                    Log.w(TAG, "Socket Exception: ${e.message}")
                }
            } else {
                Log.w(TAG, "Failed to connect to ftp server: ${ftpClient.reply}")
                Toast.makeText(
                    context,
                    "Failed to connect to ftp server: ${ftpClient.reply}",
                    LENGTH_SHORT
                ).show()
            }
        }
    }

    override fun onCreateView(
            inflater: LayoutInflater, container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFirstBinding.inflate(inflater, container, false)
        return binding.root
    }

    private fun ByteBuffer.toByteArray(): ByteArray {
        rewind()    // Rewind the buffer to zero
        val data = ByteArray(remaining())
        get(data)   // Copy the buffer into a byte array
        return data // Return the byte array
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        cameraExecutor = Executors.newSingleThreadExecutor()
        binding.viewFinder.post {
            setUpCamera()
        }
        binding.buttonCapture.setOnClickListener {
            imageCapture?.let { imageCapture ->
                imageCapture.takePicture(cameraExecutor, object : OnImageCapturedCallback() {
                    @SuppressLint("UnsafeOptInUsageError")
                    @RequiresApi(Build.VERSION_CODES.O)
                    override fun onCaptureSuccess(image: ImageProxy) {
                        val planes = image.planes
                        if (planes.size != 1) {
                            Log.e(TAG, "Expected one plane but got ${planes.size}")
                            return
                        }
                        Log.i(TAG,"Captured image ${image.width}x${image.height}")
                        upload(planes[0].buffer.toByteArray().clone())
                        image.close()
                    }

                    override fun onError(exception: ImageCaptureException) {
                        Log.e(TAG, "Photo capture failed: ${exception.message}", exception)
                    }
                })
            }
        }
    }

    private fun setUpCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            bindCameraUseCases()
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun bindCameraUseCases() {
        val cameraProvider = cameraProvider ?: throw IllegalStateException("Camera initialization failed.")
        val cameraSelector = CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_BACK).build()
        preview = Preview.Builder()
            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .setTargetRotation(ROTATION_0)
            .build()

        imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .setTargetRotation(ROTATION_0)
            .build()

        cameraProvider.unbindAll()

        try {
            camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture)
            preview?.setSurfaceProvider(binding.viewFinder.surfaceProvider)
            observerCameraState(camera?.cameraInfo!!)
        } catch (exc: Exception){
            Log.e(TAG,"Use case binding failed", exc)
        }
    }

    private fun observerCameraState(cameraInfo: CameraInfo){
        cameraInfo.cameraState.observe(viewLifecycleOwner) { cameraState ->
            run {
                val text = when (cameraState.type) {
                    CameraState.Type.PENDING_OPEN ->  "CameraState: Pending Open"
                    CameraState.Type.OPENING -> "CameraState: Opening"
                    CameraState.Type.OPEN -> "CameraState: Open"
                    CameraState.Type.CLOSED ->  "CameraState: Closed"
                    CameraState.Type.CLOSING ->  "CameraState: Closing"
                    else -> ""
                }
                if (text.isNotEmpty()) {
                    Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
                }
            }
            cameraState.error?.let { error ->
                val text = when (error.code) {
                    // Open errors
                    CameraState.ERROR_STREAM_CONFIG -> "Stream config error"
                    CameraState.ERROR_CAMERA_IN_USE ->  "Camera in use"
                    CameraState.ERROR_MAX_CAMERAS_IN_USE ->  "Max cameras in use"
                    CameraState.ERROR_OTHER_RECOVERABLE_ERROR -> "Other recoverable error"
                    CameraState.ERROR_CAMERA_DISABLED -> "Camera disabled"
                    CameraState.ERROR_CAMERA_FATAL_ERROR -> "Fatal error"
                    CameraState.ERROR_DO_NOT_DISTURB_MODE_ENABLED -> "Do not disturb mode enabled"
                    else -> ""
                }
                if (text.isNotEmpty()) {
                    Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}