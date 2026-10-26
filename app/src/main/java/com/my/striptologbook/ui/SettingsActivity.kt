package com.my.striptologbook.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.MenuItem
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.Toolbar
import com.my.striptologbook.R
import com.my.striptologbook.utils.FileManager
import com.my.striptologbook.utils.InsetsHelper

class SettingsActivity : BaseActivity() {

    private lateinit var txtWorkDirectory: TextView

    private val workDirectoryPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            if (FileManager.persistWorkDirectoryPermission(this, uri)) {
                Toast.makeText(this, "Work directory set successfully", Toast.LENGTH_SHORT).show()
                refreshWorkDirectoryDisplay()
            } else {
                Toast.makeText(
                    this,
                    "Failed to persist permission. Please try again.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        // -------- Status bar spacer ------------------------------
        InsetsHelper.applyStatusBarSpacer(findViewById(R.id.status_bar_spacer))

        // -------- Toolbar -----------------------------------------
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = "Setting"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        txtWorkDirectory = findViewById(R.id.txt_work_directory)
        refreshWorkDirectoryDisplay()

        // --- Button listeners ---
        findViewById<Button>(R.id.btn_backup).setOnClickListener {
            startActivity(Intent(this, BackupActivity::class.java))
        }
        findViewById<Button>(R.id.btn_work_directory).setOnClickListener {
            workDirectoryPickerLauncher.launch(FileManager.workDirectoryPickerInitialUri(this))
        }
        findViewById<Button>(R.id.btn_export_import).setOnClickListener {
            startActivity(Intent(this, ExportImportActivity::class.java))
        }
        findViewById<Button>(R.id.btn_preferences).setOnClickListener {
            startActivity(Intent(this, PreferencesActivity::class.java))
        }
        findViewById<Button>(R.id.btn_airports).setOnClickListener {
            startActivity(Intent(this, AirportsActivity::class.java))
        }
        findViewById<Button>(R.id.btn_updates).setOnClickListener {
            startActivity(Intent(this, UpdatesActivity::class.java))
        }
        findViewById<Button>(R.id.btn_opening_balance).setOnClickListener {
            startActivity(Intent(this, OpeningBalanceListActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        refreshWorkDirectoryDisplay()
    }

    private fun refreshWorkDirectoryDisplay() {
        val name = FileManager.workDirectoryDisplayName(this)
        txtWorkDirectory.text = "Work directory: $name"
    }

    // --- Handle the back arrow ---
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}