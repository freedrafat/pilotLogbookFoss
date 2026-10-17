package com.my.striptologbook.ui

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.Button
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.my.striptologbook.R

class PreferencesActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_preferences)

        // Toolbar
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