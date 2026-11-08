package com.my.striptologbook.utils

import android.app.AlertDialog
import android.net.Uri
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.documentfile.provider.DocumentFile

class WorkFolderGateFlow(private val activity: ComponentActivity) {

    enum class FailureReason {
        CANCELLED,
        PERSIST_FAILED,
        RESOLVE_FAILED
    }

    private data class PendingCall(
        val onReady: (DocumentFile) -> Unit,
        val onError: (FailureReason) -> Unit
    )

    private var pending: PendingCall? = null

    private val launcher = activity.registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        val call = pending
        pending = null
        if (call == null) return@registerForActivityResult
        if (uri == null) {
            call.onError(FailureReason.CANCELLED)
            return@registerForActivityResult
        }
        if (!FileManager.persistWorkFolderPermission(activity, uri)) {
            Toast.makeText(
                activity,
                "Failed to persist permission. Please try again.",
                Toast.LENGTH_LONG
            ).show()
            call.onError(FailureReason.PERSIST_FAILED)
            return@registerForActivityResult
        }
        when (val state = FileManager.resolveWorkFolder(activity)) {
            is WorkFolderState.Ready -> call.onReady(state.folder)
            else -> {
                Toast.makeText(
                    activity,
                    "Work folder not available.",
                    Toast.LENGTH_LONG
                ).show()
                call.onError(FailureReason.RESOLVE_FAILED)
            }
        }
    }

    fun runOrPick(
        onError: (FailureReason) -> Unit = {},
        onReady: (DocumentFile) -> Unit
    ) {
        when (val state = FileManager.resolveWorkFolder(activity)) {
            WorkFolderState.NotSet -> {
                Toast.makeText(
                    activity,
                    "Please select a work folder first",
                    Toast.LENGTH_SHORT
                ).show()
                showIntroDialog {
                    pending = PendingCall(onReady, onError)
                    launcher.launch(FileManager.workFolderPickerInitialUri(activity))
                }
            }
            WorkFolderState.Invalid -> {
                Toast.makeText(
                    activity,
                    "Work folder unavailable. Please select it again.",
                    Toast.LENGTH_LONG
                ).show()
                showIntroDialog {
                    pending = PendingCall(onReady, onError)
                    launcher.launch(FileManager.workFolderPickerInitialUri(activity))
                }
            }
            is WorkFolderState.Ready -> onReady(state.folder)
        }
    }

    fun pickFolder(
        onError: (FailureReason) -> Unit = {},
        onReady: (DocumentFile) -> Unit
    ) {
        showIntroDialog {
            pending = PendingCall(onReady, onError)
            launcher.launch(FileManager.workFolderPickerInitialUri(activity))
        }
    }

    private fun showIntroDialog(onContinue: () -> Unit) {
        AlertDialog.Builder(activity)
            .setMessage(
                "A folder named PilotLogbookFoss will be created inside " +
                        "the folder you choose. This folder is required for " +
                        "backups, exports and imports."
            )
            .setPositiveButton("Continue") { _, _ -> onContinue() }
            .show()
    }
}