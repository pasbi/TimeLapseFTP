package de.pakab.timelapseftp

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.camera.core.CameraInfo
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.work.Worker
import androidx.work.WorkerParameters

class CaptureWorker(val context: Context, workerParameters: WorkerParameters) : Worker(context, workerParameters), LifecycleOwner {
    val TAG = "CaptureWorker"
    private val lifecycleRegistry = LifecycleRegistry(this)
    override fun getLifecycle(): Lifecycle {
        return lifecycleRegistry
    }

    override fun doWork(): Result {
        Log.i(TAG, "start capture from worker")
        val handler = Handler(Looper.getMainLooper())
        handler.post {
            lifecycleRegistry.currentState = Lifecycle.State.CREATED
            object : FTPCamera(context, this) {
                override fun onCameraStateChanged(cameraInfo: CameraInfo) {
                    capture()
                }

                override fun onCaptureSuccessful() {
                    Log.i(TAG, "Capture successful.")
//                    lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
                }

                override fun onCaptureError() {
//                    lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
                }
            }
            Log.i(TAG, "end capture from worker")
        }
        return Result.success()
    }
}
