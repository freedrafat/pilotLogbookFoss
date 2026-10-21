package com.my.striptologbook.ui

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.widget.Button
import androidx.appcompat.widget.Toolbar
import com.my.striptologbook.R
import com.my.striptologbook.utils.InsetsHelper

class SettingsActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        // -------- Status bar spacer ------------------------------
        InsetsHelper.applyStatusBarSpacer(findViewById(R.id.status_bar_spacer))

        // -------- Toolbar -----------------------------------------
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = "Setting"              //change title
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // --- Button listeners ---
        findViewById<Button>(R.id.btn_backup).setOnClickListener {
            startActivity(Intent(this, BackupActivity::class.java))
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

    // --- Handle the back arrow ---
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}