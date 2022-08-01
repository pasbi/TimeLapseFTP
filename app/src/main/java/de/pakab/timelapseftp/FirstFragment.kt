package de.pakab.timelapseftp

import android.Manifest
import android.content.Context.CAMERA_SERVICE
import android.content.pm.PackageManager
import android.graphics.ImageFormat.JPEG
import android.hardware.camera2.*
import android.hardware.camera2.CameraCharacteristics.*
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.media.*
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import de.pakab.timelapseftp.databinding.FragmentFirstBinding
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import android.widget.Toast.LENGTH_SHORT
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.os.bundleOf
import androidx.navigation.fragment.findNavController
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPConnectionClosedException
import java.io.ByteArrayInputStream
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * A simple [Fragment] subclass as the default destination in the navigation.
 */
class FirstFragment
    : Fragment()
    , ActivityCompat.OnRequestPermissionsResultCallback
    , AdapterView.OnItemSelectedListener {

    private val TAG = "FirstFragment"
    private var _binding: FragmentFirstBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!
    private var camId: String? = null
    private var cameraDevice: CameraDevice? = null
    private var imageReader: ImageReader? = null
    private var cameraCaptureSession: CameraCaptureSession? = null

    private val serverAddress = ""
    private val userName = ""
    private val password = ""
    private val networkThread = HandlerThread("NetworkThread")
    private val ftpClient = FTPClient()

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
            }
            if (ftpClient.isAvailable) {
                try {
                    var inputStream = ByteArrayInputStream(byteArray)
                    Log.i(TAG, "uploading $filename ...")
                    if (ftpClient.appendFile(filename, inputStream)) {
                        Log.i(TAG, "Successfully uploaded image '$filename'")
                    } else {
                        Log.e(TAG, "Failed to upload image '$filename'")
                    }
                } catch (e: FTPConnectionClosedException) {
                    Log.w(TAG, "FTP Connection closed.")
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

    private fun newImageReader() {
        imageReader?.close()
        imageReader = ImageReader.newInstance(1920, 1080, JPEG, 2)
        imageReader!!.setOnImageAvailableListener(imageAvailableListener, null)
    }

    inner class ImageAvailableListener : ImageReader.OnImageAvailableListener {
        @RequiresApi(Build.VERSION_CODES.O)
        override fun onImageAvailable(reader: ImageReader?) {
            val image = reader!!.acquireLatestImage()
            Log.i(TAG, "got image ${image.width}x${image.height}.")
            if (image.planes.size != 1) {
                Log.e(TAG, "got unexpected number of planes: ${image.planes.size}.")
                return
            } else {
                val buffer = image.planes[0].buffer
                var byteArray = ByteArray(buffer.capacity())
                buffer.get(byteArray)
                upload(byteArray)
            }
            image.close()
        }
    }

    private var imageAvailableListener = ImageAvailableListener()

    inner class CameraStateCallback : CameraDevice.StateCallback() {
        @RequiresApi(Build.VERSION_CODES.P)
        override fun onOpened(camera: CameraDevice) {
            Log.i(TAG, "Camera opened.")
            cameraDevice = camera
            newImageReader()
            val outputs = listOf(OutputConfiguration(imageReader!!.surface))
            val sessionConfiguration = SessionConfiguration(SessionConfiguration.SESSION_REGULAR, outputs, requireContext().mainExecutor, stateCallback)
            cameraDevice!!.createCaptureSession(sessionConfiguration)
        }

        override fun onDisconnected(camera: CameraDevice) {
            Log.i(TAG, "Camera disconnected.")
        }

        override fun onError(camera: CameraDevice, error: Int) {
            Log.i(TAG, "Camera error: $error")
        }
    }

    inner class CaptureCallback : CameraCaptureSession.CaptureCallback() {
        override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
        }
    }

    inner class StateCallback : CameraCaptureSession.StateCallback() {
        override fun onConfigured(session: CameraCaptureSession) {
            Log.i(TAG, "Session configured.")
            cameraCaptureSession = session
        }

        override fun onConfigureFailed(session: CameraCaptureSession) {
            Log.i(TAG, "Session configuration failed.")
        }
    }

    private val captureCallback = CaptureCallback()
    private val stateCallback = StateCallback()
    private var cameraStateCallback = CameraStateCallback()

    private val requestSinglePermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        isGranted: Boolean ->
        if (!isGranted) {
            Toast.makeText(context, "Missing permission to use camera.", LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(
            inflater: LayoutInflater, container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFirstBinding.inflate(inflater, container, false)
        return binding.root
    }

    private fun cameraLabels(): List<String> {
        val cameraManager = context?.getSystemService(CAMERA_SERVICE) as CameraManager
        return cameraManager.cameraIdList.map {
            val camcar = cameraManager.getCameraCharacteristics(it)
            val face = when(camcar.get(LENS_FACING) as Int) {
                LENS_FACING_FRONT -> "Front"
                LENS_FACING_BACK -> "Back"
                LENS_FACING_EXTERNAL -> "External"
                else -> "Unknown"
            }
            val focalLengths = camcar.get(LENS_INFO_AVAILABLE_FOCAL_LENGTHS) as FloatArray
            val focalLength = when (focalLengths.size) {
                0 -> "none"
                1 -> "${focalLengths.first()}"
                else -> "${focalLengths.first()}-${focalLengths.last()}"
            }

            "$face, f=$focalLength"
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, cameraLabels())
        binding.spinner.adapter = adapter
        binding.spinner.onItemSelectedListener = this
        binding.buttonLive.setOnClickListener {
            val bundle = bundleOf("camId" to camId)
            findNavController().navigate(R.id.action_FirstFragment_to_SecondFragment, bundle)
        }
        binding.buttonCapture.setOnClickListener {
            val captureRequestBuilder = cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
            captureRequestBuilder.addTarget(imageReader!!.surface)
            if (cameraCaptureSession == null) {
                Toast.makeText(context, "Missing camera capture session.", LENGTH_SHORT).show()
            } else {
                cameraCaptureSession!!.capture(captureRequestBuilder.build(), captureCallback, null)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
        val cameraManager = context?.getSystemService(CAMERA_SERVICE) as CameraManager
        camId = cameraManager.cameraIdList[id.toInt()]
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestSinglePermissionLauncher.launch(Manifest.permission.CAMERA)
        }
        openCamera()
    }

    override fun onNothingSelected(parent: AdapterView<*>?) {
        Toast.makeText(context, "nothing selected.", LENGTH_SHORT).show()
    }

    private fun openCamera() {
        cameraCaptureSession = null
        cameraDevice?.close()

        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            val cameraManager = context?.getSystemService(CAMERA_SERVICE) as CameraManager
            cameraManager.openCamera(camId!!, cameraStateCallback, null)
        } else {
            Toast.makeText(context, "Failed to open camera: Missing permission.", LENGTH_SHORT).show()
        }
    }
}