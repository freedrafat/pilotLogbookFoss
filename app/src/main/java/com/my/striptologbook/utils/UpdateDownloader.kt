package com.my.striptologbook.utils

import android.app.DownloadManager
import android.content.Context
import android.os.Environment
import androidx.core.net.toUri

/**
 * Hands the APK download to the system DownloadManager.
 *
 * The system owns the transfer, the notification, and the
 * destination file. The app does not poll, does not observe
 * completion, and does not construct an install intent.
 */
object UpdateDownloader {

    /**
     * Enqueues an APK download into MediaStore Downloads via the
     * system DownloadManager.
     *
     * Returns the download id assigned by the system, or null if
     * the request could not be enqueued (SecurityException,
     * IllegalStateException, or any exception during request
     * building).
     */
    fun enqueue(context: Context, apkUrl: String, apkFileName: String): Long? {
        return try {
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val request = DownloadManager.Request(apkUrl.toUri()).apply {
                setTitle("Logbook update")
                setDescription(apkFileName)
                setMimeType("application/vnd.android.package-archive")
                setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                )
                setDestinationInExternalPublicDir(
                    Environment.DIRECTORY_DOWNLOADS,
                    apkFileName
                )
            }
            dm.enqueue(request)
        } catch (_: Exception) {
            null
        }
    }
}