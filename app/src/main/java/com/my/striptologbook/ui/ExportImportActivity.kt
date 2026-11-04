package com.my.striptologbook.ui

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.MenuItem
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.Toolbar
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import com.my.striptologbook.LogbookApplication
import com.my.striptologbook.R
import com.my.striptologbook.utils.FileManager
import com.my.striptologbook.utils.InsetsHelper
import com.my.striptologbook.utils.PreferencesManager
import com.my.striptologbook.utils.WorkDirectoryState
import com.my.striptologbook.utils.ExportRangeDialogHelper
import kotlinx.coroutines.launch
import java.io.File


class ExportImportActivity : BaseActivity() {

    private enum class PendingAction { EXPORT_CSV, EXPORT_PDF, IMPORT_CSV }

    private lateinit var viewModel: ExportImportViewModel
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
            is WorkDirectoryState.Ready -> dispatch(action, state.folder)
            else -> Toast.makeText(this, "Work directory not available.", Toast.LENGTH_LONG).show()
        }
    }

    private val externalCsvLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) {
            }
            startActivity(
                Intent(this, CsvImportActivity::class.java).apply {
                    putExtra("fileUri", uri.toString())
                }
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_export_import)

        // One-time cleanup of the old CSV import temp file
        File(filesDir, "temp_import.csv").delete()

        // -------- Status bar spacer ------------------------------
        InsetsHelper.applyStatusBarSpacer(findViewById(R.id.status_bar_spacer))

        // -------- Toolbar -----------------------------------------
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = "Export / Import"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        txtWorkDirectory = findViewById(R.id.txt_work_directory)
        refreshWorkDirectoryDisplay()

        // ViewModel
        val app = application as LogbookApplication
        viewModel = ExportImportViewModel(app.repository)

        // Observe result
        lifecycleScope.launch {
            viewModel.result.collect { result ->
                when (result) {
                    is ExportImportResult.Success -> {
                        Toast.makeText(
                            this@ExportImportActivity,
                            "Operation completed successfully",
                            Toast.LENGTH_SHORT
                        ).show()
                        viewModel.consumeResult()
                    }
                    is ExportImportResult.Error -> {
                        Toast.makeText(
                            this@ExportImportActivity,
                            result.message,
                            Toast.LENGTH_LONG
                        ).show()
                        viewModel.consumeResult()
                    }
                    null -> {}
                }
            }
        }

        findViewById<Button>(R.id.btn_export_csv).setOnClickListener {
            ensureWorkDirectoryThen(PendingAction.EXPORT_CSV) { folder -> doExportCsv(folder) }
        }
        findViewById<Button>(R.id.btn_import_csv).setOnClickListener {
            ensureWorkDirectoryThen(PendingAction.IMPORT_CSV) { folder -> doImportCsv(folder) }
        }
        findViewById<Button>(R.id.btn_export_pdf).setOnClickListener {
            ensureWorkDirectoryThen(PendingAction.EXPORT_PDF) { folder -> doExportPdf(folder) }
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

    private fun dispatch(action: PendingAction, folder: DocumentFile) {
        when (action) {
            PendingAction.EXPORT_CSV -> doExportCsv(folder)
            PendingAction.EXPORT_PDF -> doExportPdf(folder)
            PendingAction.IMPORT_CSV -> doImportCsv(folder)
        }
    }

    private fun doExportCsv(folder: DocumentFile) {
        viewModel.exportToCsv(contentResolver, folder)
    }

    private fun doExportPdf(folder: DocumentFile) {
        ExportRangeDialogHelper.show(this) { range, rows, owner, sig, cfg ->
            viewModel.exportPdfToFolder(
                contentResolver = contentResolver,
                folder = folder,
                range = range,
                rowsPerPage = rows,
                ownerName = owner,
                signature = sig,
                config = cfg
            )
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

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun launchExternalCsvPicker() {
        externalCsvLauncher.launch(arrayOf("*/*"))
    }

}