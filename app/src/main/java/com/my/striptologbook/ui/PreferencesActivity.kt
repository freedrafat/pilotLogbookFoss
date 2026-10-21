package com.my.striptologbook.ui

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.widget.Button
import androidx.appcompat.widget.Toolbar
import com.my.striptologbook.R
import com.my.striptologbook.utils.InsetsHelper

class PreferencesActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_preferences)

        // -------- Status bar spacer ------------------------------
        InsetsHelper.applyStatusBarSpacer(findViewById(R.id.status_bar_spacer))

        // -------- Toolbar -----------------------------------------
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = "Preferences"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)


        findViewById<Button>(R.id.btn_statistics_visibility).setOnClickListener {
            startActivity(Intent(this, StatisticsVisibilityPreferencesActivity::class.java))
        }

        findViewById<Button>(R.id.btn_sun_calculator).setOnClickListener {
            startActivity(Intent(this, FlightSunPreferencesActivity::class.java))
        }

        findViewById<Button>(R.id.btn_sun_calculator).setOnClickListener {
            startActivity(Intent(this, FlightSunPreferencesActivity::class.java))
        }

        findViewById<Button>(R.id.btn_default_fields).setOnClickListener {
            startActivity(Intent(this, DefaultFieldsPreferencesActivity::class.java))
        }

        findViewById<Button>(R.id.btn_custom_lists).setOnClickListener {
            startActivity(Intent(this, CustomListsPreferencesActivity::class.java))
        }

        findViewById<Button>(R.id.btn_leg_items).setOnClickListener {
            startActivity(Intent(this, LegItemsPreferencesActivity::class.java))
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