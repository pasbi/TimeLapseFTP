package de.pakab.timelapseftp

import android.os.Handler
import android.os.HandlerThread
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPConnectionClosedException
import java.io.ByteArrayInputStream
import java.io.IOException
import java.net.SocketException
import java.text.SimpleDateFormat
import java.util.*

class FTPUpload(log: Log) {
    private val serverAddress = "ngcobalt364.manitu.net"
    private val userName = "ftp200019888"
    private val password = "yr9md2PwnfuHJ8kK"
    private val networkThread = HandlerThread("NetworkThread")
    private var ftpClient = FTPClient()
    private val log = log

    private fun connect(): Boolean {
        log.log("FTPClient is not available. Attempt to connect ...")
        ftpClient.connect(serverAddress)
        ftpClient.login(userName, password)
        ftpClient.setFileType(FTPClient.BINARY_FILE_TYPE)
        return if (ftpClient.isAvailable) {
            log.log("Connection successful.")
            true
        } else {
            log.log("Connection failed. Reply: ${ftpClient.reply}, Status: ${ftpClient.status}")
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
                    log.log("uploading $filename ...")
                    if (ftpClient.storeFile(filename, inputStream)) {
                        log.log("Successfully uploaded image '$filename'")
                    } else {
                        log.log("Failed to upload image '$filename'")
                    }

                    log.logStatus()
                    if (ftpClient.storeFile("log.txt", log.log().byteInputStream(Charsets.UTF_8))) {
                        log.log("Successfully uploaded log.")
                    } else {
                        log.log("Failed to upload image '$filename'")
                    }
                } catch (e: FTPConnectionClosedException) {
                    log.log("FTP Connection closed: ${e.message}")
                } catch (e: SocketException) {
                    log.log("Socket Exception: ${e.message}")
                } catch (e: IOException) {
                    log.log("IO Exception: ${e.message}")
                }
            } else {
                log.log("Failed to connect to ftp server. Reply: ${ftpClient.reply}, Status: ${ftpClient.status}")
            }
            ftpClient.logout()
            ftpClient.disconnect()
        }
    }

}


