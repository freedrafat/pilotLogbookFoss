package com.my.striptologbook.ui

import androidx.appcompat.app.AlertDialog
import android.os.Bundle
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.widget.Toolbar
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.my.striptologbook.LogbookApplication
import com.my.striptologbook.R
import com.my.striptologbook.utils.formatDateOnly
import com.my.striptologbook.utils.showCustomDatePicker
import kotlinx.coroutines.launch
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import androidx.core.content.ContextCompat
import com.my.striptologbook.data.AirportDataManager
import com.my.striptologbook.utils.normalizeDuration
import com.my.striptologbook.utils.normalizeTime

import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import com.my.striptologbook.data.DefaultFields
import com.my.striptologbook.utils.MovingSunriseCalculator
import com.my.striptologbook.utils.PreferencesManager
import com.my.striptologbook.utils.extractTimeOnly
import java.util.Date

import com.my.striptologbook.utils.InsetsHelper

class LegEditActivity : BaseActivity() {

    private lateinit var viewModel: LegEditViewModel

    private lateinit var scrollView: ScrollView
    // Views
    private lateinit var toolbar: Toolbar

    private lateinit var btnEdit: Button
    private lateinit var btnCancel: Button

    private lateinit var tvDate: TextView
    private lateinit var actFlightNumber: AutoCompleteTextView
    private lateinit var actPosition: AutoCompleteTextView
    private lateinit var actCategory: AutoCompleteTextView
    private lateinit var actPicName: AutoCompleteTextView
    private lateinit var actSicName: AutoCompleteTextView
    private lateinit var jumpseatLayout: LinearLayout
    private lateinit var actJumpseat1: AutoCompleteTextView
    private lateinit var actJumpseat2: AutoCompleteTextView
    private lateinit var actCabinCrew: AutoCompleteTextView
    private lateinit var actRegistration: AutoCompleteTextView
    private lateinit var actType: AutoCompleteTextView

    private lateinit var actDeparture: AutoCompleteTextView
    private lateinit var actDestination: AutoCompleteTextView
    private lateinit var etOffBlock: EditText
    private lateinit var etOnBlock: EditText
    private lateinit var etTakeoff: EditText
    private lateinit var etLanding: EditText
    private lateinit var sunriseSunsetLayout: LinearLayout
    private lateinit var etSunrise: AutoCompleteTextView
    private lateinit var etSunset: AutoCompleteTextView
    private lateinit var etBlockTotal: EditText
    private lateinit var etBlockNight: EditText
    private lateinit var etBlockDay: EditText
    private lateinit var etFlightTotal: EditText
    private lateinit var etFlightNight: EditText
    private lateinit var etFlightDay: EditText
    private lateinit var etSimulatorTime: EditText
    private lateinit var cbIfr: CheckBox
    private lateinit var etIfrTotal: EditText
    private lateinit var etIfrNight: EditText
    private lateinit var etIfrDay: EditText
    private lateinit var instAppLayout: LinearLayout
    // inst_app_layout

    private lateinit var etInstrumentApproaches: EditText
    private lateinit var toLayout: LinearLayout

    private lateinit var etToDay: EditText
    private lateinit var etToNight: EditText
    private lateinit var etToTotal: EditText
    private lateinit var ldgLayout: LinearLayout
    private lateinit var etLdgDay: EditText
    private lateinit var etLdgNight: EditText
    private lateinit var etLdgTotal: EditText
    private lateinit var cbSimulator: CheckBox
    private lateinit var cbPilotFlying: CheckBox
    private lateinit var etComment: EditText
    private lateinit var btnSave: Button
    private lateinit var btnDelete: Button
    private lateinit var simulatorLayout: LinearLayout
    private val invalidFields = mutableSetOf<EditText>()
    private val defaultBackgrounds = mutableMapOf<EditText, android.graphics.drawable.Drawable>()

    private var isUpdatingFromViewModel = false
    private var isEditing = false
    private var isImportFromExternal = false
    private lateinit var btnCputNight: Button
    private lateinit var nightResultLauncher: ActivityResultLauncher<Intent>

    private companion object {
        const val TAG = "NightButtonFlow"
    }

