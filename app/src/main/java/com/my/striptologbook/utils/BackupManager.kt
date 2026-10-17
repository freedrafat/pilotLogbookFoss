package com.my.striptologbook.utils

import android.content.ContentResolver
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.my.striptologbook.data.Leg
import com.my.striptologbook.data.OpeningBalance

/**
 * Single place for backup + verification.
 * - createVerifiedBackup: writes JSON, reads it back, deserializes, compares counts.
 * - cleanupOldBackups: keeps only the 10 most recent logbook_backup_*.json files.
 *
 * Does NOT read the database, preferences, or show UI.
 */
object BackupManager {

    private const val TAG = "BackupManager"
    private const val MAX_BACKUPS_TO_KEEP = 10
    private const val BACKUP_PREFIX = "logbook_backup_"
    private const val BACKUP_SUFFIX = ".json"

    sealed class BackupResult {
        data class Verified(val file: DocumentFile) : BackupResult()
        data class Failed(val reason: String) : BackupResult()
    }

    fun createVerifiedBackup(
        legs: List<Leg>,
        openingBalance: List<OpeningBalance>,
        folder: DocumentFile,
        contentResolver: ContentResolver
    ): BackupResult {

        val fileName = FileManager.suggestBackupFileName()

        val file: DocumentFile = try {
            folder.createFile("application/json", fileName)
                ?: return BackupResult.Failed("Could not create backup file")
        } catch (e: Exception) {
            Log.e(TAG, "createVerifiedBackup: file creation failed", e)
            return BackupResult.Failed("Could not create backup file: ${e.message}")
        }

        val json: String = try {
            FileManager.serializeBackupToJson(legs, openingBalance)
        } catch (e: Exception) {
            Log.e(TAG, "createVerifiedBackup: serialization failed", e)
            safeDelete(file)
            return BackupResult.Failed("Serialization failed: ${e.message}")
        }

        try {
            FileManager.writeJsonToUri(contentResolver, file.uri, json)
        } catch (e: Exception) {
            Log.e(TAG, "createVerifiedBackup: write failed", e)
            safeDelete(file)
            return BackupResult.Failed("Write failed: ${e.message}")
        }

        val readBack: String = try {
            FileManager.readJsonFromUri(contentResolver, file.uri)
        } catch (e: Exception) {
            Log.e(TAG, "createVerifiedBackup: read-back failed", e)
            safeDelete(file)
            return BackupResult.Failed("Read-back failed: ${e.message}")
        }

        val restored: FileManager.BackupContent = try {
            FileManager.readBackupFromJson(readBack)
        } catch (e: Exception) {
            Log.e(TAG, "createVerifiedBackup: read-back is not valid JSON", e)
            safeDelete(file)
            return BackupResult.Failed("Read-back is not valid JSON: ${e.message}")
        }

        if (restored.legs.size != legs.size) {
            safeDelete(file)
            return BackupResult.Failed(
                "Leg count mismatch: wrote ${legs.size}, read ${restored.legs.size}"
            )
        }

        if (restored.openingBalance.size != openingBalance.size) {
            safeDelete(file)
            return BackupResult.Failed(
                "Opening balance count mismatch: wrote ${openingBalance.size}, " +
                        "read ${restored.openingBalance.size}"
            )
        }

        return BackupResult.Verified(file)
    }

    fun cleanupOldBackups(folder: DocumentFile) {
        try {
            val files = folder.listFiles()
            val backupFiles = files.filter { file ->
                file.isFile &&
                        file.name?.startsWith(BACKUP_PREFIX) == true &&
                        file.name?.endsWith(BACKUP_SUFFIX) == true
            }
            if (backupFiles.size <= MAX_BACKUPS_TO_KEEP) return

            val sorted = backupFiles.sortedByDescending { it.lastModified() }
            val toDelete = sorted.drop(MAX_BACKUPS_TO_KEEP)
            for (f in toDelete) {
                if (f.delete()) {
                    Log.d(TAG, "Deleted old backup: ${f.name}")
                } else {
                    Log.w(TAG, "Failed to delete old backup: ${f.name}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "cleanupOldBackups failed", e)
        }
    }

    private fun safeDelete(file: DocumentFile) {
        try {
            file.delete()
        } catch (e: Exception) {
            Log.w(TAG, "safeDelete failed: ${e.message}")
        }
    }
}