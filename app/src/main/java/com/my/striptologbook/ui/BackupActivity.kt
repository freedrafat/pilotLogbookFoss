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
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.Toolbar
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import com.my.striptologbook.LogbookApplication
import com.my.striptologbook.R
import com.my.striptologbook.utils.PreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.core.net.toUri

import com.my.striptologbook.utils.BackupManager
import com.my.striptologbook.utils.InsetsHelper

class BackupActivity : BaseActivity() {

    private lateinit var viewModel: ExportImportViewModel
    private lateinit var txtFolderPath: TextView

    private companion object {
        private const val TAG = "BackupActivity"
        private const val MAX_BACKUPS_TO_KEEP = 10
    }

    // Folder picker
    private val folderPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                PreferencesManager.getInstance(this).setBackupFolderUri(uri.toString())
                Toast.makeText(this, "Backup folder set successfully", Toast.LENGTH_SHORT).show()
                updateFolderPathDisplay()
            } catch (_: SecurityException) {
                Toast.makeText(this, "Failed to persist permission. Please try again.", Toast.LENGTH_LONG).show()
            }
        }
    }

    // Restore launcher (using StartActivityForResult to use extras)
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

        txtFolderPath = findViewById(R.id.txt_backup_folder_path)
        updateFolderPathDisplay()

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
                    else -> {}
                }
            }
        }

        // Buttons
        findViewById<Button>(R.id.btn_backup_json).setOnClickListener { performBackup() }
        findViewById<Button>(R.id.btn_change_backup_folder).setOnClickListener { folderPickerLauncher.launch(null) }
        findViewById<Button>(R.id.btn_restore).setOnClickListener { showRestoreConfirmDialog() }
        findViewById<Button>(R.id.btn_delete_all).setOnClickListener { showDeleteAllConfirmDialog() }
    }

    private fun updateFolderPathDisplay() {
        val folderUri = PreferencesManager.getInstance(this).getBackupFolderUri()
        txtFolderPath.text = "Backup folder: ${getDisplayPath(folderUri)}"
    }

    private fun getDisplayPath(uriString: String?): String {
        if (uriString == null) return "Not set"
        return try {
            val uri = uriString.toUri()
            val lastSegment = uri.lastPathSegment ?: "Unknown"
            lastSegment.replace("primary:", "").replace(":", "/")
        } catch (_: Exception) {
            "Selected folder"
        }
    }

    private fun performBackup() {
        val prefs = PreferencesManager.getInstance(this)
        val folderUriString = prefs.getBackupFolderUri()

        if (folderUriString == null) {
            Toast.makeText(this, "Please select a backup folder first", Toast.LENGTH_SHORT).show()
            folderPickerLauncher.launch(null)
            return
        }

        lifecycleScope.launch {
            try {
                val folderUri = folderUriString.toUri()
                val folder = DocumentFile.fromTreeUri(this@BackupActivity, folderUri)
                if (folder == null || !folder.exists() || !folder.isDirectory) {
                    prefs.setBackupFolderUri(null)
                    updateFolderPathDisplay()
                    Toast.makeText(
                        this@BackupActivity,
                        "Backup folder unavailable. Please select it again.",
                        Toast.LENGTH_LONG
                    ).show()
                    folderPickerLauncher.launch(null)
                    return@launch
                }

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
                        // Non-blocking cleanup
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

    private fun showRestoreConfirmDialog() {
        AlertDialog.Builder(this)
            .setTitle("Confirm Restore")
            .setMessage("This will replace ALL existing data with the backup. Continue?")
            .setPositiveButton("Continue") { _, _ ->
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "application/json"
                    putExtra("android.intent.extra.INITIAL_URI", getDownloadsUri())
                }
                openFileLauncher.launch(intent)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun getDownloadsUri(): Uri? {
        return try {
            android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI
        } catch (_: Exception) {
            null
        }
    }

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