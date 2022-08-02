package de.pakab.timelapseftp

import android.os.*
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.Surface.ROTATION_0
import android.view.View
import android.view.ViewGroup
import de.pakab.timelapseftp.databinding.FragmentFirstBinding
import android.widget.Toast
import android.widget.Toast.LENGTH_SHORT
import androidx.camera.core.*
import androidx.camera.core.ImageCapture.OnImageCapturedCallback
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPConnectionClosedException
import java.io.ByteArrayInputStream
import java.net.SocketException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import java.io.IOException
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.IllegalStateException
import  java.util.Date

/**
 * A simple [Fragment] subclass as the default destination in the navigation.
 */
class FirstFragment : Fragment() {

    private var stopped = true
    private val TAG = "FirstFragment"
    private var _binding: FragmentFirstBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    private val serverAddress = ""
    private val userName = ""
    private val password = ""
    private val networkThread = HandlerThread("NetworkThread")
    private var ftpClient = FTPClient()

    private var imageCapture: ImageCapture? = null
    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var preview: Preview? = null
    private lateinit var cameraExecutor: ExecutorService
    private var lastCapture: Date? = null
    private val captureDelayS = 60L * 60L * 2L  // every 2h
    private var log = ""

    private fun logTextView(code: String, text: String) {
        log = "$code: $text\n$log"
        Handler(requireContext().mainLooper).post {
            binding.textView3.text = log
        }
    }

    private fun logi(text: String) {
        logTextView("I", text)
        Log.i(TAG, text)
    }

    private fun logw(text: String) {
        logTextView("W", text)
        Log.w(TAG, text)
    }
    private fun loge(text: String) {
        logTextView("E", text)
        Log.e(TAG, text)
    }

    private fun connect(): Boolean {
        logi("FTPClient is not available. Attempt to connect ...")
        ftpClient.connect(serverAddress)
        ftpClient.login(userName, password)
        ftpClient.setFileType(FTPClient.BINARY_FILE_TYPE)
        return if (ftpClient.isAvailable) {
            logi("Connection successful.")
            true
        } else {
            logw("Connection failed. Reply: ${ftpClient.reply}, Status: ${ftpClient.status}")
            false
        }
    }

    private fun upload(byteArray: ByteArray, attempt: Int = 0) {
        if (!networkThread.isAlive) {
            networkThread.start()
        }
        val maxAttempts = 5
        if (attempt > maxAttempts) {
            logi("Max number of attempts reached. Don't repeat upload.")
            return;
        } else if (attempt > 0) {
            logi("Upload (retry #$attempt/$maxAttempts)")
        }
        val filename = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss-SSSSSS").format(Date()) + ".jpg"
        Handler(networkThread.looper).post {
            if (ftpClient.isAvailable || connect()) {
                try {
                    val inputStream = ByteArrayInputStream(byteArray)
                    logi("uploading $filename ...")
                    if (ftpClient.appendFile(filename, inputStream)) {
                        logi("Successfully uploaded image '$filename'")
                    } else {
                        loge("Failed to upload image '$filename'")
                    }
                } catch (e: FTPConnectionClosedException) {
                    loge("FTP Connection closed: ${e.message}")
                    ftpClient = FTPClient()
                    upload(byteArray, attempt + 1)
                } catch (e: SocketException) {
                    loge("Socket Exception: ${e.message}")
                    ftpClient = FTPClient()
                    upload(byteArray, attempt + 1)
                } catch (e: IOException) {
                    loge("IO Exception: ${e.message}")
                    ftpClient = FTPClient()
                    upload(byteArray, attempt + 1)
                }
            } else {
                loge("Failed to connect to ftp server. Reply: ${ftpClient.reply}, Status: ${ftpClient.status}")
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
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        cameraExecutor = Executors.newSingleThreadExecutor()
        binding.viewFinder.post {
            setUpCamera()
        }
        binding.buttonCapture.setOnClickListener {
            capture()
        }
        binding.buttonStart.setOnClickListener {
            start()
        }
        binding.buttonStop.setOnClickListener {
            stop()
        }
    }

    private fun capture() {
        imageCapture?.let { imageCapture ->
            imageCapture.takePicture(cameraExecutor, object : OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val planes = image.planes
                    if (planes.size != 1) {
                        loge("Expected one plane but got ${planes.size}")
                        return
                    }
                    logi("Captured image ${image.width}x${image.height}")
                    upload(planes[0].buffer.toByteArray().clone())
                    image.close()
                }

                override fun onError(exception: ImageCaptureException) {
                    loge("Photo capture failed: ${exception.message}")
                }
            })
        }
    }

    private fun start() {
        stopped = false
        val captureHandler = Handler(Looper.getMainLooper())
        captureHandler.post(object : Runnable {
            override fun run() {
                if (!stopped) {
                    lastCapture = Date()
                    capture()
                    updateLabel()
                    captureHandler.postDelayed(this, captureDelayS * 1000L)
                }
            }
        })
    }

    private fun stop() {
        stopped = true
        updateLabel()
    }

    private fun updateLabel() {
        val dtf = SimpleDateFormat("HH:mm:ss")
        val lastCapture = lastCapture ?: throw java.lang.IllegalStateException()
        val lastCaptureS = dtf.format(lastCapture)
        val nextCaptureS = if (stopped) {
            "never"
        } else {
            var calendar = Calendar.getInstance()
            calendar.time = lastCapture
            calendar.add(Calendar.SECOND, captureDelayS.toInt())
            dtf.format(calendar.time)
        }
        binding.textView.text = "Last Capture: ${lastCaptureS}\nNext Capture: $nextCaptureS"
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
            loge("Use case binding failed")
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