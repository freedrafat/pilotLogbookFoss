package com.my.striptologbook.utils

import android.app.Activity
import android.app.AlertDialog
import android.content.ContentResolver
import androidx.documentfile.provider.DocumentFile
import com.my.striptologbook.repository.LegRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

object VerifiedBackupFlow {

    suspend fun backupThenProceed(
        activity: Activity,
        scope: CoroutineScope,
        repository: LegRepository,
        folder: DocumentFile,
        contentResolver: ContentResolver
    ): Boolean {
        val result = try {
            val legs = repository.getAllLegsList()
            val openingBalance = repository.getAllOpeningBalancesList()
            withContext(Dispatchers.IO) {
                BackupManager.createVerifiedBackup(
                    legs = legs,
                    openingBalance = openingBalance,
                    folder = folder,
                    contentResolver = contentResolver
                )
            }
        } catch (e: Exception) {
            BackupManager.BackupResult.Failed(e.message ?: "Unknown error")
        }

        return when (result) {
            is BackupManager.BackupResult.Verified -> {
                scope.launch(Dispatchers.IO) {
                    BackupManager.cleanupOldBackups(folder)
                }
                true
            }
            is BackupManager.BackupResult.Failed -> {
                showFailWarning(activity, result.reason)
            }
        }
    }

    private suspend fun showFailWarning(activity: Activity, reason: String): Boolean =
        suspendCancellableCoroutine { cont ->
            val dialog = AlertDialog.Builder(activity)
                .setTitle("Backup failed")
                .setMessage(
                    "Old database is not saved.\n\nReason: $reason\n\nContinue anyway?"
                )
                .setPositiveButton("Continue") { _, _ ->
                    if (cont.isActive) cont.resume(true)
                }
                .setNegativeButton("Cancel") { _, _ ->
                    if (cont.isActive) cont.resume(false)
                }
                .setOnCancelListener {
                    if (cont.isActive) cont.resume(false)
                }
                .create()
            cont.invokeOnCancellation { dialog.dismiss() }
            dialog.show()
        }
}