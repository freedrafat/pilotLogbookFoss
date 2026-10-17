package com.my.striptologbook.ui

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.my.striptologbook.R
import com.my.striptologbook.utils.InsetsHelper
import com.my.striptologbook.utils.PreferencesManager

class FlightSunPreferencesActivity : BaseActivity() {

    private lateinit var etDefaultAltitude: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_flight_sun_preferences)

        // -------- Status bar spacer ------------------------------
        InsetsHelper.applyStatusBarSpacer(findViewById(R.id.status_bar_spacer))

        // -------- Toolbar -----------------------------------------
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = "Sun Calculator Preferences"              //change title
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        etDefaultAltitude = findViewById(R.id.et_default_altitude)

        // Load current default altitude
        val currentDefault = PreferencesManager.getInstance(this).getDefaultAltitude()
        etDefaultAltitude.setText(currentDefault.toString())

        // Save button
        findViewById<Button>(R.id.btn_save_altitude).setOnClickListener {
            val altitudeText = etDefaultAltitude.text.toString().trim()
            if (altitudeText.isEmpty()) {
                Toast.makeText(this, "Please enter a valid altitude", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val altitude = altitudeText.toIntOrNull()
            if (altitude == null || altitude < 0) {
                Toast.makeText(this, "Invalid altitude (must be a positive number)", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            PreferencesManager.getInstance(this).setDefaultAltitude(altitude)
            Toast.makeText(this, "Default altitude saved", Toast.LENGTH_SHORT).show()
            finish()
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