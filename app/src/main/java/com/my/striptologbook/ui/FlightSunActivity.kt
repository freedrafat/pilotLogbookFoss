package com.my.striptologbook.ui

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.my.striptologbook.R
import com.my.striptologbook.data.AirportDataManager
import com.my.striptologbook.utils.InsetsHelper
import com.my.striptologbook.utils.MovingSunriseCalculator
import com.my.striptologbook.utils.PreferencesManager
import com.my.striptologbook.utils.Waypoint
import com.my.striptologbook.utils.extractMinutesSinceMidnight
import com.my.striptologbook.utils.extractTimeOnly
import com.my.striptologbook.utils.formatCoordinate
import com.my.striptologbook.utils.formatDateOnly
import com.my.striptologbook.utils.getDateAtMidnight
import com.my.striptologbook.utils.getTodayMidnight
import com.my.striptologbook.utils.normalizeTime
import com.my.striptologbook.utils.parseCoordinate
import com.my.striptologbook.utils.showCustomDatePicker
import com.my.striptologbook.utils.toMinutesOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Date
import java.util.TimeZone

class FlightSunActivity : AppCompatActivity() {

    // UI elements
    private lateinit var actDepartureIATA: AutoCompleteTextView
    private lateinit var actDestinationIATA: AutoCompleteTextView
    private lateinit var tvDepartureDate: TextView
    private lateinit var tvOffBlock: TextView
    private lateinit var etIoTime: EditText

    private lateinit var tvArrivalDate: TextView
    private lateinit var etLdgTime: EditText
    private lateinit var tvOnBlock: TextView
    private lateinit var etElevation: EditText
    private lateinit var btnCompute: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var tvResults: TextView

    // MovingSunriseCalculator result fields
    private lateinit var etDepartureSunriseMoving: EditText
    private lateinit var etDepartureSunsetMoving: EditText
    private lateinit var etArrivalSunriseMoving: EditText
    private lateinit var etArrivalSunsetMoving: EditText

    // astronomical twilight fields
    private lateinit var etDepartureAstroDawn: EditText
    private lateinit var etDepartureAstroDusk: EditText
    private lateinit var etArrivalAstroDawn: EditText
    private lateinit var etArrivalAstroDusk: EditText

    // Stored timestamps (from extras)
    private var toTimestamp: Long? = null
    private var ldgTimestamp: Long? = null

    private var offBlockTimestamp: Long? = null
    private var onBlockTimestamp: Long? = null
    private var depSunriseTime: Date? = null
    private var depSunsetTime: Date? = null
    private var arrSunriseTime: Date? = null
    private var arrSunsetTime: Date? = null

    private val airportManager by lazy { AirportDataManager.getInstance(this) }

    private lateinit var btnAddWaypoint: Button
    private lateinit var waypointContainer: LinearLayout
    private val waypointRows = mutableListOf<WaypointRowHolder>()
    private val invalidFields = mutableSetOf<EditText>()
    private val defaultBackgrounds = mutableMapOf<EditText, android.graphics.drawable.Drawable>()
    private var computeThread: Thread? = null

    private lateinit var btnApplyToLeg: Button
    private var fromLegEdit = false

    private var lastEvents: List<MovingSunriseCalculator.SunEvent> = emptyList()

    // Computed day/night statuses (for OffBlock and OnBlock)
    private var lastToIsDay: Boolean = false
    private var lastLdgIsDay: Boolean = false
    private var lastOffBlockIsDay: Boolean? = null
    private var lastOnBlockIsDay: Boolean? = null

    private companion object {
        const val TAG1 = "OrientationDebug"
        const val TAG = "NightButtonFlow"

        private const val KEY_DEPARTURE_TIME = "departure_time"
        private const val KEY_ARRIVAL_TIME = "arrival_time"
        private const val KEY_WAYPOINTS = "waypoints"

        // ... other constants
    }

    // Helper to hold each row's data
    private class WaypointRowHolder(
        val rowView: View,
        val latEdit: EditText,
        val lonEdit: EditText,
        val timeEdit: EditText
    ) {
        var timeStr: String? = null
        var isLatTemporary = false
        var isLonTemporary = false
        var isTimeTemporary = false

        var currentValidLat: Double? = null
        var currentValidLon: Double? = null
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate: savedInstanceState=$savedInstanceState")

        setContentView(R.layout.activity_flight_sun)
        disableDictionarySuggestions(findViewById(android.R.id.content)!!)

        // -------- Status bar spacer ------------------------------
        InsetsHelper.applyStatusBarSpacer(findViewById(R.id.status_bar_spacer))

        // -------- Toolbar -----------------------------------------
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = "Flight Sun Events"              //change title
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // -------- ScrollView and focus / IME handling -------------
        val scrollView = findViewById<ScrollView>(R.id.scroll_view)
        InsetsHelper.attachScrollToFocusedView(this, scrollView)
        InsetsHelper.applyImeBottomPaddingToRoot(this)
        // ----------------------------------------------------------

        // Bind views
        actDepartureIATA = findViewById(R.id.et_departure)
        actDestinationIATA = findViewById(R.id.et_destination)
        tvDepartureDate = findViewById(R.id.tv_departure_date)
        etIoTime = findViewById(R.id.et_departure_time)
        tvArrivalDate = findViewById(R.id.tv_arrival_date)
        etLdgTime = findViewById(R.id.et_arrival_time)
        tvOffBlock = findViewById(R.id.tv_offblock)
        tvOnBlock = findViewById(R.id.tv_onblock)
        etElevation = findViewById(R.id.et_elevation)

        btnAddWaypoint = findViewById(R.id.btn_add_waypoint)
        waypointContainer = findViewById(R.id.waypoint_container)

        btnCompute = findViewById(R.id.btn_compute)
        progressBar = findViewById(R.id.progressBar)

        tvResults = findViewById(R.id.tv_results)

        etDepartureSunriseMoving = findViewById(R.id.et_departure_sunrise_time)
        etDepartureSunsetMoving = findViewById(R.id.et_departure_sunset_time)
        etArrivalSunriseMoving = findViewById(R.id.et_arrival_sunrise_time)
        etArrivalSunsetMoving = findViewById(R.id.et_arrival_sunset_time)

        etDepartureAstroDawn = findViewById(R.id.et_departure_astro_dawn)
        etDepartureAstroDusk = findViewById(R.id.et_departure_astro_dusk)
        etArrivalAstroDawn = findViewById(R.id.et_arrival_astro_dawn)
        etArrivalAstroDusk = findViewById(R.id.et_arrival_astro_dusk)

        btnApplyToLeg = findViewById(R.id.btn_apply_to_leg)
        btnApplyToLeg.visibility = View.GONE

        val btnSaveState = findViewById<Button>(R.id.btn_save_state)
        val btnLoadState = findViewById<Button>(R.id.btn_load_state)

        // Store default backgrounds for warning restoration
        defaultBackgrounds[etIoTime] = etIoTime.background
        defaultBackgrounds[etLdgTime] = etLdgTime.background

        btnSaveState.setOnClickListener { saveCurrentState() }
        btnLoadState.setOnClickListener { loadSavedState() }

        val btnClear = findViewById<Button>(R.id.btn_clear)
        btnClear.setOnClickListener { clearAllFields() }

        // Read extras
        val fromLegEditExtra = intent.getBooleanExtra("fromLegEdit", false)
        fromLegEdit = fromLegEditExtra
        Log.d(TAG, "FlightSunActivity created, fromLegEdit=$fromLegEdit")

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (fromLegEdit) {
                    setResult(RESULT_CANCELED)
                }
                finish()
            }
        })

        // Show/hide apply button
