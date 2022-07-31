package de.pakab.timelapseftp

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Camera
import android.hardware.camera2.*
import android.hardware.camera2.CameraDevice.TEMPLATE_PREVIEW
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.hardware.camera2.params.SessionConfiguration.SESSION_REGULAR
import android.os.Build
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import de.pakab.timelapseftp.databinding.FragmentSecondBinding

/**
 * A simple [Fragment] subclass as the second destination in the navigation.
 */
class SecondFragment : Fragment() {

    private var _binding: FragmentSecondBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!
    private var cameraDevice: CameraDevice? = null

    inner class CaptureCallback : CameraCaptureSession.CaptureCallback() {
        override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
        }
    }

    inner class StateCallback : CameraCaptureSession.StateCallback() {
        override fun onConfigured(session: CameraCaptureSession) {
            val captureRequestBuilder = cameraDevice!!.createCaptureRequest(TEMPLATE_PREVIEW)
            captureRequestBuilder.addTarget(binding.surfaceView.holder.surface)
            session.setRepeatingRequest(captureRequestBuilder.build(), captureCallback, null)
        }

        override fun onConfigureFailed(session: CameraCaptureSession) {
            Toast.makeText(context, "onConfiguredFailed.", Toast.LENGTH_SHORT).show()
        }

    }

    private val captureCallback = CaptureCallback()
    private val stateCallback = StateCallback()

    inner class CameraStateCallback : CameraDevice.StateCallback() {
        @RequiresApi(Build.VERSION_CODES.P)
        override fun onOpened(camera: CameraDevice) {
            cameraDevice = camera
            val outputs = listOf(OutputConfiguration(binding.surfaceView.holder.surface))
            var sessionConfiguration = SessionConfiguration(SESSION_REGULAR, outputs, requireContext().mainExecutor, stateCallback)
            cameraDevice!!.createCaptureSession(sessionConfiguration)
            Toast.makeText(context, "Opened camera.", Toast.LENGTH_SHORT).show()
        }

        override fun onDisconnected(camera: CameraDevice) {
            Toast.makeText(context, "Disconnect camera.", Toast.LENGTH_SHORT).show()
        }

        override fun onError(camera: CameraDevice, error: Int) {
            Toast.makeText(context, "On Error: $error", Toast.LENGTH_SHORT).show()
        }
    }

    private var cameraStateCallback = CameraStateCallback()

    override fun onCreateView(
            inflater: LayoutInflater, container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {

        _binding = FragmentSecondBinding.inflate(inflater, container, false)
        return binding.root

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        openCamera()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        cameraDevice?.close()
    }

    private fun openCamera() {
        val cameraManager = context?.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        cameraDevice?.close()
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            val camId = arguments?.getString("camId")
            cameraManager.openCamera(camId!!, cameraStateCallback, null)
        } else {
            Toast.makeText(context, "Failed to open camera: Missing permission.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}