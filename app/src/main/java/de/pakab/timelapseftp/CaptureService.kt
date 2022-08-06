package de.pakab.timelapseftp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.Color
import android.os.Build
import android.os.Handler
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.camera.core.CameraInfo
import androidx.lifecycle.LifecycleService
import java.util.*
import java.util.concurrent.Executor

@RequiresApi(Build.VERSION_CODES.P)
class CaptureService : LifecycleService() {
    private val TAG = "CaptureService"
    private var ftpCamera: FTPCamera? = null
    private val timer = Timer()

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Service on create")

        val ONGOING_NOTIFICATION_ID = 1
        Log.i(TAG, "from capture service: $mainExecutor")
        val channelId = createNotificationChannel("captureChannel", "CCC")
        val notification = Notification.Builder(this, channelId)
            .setContentTitle("Capturing session")
            .setContentText("More details later")
            .build()
        startForeground(ONGOING_NOTIFICATION_ID, notification)

        val timerTask = object : TimerTask() {
            override fun run() {
                ftpCamera = object : FTPCamera(this@CaptureService, this@CaptureService) {
                    override fun onCameraStateChanged(cameraInfo: CameraInfo) {
                        Log.i(TAG, "on camera state changed.")
                        capture()
                    }

                    override fun onCaptureSuccessful() {
                        Log.i(TAG, "on capture successful")
                    }

                    override fun onCaptureError() {
                        Log.i(TAG, "on capture error.")
                    }
                }
            }
        }
        timer.schedule(timerTask, 0, 10L * 1000L)
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "Service on destroy")
        timer.cancel()
        timer.purge()
    }

    private fun createNotificationChannel(channelId: String, channelName: String): String {
        val channel = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_LOW)
        channel.lightColor = Color.BLUE
        channel.lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        val service = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        service.createNotificationChannel(channel)
        return channelId
    }
}