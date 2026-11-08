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
import com.my.striptologbook.utils.SettingsBackupManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import com.my.striptologbook.utils.OpenFileIntentFactory
import com.my.striptologbook.utils.WorkFolderGateFlow

class SettingsBackupActivity : BaseActivity() {

    private lateinit var txtWorkFolder: TextView

    private lateinit var workDirGate: WorkFolderGateFlow

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

        txtWorkFolder = findViewById(R.id.txt_work_folder)
        refreshWorkFolderDisplay()
        workDirGate = WorkFolderGateFlow(this)

        findViewById<Button>(R.id.btn_save_settings).setOnClickListener {
            workDirGate.runOrPick { folder -> doSave(folder) }
        }
        findViewById<Button>(R.id.btn_restore_settings).setOnClickListener {
            showRestoreConfirmDialog()
        }
    }

    override fun onResume() {
        super.onResume()
        refreshWorkFolderDisplay()
    }

    private fun refreshWorkFolderDisplay() {
        txtWorkFolder.text = "Work folder: ${FileManager.workFolderDisplayName(this)}"
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
                workDirGate.runOrPick { folder -> doRestore(folder) }
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
                .setMessage("The work folder contains no settings backup files.")
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
        externalRestoreLauncher.launch(OpenFileIntentFactory.build("application/json"))
    }

//    private fun getDownloadsUri(): Uri? {
//        return try {
//            android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI
//        } catch (_: Exception) {
//            null
//        }
//    }

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