    private lateinit var fixedPositionList: List<String>
    private lateinit var fixedCategoryList: List<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_leg_edit)

        disableDictionarySuggestions(findViewById(android.R.id.content)!!)

        // ---- ViewModel and defaults
        val legId = intent.getLongExtra("legId", -1).takeIf { it != -1L }

        val airportManager = AirportDataManager.getInstance(this)
        val app = application as LogbookApplication
        val prefs = PreferencesManager.getInstance(this)
        fixedPositionList = prefs.getCustomPositionList()
        fixedCategoryList = prefs.getCustomCategoryList()
        val defaultFields = DefaultFields(
            isIfr = prefs.getDefaultIsIfr(),
            simuIfrApproach = prefs.getDefaultSimuIfrApproach(),
            position = prefs.getDefaultPosition(),
            // new
            picName = prefs.getDefaultPicName(),
            sicName = prefs.getDefaultSicName(),
            jumpseat1 = prefs.getDefaultJumpseat1(),
            jumpseat2 = prefs.getDefaultJumpseat2(),
            cabinCrew = prefs.getDefaultCabinCrew(),
            acRegistration = prefs.getDefaultAcRegistration(),
            flightNumberPrefix = prefs.getDefaultFlightNumberPrefix(),
            icaoDeparture = prefs.getDefaultIcaoDeparture(),
            icaoDestination = prefs.getDefaultIcaoDestination(),
            comment = prefs.getDefaultComment(),
            offBlockIsNow = prefs.getDefaultOffBlockNow(),
            isSimulatorDefault = prefs.getDefaultIsSimulator(),
            pilotFlyingDefault = prefs.getDefaultPilotFlying()
        )
        val factory = LegEditViewModelFactory(app.repository, legId, airportManager, defaultFields)
        viewModel = ViewModelProvider(this, factory)[LegEditViewModel::class.java]

        initViews()

        // ---- Shared toolbar: spacer + IME + scroll-to-focus ----
        InsetsHelper.applyStatusBarSpacer(findViewById(R.id.status_bar_spacer))
        InsetsHelper.attachScrollToFocusedView(this, scrollView)
        InsetsHelper.applyImeBottomPaddingToRoot(this)

        // ---- Toolbar  ----
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = if (legId == null) "Add Leg" else "Edit Leg"

        // -------- ScrollView and focus / IME handling -------------
        val scrollView = findViewById<ScrollView>(R.id.scroll_view)
        InsetsHelper.attachScrollToFocusedView(this, scrollView)
        InsetsHelper.applyImeBottomPaddingToRoot(this)

        setupListeners()
        setupAutosuggest()
        observeState()
        handleImportExtras()

        // --- For a new leg with a default registration, run the existing
        //     registration -> type -> category chain once, without going through
        //     the focus-loss listener or handleImportExtras(). ---
        if (legId == null && defaultFields.acRegistration.isNotEmpty()) {
            lifecycleScope.launch {
                val type = viewModel.getMostRecentTypeByRegistration(defaultFields.acRegistration)
                if (!type.isNullOrEmpty()) {
                    viewModel.updateAcType(type)
                    if (viewModel.state.value.category.isEmpty()) {
                        val category = viewModel.getMostRecentCategoryByAcType(type)
                        if (!category.isNullOrEmpty()) {
                            viewModel.updateCategory(category)
                        }
                    }
                }
            }
        }

        // set initial editing mode
        isEditing = if (legId == null) {
            // New leg -> edit mode directly
            true
        } else {
            // Existing leg -> view mode
            false
        }

        // Apply UI state (will be called again after data loads, but we set it initially)
        updateUiForMode()

        // NEW: override back behavior with dialog
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isEditing) {
                    showDiscardDialog { finish() }
                } else {
                    finish()
                }
            }
        })
    }

    private fun initViews() {
        scrollView = findViewById(R.id.scroll_view)
        toolbar = findViewById(R.id.toolbar)
        tvDate = findViewById(R.id.tv_date)
        actFlightNumber = findViewById(R.id.act_flight_number)
        actPosition = findViewById(R.id.act_position)
        actCategory = findViewById(R.id.act_category)
        actPicName = findViewById(R.id.act_pic_name)
        actSicName = findViewById(R.id.act_sic_name)
        actCabinCrew = findViewById(R.id.act_cabin_crew)
        jumpseatLayout = findViewById(R.id.jumpseat_layout)
        actJumpseat1 = findViewById(R.id.act_jumpseat1)
        actJumpseat2 = findViewById(R.id.act_jumpseat2)
        actRegistration = findViewById(R.id.act_registration)
        actType = findViewById(R.id.act_type)
        actDeparture = findViewById(R.id.act_departure)
        actDestination = findViewById(R.id.act_destination)
        etOffBlock = findViewById(R.id.et_offblock)
        etOnBlock = findViewById(R.id.et_onblock)
        etTakeoff = findViewById(R.id.et_takeoff)
        etLanding = findViewById(R.id.et_landing)
        sunriseSunsetLayout = findViewById(R.id.et_sunrise_sunset)
        etSunrise = findViewById(R.id.et_sunrise)
        etSunset = findViewById(R.id.et_sunset)
        btnCputNight = findViewById(R.id.btn_cput_night)
        etBlockTotal = findViewById(R.id.et_block_total)
        etBlockNight = findViewById(R.id.et_block_night)
        etBlockDay = findViewById(R.id.et_block_day)
        etFlightTotal = findViewById(R.id.et_flight_total)
        etFlightNight = findViewById(R.id.et_flight_night)
        etFlightDay = findViewById(R.id.et_flight_day)
        simulatorLayout = findViewById(R.id.simulator_layout)
        etSimulatorTime = findViewById(R.id.et_simulator_time)
        cbIfr = findViewById(R.id.cb_ifr)
        etIfrDay = findViewById(R.id.et_ifr_day)
        etIfrNight = findViewById(R.id.et_ifr_night)
        etIfrTotal = findViewById(R.id.et_ifr_total)
        instAppLayout = findViewById(R.id.inst_app_layout)
        etInstrumentApproaches = findViewById(R.id.et_instrument_approaches)
        toLayout = findViewById(R.id.to_layout)
        etToDay = findViewById(R.id.et_to_day)
        etToNight = findViewById(R.id.et_to_night)
        etToTotal = findViewById(R.id.et_to_total)
        ldgLayout = findViewById(R.id.ldg_layout)
        etLdgDay = findViewById(R.id.et_ldg_day)
        etLdgNight = findViewById(R.id.et_ldg_night)
        etLdgTotal = findViewById(R.id.et_ldg_total)
        cbSimulator = findViewById(R.id.cb_simulator)
        cbPilotFlying = findViewById(R.id.cb_pilot_flying)
        etComment = findViewById(R.id.et_comment)

        btnEdit = findViewById(R.id.btn_edit)
        btnCancel = findViewById(R.id.btn_cancel)

        btnSave = findViewById(R.id.btn_save)
        btnDelete = findViewById(R.id.btn_delete)

        // ----- Store default backgrounds for validated fields -----
        val validatedFields = listOf(
            etOffBlock, etOnBlock, etTakeoff, etLanding, etSunrise, etSunset,
            etBlockTotal, etBlockNight, etFlightTotal, etFlightNight,
            etSimulatorTime, etIfrNight, etIfrTotal
        )
        for (field in validatedFields) {
            defaultBackgrounds[field] = field.background
        }
    }

    private fun setupListeners() {
        // Helper function: Press Enter to clear focus
        fun setupEnterKeyToClearFocus(editText: EditText) {
            editText.setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_NEXT) {
                    editText.clearFocus()
                    true
                } else {
                    false
                }
            }
        }

        // Register ActivityResultLauncher from FlightSunActivity.kt
        nightResultLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == RESULT_OK) {
                val data = result.data
                val eventsStr = data?.getStringExtra("events") ?: ""
                val toStatus = data?.getStringExtra("toStatus") ?: ""
                val ldgStatus = data?.getStringExtra("ldgStatus") ?: ""
                val offBlockStatus = data?.getStringExtra("offBlockStatus") ?: ""
                val onBlockStatus = data?.getStringExtra("onBlockStatus") ?: ""
                val depSunrise = data?.getLongExtra("depSunrise", 0L) ?: 0L
                val depSunset = data?.getLongExtra("depSunset", 0L) ?: 0L
                val arrSunrise = data?.getLongExtra("arrSunrise", 0L) ?: 0L
                val arrSunset = data?.getLongExtra("arrSunset", 0L) ?: 0L

                // Parse events (only sunrise/sunset – already filtered in FlightSunActivity)
                val events = eventsStr.split(",").mapNotNull { part ->
                    val parts = part.split(":")
                    if (parts.size == 2) {
                        val type = parts[0]
                        val timestamp = parts[1].toLongOrNull()
                        if (timestamp != null) type to timestamp else null
                    } else null
                }

                if (events.size > 2) {
                    Toast.makeText(this, "Not yet implemented for more than 2 sun events.", Toast.LENGTH_SHORT).show()
                    return@registerForActivityResult
                }

                // Extract new sunrise/sunset (for the legacy UI fields)
                val newSunrise = when (events.size) {
                    1 -> if (events[0].first == "Sunrise") events[0].second else null
                    2 -> events.find { it.first == "Sunrise" }?.second
                    else -> null
                }
                val newSunset = when (events.size) {
                    1 -> if (events[0].first == "Sunset") events[0].second else null
                    2 -> events.find { it.first == "Sunset" }?.second
                    else -> null
                }

                // Update legacy sunrise/sunset fields (UI only)
                viewModel.updateSunriseTimeStr(newSunrise?.let { extractTimeOnly(it) } ?: "")
                viewModel.updateSunsetTimeStr(newSunset?.let { extractTimeOnly(it) } ?: "")
                viewModel.updateSunriseTimestamp(newSunrise)
                viewModel.updateSunsetTimestamp(newSunset)

                // Convert status strings to Booleans
                fun statusToBool(status: String): Boolean? = when (status) {
                    "Day" -> true
                    "Night" -> false
                    else -> null
                }

                val offBlockIsDay = statusToBool(offBlockStatus)
                val takeoffIsDay = statusToBool(toStatus)
                val landingIsDay = statusToBool(ldgStatus)
                val onBlockIsDay = statusToBool(onBlockStatus)

                // Pass all data to ViewModel
                viewModel.setSunData(
                    events = events.map { MovingSunriseCalculator.SunEvent(Date(it.second), 0.0, 0.0, it.first) },
                    offBlockIsDay = offBlockIsDay,
                    takeoffIsDay = takeoffIsDay,
                    landingIsDay = landingIsDay,
                    onBlockIsDay = onBlockIsDay,
                    depSunrise = depSunrise.takeIf { it != 0L },
                    depSunset = depSunset.takeIf { it != 0L },
                    arrSunrise = arrSunrise.takeIf { it != 0L },
                    arrSunset = arrSunset.takeIf { it != 0L }
                )

                // Trigger full recomputation of night durations and TO/LDG counts
                viewModel.recalcBlockDuration()
                viewModel.recalcFlightDuration()

                Log.d(TAG, "Sun data applied and recalculated")
            } else {
                Log.d(TAG, "Result cancelled – no updates")
            }
        }


        // ---------- Apply Enter key to all EditText fields ----------
        setupEnterKeyToClearFocus(actPosition)
        setupEnterKeyToClearFocus(actCategory)
        setupEnterKeyToClearFocus(actFlightNumber)
        setupEnterKeyToClearFocus(actPicName)
        setupEnterKeyToClearFocus(actSicName)
        setupEnterKeyToClearFocus(actJumpseat1)
        setupEnterKeyToClearFocus(actJumpseat2)
        setupEnterKeyToClearFocus(actCabinCrew)
        setupEnterKeyToClearFocus(actRegistration)
        setupEnterKeyToClearFocus(actType)
        setupEnterKeyToClearFocus(actDeparture)
        setupEnterKeyToClearFocus(actDestination)
        setupEnterKeyToClearFocus(etOffBlock)
        setupEnterKeyToClearFocus(etOnBlock)
        setupEnterKeyToClearFocus(etTakeoff)
        setupEnterKeyToClearFocus(etLanding)
        setupEnterKeyToClearFocus(etSunrise)
        setupEnterKeyToClearFocus(etSunset)
        setupEnterKeyToClearFocus(etBlockTotal)
        setupEnterKeyToClearFocus(etBlockNight)
        setupEnterKeyToClearFocus(etBlockDay)
        setupEnterKeyToClearFocus(etFlightTotal)
        setupEnterKeyToClearFocus(etFlightNight)
        setupEnterKeyToClearFocus(etFlightDay)
        setupEnterKeyToClearFocus(etSimulatorTime)
        setupEnterKeyToClearFocus(etIfrDay)
        setupEnterKeyToClearFocus(etIfrNight)
        setupEnterKeyToClearFocus(etIfrTotal)
        setupEnterKeyToClearFocus(etInstrumentApproaches)
        setupEnterKeyToClearFocus(etToDay)
        setupEnterKeyToClearFocus(etToNight)
        setupEnterKeyToClearFocus(etToTotal)
        setupEnterKeyToClearFocus(etLdgDay)
        setupEnterKeyToClearFocus(etLdgNight)
        setupEnterKeyToClearFocus(etLdgTotal)
        setupEnterKeyToClearFocus(etComment)

        // Date click
        tvDate.setOnClickListener {
            if (isEditing) {
                showCustomDatePicker(
                    context = this,
                    initialDate = viewModel.state.value.baseDate
                ) { selectedDate ->
                    viewModel.setBaseDate(selectedDate)
                    viewModel.recalculateTimestamps()
                    viewModel.recalcBlockDuration()
                    viewModel.recalcFlightDuration()
                }
            } // else do nothing
        }

        // ---------- Time Fields (normalize on focus lost + recompute) ----------
        etOffBlock.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val raw = etOffBlock.text.toString()
                val normalized = normalizeTime(raw)
                val currentStateValue = viewModel.state.value.offBlockTime

                if (normalized == "invalid") {
                    // Invalid input: revert, show warning, and regain focus
                    etOffBlock.setText(currentStateValue)
                    markFieldAsWarning(etOffBlock, true)

                } else if (normalized != currentStateValue) {
                    // Valid new value
                    if (normalized != raw) {
                        etOffBlock.setText(normalized)
                    }
                    viewModel.updateOffBlockTime(normalized)

                    markFieldAsWarning(etOffBlock, false)

                    viewModel.recalculateTimestamps()
                    viewModel.recalcBlockDuration()
                    viewModel.recalcFlightDuration()
                }
            } else {
                // Field gains focus, remove warning
                markFieldAsWarning(etOffBlock, false)
            }
        }

        etTakeoff.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val raw = etTakeoff.text.toString()
                val normalized = normalizeTime(raw)
                val currentStateValue = viewModel.state.value.takeoffTimeStr

                if (normalized == "invalid") {
                    // Invalid input: revert, show warning, and regain focus
                    etTakeoff.setText(currentStateValue)
                    markFieldAsWarning(etTakeoff, true)
                } else if (normalized != currentStateValue) {
                    // Valid new value
                    if (normalized != raw) {
                        etTakeoff.setText(normalized)
                    }
                    viewModel.updateTakeoffTimeStr(normalized)

                    markFieldAsWarning(etTakeoff, false)

                    viewModel.recalculateTimestamps()
                    viewModel.recalcBlockDuration()
                    viewModel.recalcFlightDuration()
                }
            } else {
                // Field gains focus, remove warning
                markFieldAsWarning(etTakeoff, false)
            }
        }

        etLanding.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val raw = etLanding.text.toString()
                val normalized = normalizeTime(raw)
                val currentStateValue = viewModel.state.value.landingTimeStr

                if (normalized == "invalid") {
                    // Invalid input: revert, show warning, and regain focus
                    etLanding.setText(currentStateValue)
                    markFieldAsWarning(etLanding, true)
                } else if (normalized != currentStateValue) {
                    // Valid new value
                    if (normalized != raw) {
                        etLanding.setText(normalized)
                    }
                    markFieldAsWarning(etLanding, false)

                    viewModel.updateLandingTimeStr(normalized)

                    viewModel.recalculateTimestamps()
                    viewModel.recalcBlockDuration()
                    viewModel.recalcFlightDuration()
                }
            } else {
                // Field gains focus, remove warning
                markFieldAsWarning(etLanding, false)
            }
        }

        etOnBlock.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val raw = etOnBlock.text.toString()
                val normalized = normalizeTime(raw)
                val currentStateValue = viewModel.state.value.onBlockTime

                if (normalized == "invalid") {
                    // Invalid input: revert, show warning, and regain focus
                    etOnBlock.setText(currentStateValue)
                    markFieldAsWarning(etOnBlock, true)
                } else if (normalized != currentStateValue) {
                    // Valid new value
                    if (normalized != raw) {
                        etOnBlock.setText(normalized)
                    }
                    markFieldAsWarning(etOnBlock, false)

                    viewModel.updateOnBlockTime(normalized)

                    viewModel.recalculateTimestamps()
                    viewModel.recalcBlockDuration()
                    viewModel.recalcFlightDuration()
                }
            } else {
                // Field gains focus, remove warning
                markFieldAsWarning(etOnBlock, false)
            }
        }

        etSunrise.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                Log.d("LegEditActivity_sun", "Sunrise lost focus, raw='${etSunrise.text}'")
                val raw = etSunrise.text.toString()
                val normalized = normalizeTime(raw)
                val currentStateValue = viewModel.state.value.sunriseTimeStr

                if (normalized == "invalid") {
                    etSunrise.setText(currentStateValue)
                    markFieldAsWarning(etSunrise, true)
                } else if (normalized != currentStateValue) {
                    if (normalized != raw) {
                        etSunrise.setText(normalized)
                    }
                    viewModel.updateSunriseTimeStr(normalized)
                    viewModel.clearSunData()
                    markFieldAsWarning(etSunrise, false)
                    viewModel.recalculateTimestamps()
                    Log.d(TAG, "New state: sunrise=${viewModel.state.value.sunriseTimeStr}, sunset=${viewModel.state.value.sunsetTimeStr}")

                    viewModel.recalcBlockDuration()
                    viewModel.recalcFlightDuration()
                }
            } else {
                markFieldAsWarning(etSunrise, false)
                Log.d("LegEditActivity_sun", "Sunrise field gained focus")
                val adapter = etSunrise.adapter
                if (adapter != null) {
                    Log.d("LegEditActivity_sun", "Sunrise adapter count: ${adapter.count}")
                    if (adapter.count > 0) {
                        Log.d("LegEditActivity_sun", "Showing sunrise dropdown")
                        etSunrise.showDropDown()
                    } else {
                        Log.d("LegEditActivity_sun", "Sunrise adapter is empty (count=0)")
                    }
                } else {
                    Log.d("LegEditActivity_sun", "Sunrise adapter is null")
                }
            }
        }

        etSunset.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                Log.d("LegEditActivity_sun", "Sunset lost focus, raw='${etSunrise.text}'")
                val raw = etSunset.text.toString()
                val normalized = normalizeTime(raw)
                val currentStateValue = viewModel.state.value.sunsetTimeStr

                if (normalized == "invalid") {
                    etSunset.setText(currentStateValue)
                    markFieldAsWarning(etSunset, true)
                } else if (normalized != currentStateValue) {
                    if (normalized != raw) {
                        etSunset.setText(normalized)
                    }
                    viewModel.updateSunsetTimeStr(normalized)
                    markFieldAsWarning(etSunset, false)
                    viewModel.recalculateTimestamps()
                    viewModel.recalcBlockDuration()
                    viewModel.recalcFlightDuration()
                }
            } else {
                markFieldAsWarning(etSunset, false)
                Log.d("LegEditActivity_sun", "Sunset field gained focus")
                val adapter = etSunset.adapter
                if (adapter != null) {
                    Log.d("LegEditActivity_sun", "Sunset adapter count: ${adapter.count}")
                    if (adapter.count > 0) {
                        Log.d("LegEditActivity_sun", "Showing sunset dropdown")
                        etSunset.showDropDown()
                    } else {
                        Log.d("LegEditActivity_sun", "Sunset adapter is empty (count=0)")
                    }
                } else {
                    Log.d("LegEditActivity_sun", "Sunset adapter is null")
                }
            }
        }

        // Click listener for Night button
        btnCputNight.setOnClickListener {
            if (!isEditing) {
                Log.d(TAG, "Night button clicked but not in edit mode – ignoring")
                return@setOnClickListener
            }

            Log.d(TAG, "Night button clicked – reading state")
            val depAirport = actDeparture.text.toString().trim()
            val arrAirport = actDestination.text.toString().trim()
            Log.d(TAG, "Airports: dep='$depAirport', arr='$arrAirport'")

            val state = viewModel.state.value
            var depTime = state.takeoffTime ?: state.offBlock
            var arrTime = state.landingTime ?: state.onBlock
            Log.d(TAG, "Raw timestamps: dep=$depTime, arr=$arrTime")

            // Fill missing with fallback
            if (depTime == null) {
                depTime = System.currentTimeMillis()
                arrTime = depTime + 3 * 3600_000L
                Log.d(TAG, "Departure time missing – set to now + 3h: dep=$depTime, arr=$arrTime")
            } else if (arrTime == null) {
                arrTime = depTime + 3 * 3600_000L
                Log.d(TAG, "Arrival time missing – set to dep + 3h: arr=$arrTime")
            } else if (arrTime <= depTime) {
                val adjusted = arrTime + 24 * 3600_000L
                Log.d(TAG, "Arrival ($arrTime) <= departure ($depTime) – adding 24h -> $adjusted")
                arrTime = adjusted
            }

            Log.d(TAG, "Final timestamps: dep=$depTime, arr=$arrTime")
            val intent = Intent(this, FlightSunActivity::class.java).apply {
                putExtra("departureIATA", depAirport)
                putExtra("arrivalIATA", arrAirport)
                putExtra("departureTime", depTime)
                putExtra("arrivalTime", arrTime)
                putExtra("fromLegEdit", true)
                // NEW: add offBlock and onBlock
                putExtra("offBlock", state.offBlock ?: 0L)
                putExtra("onBlock", state.onBlock ?: 0L)
            }
            Log.d(TAG, "Launching FlightSunActivity with extras: $intent.extras")
            nightResultLauncher.launch(intent)
        }

        // ---------- Registration → Type autofill ----------
        actRegistration.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val registration = actRegistration.text.toString()
                if (registration.isNotEmpty()) {
                    lifecycleScope.launch {
                        val type = viewModel.getMostRecentTypeByRegistration(registration)
                        if (!type.isNullOrEmpty()) {
                            actType.setText(type)
                            viewModel.updateAcType(type)

                            // ---------- Type → Category autofill (after registration) ----------
                            val category = viewModel.getMostRecentCategoryByAcType(type)
                            if (!category.isNullOrEmpty()) {
                                actCategory.setText(category)
                                viewModel.updateCategory(category)
                            }
                        }
                    }
                }
            }
        }

        // ---------- Type → Category autofill ----------
        actType.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val type = actType.text.toString()
                if (type.isNotEmpty()) {
                    lifecycleScope.launch {
                        val category = viewModel.getMostRecentCategoryByAcType(type)
                        if (!category.isNullOrEmpty()) {
                            actCategory.setText(category)
                            viewModel.updateCategory(category)
                        }
                    }
                }
            }
        }

