package com.my.striptologbook.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.my.striptologbook.LogbookApplication
import com.my.striptologbook.R
import com.my.striptologbook.utils.FileManager
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException

import android.app.AlertDialog
import android.content.Context
import android.widget.RadioButton
import com.my.striptologbook.utils.PreferencesManager
import com.my.striptologbook.utils.showCustomDatePicker
import com.my.striptologbook.utils.getDateAtMidnight
import java.text.SimpleDateFormat
import java.util.*

import android.graphics.BitmapFactory
import android.widget.EditText
import com.my.striptologbook.utils.InsetsHelper

class ExportImportActivity : BaseActivity() {

    private lateinit var viewModel: ExportImportViewModel

    private val exportCsvLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.exportToCsv(contentResolver, uri)
        }
    }

    private val importCsvLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val content = contentResolver.openInputStream(uri)?.use { inputStream ->
                    val tempFile = File(filesDir, "temp_import.csv")
                    tempFile.outputStream().use { output ->
                        inputStream.copyTo(output)
                    }
                    tempFile.absolutePath
                } ?: throw IOException("Failed to read file")

                val intent = Intent(this, CsvImportActivity::class.java).apply {
                    putExtra("filePath", content)
                }
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "Failed to read file: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }


    private var pendingPdfBytes: ByteArray? = null

    private val createPdfLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri: Uri? ->
        val bytes = pendingPdfBytes
        pendingPdfBytes = null
        if (uri != null && bytes != null) {
            try {
                contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                Toast.makeText(this, "PDF exported", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, "Failed to write PDF: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_export_import)

        // -------- Status bar spacer ------------------------------
        InsetsHelper.applyStatusBarSpacer(findViewById(R.id.status_bar_spacer))

        // -------- Toolbar -----------------------------------------
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = "Export / Import"              //change title
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // ViewModel
        val app = application as LogbookApplication
        viewModel = ExportImportViewModel(app.repository)

        // Observe result – single collector handles ALL cases
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
                    is ExportImportResult.PdfReady -> {
                        pendingPdfBytes = result.bytes
                        createPdfLauncher.launch(result.suggestedName)
                        viewModel.consumeResult()
                    }
                    null -> { /* nothing to do */ }
                }
            }
        }

        findViewById<Button>(R.id.btn_export_csv).setOnClickListener {
            exportCsvLauncher.launch(FileManager.suggestExportFileName())
        }

        findViewById<Button>(R.id.btn_import_csv).setOnClickListener {
            importCsvLauncher.launch(arrayOf("*/*"))
        }

        findViewById<Button>(R.id.btn_format_pdf).setOnClickListener {
            startActivity(Intent(this, PdfFormatActivity::class.java))
        }

        findViewById<Button>(R.id.btn_export_pdf).setOnClickListener {
            showDateRangeDialog()
        }
    }
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun showDateRangeDialog() {
        val ctx: Context = this
        val view = layoutInflater.inflate(R.layout.dialog_date_range, null)
        val rgAll        = view.findViewById<RadioButton>(R.id.rb_all)
        val rgCustom     = view.findViewById<RadioButton>(R.id.rb_custom_range)
        val rgLastPages  = view.findViewById<RadioButton>(R.id.rb_last_pages)
        val btnFrom      = view.findViewById<Button>(R.id.btn_from_date)
        val btnTo        = view.findViewById<Button>(R.id.btn_to_date)
        val datesRow     = view.findViewById<View>(R.id.layout_dates)
        val lastPagesRow = view.findViewById<View>(R.id.layout_last_pages)
        val etLastPages  = view.findViewById<EditText>(R.id.et_last_pages_count)

        var fromDate: Long? = null
        var toDate: Long? = null

        fun refreshRows() {
            datesRow.visibility     = if (rgCustom.isChecked)    View.VISIBLE else View.GONE
            lastPagesRow.visibility = if (rgLastPages.isChecked) View.VISIBLE else View.GONE
        }
        rgAll.isChecked = true
        refreshRows()
        rgAll.setOnCheckedChangeListener { _, _ -> refreshRows() }
        rgCustom.setOnCheckedChangeListener { _, _ -> refreshRows() }
        rgLastPages.setOnCheckedChangeListener { _, _ -> refreshRows() }

        btnFrom.setOnClickListener {
            showCustomDatePicker(ctx, fromDate) { picked ->
                fromDate = getDateAtMidnight(picked)
                btnFrom.text = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(
                    fromDate
                ))
            }
        }
        btnTo.setOnClickListener {
            showCustomDatePicker(ctx, toDate) { picked ->
                toDate = getDateAtMidnight(picked) + 86_399_000L // end of day
                btnTo.text = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(toDate))
            }
        }

        AlertDialog.Builder(this)
            .setTitle("Export PDF – range")
            .setView(view)
            .setPositiveButton("Generate") { _, _ ->
                val range: DateRange = when {
                    rgLastPages.isChecked -> {
                        val n = etLastPages.text.toString().trim().toIntOrNull() ?: 0
                        if (n < 1) {
                            Toast.makeText(this, "Enter a page count of at least 1",
                                Toast.LENGTH_SHORT).show()
                            return@setPositiveButton
                        }
                        DateRange.LastPages(n)
                    }
                    rgCustom.isChecked -> {
                        val f = fromDate ?: return@setPositiveButton
                        val t = toDate ?: return@setPositiveButton
                        if (f > t) {
                            Toast.makeText(this, "From date must be before To date",
                                Toast.LENGTH_SHORT).show()
                            return@setPositiveButton
                        }
                        DateRange.Custom(f, t)
                    }
                    else -> DateRange.All
                }
                val prefs = PreferencesManager.getInstance(this)
                val rows = prefs.getPdfRowsPerPage()
                val owner = prefs.getPdfOwnerName()
                val cfg = prefs.getPdfLayoutConfig()
                val sig = prefs.getPdfSignaturePath()?.let { path ->
                    if (File(path).exists()) BitmapFactory.decodeFile(path) else null
                }
                viewModel.exportPdf(range, rows, owner, sig, cfg)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

}