package de.pakab.timelapseftp

import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPConnectionClosedException
import java.io.ByteArrayInputStream
import java.io.IOException
import java.net.SocketException
import java.text.SimpleDateFormat
import java.util.*

class FTPUpload() {
    private val TAG = "FTPUpload"
    private val serverAddress = "ngcobalt364.manitu.net"
    private val userName = "ftp200019888"
    private val password = "yr9md2PwnfuHJ8kK"
    private val networkThread = HandlerThread("NetworkThread")
    private var ftpClient = FTPClient()

    private fun connect(): Boolean {
        Log.i(TAG, "FTPClient is not available. Attempt to connect ...")
        ftpClient.connect(serverAddress)
        ftpClient.login(userName, password)
        ftpClient.setFileType(FTPClient.BINARY_FILE_TYPE)
        return if (ftpClient.isAvailable) {
            Log.i(TAG, "Connection successful.")
            true
        } else {
            Log.e(TAG, "Connection failed. Reply: ${ftpClient.reply}, Status: ${ftpClient.status}")
            false
        }
    }

    fun upload(image: ByteArray) {
        if (!networkThread.isAlive) {
            networkThread.start()
        }
        val filename = "deleteme-" + SimpleDateFormat("yyyy-MM-dd_HH-mm-ss-SSSSSS").format(Date()) + ".jpg"
        Handler(networkThread.looper).post {
            if (connect()) {
                try {
                    val inputStream = ByteArrayInputStream(image)
                    Log.i(TAG,"uploading $filename ...")
                    if (ftpClient.storeFile(filename, inputStream)) {
                        Log.i(TAG, "Successfully uploaded image '$filename'")
                    } else {
                        Log.e(TAG, "Failed to upload image '$filename'")
                    }

//                    logStatus()
//                    if (ftpClient.storeFile("log.txt", log.log().byteInputStream(Charsets.UTF_8))) {
//                        log.log("Successfully uploaded log.")
//                    } else {
//                        log.log("Failed to upload image '$filename'")
//                    }
                } catch (e: FTPConnectionClosedException) {
                    Log.e(TAG, "FTP Connection closed: ${e.message}")
                } catch (e: SocketException) {
                    Log.e(TAG, "Socket Exception: ${e.message}")
                } catch (e: IOException) {
                    Log.e(TAG, "IO Exception: ${e.message}")
                }
            } else {
                Log.e(TAG, "Failed to connect to ftp server. Reply: ${ftpClient.reply}, Status: ${ftpClient.status}")
            }
            ftpClient.logout()
            ftpClient.disconnect()
        }
    }

}