// ---------- Duration Fields (normalize on focus lost) ----------
        etBlockTotal.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val raw = etBlockTotal.text.toString()
                val normalized = normalizeDuration(raw)
                val currentStateValue = viewModel.state.value.blockTimeTotal

                if (normalized == "invalid") {
                    // Invalid input: revert and show warning
                    etBlockTotal.setText(currentStateValue)
                    markFieldAsWarning(etBlockTotal, true)
                } else if (normalized != currentStateValue) {
                    // Valid new value
                    if (normalized != raw) {
                        etBlockTotal.setText(normalized)
                    }
                    viewModel.updateBlockTimeTotal(normalized)
                    viewModel.recalcBlockDay()
                    markFieldAsWarning(etBlockTotal, false)
                }
            } else {
                // Field gains focus, remove warning
                markFieldAsWarning(etBlockTotal, false)
            }
        }

        etBlockNight.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val raw = etBlockNight.text.toString()
                val normalized = normalizeDuration(raw)
                val currentStateValue = viewModel.state.value.blockTimeNight

                if (normalized == "invalid") {
                    etBlockNight.setText(currentStateValue)
                    markFieldAsWarning(etBlockNight, true)
                } else if (normalized != currentStateValue) {
                    if (normalized != raw) {
                        etBlockNight.setText(normalized)
                    }
                    viewModel.updateBlockTimeNight(normalized)
                    viewModel.recalcBlockDay()
                    markFieldAsWarning(etBlockNight, false)
                }
            } else {
                markFieldAsWarning(etBlockNight, false)
            }
        }

        etFlightTotal.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val raw = etFlightTotal.text.toString()
                val normalized = normalizeDuration(raw)
                val currentStateValue = viewModel.state.value.flightTimeTotal

                if (normalized == "invalid") {
                    etFlightTotal.setText(currentStateValue)
                    markFieldAsWarning(etFlightTotal, true)
                } else if (normalized != currentStateValue) {
                    if (normalized != raw) {
                        etFlightTotal.setText(normalized)
                    }
                    viewModel.updateFlightTimeTotal(normalized)
                    viewModel.recalcFlightDay()
                    viewModel.recalcIfrFromFlight()
                    markFieldAsWarning(etFlightTotal, false)
                }
            } else {
                markFieldAsWarning(etFlightTotal, false)
            }
        }

        etFlightNight.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val raw = etFlightNight.text.toString()
                val normalized = normalizeDuration(raw)
                val currentStateValue = viewModel.state.value.flightTimeNight

                if (normalized == "invalid") {
                    etFlightNight.setText(currentStateValue)
                    markFieldAsWarning(etFlightNight, true)
                } else if (normalized != currentStateValue) {
                    if (normalized != raw) {
                        etFlightNight.setText(normalized)
                    }
                    viewModel.updateFlightTimeNight(normalized)
                    viewModel.recalcFlightDay()
                    viewModel.recalcIfrFromFlight()
                    markFieldAsWarning(etFlightNight, false)
                }
            } else {
                markFieldAsWarning(etFlightNight, false)
            }
        }

        etSimulatorTime.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val raw = etSimulatorTime.text.toString()
                val normalized = normalizeDuration(raw)
                val currentStateValue = viewModel.state.value.simulatorTime

                if (normalized == "invalid") {
                    etSimulatorTime.setText(currentStateValue)
                    markFieldAsWarning(etSimulatorTime, true)
                } else if (normalized != currentStateValue) {
                    if (normalized != raw) {
                        etSimulatorTime.setText(normalized)
                    }
                    viewModel.updateSimulatorTime(normalized)
                    markFieldAsWarning(etSimulatorTime, false)
                }
            } else {
                markFieldAsWarning(etSimulatorTime, false)
            }
        }

        etIfrNight.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val raw = etIfrNight.text.toString()
                val normalized = normalizeDuration(raw)
                val currentStateValue = viewModel.state.value.ifrNight

                if (normalized == "invalid") {
                    etIfrNight.setText(currentStateValue)
                    markFieldAsWarning(etIfrNight, true)
                } else if (normalized != currentStateValue) {
                    if (normalized != raw) {
                        etIfrNight.setText(normalized)
                    }
                    viewModel.updateIfrNight(normalized)
                    markFieldAsWarning(etIfrNight, false)
                }
            } else {
                markFieldAsWarning(etIfrNight, false)
            }
        }

        etIfrTotal.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val raw = etIfrTotal.text.toString()
                val normalized = normalizeDuration(raw)
                val currentStateValue = viewModel.state.value.ifrTotal

                if (normalized == "invalid") {
                    etIfrTotal.setText(currentStateValue)
                    markFieldAsWarning(etIfrTotal, true)
                } else if (normalized != currentStateValue) {
                    if (normalized != raw) {
                        etIfrTotal.setText(normalized)
                    }
                    viewModel.updateIfrTotal(normalized)
                    markFieldAsWarning(etIfrTotal, false)
                }
            } else {
                markFieldAsWarning(etIfrTotal, false)
            }
        }

        etInstrumentApproaches.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val raw = etInstrumentApproaches.text.toString()
                val currentStateValue = viewModel.state.value.numberInstrumentApproaches
                if (raw != currentStateValue) {
                    viewModel.updateNumberInstrumentApproaches(raw)
                }
            }
        }
