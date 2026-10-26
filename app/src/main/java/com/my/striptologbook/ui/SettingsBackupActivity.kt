package com.my.striptologbook.ui

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.MenuItem
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.Toolbar
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import com.my.striptologbook.R
import com.my.striptologbook.utils.FileManager
import com.my.striptologbook.utils.InsetsHelper
import com.my.striptologbook.utils.PreferencesManager
import com.my.striptologbook.utils.SettingsBackupManager
import com.my.striptologbook.utils.WorkDirectoryState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsBackupActivity : BaseActivity() {

    private enum class PendingAction { SAVE, RESTORE }

    private lateinit var txtWorkDirectory: TextView
    private var pendingAction: PendingAction? = null

    private val workDirPickerLauncher = registerForActivityResult(
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
        refreshWorkDirectoryDisplay()
        when (val state = FileManager.resolveWorkDirectory(this)) {
            is WorkDirectoryState.Ready -> when (action) {
                PendingAction.SAVE -> doSave(state.folder)
                PendingAction.RESTORE -> doRestore(state.folder)
            }

            else -> Toast.makeText(this, "Work directory not available.", Toast.LENGTH_LONG).show()
        }
    }

    private val externalRestoreLauncher = registerForActivityResult(
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
                } catch (_: SecurityException) {
                }
                applyRestoreFromUri(uri)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings_backup)

        InsetsHelper.applyStatusBarSpacer(findViewById(R.id.status_bar_spacer))

        val scrollView = findViewById<ScrollView>(R.id.scroll_view)
        InsetsHelper.attachScrollToFocusedView(this, scrollView)
        InsetsHelper.applyImeBottomPaddingToRoot(this)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = "Backup / Restore Settings"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        txtWorkDirectory = findViewById(R.id.txt_work_directory)
        refreshWorkDirectoryDisplay()

        findViewById<Button>(R.id.btn_save_settings).setOnClickListener {
            ensureWorkDirectoryThen(PendingAction.SAVE) { folder -> doSave(folder) }
        }
        findViewById<Button>(R.id.btn_restore_settings).setOnClickListener {
            showRestoreConfirmDialog()
        }
    }

    override fun onResume() {
        super.onResume()
        refreshWorkDirectoryDisplay()
    }

    private fun refreshWorkDirectoryDisplay() {
        txtWorkDirectory.text = "Work directory: ${FileManager.workDirectoryDisplayName(this)}"
    }

    private fun ensureWorkDirectoryThen(action: PendingAction, onReady: (DocumentFile) -> Unit) {
        when (val state = FileManager.resolveWorkDirectory(this)) {
            WorkDirectoryState.NotSet -> {
                Toast.makeText(this, "Please select a work directory first", Toast.LENGTH_SHORT)
                    .show()
                pendingAction = action
                workDirPickerLauncher.launch(FileManager.workDirectoryPickerInitialUri(this))
            }

            WorkDirectoryState.Invalid -> {
                PreferencesManager.getInstance(this).setBackupFolderUri(null)
                Toast.makeText(
                    this,
                    "Work directory unavailable. Please select it again.",
                    Toast.LENGTH_LONG
                ).show()
                pendingAction = action
                workDirPickerLauncher.launch(FileManager.workDirectoryPickerInitialUri(this))
            }

            is WorkDirectoryState.Ready -> onReady(state.folder)
        }
    }

    private fun doSave(folder: DocumentFile) {
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                SettingsBackupManager.save(
                    context = this@SettingsBackupActivity,
                    folder = folder,
                    contentResolver = contentResolver
                )
            }
            when (result) {
                is SettingsBackupManager.SaveResult.Verified -> {
                    Toast.makeText(
                        this@SettingsBackupActivity,
                        "Settings saved: ${result.file.name}",
                        Toast.LENGTH_LONG
                    ).show()
                }

                is SettingsBackupManager.SaveResult.Failed -> {
                    Toast.makeText(
                        this@SettingsBackupActivity,
                        "Settings save failed: ${result.reason}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun showRestoreConfirmDialog() {
        AlertDialog.Builder(this)
            .setTitle("Confirm Restore")
            .setMessage("This will replace your current settings with the backup. Continue?")
            .setPositiveButton("Continue") { _, _ ->
                ensureWorkDirectoryThen(PendingAction.RESTORE) { folder -> doRestore(folder) }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun doRestore(folder: DocumentFile) {
        val prefsFiles = folder.listFiles()
            .filter {
                it.isFile &&
                        it.name?.startsWith(SettingsBackupManager.FILE_PREFIX) == true &&
                        it.name?.endsWith(SettingsBackupManager.FILE_SUFFIX) == true
            }
            .sortedByDescending { it.lastModified() }

        if (prefsFiles.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("No settings backups found")
                .setMessage("The work directory contains no settings backup files.")
                .setPositiveButton("Browse elsewhere") { _, _ -> launchExternalRestorePicker() }
                .setNegativeButton("Cancel", null)
                .show()
            return
        }

        val names = prefsFiles.map { it.name ?: "(unnamed)" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Select settings backup")
            .setItems(names) { _, which ->
                applyRestoreFromUri(prefsFiles[which].uri)
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
        externalRestoreLauncher.launch(intent)
    }

    private fun getDownloadsUri(): Uri? {
        return try {
            android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI
        } catch (_: Exception) {
            null
        }
    }

    private fun applyRestoreFromUri(uri: Uri) {
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                SettingsBackupManager.restore(
                    context = this@SettingsBackupActivity,
                    uri = uri,
                    contentResolver = contentResolver
                )
            }
            when (result) {
                is SettingsBackupManager.RestoreResult.Restored -> {
                    val warning = result.signatureWarning
                    val msg = if (warning == null) "Settings restored successfully"
                    else "Settings restored. $warning"
                    Toast.makeText(
                        this@SettingsBackupActivity,
                        msg,
                        if (warning == null) Toast.LENGTH_SHORT else Toast.LENGTH_LONG
                    ).show()
                }

                is SettingsBackupManager.RestoreResult.Failed -> {
                    Toast.makeText(
                        this@SettingsBackupActivity,
                        "Restore failed: ${result.reason}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}