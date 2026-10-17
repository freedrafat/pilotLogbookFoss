package com.my.striptologbook.utils

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import androidx.core.net.toUri

object UpdateDownloader {

    private const val TAG = "UpdateDownloader"


    /**
     * Starts the download without showing the confirmation dialog.
     * Used by UpdatesActivity when the user has already accepted the
     * "Update Available" prompt on Home.
     *
     * Mirrors the install-permission check that promptUpdate performs
     * before showing its dialog, so an auto-triggered download does not
     * skip that safeguard.
     */
    fun startDownload(context: Context, apkUrl: String, apkFileName: String) {
        val autoInstall = PreferencesManager.getInstance(context).isAutoInstallEnabled()
        if (autoInstall && !context.packageManager.canRequestPackageInstalls()) {
            showInstallPermissionDialog(context)
            return
        }
        downloadApk(context, apkUrl, apkFileName, autoInstall)
    }


    /**
     * Start the download with a progress dialog.
     */
    @SuppressLint("SetTextI18n")
    private fun downloadApk(context: Context, apkUrl: String, apkFileName: String, autoInstall: Boolean) {
        // Show progress dialog with a TextView for progress and a Cancel button
        val progressView = TextView(context).apply {
            text = "Connecting..."
            textSize = 16f
            setPadding(40, 20, 40, 20)
        }
        val progressDialog = AlertDialog.Builder(context)
            .setTitle("Downloading update")
            .setView(progressView)
            .setCancelable(false)
            .setNegativeButton("Cancel") { _, _ ->
                // Cancel flag will be checked in coroutine
            }
            .show()

        val isCancelled = AtomicBoolean(false)
        progressDialog.setButton(AlertDialog.BUTTON_NEGATIVE, "Cancel") { _, _ ->
            isCancelled.set(true)
            progressDialog.dismiss()
        }

        GlobalScope.launch(Dispatchers.IO) {
            try {
                // Use UnsafeHttpClient
                val client = UnsafeHttpClient.getUnsafeOkHttpClient()
                val request = Request.Builder().url(apkUrl).build()
                val response = client.newCall(request).execute()

                if (!response.isSuccessful) {
                    withContext(Dispatchers.Main) {
                        progressDialog.dismiss()
                        Toast.makeText(context, "Download failed: HTTP ${response.code}", Toast.LENGTH_LONG).show()
                    }
                    return@launch
                }

                val body = response.body
                val contentLength = body.contentLength()
                val inputStream = body.byteStream()

                // Decide destination
                val destinationFile = if (autoInstall) {
                    // Save to cache
                    val cacheDir = File(context.cacheDir, "apk")
                    if (!cacheDir.exists()) cacheDir.mkdirs()
                    File(cacheDir, apkFileName)
                } else {
                    null // we'll handle manually later
                }

                if (autoInstall) {
                    // ---- Auto-install: write to cache ----
                    destinationFile?.let { file ->
                        FileOutputStream(file).use { output ->
                            val buffer = ByteArray(8192)
                            var bytesRead: Int
                            var totalRead = 0L
                            var lastProgress = -1
                            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                                if (isCancelled.get()) {
                                    file.delete()
                                    throw CancellationException("Download cancelled")
                                }
                                totalRead += bytesRead
                                output.write(buffer, 0, bytesRead)
                                if (contentLength > 0) {
                                    val progress = ((totalRead * 100) / contentLength).toInt()
                                    if (progress != lastProgress) {
                                        lastProgress = progress
                                        withContext(Dispatchers.Main) {
                                            progressView.text = "Downloading... $progress%"
                                        }
                                    }
                                } else {
                                    // Unknown size: update every 1 MB
                                    if (totalRead % (1024 * 1024) == 0L) {
                                        withContext(Dispatchers.Main) {
                                            progressView.text = "Downloaded: ${formatSize(totalRead)}"
                                        }
                                    }
                                }
                            }
                            // Final update
                            withContext(Dispatchers.Main) {
                                if (contentLength > 0) progressView.text = "Download complete"
                                else progressView.text = "Downloaded: ${formatSize(totalRead)}"
                            }
                        }
                        // Success: install
                        withContext(Dispatchers.Main) {
                            progressDialog.dismiss()
                            installApk(context, file)
                        }
                    }
                } else {
                    // ---- Manual: save via MediaStore ----
                    val contentValues = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, apkFileName)
                        put(MediaStore.MediaColumns.MIME_TYPE, "application/vnd.android.package-archive")
                        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    }
                    val uri = context.contentResolver.insert(
                        MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                        contentValues
                    ) ?: throw IOException("Failed to create MediaStore entry")

                    context.contentResolver.openOutputStream(uri)?.use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        var totalRead = 0L
                        var lastProgress = -1
                        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                            if (isCancelled.get()) {
                                context.contentResolver.delete(uri, null, null)
                                throw CancellationException("Download cancelled")
                            }
                            totalRead += bytesRead
                            output.write(buffer, 0, bytesRead)
                            if (contentLength > 0) {
                                val progress = ((totalRead * 100) / contentLength).toInt()
                                if (progress != lastProgress) {
                                    lastProgress = progress
                                    withContext(Dispatchers.Main) {
                                        progressView.text = "Downloading... $progress%"
                                    }
                                }
                            } else {
                                if (totalRead % (1024 * 1024) == 0L) {
                                    withContext(Dispatchers.Main) {
                                        progressView.text = "Downloaded: ${formatSize(totalRead)}"
                                    }
                                }
                            }
                        }
                        withContext(Dispatchers.Main) {
                            if (contentLength > 0) progressView.text = "Download complete"
                            else progressView.text = "Downloaded: ${formatSize(totalRead)}"
                        }
                    }

                    // Success: open Downloads folder
                    withContext(Dispatchers.Main) {
                        progressDialog.dismiss()
                        Toast.makeText(context, "Download completed. File saved in Downloads folder.", Toast.LENGTH_LONG).show()
                        val intent = Intent(DownloadManager.ACTION_VIEW_DOWNLOADS)
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Download error", e)
                withContext(Dispatchers.Main) {
                    progressDialog.dismiss()
                    Toast.makeText(context, "Download failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun installApk(context: Context, apkFile: File) {
        if (!context.packageManager.canRequestPackageInstalls()) {
            showInstallPermissionDialog(context)
            return
        }
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    private fun showInstallPermissionDialog(context: Context) {
        AlertDialog.Builder(context)
            .setTitle("Install permission needed")
            .setMessage("To auto-install updates, please allow \"Install unknown apps\" for this app in system settings.\n\nAfter granting permission, try the update again.")
            .setPositiveButton("Settings") { _, _ ->
                val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                intent.data = "package:${context.packageName}".toUri()
                context.startActivity(intent)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun formatSize(bytes: Long): String {
        return when {
            bytes >= 1024 * 1024 -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
            bytes >= 1024 -> String.format(Locale.US, "%.0f KB", bytes / 1024.0)
            else -> "$bytes B"
        }
    }

}