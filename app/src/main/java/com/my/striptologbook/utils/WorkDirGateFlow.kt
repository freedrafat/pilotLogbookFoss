package com.my.striptologbook.utils

import android.net.Uri
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.documentfile.provider.DocumentFile

class WorkDirGateFlow(private val activity: ComponentActivity) {

    private var pendingResume: ((DocumentFile) -> Unit)? = null

    private val launcher = activity.registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        val action = pendingResume
        pendingResume = null
        if (uri == null || action == null) return@registerForActivityResult
        if (!FileManager.persistWorkDirectoryPermission(activity, uri)) {
            Toast.makeText(
                activity,
                "Failed to persist permission. Please try again.",
                Toast.LENGTH_LONG
            ).show()
            return@registerForActivityResult
        }
        when (val state = FileManager.resolveWorkDirectory(activity)) {
            is WorkDirectoryState.Ready -> action(state.folder)
            else -> Toast.makeText(
                activity,
                "Work directory not available.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    fun runOrPick(onReady: (DocumentFile) -> Unit) {
        when (val state = FileManager.resolveWorkDirectory(activity)) {
            WorkDirectoryState.NotSet -> {
                Toast.makeText(
                    activity,
                    "Please select a work directory first",
                    Toast.LENGTH_SHORT
                ).show()
                pendingResume = onReady
                launcher.launch(FileManager.workDirectoryPickerInitialUri(activity))
            }
            WorkDirectoryState.Invalid -> {
                PreferencesManager.getInstance(activity).setBackupFolderUri(null)
                Toast.makeText(
                    activity,
                    "Work directory unavailable. Please select it again.",
                    Toast.LENGTH_LONG
                ).show()
                pendingResume = onReady
                launcher.launch(FileManager.workDirectoryPickerInitialUri(activity))
            }
            is WorkDirectoryState.Ready -> onReady(state.folder)
        }
    }
}