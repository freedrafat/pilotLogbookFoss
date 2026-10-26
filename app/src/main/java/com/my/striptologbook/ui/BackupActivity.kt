package com.my.striptologbook.ui

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.ScrollView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.Toolbar
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import com.my.striptologbook.LogbookApplication
import com.my.striptologbook.R
import com.my.striptologbook.utils.BackupManager
import com.my.striptologbook.utils.FileManager
import com.my.striptologbook.utils.InsetsHelper
import com.my.striptologbook.utils.PreferencesManager
import com.my.striptologbook.utils.WorkDirectoryState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BackupActivity : BaseActivity() {

    private enum class PendingAction { BACKUP, RESTORE }

    private lateinit var viewModel: ExportImportViewModel

    private var pendingAction: PendingAction? = null

    private companion object {
        private const val TAG = "BackupActivity"
    }

    // Work-directory picker (Backup and Restore)
    private val folderPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        val action = pendingAction
        pendingAction = null
        if (uri == null || action == null) return@registerForActivityResult
        if (!FileManager.persistWorkDirectoryPermission(this, uri)) {
            Toast.makeText(
                this,
                "Failed to persist permission. Please try again.",
                Toast.LENGTH_LONG
            ).show()
            return@registerForActivityResult
        }
        when (val state = FileManager.resolveWorkDirectory(this)) {
            is WorkDirectoryState.Ready -> when (action) {
                PendingAction.BACKUP -> doBackup(state.folder)
                PendingAction.RESTORE -> doRestore(state.folder)
            }

            else -> Toast.makeText(this, "Work directory not available.", Toast.LENGTH_LONG).show()
        }
    }

    // External file picker — restore from anywhere
    private val openFileLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val uri = result.data?.data
            if (uri != null) {
                try {
                    contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: SecurityException) { }
                viewModel.restoreFromJson(contentResolver, uri)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_backup)

        // -------- Toolbar ------------
        val spacer = findViewById<View>(R.id.status_bar_spacer)
        InsetsHelper.applyStatusBarSpacer(spacer)

        val scrollView = findViewById<ScrollView>(R.id.scroll_view)
        InsetsHelper.attachScrollToFocusedView(this, scrollView)
        InsetsHelper.applyImeBottomPaddingToRoot(this)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = "Backup"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        // -------- Toolbar ------------

        // ViewModel
        val app = application as LogbookApplication
        viewModel = ExportImportViewModel(app.repository)

        // Observe result
        lifecycleScope.launch {
            viewModel.result.collect { result ->
                when (result) {
                    is ExportImportResult.Success -> {
                        Toast.makeText(this@BackupActivity, "Operation completed successfully", Toast.LENGTH_SHORT).show()
                        viewModel.consumeResult()
                    }
                    is ExportImportResult.Error -> {
                        Toast.makeText(this@BackupActivity, result.message, Toast.LENGTH_LONG).show()
                        viewModel.consumeResult()
                    }
                    null -> {}
                }
            }
        }

        // Buttons
        findViewById<Button>(R.id.btn_backup_json).setOnClickListener { performBackup() }
        findViewById<Button>(R.id.btn_restore).setOnClickListener { showRestoreConfirmDialog() }
        findViewById<Button>(R.id.btn_delete_all).setOnClickListener { showDeleteAllConfirmDialog() }
    }

    // ------------------------------------------------------------------------
    // Work-directory gate (shared by Backup and Restore)
    // ------------------------------------------------------------------------

    private fun ensureWorkDirectoryThen(action: PendingAction, onReady: (DocumentFile) -> Unit) {
        when (val state = FileManager.resolveWorkDirectory(this)) {
            WorkDirectoryState.NotSet -> {
                Toast.makeText(this, "Please select a work directory first", Toast.LENGTH_SHORT)
                    .show()
                pendingAction = action
                folderPickerLauncher.launch(FileManager.workDirectoryPickerInitialUri(this))
            }

            WorkDirectoryState.Invalid -> {
                PreferencesManager.getInstance(this).setBackupFolderUri(null)
                Toast.makeText(
                    this,
                    "Work directory unavailable. Please select it again.",
                    Toast.LENGTH_LONG
                ).show()
                pendingAction = action
                folderPickerLauncher.launch(FileManager.workDirectoryPickerInitialUri(this))
            }

            is WorkDirectoryState.Ready -> onReady(state.folder)
        }
    }

    // ------------------------------------------------------------------------
    // Backup
    // ------------------------------------------------------------------------

    private fun performBackup() {
        ensureWorkDirectoryThen(PendingAction.BACKUP) { folder -> doBackup(folder) }
    }

    private fun doBackup(folder: DocumentFile) {
        lifecycleScope.launch {
            try {
                val repository = (application as LogbookApplication).repository
                val legs = repository.getAllLegsList()
                val openingBalance = repository.getAllOpeningBalancesList()

                val result = withContext(Dispatchers.IO) {
                    BackupManager.createVerifiedBackup(
                        legs = legs,
                        openingBalance = openingBalance,
                        folder = folder,
                        contentResolver = contentResolver
                    )
                }

                when (result) {
                    is BackupManager.BackupResult.Verified -> {
                        Toast.makeText(
                            this@BackupActivity,
                            "Backup completed successfully",
                            Toast.LENGTH_SHORT
                        ).show()
                        lifecycleScope.launch(Dispatchers.IO) {
                            BackupManager.cleanupOldBackups(folder)
                        }
                    }
                    is BackupManager.BackupResult.Failed -> {
                        Toast.makeText(
                            this@BackupActivity,
                            "Backup failed: ${result.reason}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Backup error", e)
                Toast.makeText(
                    this@BackupActivity,
                    "Error: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    // ------------------------------------------------------------------------
    // Restore
    // ------------------------------------------------------------------------

    private fun showRestoreConfirmDialog() {
        AlertDialog.Builder(this)
            .setTitle("Confirm Restore")
            .setMessage("This will replace ALL existing data with the backup. Continue?")
            .setPositiveButton("Continue") { _, _ ->
                ensureWorkDirectoryThen(PendingAction.RESTORE) { folder -> doRestore(folder) }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun doRestore(folder: DocumentFile) {
        val jsonFiles = FileManager.listWorkDirFilesByExtension(folder, "json")
        if (jsonFiles.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("No backups found")
                .setMessage("The work directory contains no JSON backups.")
                .setPositiveButton("Browse elsewhere") { _, _ -> launchExternalRestorePicker() }
                .setNegativeButton("Cancel", null)
                .show()
            return
        }

        val names = jsonFiles.map { it.name ?: "(unnamed)" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Select backup to restore")
            .setItems(names) { _, which ->
                val file = jsonFiles[which]
                try {
                    contentResolver.takePersistableUriPermission(
                        file.uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: SecurityException) {
                }
                viewModel.restoreFromJson(contentResolver, file.uri)
            }
            .setNeutralButton("Browse elsewhere") { _, _ -> launchExternalRestorePicker() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun launchExternalRestorePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
            putExtra("android.intent.extra.INITIAL_URI", getDownloadsUri())
        }
        openFileLauncher.launch(intent)
    }

    private fun getDownloadsUri(): Uri? {
        return try {
            android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI
        } catch (_: Exception) {
            null
        }
    }

    // ------------------------------------------------------------------------
    // Delete All
    // ------------------------------------------------------------------------

    private fun showDeleteAllConfirmDialog() {
        AlertDialog.Builder(this)
            .setTitle("Delete All Data")
            .setMessage("Are you sure you want to delete ALL logbook entries? This action cannot be undone!")
            .setPositiveButton("Delete All") { _, _ ->
                viewModel.deleteAllLegs()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}