package com.my.striptologbook.ui

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.Toolbar
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import com.my.striptologbook.LogbookApplication
import com.my.striptologbook.R
import com.my.striptologbook.utils.ExportRangeDialogHelper
import com.my.striptologbook.utils.FileManager
import com.my.striptologbook.utils.InsetsHelper
import com.my.striptologbook.utils.WorkFolderGateFlow
import kotlinx.coroutines.launch

class ToolsActivity : BaseActivity() {

    private lateinit var exportViewModel: ExportImportViewModel
    private lateinit var txtWorkFolder: TextView
    private lateinit var workDirGate: WorkFolderGateFlow

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tools)

        InsetsHelper.applyStatusBarSpacer(findViewById(R.id.status_bar_spacer))

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = "Tools"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        txtWorkFolder = findViewById(R.id.txt_work_folder)
        refreshWorkFolderDisplay()

        val app = application as LogbookApplication
        exportViewModel = ExportImportViewModel(app.repository)

        workDirGate = WorkFolderGateFlow(this)

        findViewById<Button>(R.id.btn_export_pdf).setOnClickListener {
            workDirGate.runOrPick { folder -> doExportPdf(folder) }
        }
        findViewById<Button>(R.id.btn_flight_sun).setOnClickListener {
            startActivity(Intent(this, FlightSunActivity::class.java))
        }
        findViewById<Button>(R.id.btn_airports).setOnClickListener {
            startActivity(Intent(this, AirportsActivity::class.java))
        }
        findViewById<Button>(R.id.btn_backup).setOnClickListener {
            startActivity(Intent(this, BackupActivity::class.java))
        }
        findViewById<Button>(R.id.btn_opening_balance).setOnClickListener {
            startActivity(Intent(this, OpeningBalanceListActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        refreshWorkFolderDisplay()
    }

    private fun refreshWorkFolderDisplay() {
        txtWorkFolder.text = "Work folder: ${FileManager.workFolderDisplayName(this)}"
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

    private fun doExportPdf(folder: DocumentFile) {
        ExportRangeDialogHelper.show(this) { range, rows, owner, sig, cfg ->
            lifecycleScope.launch {
                val r = exportViewModel.exportPdfToFolder(
                    contentResolver = contentResolver,
                    folder = folder,
                    range = range,
                    rowsPerPage = rows,
                    ownerName = owner,
                    signature = sig,
                    config = cfg
                )
                toastResult(r)
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