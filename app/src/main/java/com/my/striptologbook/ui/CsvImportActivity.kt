package com.my.striptologbook.ui

import android.app.AlertDialog
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.widget.Toolbar
import androidx.core.net.toUri
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.my.striptologbook.LogbookApplication
import com.my.striptologbook.R
import com.my.striptologbook.utils.FileManager
import com.my.striptologbook.utils.InsetsHelper
import com.my.striptologbook.utils.PreferencesManager
import com.my.striptologbook.utils.WorkDirectoryState
import kotlinx.coroutines.launch

import  android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts
import androidx.documentfile.provider.DocumentFile
import com.my.striptologbook.utils.VerifiedBackupFlow


class CsvImportActivity : BaseActivity() {

    private val TAG = "CsvImportLogActivity"

    private lateinit var viewModel: CsvImportViewModel
    private lateinit var adapter: CsvMappingAdapter
    private lateinit var tvFileName: TextView
    private lateinit var tvTotalRows: TextView
    private lateinit var rvMappings: RecyclerView
    private lateinit var btnImport: Button
    private lateinit var btnCancel: Button


    private var pendingAction: Boolean = false

    private val workDirPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        val action = pendingAction
        pendingAction = false
        if (uri == null || !action) {
            btnImport.isEnabled = true
            return@registerForActivityResult
        }
        if (!FileManager.persistWorkDirectoryPermission(this, uri)) {
            Toast.makeText(
                this,
                "Failed to persist permission. Please try again.",
                Toast.LENGTH_LONG
            ).show()
            btnImport.isEnabled = true
            return@registerForActivityResult
        }
        when (val state = FileManager.resolveWorkDirectory(this)) {
            is WorkDirectoryState.Ready -> doBackupThenImport(state.folder)
            else -> {
                Toast.makeText(this, "Work directory not available.", Toast.LENGTH_LONG).show()
                btnImport.isEnabled = true
            }
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_csv_import)

        // -------- Status bar spacer ------------------------------
        InsetsHelper.applyStatusBarSpacer(findViewById(R.id.status_bar_spacer))

