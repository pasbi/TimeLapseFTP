package de.pakab.timelapseftp

import android.Manifest
import android.content.Context.CAMERA_SERVICE
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics.*
import android.hardware.camera2.CameraDevice
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.fragment.findNavController
import de.pakab.timelapseftp.databinding.FragmentFirstBinding
import android.hardware.camera2.CameraManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.Toast
import android.widget.Toast.LENGTH_SHORT
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat

/**
 * A simple [Fragment] subclass as the default destination in the navigation.
 */
class FirstFragment
    : Fragment()
    , View.OnClickListener
    , ActivityCompat.OnRequestPermissionsResultCallback
    , AdapterView.OnItemSelectedListener {

    private var _binding: FragmentFirstBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!
    private var cameraManager: CameraManager? = null
    private var cameraDevice: CameraDevice? = null
    private var camId: String? = null

    inner class CameraStateCallback : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            cameraDevice = camera
            Toast.makeText(context, "Opened camera.", LENGTH_SHORT).show()
        }

        override fun onDisconnected(camera: CameraDevice) {
            Toast.makeText(context, "Disconnect camera.", LENGTH_SHORT).show()
        }

        override fun onError(camera: CameraDevice, error: Int) {
            Toast.makeText(context, "On Error: $error", LENGTH_SHORT).show()
        }
    }

    private var cameraStateCallback = CameraStateCallback()

    private val requestSinglePermissionLauncher =registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        isGranted: Boolean ->
        if (!isGranted) {
            Toast.makeText(context, "Missing permission to use camera.", LENGTH_SHORT).show()
        } else {
            openCamera()
        }
    }

    override fun onCreateView(
            inflater: LayoutInflater, container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {

        container?.setOnClickListener(this)

        _binding = FragmentFirstBinding.inflate(inflater, container, false)
        return binding.root

    }

    private fun cameraLabels(): List<String> {
        return cameraManager!!.cameraIdList.map {
            val camcar = cameraManager!!.getCameraCharacteristics(it)
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
        cameraManager = context?.getSystemService(CAMERA_SERVICE) as CameraManager
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, cameraLabels())
        binding.spinner.adapter = adapter
        binding.spinner.onItemSelectedListener = this
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onClick(p0: View?) {
    }

    private fun openCamera() {
        cameraDevice?.close()
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            cameraManager!!.openCamera(camId!!, cameraStateCallback, null)
        } else {
            Toast.makeText(context, "Failed to open camera: Missing permission.", LENGTH_SHORT).show()
        }
    }

    override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
        camId = cameraManager!!.cameraIdList[id.toInt()]
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestSinglePermissionLauncher.launch(Manifest.permission.CAMERA)
        } else {
            openCamera()
        }
    }

    override fun onNothingSelected(parent: AdapterView<*>?) {
        Toast.makeText(context, "nothing selected.", LENGTH_SHORT).show()
    }
}