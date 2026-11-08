package com.my.striptologbook.ui

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.Toolbar
import com.my.striptologbook.R
import com.my.striptologbook.utils.FileManager
import com.my.striptologbook.utils.InsetsHelper
import com.my.striptologbook.utils.WorkFolderGateFlow

class SettingsActivity : BaseActivity() {

    private lateinit var txtWorkFolder: TextView

    private lateinit var workFolderGate: WorkFolderGateFlow

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

        txtWorkFolder = findViewById(R.id.txt_work_folder)
        refreshWorkFolderDisplay()

        workFolderGate = WorkFolderGateFlow(this)

        // --- Button listeners ---

        findViewById<Button>(R.id.btn_settings_backup).setOnClickListener {
            startActivity(Intent(this, SettingsBackupActivity::class.java))
        }
        findViewById<Button>(R.id.btn_work_folder).setOnClickListener {
            workFolderGate.pickFolder { _ ->
                Toast.makeText(this, "Work folder set successfully", Toast.LENGTH_SHORT).show()
                refreshWorkFolderDisplay()
            }
        }
        findViewById<Button>(R.id.btn_preferences).setOnClickListener {
            startActivity(Intent(this, PreferencesActivity::class.java))
        }
        findViewById<Button>(R.id.btn_updates).setOnClickListener {
            startActivity(Intent(this, UpdatesActivity::class.java))
        }
        findViewById<Button>(R.id.btn_export_format).setOnClickListener {
            startActivity(Intent(this, SettingsExportFormatActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        refreshWorkFolderDisplay()
    }

    private fun refreshWorkFolderDisplay() {
        val name = FileManager.workFolderDisplayName(this)
        txtWorkFolder.text = "Work folder: $name"
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