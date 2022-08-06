package de.pakab.timelapseftp

import android.content.Intent
import android.os.*
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import de.pakab.timelapseftp.databinding.FragmentFirstBinding
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.camera.core.*
import java.text.SimpleDateFormat
import java.util.*
import  java.util.Date

/**
 * A simple [Fragment] subclass as the default destination in the navigation.
 */
class FirstFragment : Fragment() {

    private var captureIntent: Intent? = null
    private val TAG = "FirstFragment"
    private var stopped = true
    private var _binding: FragmentFirstBinding? = null
    private val captureHandler = Handler(Looper.getMainLooper())
    private var ftpCamera: FTPCamera? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!
    private var lastCapture: Date? = null
    private val captureDelayS = 60L * 30L // every 30 minutes

    override fun onCreateView(
            inflater: LayoutInflater, container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFirstBinding.inflate(inflater, container, false)
        return binding.root
    }


    @RequiresApi(Build.VERSION_CODES.P)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        ftpCamera = object : FTPCamera(requireContext(), viewLifecycleOwner) {
            override fun onCameraStateChanged(cameraInfo: CameraInfo) {
                cameraInfo.cameraState.observe(viewLifecycleOwner) { cameraState ->
                    run {
                        val text = when (cameraState.type) {
                            CameraState.Type.PENDING_OPEN -> "CameraState: Pending Open"
                            CameraState.Type.OPENING -> "CameraState: Opening"
                            CameraState.Type.OPEN -> "CameraState: Open"
                            CameraState.Type.CLOSED -> "CameraState: Closed"
                            CameraState.Type.CLOSING -> "CameraState: Closing"
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
                            CameraState.ERROR_CAMERA_IN_USE -> "Camera in use"
                            CameraState.ERROR_MAX_CAMERAS_IN_USE -> "Max cameras in use"
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

            override fun onCaptureSuccessful() {
                lastCapture = Date()
                updateLabel()
            }

            override fun onCaptureError() {
            }
        }
        binding.buttonCapture.setOnClickListener {
            ftpCamera!!.capture()
        }
        binding.buttonStart.setOnClickListener {
            start()
        }
        binding.buttonStop.setOnClickListener {
            stop()
        }
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun start() {
        val context = requireContext()
        if (captureIntent != null) {
            stop()
        }
        captureIntent = Intent(context, CaptureService::class.java)
        context.startForegroundService(captureIntent)
    }

    private fun stop() {
        stopped = true
        requireContext().stopService(captureIntent)
        updateLabel()
    }

    private fun updateLabel() {
        val dtf = SimpleDateFormat("HH:mm:ss")
        val lastCaptureS = if (lastCapture == null) { "never" } else { dtf.format(lastCapture) }
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}