//        btnApplyToLeg.visibility = if (fromLegEdit) View.VISIBLE else View.GONE

        // Also log received airport codes and timestamps
        val depIATA = intent.getStringExtra("departureIATA") ?: ""
        val arrIATA = intent.getStringExtra("arrivalIATA") ?: ""
        val depTime = intent.getLongExtra("departureTime", 0L)
        val arrTime = intent.getLongExtra("arrivalTime", 0L)
        Log.d(TAG, "Received extras: depIATA='$depIATA', arrIATA='$arrIATA', depTime=$depTime, arrTime=$arrTime")

        val offBlockExtra = intent.getLongExtra("offBlock", 0L)
        val onBlockExtra = intent.getLongExtra("onBlock", 0L)

        offBlockTimestamp = if (offBlockExtra != 0L) offBlockExtra else null
        onBlockTimestamp = if (onBlockExtra != 0L) onBlockExtra else null

        // Display – moved outside the if/else
        tvOffBlock.text = offBlockTimestamp?.let { formatDateOnly(it) + " " + extractTimeOnly(it) } ?: ""
        tvOnBlock.text = onBlockTimestamp?.let { formatDateOnly(it) + " " + extractTimeOnly(it) } ?: ""

        // --- PRELOAD AIRPORT DATA IN BACKGROUND ---
        lifecycleScope.launch(Dispatchers.IO) {
            airportManager.ensureDataLoaded()
        }

        // Setup autosuggest for airport codes
        setupAirportAutosuggest()

        // --- INITIALISE TIMESTAMPS AND UI ---
        // --- EXTRAS HANDLING ---
        if (savedInstanceState != null) {
            // Restore from saved state (rotation etc.)
            val depTime = savedInstanceState.getLong(KEY_DEPARTURE_TIME, 0L)
            val arrTime = savedInstanceState.getLong(KEY_ARRIVAL_TIME, 0L)
            if (depTime != 0L) toTimestamp = depTime
            if (arrTime != 0L) ldgTimestamp = arrTime
            // Restore waypoints (your existing code remains here)
            val waypointData = savedInstanceState.getStringArrayList(KEY_WAYPOINTS) ?: emptyList()
            for (data in waypointData) {
                val parts = data.split(",")
                if (parts.size >= 3) {
                    // New saves store raw doubles and "HH:mm" strings.
                    // Legacy values (formatted strings, Long times) are discarded.
                    val lat = parts[0].ifEmpty { null }?.toDoubleOrNull()
                    val lon = parts[1].ifEmpty { null }?.toDoubleOrNull()
                    val time = parts[2].ifEmpty { null }?.takeIf { Regex("^\\d{2}:\\d{2}$").matches(it) }
                    val latTemp = parts.size > 3 && parts[3] == "1"
                    val lonTemp = parts.size > 4 && parts[4] == "1"
                    val timeTemp = parts.size > 5 && parts[5] == "1"
                    addWaypointRow(lat, lon, time, latTemp, lonTemp, timeTemp)
                }
            }
            Log.d(TAG, "Restored from saved state: dep=$toTimestamp, arr=$ldgTimestamp")
        }
        else {
            // Fresh start – use extras or fallback to defaults
            val depIATA = intent.getStringExtra("departureIATA") ?: ""
            val arrIATA = intent.getStringExtra("arrivalIATA") ?: ""
            val depTime = intent.getLongExtra("departureTime", 0L)
            val arrTime = intent.getLongExtra("arrivalTime", 0L)
            Log.d(TAG, "Fresh start – extras: depIATA='$depIATA', arrIATA='$arrIATA', depTime=$depTime, arrTime=$arrTime")

            // Set airport fields if provided
            if (depIATA.isNotEmpty()) {
                actDepartureIATA.setText(depIATA)
                Log.d(TAG, "Set departure field to: $depIATA")
            }
            if (arrIATA.isNotEmpty()) {
                actDestinationIATA.setText(arrIATA)
                Log.d(TAG, "Set arrival field to: $arrIATA")
            }

//            // Display OffBlock/OnBlock if provided
//            tvOffBlock.text = offBlockTimestamp?.let { formatDateOnly(it) + " " + extractTimeOnly(it)} ?: ""
//            tvOnBlock.text = onBlockTimestamp?.let { formatDateOnly(it) + " " + extractTimeOnly(it)} ?: ""

            // Set departure timestamp
            if (depTime != 0L) {
                toTimestamp = depTime
                Log.d(TAG, "Set departure timestamp from extra: $depTime")
            } else {
                toTimestamp = System.currentTimeMillis()
                Log.d(TAG, "Departure extra missing – using now: $toTimestamp")
            }

            // Set arrival timestamp
            if (arrTime != 0L) {
                ldgTimestamp = arrTime
                Log.d(TAG, "Set arrival timestamp from extra: $arrTime")
            } else {
                ldgTimestamp = (toTimestamp ?: System.currentTimeMillis()) + 3 * 3600_000L
                Log.d(TAG, "Arrival extra missing – using dep+3h: $ldgTimestamp")
            }
        }

        // Update UI date/time fields with current timestamps
        updateDepartureDateDisplay()

        toTimestamp?.let { etIoTime.setText(extractTimeOnly(it)) }
        ldgTimestamp?.let { etLdgTime.setText(extractTimeOnly(it)) }

        ensureArrivalAfterDeparture()
//        ensureArrivalWithinOneDay()

        updateArrivalDateDisplay()

        // Set elevation default (if empty)
        if (etElevation.text.toString().trim().isEmpty()) {
            val defaultAlt = PreferencesManager.getInstance(this).getDefaultAltitude()
            etElevation.setText(defaultAlt.toString())
        }

        // Set click listeners for date field
        tvDepartureDate.setOnClickListener {
            showCustomDatePicker(this, toTimestamp) { dateMidnight ->
                // Preserve the current time-of-day from departureTimestamp, or use current UTC time
                val currentTimeMinutes = toTimestamp?.let {
                    val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = it }
                    cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
                } ?: run {
                    val now = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
                    now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
                }
                val newTimestamp = dateMidnight + currentTimeMinutes * 60_000L
                toTimestamp = newTimestamp
                etIoTime.setText(extractTimeOnly(newTimestamp))
                tvDepartureDate.text = formatDateOnly(newTimestamp)
                ensureArrivalAfterDeparture()
                ensureArrivalWithinOneDay()
            }
        }

        tvArrivalDate.setOnClickListener {
            showCustomDatePicker(this, ldgTimestamp) { dateMidnight ->
                // Preserve current time-of-day from arrivalTimestamp
                val currentMinutes = ldgTimestamp?.let { timestamp ->
                    Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = timestamp }
                        .let { cal -> cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE) }
                } ?: 0
                val newTimestamp = dateMidnight + currentMinutes * 60_000L
                ldgTimestamp = newTimestamp
                etLdgTime.setText(extractTimeOnly(newTimestamp))
                updateArrivalDateDisplay()
                ensureArrivalAfterDeparture()
