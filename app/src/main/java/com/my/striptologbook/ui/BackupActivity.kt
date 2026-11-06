package com.my.striptologbook.ui

import android.app.AlertDialog
import android.content.Intent
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
import com.my.striptologbook.data.CsvExportFormat
import com.my.striptologbook.data.CsvFormatType
import com.my.striptologbook.utils.BackupManager
import com.my.striptologbook.utils.FileManager
import com.my.striptologbook.utils.InsetsHelper
import com.my.striptologbook.utils.OpenFileIntentFactory
import com.my.striptologbook.utils.VerifiedBackupFlow
import com.my.striptologbook.utils.WorkDirGateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BackupActivity : BaseActivity() {

    private lateinit var viewModel: ExportImportViewModel
    private lateinit var txtWorkDirectory: TextView
    private lateinit var workDirGate: WorkDirGateFlow

    private companion object {
        private const val TAG = "BackupActivity"
    }

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
                lifecycleScope.launch {
                    val r = viewModel.restoreFromJson(contentResolver, uri)
                    toastResult(r)
                }
            }
        }
    }

    private val externalCsvLauncher = registerForActivityResult(
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
                startActivity(
                    Intent(this, CsvImportActivity::class.java).apply {
                        putExtra("fileUri", uri.toString())
                    }
                )
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

        txtWorkDirectory = findViewById(R.id.txt_work_directory)
        refreshWorkDirectoryDisplay()

        // ViewModel
        val app = application as LogbookApplication
        viewModel = ExportImportViewModel(app.repository)

        // Work-directory gate
        workDirGate = WorkDirGateFlow(this)

        // Buttons
        findViewById<Button>(R.id.btn_backup_json).setOnClickListener { performBackup() }
        findViewById<Button>(R.id.btn_restore_json).setOnClickListener { showRestoreConfirmDialog() }
        findViewById<Button>(R.id.btn_delete_all).setOnClickListener { showDeleteAllConfirmDialog() }
        findViewById<Button>(R.id.btn_export_csv).setOnClickListener {
            workDirGate.runOrPick { folder -> doExportCsv(folder) }
        }
        findViewById<Button>(R.id.btn_import_csv).setOnClickListener {
            workDirGate.runOrPick { folder -> doImportCsv(folder) }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshWorkDirectoryDisplay()
    }

    private fun refreshWorkDirectoryDisplay() {
        txtWorkDirectory.text = "Work directory: ${FileManager.workDirectoryDisplayName(this)}"
    }

    private fun toastResult(result: ExportImportResult) {
        when (result) {
            is ExportImportResult.Success -> Toast.makeText(
                this,
                "Operation completed successfully",
                Toast.LENGTH_SHORT
            ).show()
            is ExportImportResult.Error -> Toast.makeText(
                this,
                result.message,
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // ------------------------------------------------------------------------
    // Backup
    // ------------------------------------------------------------------------

    private fun performBackup() {
        workDirGate.runOrPick { folder -> doBackup(folder) }
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
                workDirGate.runOrPick { folder -> doBackupThenRestore(folder) }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun doBackupThenRestore(folder: DocumentFile) {
        lifecycleScope.launch {
            val ok = VerifiedBackupFlow.backupThenProceed(
                activity = this@BackupActivity,
                scope = lifecycleScope,
                repository = (application as LogbookApplication).repository,
                folder = folder,
                contentResolver = contentResolver
            )
            if (ok) doRestore(folder)
        }
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
                lifecycleScope.launch {
                    val r = viewModel.restoreFromJson(contentResolver, file.uri)
                    toastResult(r)
                }
            }
            .setNeutralButton("Browse elsewhere") { _, _ -> launchExternalRestorePicker() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun launchExternalRestorePicker() {
        openFileLauncher.launch(OpenFileIntentFactory.build("application/json"))
    }

    // ------------------------------------------------------------------------
    // Delete All
    // ------------------------------------------------------------------------

    private fun showDeleteAllConfirmDialog() {
        AlertDialog.Builder(this)
            .setTitle("Delete All Data")
            .setMessage("Are you sure you want to delete ALL logbook entries? This action cannot be undone!")
            .setPositiveButton("Delete All") { _, _ ->
                workDirGate.runOrPick { folder -> doBackupThenDeleteAll(folder) }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun doBackupThenDeleteAll(folder: DocumentFile) {
        lifecycleScope.launch {
            val ok = VerifiedBackupFlow.backupThenProceed(
                activity = this@BackupActivity,
                scope = lifecycleScope,
                repository = (application as LogbookApplication).repository,
                folder = folder,
                contentResolver = contentResolver
            )
            if (ok) {
                val r = viewModel.deleteAllLogbook()
                toastResult(r)
            }
        }
    }

    // ------------------------------------------------------------------------
    // CSV Export / Import (merged from ExportImportActivity)
    // ------------------------------------------------------------------------

    private fun doExportCsv(folder: DocumentFile) {
        lifecycleScope.launch {
            val legs = (application as LogbookApplication).repository.getAllLegsList()
            val r = viewModel.exportCsvLegList(
                contentResolver = contentResolver,
                folder = folder,
                legs = legs,
                config = CsvExportFormat(formatType = CsvFormatType.RAW),
                useFilteredName = false
            )
            toastResult(r)
        }
    }

    private fun doImportCsv(folder: DocumentFile) {
        val csvFiles = FileManager.listWorkDirFilesByExtension(folder, "csv")
        if (csvFiles.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("No CSV files found")
                .setMessage("The work directory contains no CSV files.")
                .setPositiveButton("Browse elsewhere") { _, _ -> launchExternalCsvPicker() }
                .setNegativeButton("Cancel", null)
                .show()
            return
        }
        val names = csvFiles.map { it.name ?: "(unnamed)" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Select CSV to import")
            .setItems(names) { _, which ->
                val file = csvFiles[which]
                startActivity(
                    Intent(this, CsvImportActivity::class.java).apply {
                        putExtra("fileUri", file.uri.toString())
                    }
                )
            }
            .setNeutralButton("Browse elsewhere") { _, _ -> launchExternalCsvPicker() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun launchExternalCsvPicker() {
        externalCsvLauncher.launch(OpenFileIntentFactory.build("*/*"))
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}