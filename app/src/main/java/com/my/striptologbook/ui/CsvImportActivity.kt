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
import com.my.striptologbook.utils.BackupManager
import com.my.striptologbook.utils.FileManager
import com.my.striptologbook.utils.InsetsHelper
import com.my.striptologbook.utils.PreferencesManager
import com.my.striptologbook.utils.WorkDirectoryState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CsvImportActivity : BaseActivity() {

    private val TAG = "CsvImportLogActivity"

    private lateinit var viewModel: CsvImportViewModel
    private lateinit var adapter: CsvMappingAdapter
    private lateinit var tvFileName: TextView
    private lateinit var tvTotalRows: TextView
    private lateinit var rvMappings: RecyclerView
    private lateinit var btnImport: Button
    private lateinit var btnCancel: Button

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

        val prefs = PreferencesManager.getInstance(this)

        val folder = when (val state = FileManager.resolveWorkDirectory(this)) {
            WorkDirectoryState.NotSet -> {
                Toast.makeText(this, "Please set a backup folder first", Toast.LENGTH_LONG).show()
                btnImport.isEnabled = true
                return
            }

            WorkDirectoryState.Invalid -> {
                prefs.setBackupFolderUri(null)
                Toast.makeText(
                    this,
                    "Backup folder unavailable. Please set it again.",
                    Toast.LENGTH_LONG
                ).show()
                btnImport.isEnabled = true
                return
            }

            is WorkDirectoryState.Ready -> state.folder
        }

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
                        lifecycleScope.launch(Dispatchers.IO) {
                            BackupManager.cleanupOldBackups(folder)
                        }
                        viewModel.performImport()
                    }
                    is BackupManager.BackupResult.Failed -> {
                        Toast.makeText(
                            this@CsvImportActivity,
                            "Backup failed: ${result.reason}",
                            Toast.LENGTH_LONG
                        ).show()
                        btnImport.isEnabled = true
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Backup before import failed", e)
                Toast.makeText(
                    this@CsvImportActivity,
                    "Backup failed: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
                btnImport.isEnabled = true
            }
        }
    }
}