//                ensureArrivalWithinOneDay()
            }
        }

        // Setup focus listeners for time fields
        setupTimeField(etIoTime, isDeparture = true)
        setupTimeField(etLdgTime, isDeparture = false)

        // Time fields already have Entered listener
        setupEnterKeyToClearFocus(etIoTime)
        setupEnterKeyToClearFocus(etLdgTime)

        // Airport fields
        setupEnterKeyToClearFocus(actDepartureIATA)
        setupEnterKeyToClearFocus(actDestinationIATA)

        // Elevation
        setupEnterKeyToClearFocus(etElevation)


        btnAddWaypoint.setOnClickListener { addWaypointRow() }
        btnCompute.setOnClickListener { computeAndDisplay() }

        btnApplyToLeg.setOnClickListener {
            // Filter only Sunrise and Sunset events
            val filteredEvents = lastEvents.filter { it.type == "Sunrise" || it.type == "Sunset" }
            val eventsStr = filteredEvents.joinToString(",") { "${it.type}:${it.timeUTC.time}" }
            val toStatus = if (lastToIsDay) "Day" else "Night"
            val ldgStatus = if (lastLdgIsDay) "Day" else "Night"
            val resultIntent = Intent().apply {
                putExtra("events", eventsStr)
                putExtra("offBlockStatus", if (lastOffBlockIsDay == true) "Day" else if (lastOffBlockIsDay == false) "Night" else "")
                putExtra("toStatus", toStatus)
                putExtra("ldgStatus", ldgStatus)
                putExtra("onBlockStatus", if (lastOnBlockIsDay == true) "Day" else if (lastOnBlockIsDay == false) "Night" else "")
                putExtra("depSunrise", depSunriseTime?.time ?: 0L)
                putExtra("depSunset", depSunsetTime?.time ?: 0L)
                putExtra("arrSunrise", arrSunriseTime?.time ?: 0L)
                putExtra("arrSunset", arrSunsetTime?.time ?: 0L)
            }
            setResult(RESULT_OK, resultIntent)
            finish()
        }
        Log.d(TAG1, "onCreate: departureTimestamp=$toTimestamp, arrivalTimestamp=$ldgTimestamp, waypointRows.size=${waypointRows.size}")
        Log.d(TAG1, "onCreate after setContentView: tvResults.text = ${tvResults.text}")


    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        Log.d(TAG1, "onSaveInstanceState: saving departure=$toTimestamp, arrival=$ldgTimestamp, waypoints=${waypointRows.size}")

        outState.putLong(KEY_DEPARTURE_TIME, toTimestamp ?: 0L)
        outState.putLong(KEY_ARRIVAL_TIME, ldgTimestamp ?: 0L)

        // Save waypoints as a list of strings: "lat,lon,time"
        val waypointData = waypointRows.mapNotNull { holder ->
            val lat = holder.currentValidLat
            val lon = holder.currentValidLon
            val time = holder.timeStr
            if (lat != null || lon != null || time != null) {
                val latTemp = if (holder.isLatTemporary) "1" else "0"
                val lonTemp = if (holder.isLonTemporary) "1" else "0"
                val timeTemp = if (holder.isTimeTemporary) "1" else "0"
                "${lat ?: ""},${lon ?: ""},${time ?: ""},$latTemp,$lonTemp,$timeTemp"
            } else null
        }
        outState.putStringArrayList(KEY_WAYPOINTS, ArrayList(waypointData))

        Log.d(TAG1, "onSaveInstanceState: saving departure=$toTimestamp, arrival=$ldgTimestamp, waypoints=${waypointRows.size}")
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        Log.d(TAG1, "onRestoreInstanceState: bundle=$savedInstanceState")
        Log.d(TAG1, "onRestoreInstanceState: tvResults.text = ${tvResults.text}")

    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG1, "onDestroy")
    }

    private fun updateDepartureDateDisplay() {
        tvDepartureDate.text = toTimestamp?.let { formatDateOnly(it) } ?: "Select date"
    }

    private fun updateArrivalDateDisplay() {
        tvArrivalDate.text = ldgTimestamp?.let { formatDateOnly(it) } ?: "Select date"
    }

    private fun setupTimeField(editText: EditText, isDeparture: Boolean) {
        editText.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val raw = editText.text.toString().trim()
                val normalized = normalizeTime(raw)
                val currentTimestamp = if (isDeparture) toTimestamp else ldgTimestamp
                val currentTimeStr = currentTimestamp?.let { extractTimeOnly(it) } ?: ""

                if (normalized == "invalid") {
                    // Revert to previous valid value and mark warning
                    editText.setText(currentTimeStr)
                    markWarning(editText)
                } else if (normalized != currentTimeStr) {
                    // Valid new value
                    if (normalized != raw) {
                        editText.setText(normalized)
                    }
                    // Update timestamp: combine with current date
                    val minutes = normalized.toMinutesOrNull()
                    if (minutes != null) {
                        val dateMidnight = currentTimestamp?.let { getDateAtMidnight(it) }
                            ?: getTodayMidnight()
                        val newTimestamp = dateMidnight + minutes * 60_000L
                        if (isDeparture) {
                            toTimestamp = newTimestamp
                            updateDepartureDateDisplay() // if you have a date view
                            // Adjust arrival if needed
                            ensureArrivalAfterDeparture()
                            ensureArrivalWithinOneDay()
                            // Update arrival time field if adjusted
                            updateArrivalDateDisplay()
                            etLdgTime.setText(ldgTimestamp?.let { extractTimeOnly(it) } ?: "")
                        } else {
                            ldgTimestamp = newTimestamp
                            // Ensure arrival is after departure
                            ensureArrivalAfterDeparture()
                            ensureArrivalWithinOneDay()
                            // Update the time field if adjusted
                            updateArrivalDateDisplay()
                            etLdgTime.setText(ldgTimestamp?.let { extractTimeOnly(it) } ?: "")
                        }
                    }
                    clearWarning(editText)
                }
            } else {
                // Field gains focus – clear warning
                clearWarning(editText)
            }
        }
    }

    // ----- Autosuggest for Airport Codes (new) -----
    private fun setupAirportAutosuggest() {
        fun setupAutoComplete(
            autoComplete: AutoCompleteTextView,
            updateCode: (String) -> Unit
        ) {
            autoComplete.threshold = 0
            autoComplete.adapter =
                ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, emptyList<String>())

            autoComplete.addTextChangedListener(object : TextWatcher {
                private var lastQuery = ""
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    val query = s.toString().trim()
                    if (!autoComplete.hasFocus()) return
                    if (query.isEmpty()) {
                        autoComplete.dismissDropDown()
                        return
                    }
                    if (query == lastQuery) return
                    lastQuery = query

                    // Run search on background thread, update UI on main
                    lifecycleScope.launch(Dispatchers.IO) {
                        val suggestions = airportManager.searchAirportCodes(query)
                        withContext(Dispatchers.Main) {
                            Log.d("FlightSun", "Suggestions for $query: $suggestions")
                            if (suggestions.isNotEmpty()) {
                                val adapter = ArrayAdapter(
                                    this@FlightSunActivity,
                                    android.R.layout.simple_dropdown_item_1line,
                                    suggestions
                                )
                                autoComplete.adapter = adapter
                                autoComplete.showDropDown()
                            } else {
                                autoComplete.dismissDropDown()
                            }
                        }
                    }
                }
            })

            autoComplete.setOnItemClickListener { parent, _, position, _ ->
                val selected = parent.getItemAtPosition(position) as String
                autoComplete.setText(selected)
                updateCode(selected)
                autoComplete.clearFocus()
                autoComplete.dismissDropDown()
            }
        }

        setupAutoComplete(actDepartureIATA) { /* no extra storage needed */ }
        setupAutoComplete(actDestinationIATA) { /* no extra storage needed */ }
    }

    // ----- Helper to guarantee arrival > departure -----
    /**
     * Ensures arrival is strictly after departure.
     * If arrival is on or before departure, adds whole days until it is.
     */
    private fun ensureArrivalAfterDeparture() {
        val dep = toTimestamp ?: return
        val arr = ldgTimestamp ?: return

        val depDateMidnight = getDateAtMidnight(dep)
        val depMinutes = extractMinutesSinceMidnight(dep)
        val arrMinutes = extractMinutesSinceMidnight(arr)   // preserved

        var daysDiff = ((getDateAtMidnight(arr) - depDateMidnight) / (24 * 60 * 60 * 1000)).toInt()
        var diffMinutes = daysDiff * 1440 + (arrMinutes - depMinutes)

        while (diffMinutes <= 0) {
            daysDiff++
            diffMinutes += 1440
        }

        val newArrTimestamp = depDateMidnight + daysDiff * 24 * 60 * 60 * 1000L + arrMinutes * 60 * 1000L

        if (newArrTimestamp != arr) {
            ldgTimestamp = newArrTimestamp
            etLdgTime.setText(extractTimeOnly(newArrTimestamp))
            updateArrivalDateDisplay()
        }
    }

    /**
     * Ensures flight duration does not exceed 24 hours.
     * If arrival is more than 24h after departure, subtracts whole days until it is ≤ 24h.
     */
    private fun ensureArrivalWithinOneDay() {
        val dep = toTimestamp ?: return
        val arr = ldgTimestamp ?: return

        val depDateMidnight = getDateAtMidnight(dep)
        val depMinutes = extractMinutesSinceMidnight(dep)
        val arrMinutes = extractMinutesSinceMidnight(arr)   // preserved

        var daysDiff = ((getDateAtMidnight(arr) - depDateMidnight) / (24 * 60 * 60 * 1000)).toInt()
        var diffMinutes = daysDiff * 1440 + (arrMinutes - depMinutes)

        while (diffMinutes > 1440) {
            daysDiff--
            diffMinutes -= 1440
        }

        val newArrTimestamp = depDateMidnight + daysDiff * 24 * 60 * 60 * 1000L + arrMinutes * 60 * 1000L

        if (newArrTimestamp != arr) {
            ldgTimestamp = newArrTimestamp
            etLdgTime.setText(extractTimeOnly(newArrTimestamp))
            updateArrivalDateDisplay()
        }
    }

    private fun addWaypointRow(
        initialLat: Double? = null,
        initialLon: Double? = null,
        initialTime: String? = null,
        latTemporary: Boolean = false,
        lonTemporary: Boolean = false,
        timeTemporary: Boolean = false
    ) {
        Log.d(TAG1, "Waypoint added, new size=${waypointRows.size}")

        val rowView = layoutInflater.inflate(R.layout.item_waypoint, waypointContainer, false)
        val latEdit = rowView.findViewById<EditText>(R.id.et_waypoint_lat)
        val lonEdit = rowView.findViewById<EditText>(R.id.et_waypoint_lon)
        val timeEdit = rowView.findViewById<EditText>(R.id.et_waypoint_time)
        val removeBtn = rowView.findViewById<ImageButton>(R.id.btn_remove_waypoint)

        defaultBackgrounds[latEdit] = latEdit.background
        defaultBackgrounds[lonEdit] = lonEdit.background
        defaultBackgrounds[timeEdit] = timeEdit.background

        // Create holder FIRST
        // Create holder FIRST
        val holder = WaypointRowHolder(rowView, latEdit, lonEdit, timeEdit)
        latEdit.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (holder.isLatTemporary) {
                    holder.isLatTemporary = false
                    // Reset to normal style
                    latEdit.setTextAppearance(R.style.EditTextStyle)
                }
            }
        })

        lonEdit.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (holder.isLonTemporary) {
                    holder.isLonTemporary = false
                    lonEdit.setTextAppearance(R.style.EditTextStyle)
                }
            }
        })

        latEdit.onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                var valid = true
                val text = latEdit.text.toString().trim()
                if (text.isEmpty()) {
                    holder.currentValidLat = null
                } else {
                    val parsed = parseCoordinate(text, true)
                    if (parsed == null) {
                        markWarning(latEdit)
                        val revertText = holder.currentValidLat?.let { formatCoordinate(it, true) } ?: ""
                        latEdit.setText(revertText)
                        valid = false
                    } else {
                        holder.currentValidLat = parsed
                        latEdit.setText(formatCoordinate(parsed, true))
                    }
                }
                if (valid) applyCoordinatePairState(holder)
            } else {
                clearWarning(latEdit)
            }
        }

        lonEdit.onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                var valid = true
                val text = lonEdit.text.toString().trim()
                if (text.isEmpty()) {
                    holder.currentValidLon = null
                } else {
                    val parsed = parseCoordinate(text, false)
                    if (parsed == null) {
                        markWarning(lonEdit)
                        val revertText = holder.currentValidLon?.let { formatCoordinate(it, false) } ?: ""
                        lonEdit.setText(revertText)
                        valid = false
                    } else {
                        holder.currentValidLon = parsed
                        lonEdit.setText(formatCoordinate(parsed, false))
                    }
                }
                if (valid) applyCoordinatePairState(holder)
            } else {
                clearWarning(lonEdit)
            }
        }

        timeEdit.onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val raw = timeEdit.text.toString().trim()
                val normalized = normalizeTime(raw)
                val previousTimeStr = holder.timeStr ?: ""

                if (normalized == "invalid") {
                    timeEdit.setText(previousTimeStr)
                    markWarning(timeEdit)
                    return@OnFocusChangeListener
                }

                if (normalized.isEmpty()) {
                    holder.timeStr = null
                    clearWarning(timeEdit)
                    return@OnFocusChangeListener
                }

                if (normalized != raw) {
                    timeEdit.setText(normalized)
                }

                // Any focus loss on a valid, non-empty time promotes it to user-entered
                holder.isTimeTemporary = false
                timeEdit.setTextAppearance(R.style.EditTextStyle)

                holder.timeStr = normalized

                val depTs = toTimestamp
                if (depTs == null) {
                    clearWarning(timeEdit)
                    return@OnFocusChangeListener
                }

                val arrTs = ldgTimestamp

                if (arrTs != null && arrTs - depTs > 24L * 60 * 60 * 1000) {
                    markWarning(timeEdit)
                    return@OnFocusChangeListener
                }

                val minutes = normalized.toMinutesOrNull()
                if (minutes == null) {
                    markWarning(timeEdit)
                    return@OnFocusChangeListener
                }

                val base = getDateAtMidnight(depTs)
                var candidate = base + minutes * 60_000L
                if (candidate < depTs) candidate += 24L * 60 * 60 * 1000
                if (candidate < depTs) {
                    markWarning(timeEdit)
                    return@OnFocusChangeListener
                }
                if (arrTs != null && candidate > arrTs) {
                    markWarning(timeEdit)
                    return@OnFocusChangeListener
                }

                clearWarning(timeEdit)

                // Sort + fill missing coordinates if every non-empty row has a user time
                if (allNonEmptyRowsHaveUserTime()) {
                    sortRowsByTime()
                    val route = buildRouteFromUi()
                    if (route != null) {
                        MovingSunriseCalculator.fillMissingCoordinates(route)
                        applyRouteBackToUi(route)
                    }
                }
            } else {
                clearWarning(timeEdit)
            }
        }

        setupEnterKeyToClearFocus(latEdit)
        setupEnterKeyToClearFocus(lonEdit)
        setupEnterKeyToClearFocus(timeEdit)

        // Set initial values if provided (now holder exists)
        initialLat?.let {
            holder.currentValidLat = it
            latEdit.setText(formatCoordinate(it, true))
            if (latTemporary) {
                holder.isLatTemporary = true
                latEdit.setTextAppearance(R.style.EditTextTemporaryStyle)
            }
        }
        initialLon?.let {
            holder.currentValidLon = it
            lonEdit.setText(formatCoordinate(it, false))
            if (lonTemporary) {
                holder.isLonTemporary = true
                lonEdit.setTextAppearance(R.style.EditTextTemporaryStyle)
            }
        }
        initialTime?.let {
            holder.timeStr = it
            timeEdit.setText(it)
            if (timeTemporary) {
                holder.isTimeTemporary = true
                timeEdit.setTextAppearance(R.style.EditTextTemporaryStyle)
            }
        }

        waypointRows.add(holder)
        waypointContainer.addView(rowView)

        removeBtn.setOnClickListener {
            waypointContainer.removeView(rowView)
            waypointRows.remove(holder)
            invalidFields.remove(latEdit)
            invalidFields.remove(lonEdit)
        }

        Log.d(TAG1, "Waypoint added, new size=${waypointRows.size}")
    }

    private fun clearAllFields() {
        // 1. Clear airport codes
        actDepartureIATA.setText("")
        actDestinationIATA.setText("")

        // 2. Reset timestamps: departure = now, arrival = now + 3h
        val now = System.currentTimeMillis()
        toTimestamp = now
        ldgTimestamp = now + 3 * 3600_000L

        // 3. Update date/time UI
        updateDepartureDateDisplay()
        etIoTime.setText(extractTimeOnly(now))
        updateArrivalDateDisplay()
        etLdgTime.setText(extractTimeOnly(ldgTimestamp!!))

        // 4. Reset elevation to default (from preferences)
        val defaultAlt = PreferencesManager.getInstance(this).getDefaultAltitude()
        etElevation.setText(defaultAlt.toString())

        // 5. Clear all waypoints
        waypointContainer.removeAllViews()
        waypointRows.clear()

        // 6. Clear result fields
        tvResults.text = ""
        etDepartureSunriseMoving.setText("")
        etDepartureSunsetMoving.setText("")
        etArrivalSunriseMoving.setText("")
        etArrivalSunsetMoving.setText("")
        etDepartureAstroDawn.setText("")
        etDepartureAstroDusk.setText("")
        etArrivalAstroDawn.setText("")
        etArrivalAstroDusk.setText("")

        // 7. Hide apply button
        btnApplyToLeg.visibility = View.GONE

        // 8. Clear any warnings (invalid fields)
        for (editText in invalidFields) {
            clearWarning(editText)
        }
        invalidFields.clear()

        // 9. Reset any stored events
        lastEvents = emptyList()
        lastToIsDay = false
        lastLdgIsDay = false

        // 10. Dismiss keyboard and clear focus
        clearFocusAndHideKeyboard()

        // 11. Ensure arrival is after departure (just in case)
        ensureArrivalAfterDeparture()
        ensureArrivalWithinOneDay()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    // ----- Helper to compute horizon elevation from altitude (meters) -----
    private fun getHorizonElevation(elevationMeters: Double): Double {
        val effectiveElevation = maxOf(0.0, elevationMeters)
        val elevationAdjustment = 0.0347 * kotlin.math.sqrt(effectiveElevation)
        return -0.833 - elevationAdjustment
    }

    // ----- Main computation -----
    private fun computeAndDisplay() {
        clearFocusAndHideKeyboard()

        if (invalidFields.isNotEmpty()) {
            Toast.makeText(this, "Please correct the marked fields", Toast.LENGTH_SHORT).show()
            return
        }
        val depIATA = actDepartureIATA.text.toString().trim().uppercase()
        val arrIATA = actDestinationIATA.text.toString().trim().uppercase()

        if (depIATA.isEmpty() || arrIATA.isEmpty()) {
            Toast.makeText(this, "Please enter both airport codes", Toast.LENGTH_SHORT).show()
            return
        }

        val depAirport = airportManager.findAirportByCode(depIATA)
        val arrAirport = airportManager.findAirportByCode(arrIATA)

        if (depAirport == null) {
            Toast.makeText(this, "Departure airport '$depIATA' not found", Toast.LENGTH_SHORT).show()
            return
        }
        if (arrAirport == null) {
            Toast.makeText(this, "Destination airport '$arrIATA' not found", Toast.LENGTH_SHORT).show()
            return
        }

        val toTime = toTimestamp
        val ldgTime = ldgTimestamp

        if (toTime == null || ldgTime == null) {
            Toast.makeText(this, "Please set both departure and arrival times", Toast.LENGTH_SHORT).show()
            return
        }

        if (ldgTime <= toTime) {
            ensureArrivalAfterDeparture()
//            ensureArrivalWithinOneDay()
            Toast.makeText(this, "Arrival was adjusted to be after departure", Toast.LENGTH_SHORT).show()
            return
        }

        val depElevMeters = depAirport.elevation * 0.3048
        val arrElevMeters = arrAirport.elevation * 0.3048

        val userElevationFeet = etElevation.text.toString().toIntOrNull() ?: 0
        val userElevationMeters = userElevationFeet.toDouble() * 0.3048

        val depHorizon = getHorizonElevation(depElevMeters)
        val arrHorizon = getHorizonElevation(arrElevMeters)

        // ----- OffBlock / OnBlock day/night (using local immutable copies) -----
        val offTs = offBlockTimestamp
        val onTs = onBlockTimestamp

        lastOffBlockIsDay = if (offTs != null) {
            val elevation = MovingSunriseCalculator.computeSolarElevation(
                depAirport.latitude, depAirport.longitude, offTs
            )
            elevation > depHorizon
        } else null

        lastOnBlockIsDay = if (onTs != null) {
            val elevation = MovingSunriseCalculator.computeSolarElevation(
                arrAirport.latitude, arrAirport.longitude, onTs
            )
            elevation > arrHorizon
        } else null

        btnApplyToLeg.visibility = View.GONE
        progressBar.visibility = View.VISIBLE
        btnCompute.isEnabled = false
        tvResults.text = "Computing..."

        // --- Collect waypoints from holders (source of truth = parsed values) ---
        val waypoints = waypointRows.mapNotNull { holder ->
            val lat = holder.currentValidLat
            val lon = holder.currentValidLon
            val time = holder.timeStr?.let { str ->
                val minutes = str.toMinutesOrNull() ?: return@let null
                val depTs = toTimestamp
                val base = depTs?.let { getDateAtMidnight(it) } ?: getTodayMidnight()
                var c = base + minutes * 60_000L
                if (depTs != null && c < depTs) c += 24L * 60 * 60 * 1000
                c
            }

            if (lat != null || lon != null || time != null) {
                Waypoint(lat, lon, time)
            } else null
        }


        // Cancel any previously running thread
        computeThread?.interrupt()
        computeThread = Thread {
            try {
                // --- 4. Solar elevation angles (raw) ---
                val depElevation = MovingSunriseCalculator.computeSolarElevation(
                    depAirport.latitude, depAirport.longitude, toTime
                )
                // Check interruption before continuing heavy work
                if (Thread.interrupted()) throw InterruptedException()

                val arrElevation = MovingSunriseCalculator.computeSolarElevation(
                    arrAirport.latitude, arrAirport.longitude, ldgTime
                )
                if (Thread.interrupted()) throw InterruptedException()

                val depIsDay = depElevation > depHorizon
                val arrIsDay = arrElevation > arrHorizon

                // --- 6. Moving flight events ---
                val result = MovingSunriseCalculator.computeEventsWithWaypointsAndFilledRoute(
                    airportManager = airportManager,
                    depIATA = depIATA,
                    arrIATA = arrIATA,
                    depUTC = toTime,
                    arrUTC = ldgTime,
                    elevationMeters = userElevationMeters,
                    waypoints = waypoints
                )
                val events = result.events
                val filledRoute = result.filledWaypoints

                if (Thread.interrupted()) throw InterruptedException()

                // --- 7. Static sunrise/sunset for departure ---
                val depCal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
                    .apply { timeInMillis = toTime }
                val arrCal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
                    .apply { timeInMillis = ldgTime }

                // departure Sunrise/sunset
                val depSunriseTime = findSunriseSunsetUsingMoving(
                    depAirport.latitude, depAirport.longitude, depElevMeters, depCal, true
                )
                val depSunriseStr = depSunriseTime?.let { extractTimeOnly(it.time) } ?: ""

                val depSunsetTime = findSunriseSunsetUsingMoving(
                    depAirport.latitude, depAirport.longitude, depElevMeters, depCal, false
                )
                val depSunsetStr = depSunsetTime?.let { extractTimeOnly(it.time) } ?: ""


                // arrival Sunrise/sunset
                val arrSunriseTime = findSunriseSunsetUsingMoving(
                    arrAirport.latitude, arrAirport.longitude, arrElevMeters, arrCal, true
                )
                val arrSunriseStr = arrSunriseTime?.let { extractTimeOnly(it.time) } ?: ""

                val arrSunsetTime = findSunriseSunsetUsingMoving(
                    arrAirport.latitude, arrAirport.longitude, arrElevMeters, arrCal, false
                )
                val arrSunsetStr = arrSunsetTime?.let { extractTimeOnly(it.time) } ?: ""

                // NEW: astronomical twilight for departure
                val depAstroDawnTime = findTwilightUsingMoving(depAirport.latitude, depAirport.longitude, depCal, true)
                val depAstroDuskTime = findTwilightUsingMoving(depAirport.latitude, depAirport.longitude, depCal, false)
                val depAstroDawnStr = depAstroDawnTime?.let { extractTimeOnly(it.time) } ?: ""
                val depAstroDuskStr = depAstroDuskTime?.let { extractTimeOnly(it.time) } ?: ""

                // NEW: astronomical twilight for arrival
                val arrAstroDawnTime = findTwilightUsingMoving(arrAirport.latitude, arrAirport.longitude, arrCal, true)
                val arrAstroDuskTime = findTwilightUsingMoving(arrAirport.latitude, arrAirport.longitude, arrCal, false)
                val arrAstroDawnStr = arrAstroDawnTime?.let { extractTimeOnly(it.time) } ?: ""
                val arrAstroDuskStr = arrAstroDuskTime?.let { extractTimeOnly(it.time) } ?: ""
                if (Thread.interrupted()) throw InterruptedException()

                // Update UI on main thread only if not interrupted
                runOnUiThread {
                    progressBar.visibility = View.GONE
                    btnCompute.isEnabled = true

                    etDepartureSunriseMoving.setText(depSunriseStr)
                    etDepartureSunsetMoving.setText(depSunsetStr)
                    etArrivalSunriseMoving.setText(arrSunriseStr)
                    etArrivalSunsetMoving.setText(arrSunsetStr)

                    etDepartureAstroDawn.setText(depAstroDawnStr)
                    etDepartureAstroDusk.setText(depAstroDuskStr)
                    etArrivalAstroDawn.setText(arrAstroDawnStr)
                    etArrivalAstroDusk.setText(arrAstroDuskStr)

                    // Update waypoint rows with computed values (only if fields are empty).
                    // filledRoute was built from the non-empty rows, so match by that list,
                    // not by waypointRows (which may contain empty rows at other indices).
                    val nonEmptyRows = getNonEmptyRows()
                    val filledWaypoints = filledRoute.drop(1).dropLast(1)
                    for (i in filledWaypoints.indices) {
                        val holder = nonEmptyRows.getOrNull(i) ?: continue
                        val wp = filledWaypoints[i]

                        wp.latitude?.let { lat ->
                            if (holder.currentValidLat == null) {
                                holder.currentValidLat = lat
                                clearWarning(holder.latEdit)
                                holder.latEdit.setText(formatCoordinate(lat, true))
                                holder.isLatTemporary = true
                                holder.latEdit.setTextAppearance(R.style.EditTextTemporaryStyle)
                            }
                        }
                        wp.longitude?.let { lon ->
                            if (holder.currentValidLon == null) {
                                holder.currentValidLon = lon
                                clearWarning(holder.lonEdit)
                                holder.lonEdit.setText(formatCoordinate(lon, false))
                                holder.isLonTemporary = true
                                holder.lonEdit.setTextAppearance(R.style.EditTextTemporaryStyle)
                            }
                        }
                        // Fill time if missing
                        wp.timeUTC?.let { time ->
                            if (holder.timeStr == null) {
                                val timeStr = extractTimeOnly(time)
                                holder.timeEdit.setText(timeStr)
                                holder.isTimeTemporary = true
                                holder.timeEdit.setTextAppearance(R.style.EditTextTemporaryStyle)
                                holder.timeStr = timeStr
                            }
                        }
                    }

                    displayResults(events, depIsDay, arrIsDay)
                    btnApplyToLeg.visibility = if (fromLegEdit) View.VISIBLE else View.GONE
                }

                lastEvents = events
                lastToIsDay = depIsDay
                lastLdgIsDay = arrIsDay

            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    progressBar.visibility = View.GONE
                    btnCompute.isEnabled = true
                    tvResults.text = "Error: ${e.message}"
                    btnApplyToLeg.visibility = View.GONE
                }
            } finally {
                // Ensure UI is re-enabled if we get here without interruption
                if (!Thread.currentThread().isInterrupted) {
                    runOnUiThread {
                        // In case the progress bar is still visible due to an unhandled case
                        if (progressBar.isVisible) {
                            progressBar.visibility = View.GONE
                            btnCompute.isEnabled = true
                        }
                    }
                }
            }
        }
        computeThread?.start()
    }
    private fun displayResults(
        events: List<MovingSunriseCalculator.SunEvent>,
        depIsDay: Boolean,
        arrIsDay: Boolean
    ) {
        val sb = StringBuilder()
        if (events.isEmpty()) {
            sb.append("No sun event occurred during this flight.\n\n")
        } else {
            sb.append("Found ${events.size} event(s):\n\n")
            for ((index, event) in events.withIndex()) {
                sb.append("${index + 1}. ${event.type}\n")
                sb.append("   Time: ${extractTimeOnly(event.timeUTC.time)} UTC\n")
                sb.append("   Position: ${formatCoordinate(event.latitude, true)} ${formatCoordinate(event.longitude, false)}\n")
                sb.append("\n")
            }
        }
        sb.append("OffBlock by ${if (lastOffBlockIsDay == true) "Day" else if (lastOffBlockIsDay == false) "Night" else "N/A"}\n")
        sb.append("Takeoff by ${if (depIsDay) "Day" else "Night"}\n")
        sb.append("Landing by ${if (arrIsDay) "Day" else "Night"}\n")
        sb.append("OnBlock by ${if (lastOnBlockIsDay == true) "Day" else if (lastOnBlockIsDay == false) "Night" else "N/A"}")
        tvResults.text = sb.toString()
    }

    // Helper to find sunrise/sunset (unchanged algorithm)
    private fun findSunriseSunsetUsingMoving(
        latitude: Double,
        longitude: Double,
        elevationMeters: Double,
        dateCalendar: Calendar,
        findSunrise: Boolean
    ): Date? {
        val effectiveElevation = maxOf(0.0, elevationMeters)
        val elevationAdjustment = 0.0347 * kotlin.math.sqrt(effectiveElevation)
        val horizonElevation = -0.833 - elevationAdjustment

        val cal = dateCalendar.clone() as Calendar
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val startOfDay = cal.timeInMillis
        val endOfDay = startOfDay + 24 * 60 * 60 * 1000L

        val step = 60_000L
        var prevElevation: Double? = null
        var prevTime = startOfDay

        var t = startOfDay
        while (t <= endOfDay) {
            val elevation = MovingSunriseCalculator.computeSolarElevation(latitude, longitude, t)
            if (prevElevation != null) {
                if ((prevElevation < horizonElevation && elevation > horizonElevation) ||
                    (prevElevation > horizonElevation && elevation < horizonElevation)
                ) {
                    val ratio = (horizonElevation - prevElevation) / (elevation - prevElevation)
                    val crossTime = prevTime + (ratio * (t - prevTime)).toLong()
                    val isSunrise = prevElevation < horizonElevation
                    if (isSunrise == findSunrise) {
                        return Date(crossTime)
                    }
                }
            }
            prevElevation = elevation
            prevTime = t
            t += step
        }
        return null
    }

    /**
     * Finds the astronomical dawn or dusk time at a given location on a given date.
     * @param findDawn true for dawn (Sun rises above -18°), false for dusk (Sun sets below -18°)
     */
    private fun findTwilightUsingMoving(
        latitude: Double,
        longitude: Double,
        dateCalendar: Calendar,
        findDawn: Boolean
    ): Date? {
        val targetElevation = -18.0 // astronomical twilight (geometric)

        val cal = dateCalendar.clone() as Calendar
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val startOfDay = cal.timeInMillis
        val endOfDay = startOfDay + 24 * 60 * 60 * 1000L

        val step = 60_000L
        var prevElevation: Double? = null
        var prevTime = startOfDay

        var t = startOfDay
        while (t <= endOfDay) {
            val elevation = MovingSunriseCalculator.computeSolarElevation(latitude, longitude, t)
            if (prevElevation != null) {
                if ((prevElevation < targetElevation && elevation > targetElevation) ||
                    (prevElevation > targetElevation && elevation < targetElevation)
                ) {
                    val ratio = (targetElevation - prevElevation) / (elevation - prevElevation)
                    val crossTime = prevTime + (ratio * (t - prevTime)).toLong()
                    val isDawn = prevElevation < targetElevation
                    if (isDawn == findDawn) {
                        return Date(crossTime)
                    }
                }
            }
            prevElevation = elevation
            prevTime = t
            t += step
        }
        return null
    }

    private fun getNonEmptyRows(): List<WaypointRowHolder> =
        waypointRows.filter {
            it.currentValidLat != null || it.currentValidLon != null || it.timeStr != null
        }

    private fun allNonEmptyRowsHaveUserTime(): Boolean {
        val nonEmpty = getNonEmptyRows()
        return nonEmpty.isNotEmpty() && nonEmpty.all { it.timeStr != null && !it.isTimeTemporary }
    }

    private fun buildRouteFromUi(): MutableList<Waypoint>? {
        val depTs = toTimestamp ?: return null
        val arrTs = ldgTimestamp ?: return null
        val depCode = actDepartureIATA.text.toString().trim().uppercase()
        val arrCode = actDestinationIATA.text.toString().trim().uppercase()
        if (depCode.isEmpty() || arrCode.isEmpty()) return null
        val depAirport = airportManager.findAirportByCode(depCode) ?: return null
        val arrAirport = airportManager.findAirportByCode(arrCode) ?: return null

        val route = mutableListOf<Waypoint>()
        route.add(Waypoint(depAirport.latitude, depAirport.longitude, depTs))

        for (holder in getNonEmptyRows()) {
            val hasLat = holder.currentValidLat != null
            val hasLon = holder.currentValidLon != null
            if (hasLat != hasLon) return null

            val time = holder.timeStr?.let { str ->
                val minutes = str.toMinutesOrNull() ?: return@let null
                val base = getDateAtMidnight(depTs)
                var c = base + minutes * 60_000L
                if (c < depTs) c += 24L * 60 * 60 * 1000
                c
            }
            route.add(Waypoint(holder.currentValidLat, holder.currentValidLon, time))
        }

        route.add(Waypoint(arrAirport.latitude, arrAirport.longitude, arrTs))
        return route
    }

    private fun applyRouteBackToUi(route: List<Waypoint>) {
        val nonEmpty = getNonEmptyRows()
        val filled = route.drop(1).dropLast(1)
        for (i in nonEmpty.indices) {
            val holder = nonEmpty[i]
            val wp = filled.getOrNull(i) ?: continue

            val wpLat = wp.latitude
            if (holder.currentValidLat == null && wpLat != null) {
                holder.currentValidLat = wpLat
                clearWarning(holder.latEdit)
                holder.latEdit.setText(formatCoordinate(wpLat, true))
                holder.isLatTemporary = true
                holder.latEdit.setTextAppearance(R.style.EditTextTemporaryStyle)
            }

            val wpLon = wp.longitude
            if (holder.currentValidLon == null && wpLon != null) {
                holder.currentValidLon = wpLon
                clearWarning(holder.lonEdit)
                holder.lonEdit.setText(formatCoordinate(wpLon, false))
                holder.isLonTemporary = true
                holder.lonEdit.setTextAppearance(R.style.EditTextTemporaryStyle)
            }

            val wpTime = wp.timeUTC
            if (holder.timeStr == null && wpTime != null) {
                val timeStr = extractTimeOnly(wpTime)
                holder.timeStr = timeStr
                holder.timeEdit.setText(timeStr)
                holder.isTimeTemporary = true
                holder.timeEdit.setTextAppearance(R.style.EditTextTemporaryStyle)
            }
        }
    }

    private fun applyCoordinatePairState(holder: WaypointRowHolder) {
        val hasLat = holder.currentValidLat != null
        val hasLon = holder.currentValidLon != null
        when {
            hasLat && hasLon -> {
                clearWarning(holder.latEdit)
                clearWarning(holder.lonEdit)
                triggerCoordinateFill()
            }
            hasLat && !hasLon -> {
                clearWarning(holder.latEdit)
                markWarning(holder.lonEdit)
            }
            !hasLat && hasLon -> {
                markWarning(holder.latEdit)
                clearWarning(holder.lonEdit)
            }
            else -> {
                clearWarning(holder.latEdit)
                clearWarning(holder.lonEdit)
            }
        }
    }

    private fun triggerCoordinateFill() {
        val route = buildRouteFromUi() ?: return
        MovingSunriseCalculator.fillMissingTimes(route)
        MovingSunriseCalculator.fillMissingCoordinates(route)
        applyRouteBackToUi(route)
    }

    private fun sortRowsByTime() {
        val toRemove = waypointRows.filter {
            it.currentValidLat == null && it.currentValidLon == null && it.timeStr == null
        }
        for (holder in toRemove) {
            waypointContainer.removeView(holder.rowView)
            invalidFields.remove(holder.latEdit)
            invalidFields.remove(holder.lonEdit)
            invalidFields.remove(holder.timeEdit)
        }
        waypointRows.removeAll(toRemove)

        waypointRows.sortBy { it.timeStr ?: "" }

        waypointContainer.removeAllViews()
        for (holder in waypointRows) {
            waypointContainer.addView(holder.rowView)
        }
    }

    private fun saveCurrentState() {
        val depIATA = actDepartureIATA.text.toString().trim()
        val arrIATA = actDestinationIATA.text.toString().trim()
        val depTime = toTimestamp
        val arrTime = ldgTimestamp
        val elevation = etElevation.text.toString().toIntOrNull() ?: 0
        // Collect waypoint data
        val waypoints = waypointRows.map { holder ->
            PreferencesManager.WaypointRowData(
                lat = holder.currentValidLat,
                lon = holder.currentValidLon,
                time = holder.timeStr
            )
        }

        PreferencesManager.getInstance(this).saveFlightSunState(
            depIATA = depIATA,
            arrIATA = arrIATA,
            depTime = depTime,
            arrTime = arrTime,
            elevation = elevation,
            waypoints = waypoints
        )
        Toast.makeText(this, "State saved", Toast.LENGTH_SHORT).show()
    }

    private fun loadSavedState() {
        val state = PreferencesManager.getInstance(this).loadFlightSunState() ?: run {
            Toast.makeText(this, "No saved state found", Toast.LENGTH_SHORT).show()
            return
        }



        // Set timestamps
        toTimestamp = state.toTime
        ldgTimestamp = state.ldgTime
        etIoTime.setText(state.toTime?.let { extractTimeOnly(it) } ?: "")
        etLdgTime.setText(state.ldgTime?.let { extractTimeOnly(it) } ?: "")

        updateDepartureDateDisplay()
        updateArrivalDateDisplay()

        clearWarning(etIoTime)
        clearWarning(etLdgTime)

        // Set airport fields
        actDepartureIATA.setText(state.depIATA)
        actDestinationIATA.setText(state.arrIATA)

        // Elevation
        etElevation.setText(state.elevation.toString())

        // Remove all existing waypoint rows
        waypointContainer.removeAllViews()
        waypointRows.clear()

        // Recreate waypoints
        for (wp in state.waypoints) {
            addWaypointRow(
                initialLat = wp.lat,
                initialLon = wp.lon,
                initialTime = wp.time,
                latTemporary = false,
                lonTemporary = false,
                timeTemporary = false
            )
        }

        // Ensure arrival is after departure
//        ensureArrivalAfterDeparture()
//        ensureArrivalWithinOneDay()

        Toast.makeText(this, "State loaded", Toast.LENGTH_SHORT).show()
    }


    fun disableDictionarySuggestions(viewGroup: ViewGroup) {
        for (i in 0 until viewGroup.childCount) {
            when (val child = viewGroup.getChildAt(i)) {
                is EditText -> setNoSuggestions(child)
                is ViewGroup -> disableDictionarySuggestions(child)
            }
        }
    }

    fun setNoSuggestions(editText: EditText) {
        val current = editText.inputType
        val cls = current and InputType.TYPE_MASK_CLASS
        if (cls == InputType.TYPE_CLASS_TEXT) {
            // Keep class and existing flags, add NO_SUGGESTIONS
            val newType = cls or
                    (current and InputType.TYPE_MASK_FLAGS) or
                    InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            editText.inputType = newType
        }
   }

    private fun setupEnterKeyToClearFocus(textView: TextView) {
        textView.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_NEXT) {
                textView.clearFocus()
                // Hide the keyboard
                val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(textView.windowToken, 0)
                true
            } else {
                false
            }
        }
    }
    private fun markWarning(editText: EditText) {
        editText.setBackgroundColor(ContextCompat.getColor(this, R.color.warning))
        invalidFields.add(editText)
    }

    private fun clearWarning(editText: EditText) {
        val default = defaultBackgrounds[editText]
        if (default != null) {
            editText.background = default
        } else {
            editText.setBackgroundResource(android.R.drawable.editbox_background)
        }
        invalidFields.remove(editText)
    }

    private fun clearFocusAndHideKeyboard() {
        val view = currentFocus
        view?.clearFocus()
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view?.windowToken, 0)
    }

}