// ---------- Counter Fields (update on focus lost) ----------
        etToDay.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val raw = etToDay.text.toString()
                val currentStateValue = viewModel.state.value.numberToDay
                if (raw != currentStateValue) {
                    viewModel.updateNumberToDay(raw)

                    viewModel.recalcToTotal()
                }
            }
        }

        etToNight.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val raw = etToNight.text.toString()
                val currentStateValue = viewModel.state.value.numberToNight
                if (raw != currentStateValue) {
                    viewModel.updateNumberToNight(raw)

                    viewModel.recalcToTotal()
                }
            }
        }

        etToTotal.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val raw = etToTotal.text.toString()
                val currentStateValue = viewModel.state.value.numberToTotal
                if (raw != currentStateValue) {
                    viewModel.updateNumberToTotal(raw)
                }
            }
        }

        etLdgDay.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val raw = etLdgDay.text.toString()
                val currentStateValue = viewModel.state.value.numberLdgDay
                if (raw != currentStateValue) {
                    viewModel.updateNumberLdgDay(raw)

                    viewModel.recalcLdgTotal()
                }
            }
        }

        etLdgNight.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val raw = etLdgNight.text.toString()
                val currentStateValue = viewModel.state.value.numberLdgNight
                if (raw != currentStateValue) {
                    viewModel.updateNumberLdgNight(raw)

                    viewModel.recalcLdgTotal()
                }
            }
        }

        etLdgTotal.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val raw = etLdgTotal.text.toString()
                val currentStateValue = viewModel.state.value.numberLdgTotal
                if (raw != currentStateValue) {
                    viewModel.updateNumberLdgTotal(raw)
                }
            }
        }

        etComment.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val raw = etComment.text.toString()
                val currentStateValue = viewModel.state.value.comment
                if (raw != currentStateValue) {
                    viewModel.updateComment(raw)
                }
            }
        }

        // ---------- Checkboxes ----------
        cbSimulator.setOnCheckedChangeListener { _, isChecked ->
            if (!isUpdatingFromViewModel) {
                viewModel.updateIsSimulator(isChecked)

                viewModel.recalcBlockDuration()
                viewModel.recalcFlightDuration()
            }
        }

        cbPilotFlying.setOnCheckedChangeListener { _, isChecked ->
            if (!isUpdatingFromViewModel) {
                viewModel.updatePilotFlying(isChecked)
                viewModel.autoFillTakeoffLandingCounts()
                updateUiForMode()
            }
        }

        cbIfr.setOnCheckedChangeListener { _, isChecked ->
            if (!isUpdatingFromViewModel) {
                viewModel.updateIsIfr(isChecked)
            }
        }

        // ---------- Buttons ----------
        btnSave.setOnClickListener {
            // 1. Force focus loss to trigger validation
            currentFocus?.clearFocus()

            // 2. Check if any field is invalid
            if (invalidFields.isNotEmpty()) {
                Toast.makeText(this, "Please correct the marked fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener  // stay on the page
            }

            // 3. All clear – save and finish
            viewModel.saveLeg()

            // 4. Decide navigation behavior
            if (isImportFromExternal) {
                // Launch the main leg list activity in a new task (the logbook app's own task)
                val intent = Intent(this, LegListActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                startActivity(intent)
                finish()
            } else {
                // Normal internal navigation – just finish and return to previous screen
                finish()
            }
        }

        btnDelete.setOnClickListener {
            AlertDialog.Builder(this, R.style.DeleteDialogTheme)
                .setTitle("Delete Leg")
                .setMessage("Are you sure you want to delete this leg? This action cannot be undone.")
                .setPositiveButton("Delete") { _, _ ->
                    viewModel.deleteLeg()
                    finish()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        btnEdit.setOnClickListener {
            isEditing = true
            updateUiForMode()
        }

        // NEW: Cancel button
        btnCancel.setOnClickListener {
            if (viewModel.isEditing()) { // existing leg
                // Reload the leg from repository
                viewModel.reloadLeg()
                // Switch back to view mode
                isEditing = false
                updateUiForMode()
            } else {
                // New leg – show discard dialog and finish
                showDiscardDialog { finish() }
            }

        }
    }

    // Helper to update UI based on isEditing flag
    private fun updateUiForMode() {
        // Enable/disable all input fields
        val allInputs = listOf(
            tvDate,
            actFlightNumber, actPosition, actCategory, actPicName, actSicName,
            actCabinCrew, actJumpseat1, actJumpseat2, actRegistration, actType,
            actDeparture, actDestination,
            etOffBlock, etOnBlock, etTakeoff, etLanding, etSunrise, etSunset,
            etBlockTotal, etBlockNight, etBlockDay, etFlightTotal, etFlightNight, etFlightDay,
            etSimulatorTime, etIfrDay, etIfrNight, etIfrTotal, etInstrumentApproaches,
            etToDay, etToNight, etToTotal, etLdgDay, etLdgNight, etLdgTotal,
            etComment,
            cbSimulator, cbPilotFlying, cbIfr
        )
        for (view in allInputs) {
            view.isEnabled = isEditing
        }

        // Date field (TextView) is not enabled/disabled, but we guard its click listener
        // (already done in the listener)

// Save and Delete buttons
        btnSave.isEnabled = isEditing
        btnSave.visibility = if (isEditing) View.VISIBLE else View.GONE

// Delete is enabled and visible only when editing an existing leg
        btnDelete.isEnabled = isEditing && viewModel.isEditing()
        btnDelete.visibility = if (isEditing && viewModel.isEditing()) View.VISIBLE else View.GONE

        // Also ensure delete is visible only for existing legs (already handled in observeState)

        // Edit button
        btnEdit.visibility = if (isEditing) View.GONE else View.VISIBLE
        btnEdit.isEnabled = !isEditing
//        btnEdit.text = if (isEditing) "Editing…" else "Edit"

        // Cancel button
        btnCancel.visibility = if (isEditing) View.VISIBLE else View.GONE

        // jumpseat layout visibility
        if (isEditing) {
            jumpseatLayout.visibility = View.VISIBLE
        } else {
            // view mode: show only if jumpseat1 and jumpseat2 are not empty
            val js1 = viewModel.state.value.jumpseat1
            val js2 = viewModel.state.value.jumpseat2
            jumpseatLayout.visibility = if (js1 != "" || js2 != "") View.VISIBLE else View.GONE
        }

        sunriseSunsetLayout.visibility = if (isEditing) View.VISIBLE else View.GONE

        // Compute Night button
//        btnCputNight.isEnabled = isEditing
//        btnCputNight.visibility = if (isEditing) View.VISIBLE else View.GONE

        // Simulator layout visibility
        if (isEditing) {
            simulatorLayout.visibility = View.VISIBLE
        } else {
            // view mode: show only if isSimulator is true
            val isSim = viewModel.state.value.isSimulator ?: false
            simulatorLayout.visibility = if (isSim) View.VISIBLE else View.GONE
        }

        // checkbox visibility
        cbIfr.visibility = if (isEditing) View.VISIBLE else View.GONE
        cbSimulator.visibility = if (isEditing) View.VISIBLE else View.GONE

        // insApp-To-Ldg layout visibility
        val isFlying = viewModel.state.value.pilotFlying ?: false
        cbPilotFlying.visibility = if (isEditing) View.VISIBLE else View.GONE
        instAppLayout.visibility = if (isFlying) View.VISIBLE else View.GONE
        toLayout.visibility = if (isFlying) View.VISIBLE else View.GONE
        ldgLayout.visibility = if (isFlying) View.VISIBLE else View.GONE
    }

    // Show discard confirmation dialog
    private fun showDiscardDialog(onConfirm: () -> Unit) {
        AlertDialog.Builder(this)
            .setTitle("Discard changes?")
            .setMessage("You have unsaved changes. Are you sure you want to discard them?")
            .setPositiveButton("Discard") { _, _ -> onConfirm() }
            .setNegativeButton("Stay", null)
            .show()
    }

    private fun setupAutosuggest() {
        fun setupAutoComplete(
            autoComplete: AutoCompleteTextView,
            suggestionFunction: suspend (String) -> List<String>,
            updateFunction: (String) -> Unit,
            fixedList: List<String>? = null
        ) {
            var updatedFlag = false

            // Focus-gain: for fixed-list fields only, show the full list when empty.
            if (fixedList != null) {
                autoComplete.setOnFocusChangeListener { _, hasFocus ->
                    if (hasFocus) {
                        if (autoComplete.text.isEmpty()) {
                            val adapter = ArrayAdapter(
                                this@LegEditActivity,
                                android.R.layout.simple_dropdown_item_1line,
                                fixedList
                            )
                            autoComplete.setAdapter(adapter)
                            autoComplete.showDropDown()
                        }
                    }
                }
            }

            autoComplete.addTextChangedListener(object : TextWatcher {
                private var lastQuery = ""
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    val query = s.toString()

                    Log.d("LegEditActivity", "afterTextChanged: field=${autoComplete.hint}, query='$query', hasFocus=${autoComplete.hasFocus()}")

                    // If field doesn't have focus, don't process anything
                    if (!autoComplete.hasFocus()) {
                        Log.d("LegEditActivity", "afterTextChanged: no focus, returning")
                        return
                    }

                    if (autoComplete.isPerformingCompletion) {
                        Log.d("LegEditActivity", "afterTextChanged: isPerformingCompletion, updating function with '$query'")
                        updateFunction(query)
                        updatedFlag = true
                        return
                    }

                    // ---------- Fixed-list branch (only when fixedList provided) ----------
                    if (fixedList != null && query.length < 2) {
                        val filtered = if (query.isEmpty()) {
                            fixedList
                        } else {
                            fixedList.filter { it.contains(query, ignoreCase = true) }
                        }
                        if (filtered.isNotEmpty()) {
                            val adapter = ArrayAdapter(
                                this@LegEditActivity,
                                android.R.layout.simple_dropdown_item_1line,
                                filtered
                            )
                            autoComplete.setAdapter(adapter)
                            autoComplete.showDropDown()
                        } else {
                            autoComplete.dismissDropDown()
                        }
                        updateFunction(query)
                        updatedFlag = true
                        return
                    }
                    // ---------- End fixed-list branch ----------

                    if (query.length >= 2 && query != lastQuery) {
                        lastQuery = query
                        Log.d("LegEditActivity", "afterTextChanged: query length >=2 and changed, fetching suggestions for '$query'")
                        lifecycleScope.launch {
                            try {
                                val suggestions = suggestionFunction(query)
                                Log.d("LegEditActivity", "suggestionFunction result for '$query': $suggestions")
                                if (suggestions.isNotEmpty()) {
                                    val adapter = ArrayAdapter(
                                        this@LegEditActivity,
                                        android.R.layout.simple_dropdown_item_1line,
                                        suggestions
                                    )
                                    autoComplete.setAdapter(adapter)
                                    autoComplete.showDropDown()
                                    Log.d("LegEditActivity", "Adapter set and dropdown shown")
                                } else {
                                    autoComplete.dismissDropDown()
                                    Log.d("LegEditActivity", "No suggestions, dropdown dismissed")
                                }
                            } catch (e: Exception) {
                                Log.e("LegEditActivity", "Error fetching suggestions", e)
                            }
                        }
                    } else if (query.isEmpty()) {
                        autoComplete.dismissDropDown()
                        Log.d("LegEditActivity", "query empty, dismissed dropdown")
                    } else {
                        Log.d("LegEditActivity", "query length < 2 or same as last, skipping fetch")
                    }
                    updateFunction(query)
                    updatedFlag = true
                }
            })

            autoComplete.setOnItemClickListener { adapterView, _, position, _ ->
                val selected = adapterView.getItemAtPosition(position) as? String ?: return@setOnItemClickListener
                if (!updatedFlag) { updateFunction(selected) }
                updatedFlag = false
                autoComplete.clearFocus()
                autoComplete.dismissDropDown()
            }
        }

        setupAutoComplete(
            actPosition,
            { query -> viewModel.searchPosition(query) },
            { viewModel.updatePosition(it) },
            fixedList = fixedPositionList
        )

        setupAutoComplete(
            actPicName,
            { query -> viewModel.searchPicName(query) },
            { viewModel.updatePicName(it) }
        )

        setupAutoComplete(
            actSicName,
            { query -> viewModel.searchSicName(query) },
            { viewModel.updateSicName(it) }
        )

        setupAutoComplete(
            actJumpseat1,
            { query -> viewModel.searchJumpseat1(query) },
            { viewModel.updateJumpseat1(it) }
        )

        setupAutoComplete(
            actJumpseat2,
            { query -> viewModel.searchJumpseat2(query) },
            { viewModel.updateJumpseat2(it) }
        )

        setupAutoComplete(
            actCabinCrew,
            { query -> viewModel.searchCabinCrew(query) },
            { viewModel.updateCabinCrew(it) }
        )

        setupAutoComplete(
            actRegistration,
            { query -> viewModel.searchAcRegistration(query) },
            { viewModel.updateAcRegistration(it) }
        )

        setupAutoComplete(
            actType,
            { query -> viewModel.searchAcType(query) },
            { viewModel.updateAcType(it) }
        )

        setupAutoComplete(
            actFlightNumber,
            { query -> viewModel.searchFlightNumber(query) },
            { viewModel.updateFlightNumber(it) }
        )

        setupAutoComplete(
            actCategory,
            { query -> viewModel.searchCategory(query) },
            { viewModel.updateCategory(it) },
            fixedList = fixedCategoryList
        )

        setupAutoComplete(
            actDeparture,
            { query -> viewModel.searchIcaoDeparture(query) },
            { viewModel.updateIcaoDeparture(it) }
        )

        setupAutoComplete(
            actDestination,
            { query -> viewModel.searchIcaoDestination(query) },
            { viewModel.updateIcaoDestination(it) }
        )
    }

    private fun observeState() {
        lifecycleScope.launch {
            viewModel.state.collect { state ->
                isUpdatingFromViewModel = true

                // Date (always update - not a text input field)
                tvDate.text = state.baseDate?.let { formatDateOnly(it) } ?: "Select date"

                // Helper: only set text if field doesn't have focus
                fun setTextIfNotFocused(editText: EditText, text: String) {
                    if (!editText.hasFocus()) {
                        editText.setText(text)
                    }
                }

                // Text fields (update only if not focused)
                setTextIfNotFocused(actFlightNumber, state.flightNumber)
                setTextIfNotFocused(actPosition, state.position)
                setTextIfNotFocused(actCategory, state.category)
                setTextIfNotFocused(actPicName, state.picName)
                setTextIfNotFocused(actSicName, state.sicName)
                setTextIfNotFocused(actCabinCrew, state.cabinCrew)
                setTextIfNotFocused(actJumpseat1, state.jumpseat1)
                setTextIfNotFocused(actJumpseat2, state.jumpseat2)
                setTextIfNotFocused(actRegistration, state.acRegistration)
                setTextIfNotFocused(actType, state.acType)
                setTextIfNotFocused(actDeparture, state.icaoDeparture)
                setTextIfNotFocused(actDestination, state.icaoDestination)

                // Time fields (update only if not focused)
                setTextIfNotFocused(etOffBlock, state.offBlockTime)
                setTextIfNotFocused(etOnBlock, state.onBlockTime)
                setTextIfNotFocused(etTakeoff, state.takeoffTimeStr)
                setTextIfNotFocused(etLanding, state.landingTimeStr)
                setTextIfNotFocused(etSunrise, state.sunriseTimeStr)
                setTextIfNotFocused(etSunset, state.sunsetTimeStr)

                // Duration fields (update only if not focused)
                setTextIfNotFocused(etBlockTotal, state.blockTimeTotal)
                setTextIfNotFocused(etBlockNight, state.blockTimeNight)
                setTextIfNotFocused(etBlockDay, state.blockTimeDay)
                setTextIfNotFocused(etFlightTotal, state.flightTimeTotal)
                setTextIfNotFocused(etFlightNight, state.flightTimeNight)
                setTextIfNotFocused(etFlightDay, state.flightTimeDay)
                setTextIfNotFocused(etSimulatorTime, state.simulatorTime)
                setTextIfNotFocused(etIfrDay, state.ifrDay)
                setTextIfNotFocused(etIfrNight, state.ifrNight)
                setTextIfNotFocused(etIfrTotal, state.ifrTotal)

                // Number fields (update only if not focused)
                setTextIfNotFocused(etInstrumentApproaches, state.numberInstrumentApproaches)
                setTextIfNotFocused(etToDay, state.numberToDay)
                setTextIfNotFocused(etToNight, state.numberToNight)
                setTextIfNotFocused(etToTotal, state.numberToTotal)
                setTextIfNotFocused(etLdgDay, state.numberLdgDay)
                setTextIfNotFocused(etLdgNight, state.numberLdgNight)
                setTextIfNotFocused(etLdgTotal, state.numberLdgTotal)

                // Comment (update only if not focused)
                setTextIfNotFocused(etComment, state.comment)

                // Booleans
                cbSimulator.isChecked = state.isSimulator ?: false
                cbPilotFlying.isChecked = state.pilotFlying ?: false
                cbIfr.isChecked = state.isIfr

                // Delete button visibility
                btnDelete.visibility = if (state.legId != null) View.VISIBLE else View.GONE

                isUpdatingFromViewModel = false

                updateUiForMode()

            }
        }
    }

    private fun handleImportExtras() {
        val intent = intent
        if (intent.action != "com.my.striptologbook.ACTION_IMPORT_LEG") return

        isImportFromExternal = true

        Log.d("LegEditActivity", "handleImportExtras: action matched")

        fun getString(key: String): String? = intent.getStringExtra(key)

        val dateMillis = intent.getLongExtra("dateMillis", -1L)
        if (dateMillis != -1L) {
            Log.d("LegEditActivity", "dateMillis: $dateMillis")
            viewModel.setOffBlockFromTimestamp(dateMillis)
//            viewModel.setBaseDate(getDateAtMidnight(dateMillis))
        }

//        getString("offBlockTime")?.let {
//            Log.d("LegEditActivity", "offBlockTime: $it")
//            viewModel.updateOffBlockTime(it)
//        }

//        getString("onBlockTime")?.let {
//            Log.d("LegEditActivity", "onBlockTime: $it")
//            viewModel.updateOnBlockTime(it)
//        }

//        val offBlockTime = getString("offBlockTime")
//        if (!offBlockTime.isNullOrEmpty() && dateMillis != -1L) {
//            viewModel.recalculateTimestamps()
//        }

        getString("flightNumber")?.let {
            Log.d("LegEditActivity", "flightNumber: $it")
            viewModel.updateFlightNumber(it)
        }
        getString("departureIcao")?.let {
            Log.d("LegEditActivity", "departureIcao: $it")
            viewModel.updateIcaoDeparture(it)
        }
        getString("destinationIcao")?.let {
            Log.d("LegEditActivity", "destinationIcao: $it")
            viewModel.updateIcaoDestination(it)
        }
        getString("registration")?.let {
            Log.d("LegEditActivity", "registration: $it")
            viewModel.updateAcRegistration(it)
        }
        // --- auto‑resolve acType from registration (if provided and acType not already set) ---
        val registration = getString("registration")
        if (!registration.isNullOrEmpty()) {
            // Check if acType is currently empty (to avoid overriding a manually entered value)
            if (viewModel.state.value.acType.isEmpty()) {
                lifecycleScope.launch {
                    val resolvedType = viewModel.getMostRecentTypeByRegistration(registration)
                    if (!resolvedType.isNullOrEmpty()) {
                        viewModel.updateAcType(resolvedType)
                        actType.setText(resolvedType)   // update UI immediately

                        // ---------- Type → Category autofill (import, only if category empty) ----------
                        if (viewModel.state.value.category.isEmpty()) {
                            val resolvedCategory = viewModel.getMostRecentCategoryByAcType(resolvedType)
                            if (!resolvedCategory.isNullOrEmpty()) {
                                viewModel.updateCategory(resolvedCategory)
                                actCategory.setText(resolvedCategory)
                            }
                        }
                    }
                }
            }
        }

        getString("picName")?.let {
            Log.d("LegEditActivity", "picName: $it")
            viewModel.updatePicName(it)
        }
        getString("sicName")?.let {
            Log.d("LegEditActivity", "sicName: $it")
            viewModel.updateSicName(it)
        }
        getString("cabinCrew")?.let {
            Log.d("LegEditActivity", "cabinCrew: $it")
            viewModel.updateCabinCrew(it)
        }
        getString("jumpseat1")?.let {
            Log.d("LegEditActivity", "jumpseat1: $it")
            viewModel.updateJumpseat1(it)
        }
        getString("position")?.let {
            Log.d("LegEditActivity", "position: $it")
            viewModel.updatePosition(it)
        }
        getString("comment")?.let {
            Log.d("LegEditActivity", "comment: $it")
            viewModel.updateComment(it)
        }

        // Sunrise/Sunset suggestions
        val sunriseSuggestions = mutableListOf<String>()
        getString("departureSunrise")?.let { if (it.isNotEmpty()) sunriseSuggestions.add(it) }
        getString("arrivalSunrise")?.let { if (it.isNotEmpty()) sunriseSuggestions.add(it) }

        val sunsetSuggestions = mutableListOf<String>()
        getString("departureSunset")?.let { if (it.isNotEmpty()) sunsetSuggestions.add(it) }
        getString("arrivalSunset")?.let { if (it.isNotEmpty()) sunsetSuggestions.add(it) }

        if (sunriseSuggestions.isNotEmpty()) {
            Log.d("LegEditActivity_sun", "Setting sunrise adapter with suggestions: $sunriseSuggestions")
            val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, sunriseSuggestions)
            etSunrise.setAdapter(adapter)
            etSunrise.threshold = 0
        } else {
            Log.d("LegEditActivity_sun", "Sunrise suggestions are empty")
        }
        if (sunsetSuggestions.isNotEmpty()) {
            Log.d("LegEditActivity_sun", "Setting sunset adapter with suggestions: $sunsetSuggestions")
            val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, sunsetSuggestions)
            etSunset.setAdapter(adapter)
            etSunset.threshold = 0
        } else {
            Log.d("LegEditActivity_sun", "Sunset suggestions are empty")
        }
    }




    private fun markFieldAsWarning(editText: EditText, isWarning: Boolean) {
        if (isWarning) {
            // Set warning background
            editText.setBackgroundColor(ContextCompat.getColor(this, R.color.warning))
            invalidFields.add(editText)
        } else {
            // Restore default background
            val default = defaultBackgrounds[editText]
            if (default != null) {
                editText.background = default
            } else {
                // Fallback: if not stored, use a default background from theme
                editText.setBackgroundResource(android.R.drawable.editbox_background)
            }
            invalidFields.remove(editText)
        }
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

}