        // -------- Toolbar -----------------------------------------
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = "CSV Import"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                viewModel.resetState()
                finish()
            }
        })

        // Initialize views
        tvFileName = findViewById(R.id.tv_file_name)
        tvTotalRows = findViewById(R.id.tv_total_rows)
        rvMappings = findViewById(R.id.rv_mappings)
        btnImport = findViewById(R.id.btn_import)
        btnCancel = findViewById(R.id.btn_cancel)

        // Setup RecyclerView
        rvMappings.layoutManager = LinearLayoutManager(this)
        adapter = CsvMappingAdapter(
            mappings = emptyList(),
            allFields = LegField.entries.toList(),
            onFieldSelected = { header, field ->
                viewModel.updateMapping(header, field)
            }
        )
        rvMappings.adapter = adapter

        // Get ViewModel
        val app = application as LogbookApplication
        val factory = CsvImportViewModelFactory(app.repository)
        viewModel = ViewModelProvider(this, factory)[CsvImportViewModel::class.java]

        // Observe state
        lifecycleScope.launch {
            viewModel.state.collect { state ->
                tvFileName.text = "File: ${state.fileName}"
                tvTotalRows.text = "Total rows: ${state.totalRows}"
                adapter.updateMappings(state.columnMappings.toList())
                btnImport.isEnabled = !state.isImporting &&
                        state.columnMappings.isNotEmpty() &&
                        state.validationError == null
                btnImport.text = if (state.isImporting) "Importing..." else "Import"
            }
        }

        // Observe import result
        lifecycleScope.launch {
            viewModel.state.collect { state ->
                when (val result = state.importResult) {
                    is ImportResult.Success -> {
                        AlertDialog.Builder(this@CsvImportActivity)
                            .setTitle("Import Complete")
                            .setMessage("All rows imported successfully!")
                            .setPositiveButton("OK") { _, _ ->
                                finish()
                            }
                            .setCancelable(false)
                            .show()
                    }

                    is ImportResult.Partial -> {
                        val summary = "Imported: ${result.imported}\n" +
                                "Skipped: ${result.skipped}\n" +
                                "Errors: ${result.errors}"

                        val details = if (result.skipDetails.isNotEmpty()) {
                            val sb = StringBuilder("\n\nSkipped Rows Details:\n")
                            sb.append("---------------------\n")

                            val entriesToShow = result.skipDetails.take(20)
                            entriesToShow.forEach { detail ->
                                sb.append("Row ${detail.rowIndex} (OB: ${detail.offBlock}, DEP: ${detail.departure}, ARR: ${detail.arrival})\n")
                                sb.append("  → ${detail.reason}\n")
                            }

                            if (result.skipDetails.size > 20) {
                                sb.append("\n... and ${result.skipDetails.size - 20} more skipped rows.\n")
                            }
                            sb.toString()
                        } else {
                            ""
                        }

                        AlertDialog.Builder(this@CsvImportActivity)
                            .setTitle("Import Summary")
                            .setMessage(summary + details)
                            .setPositiveButton("OK") { _, _ ->
                                viewModel.consumeResult()
                                finish()
                            }
                            .setCancelable(false)
                            .show()
                    }

                    is ImportResult.Error -> {
                        Toast.makeText(
                            this@CsvImportActivity,
                            result.message,
                            Toast.LENGTH_LONG
                        ).show()
                        viewModel.consumeResult()
                    }

                    else -> {}
                }
            }
        }

        val fileUriString = intent.getStringExtra("fileUri")
        if (fileUriString != null) {
            try {
                val uri = fileUriString.toUri()
                val inputStream = contentResolver.openInputStream(uri)
                if (inputStream != null) {
                    val fileName = uri.lastPathSegment ?: "imported.csv"
                    viewModel.loadCsvFile(inputStream, fileName)
                    Log.d(TAG, "File loaded from URI: $uri")
                } else {
                    Toast.makeText(this, "Cannot open file", Toast.LENGTH_LONG).show()
                    finish()
                }
            } catch (e: Exception) {
                Toast.makeText(this, "Error loading file: ${e.message}", Toast.LENGTH_LONG).show()
                Log.e(TAG, "Error loading file", e)
                finish()
            }
        } else {
            Toast.makeText(this, "No file URI", Toast.LENGTH_LONG).show()
            finish()
        }

        btnImport.setOnClickListener {
            Log.d(TAG, "Import button clicked")
            performBackupThenImport()
        }

        btnCancel.setOnClickListener {
            Log.d(TAG, "Cancel button clicked, resetting state")
            viewModel.resetState()
            finish()
        }
    }

    private fun performBackupThenImport() {
        btnImport.isEnabled = false
        AlertDialog.Builder(this)
            .setTitle("Confirm Import")
            .setMessage("Legs database will be altered. Continue?")
            .setPositiveButton("Continue") { _, _ ->
                ensureWorkDirectoryThen { folder -> doBackupThenImport(folder) }
            }
            .setNegativeButton("Cancel") { _, _ ->
                btnImport.isEnabled = true
            }
            .setOnCancelListener {
                btnImport.isEnabled = true
            }
            .show()
    }

    private fun ensureWorkDirectoryThen(onReady: (DocumentFile) -> Unit) {
        when (val state = FileManager.resolveWorkDirectory(this)) {
            WorkDirectoryState.NotSet -> {
                Toast.makeText(this, "Please select a work directory first", Toast.LENGTH_SHORT).show()
                pendingAction = true
                workDirPickerLauncher.launch(FileManager.workDirectoryPickerInitialUri(this))
            }
            WorkDirectoryState.Invalid -> {
                PreferencesManager.getInstance(this).setBackupFolderUri(null)
                Toast.makeText(
                    this,
                    "Work directory unavailable. Please select it again.",
                    Toast.LENGTH_LONG
                ).show()
                pendingAction = true
                workDirPickerLauncher.launch(FileManager.workDirectoryPickerInitialUri(this))
            }
            is WorkDirectoryState.Ready -> onReady(state.folder)
        }
    }

    private fun doBackupThenImport(folder: DocumentFile) {
        lifecycleScope.launch {
            val ok = VerifiedBackupFlow.backupThenProceed(
                activity = this@CsvImportActivity,
                scope = lifecycleScope,
                repository = (application as LogbookApplication).repository,
                folder = folder,
                contentResolver = contentResolver
            )
            if (ok) {
                viewModel.performImport()
            } else {
                btnImport.isEnabled = true
            }
        }
    }


}