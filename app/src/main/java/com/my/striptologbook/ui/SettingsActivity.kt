package com.my.striptologbook.ui

import android.os.Bundle
import android.view.MenuItem
import android.widget.Button
import android.content.Intent
import android.view.View
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.my.striptologbook.R

class SettingsActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        // --- Spacer ---
        val spacer = findViewById<View>(R.id.status_bar_spacer)
        ViewCompat.setOnApplyWindowInsetsListener(spacer) { view, insets ->
            val statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top
            if (statusBarHeight > 0) {
                val params = view.layoutParams
                params.height = statusBarHeight
                view.requestLayout()
            }
            insets
        }

        // --- Toolbar ---
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = "Settings"
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