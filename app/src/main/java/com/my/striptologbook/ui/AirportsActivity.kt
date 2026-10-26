package com.my.striptologbook.ui

import android.app.AlertDialog
import android.content.ContentValues
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import com.my.striptologbook.R
import com.my.striptologbook.data.AirportDataManager
import com.my.striptologbook.data.AirportDataManager.Airport
import com.my.striptologbook.utils.InsetsHelper
import com.my.striptologbook.utils.PreferencesManager
import com.my.striptologbook.utils.formatDateOnly
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

class AirportsActivity : BaseActivity() {

    private lateinit var airportDataManager: AirportDataManager
    private lateinit var progressBar: ProgressBar
    private lateinit var txtStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_airports)

        // -------- Toolbar ------------
        val spacer = findViewById<View>(R.id.status_bar_spacer)
        InsetsHelper.applyStatusBarSpacer(spacer)

        val scrollView = findViewById<ScrollView>(R.id.scroll_view)
        InsetsHelper.attachScrollToFocusedView(this, scrollView)
        InsetsHelper.applyImeBottomPaddingToRoot(this)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = "Airports"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        // -------- Toolbar ------------

        airportDataManager = AirportDataManager.getInstance(this)
        airportDataManager.ensureInitialDatabase()

        progressBar = findViewById(R.id.progressBarAirport)
        txtStatus = findViewById(R.id.txtAirportStatus)

        updateStatusText()

        findViewById<Button>(R.id.btn_update_airports).setOnClickListener {
            downloadAirportDatabase()
        }

        findViewById<Button>(R.id.btn_airport_database).setOnClickListener {
            startActivity(Intent(this, AirportEditorActivity::class.java))
        }

        findViewById<Button>(R.id.btn_export_airports).setOnClickListener {
            exportAirports()
        }

        findViewById<Button>(R.id.btn_clear_user_airports).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Clear User Modifications")
                .setMessage("This will remove all airports you have added, edited, or deleted.\n\nThe original database will remain unchanged.\n\nAre you sure?")
                .setPositiveButton("Clear") { _, _ ->
                    airportDataManager.clearUserModifications()
                    updateStatusText()
                    Toast.makeText(this, "User modifications cleared", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        findViewById<Button>(R.id.btn_delete_all_airports).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Delete All Airport Data")
                .setMessage("This will permanently delete the entire airport database.\n\nYou will need to re-download or re‑copy from assets.\n\nAre you sure?")
                .setPositiveButton("Delete") { _, _ ->
                    airportDataManager.deleteAllAirportData()
                    PreferencesManager.getInstance(this).setLastAirportsDownload(null)
                    updateStatusText()
                    Toast.makeText(this, "Airport database deleted", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun updateStatusText() {
        val lastDownload = PreferencesManager.getInstance(this).getLastAirportsDownload()
        txtStatus.text = if (lastDownload != null) {
            "A/P DB of $lastDownload"
        } else {
            // Check if the built-in database file exists
            val file = File(filesDir, "airports.json")
            if (file.exists()) "Built-in A/P DB" else "no A/P DB"
        }
    }

    private fun isNetworkAvailable(): Boolean {
        val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private fun downloadAirportDatabase() {
        if (checkSelfPermission(android.Manifest.permission.INTERNET) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "INTERNET permission required", Toast.LENGTH_LONG).show()
            return
        }
        if (!isNetworkAvailable()) {
            Toast.makeText(this, "No internet connection", Toast.LENGTH_LONG).show()
            return
        }

        progressBar.visibility = View.VISIBLE
        txtStatus.visibility = View.GONE

        lifecycleScope.launch(Dispatchers.IO) {
            val success = airportDataManager.downloadAndLoadDatabase()
            withContext(Dispatchers.Main) {
                progressBar.visibility = View.GONE
                txtStatus.visibility = View.VISIBLE

                if (success) {
                    val loaded = airportDataManager.loadedAirportCount
                    val skipped = airportDataManager.skippedAirportCount
                    val currentDate = formatDateOnly(System.currentTimeMillis())
                    PreferencesManager.getInstance(this@AirportsActivity).setLastAirportsDownload(currentDate)
                    updateStatusText()

                    Toast.makeText(
                        this@AirportsActivity,
                        "Loaded: $loaded airports\nSkipped: $skipped (no IATA)",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    Toast.makeText(this@AirportsActivity, "Download failed. Please try again.", Toast.LENGTH_LONG).show()
                }
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

    private fun exportAirports() {
        lifecycleScope.launch(Dispatchers.IO) {
            val allAirports = airportDataManager.getAllAirports()
            if (allAirports.isEmpty()) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@AirportsActivity,
                        "No airport data to export",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                return@launch
            }

            val json = buildJsonExport(allAirports)
            val fileName = "airports_export_${System.currentTimeMillis()}.json"

            var failure: Exception? = null
            try {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }

                val uri = contentResolver.insert(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    values
                ) ?: throw IllegalStateException("Failed to create MediaStore entry")

                try {
                    contentResolver.openOutputStream(uri)?.use { outputStream ->
                        outputStream.write(json.toByteArray())
                    } ?: throw IllegalStateException("Failed to open output stream")

                    val publishValues = ContentValues().apply {
                        put(MediaStore.MediaColumns.IS_PENDING, 0)
                    }
                    contentResolver.update(uri, publishValues, null, null)
                } catch (writeError: Exception) {
                    try {
                        contentResolver.delete(uri, null, null)
                    } catch (_: Exception) {
                    }
                    throw writeError
                }
            } catch (e: Exception) {
                failure = e
            }

            withContext(Dispatchers.Main) {
                val fail = failure
                if (fail == null) {
                    Toast.makeText(
                        this@AirportsActivity,
                        "airport DB successfull on /Downloads",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Toast.makeText(
                        this@AirportsActivity,
                        "Export failed: ${fail.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun buildJsonExport(airports: Set<Airport>): String {
        val root = JSONObject()
        for (airport in airports) {
            val obj = JSONObject()
            obj.put("name", airport.name ?: "")
            obj.put("city", airport.city ?: "")
            obj.put("state", airport.state ?: "")
            obj.put("country", airport.country ?: "")
            obj.put("iata", airport.iata ?: "")
            obj.put("icao", airport.icao ?: "")
            obj.put("lat", if (airport.latitude.isNaN()) JSONObject.NULL else airport.latitude)
            obj.put("lon", if (airport.longitude.isNaN()) JSONObject.NULL else airport.longitude)
            obj.put("elevation", if (airport.elevation.isNaN()) JSONObject.NULL else airport.elevation)
            obj.put("tz", airport.timezone ?: "")
            root.put(airport.icao, obj)
        }
        return root.toString(2)
    }
}