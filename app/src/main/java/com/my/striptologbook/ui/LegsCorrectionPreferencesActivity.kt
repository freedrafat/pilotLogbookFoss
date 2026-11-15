package com.my.striptologbook.ui

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.Toast
import androidx.appcompat.widget.Toolbar
import com.my.striptologbook.R
import com.my.striptologbook.data.IfrMode
import com.my.striptologbook.data.LegsCorrectionConfig
import com.my.striptologbook.data.NightMode
import com.my.striptologbook.data.PilotFlyingMode
import com.my.striptologbook.data.TakeoffLandingMode
import com.my.striptologbook.utils.InsetsHelper
import com.my.striptologbook.utils.PreferencesManager
import android.widget.AutoCompleteTextView
import android.text.Editable
import android.text.TextWatcher
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import androidx.lifecycle.lifecycleScope
import com.my.striptologbook.LogbookApplication
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import com.my.striptologbook.data.AcTypeMode
import com.my.striptologbook.data.CategoryMode

class LegsCorrectionPreferencesActivity : BaseActivity() {

    private lateinit var prefs: PreferencesManager

    private lateinit var rgTakeoffLanding: RadioGroup
    private lateinit var rbTlSkip: android.widget.RadioButton
    private lateinit var rbTlAdd: android.widget.RadioButton
    private lateinit var llTlMinutes: LinearLayout
    private lateinit var etTakeoffOffset: EditText
    private lateinit var etLandingOffset: EditText

    private lateinit var rgIfr: RadioGroup
    private lateinit var rbIfrSkip: android.widget.RadioButton
    private lateinit var rbIfrBlock: android.widget.RadioButton
    private lateinit var rbIfrFlight: android.widget.RadioButton

    private lateinit var rgNight: RadioGroup
    private lateinit var rbNightSkip: android.widget.RadioButton
    private lateinit var rbNightReal: android.widget.RadioButton

    private lateinit var rgPilotFlying: RadioGroup
    private lateinit var rbPfSkip: android.widget.RadioButton
    private lateinit var rbPfAlternate: android.widget.RadioButton
    private lateinit var rgAcType: RadioGroup
    private lateinit var rbAcTypeSkip: android.widget.RadioButton
    private lateinit var rbAcTypeInput: android.widget.RadioButton
    private lateinit var actAcTypeValue: AutoCompleteTextView

    private lateinit var rgCategory: RadioGroup
    private lateinit var rbCategorySkip: android.widget.RadioButton
    private lateinit var rbCategoryInput: android.widget.RadioButton
    private lateinit var actCategoryValue: AutoCompleteTextView



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_legs_correction_preferences)

        val spacer = findViewById<View>(R.id.status_bar_spacer)
        InsetsHelper.applyStatusBarSpacer(spacer)

        val scrollView = findViewById<android.widget.ScrollView>(R.id.scroll_legs_correction)
        InsetsHelper.attachScrollToFocusedView(this, scrollView)
        InsetsHelper.applyImeBottomPaddingToRoot(this)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = "Legs Correction"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        prefs = PreferencesManager.getInstance(this)

        initViews()
        loadPreferences()

        rgTakeoffLanding.setOnCheckedChangeListener { _, _ ->
            updateMinutesVisibility()
        }

        findViewById<Button>(R.id.btn_restore_defaults_correction).setOnClickListener {
            loadConfig(LegsCorrectionConfig.DEFAULT)
            Toast.makeText(this, "Defaults restored (not yet saved)", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.btn_save_correction).setOnClickListener {
            savePreferences()
        }
    }

    private fun initViews() {
        rgTakeoffLanding = findViewById(R.id.rg_takeoff_landing)
        rbTlSkip = findViewById(R.id.rb_tl_skip)
        rbTlAdd = findViewById(R.id.rb_tl_add)
        llTlMinutes = findViewById(R.id.ll_takeoff_landing_minutes)
        etTakeoffOffset = findViewById(R.id.et_takeoff_offset)
        etLandingOffset = findViewById(R.id.et_landing_offset)

        rgIfr = findViewById(R.id.rg_ifr)
        rbIfrSkip = findViewById(R.id.rb_ifr_skip)
        rbIfrBlock = findViewById(R.id.rb_ifr_block)
        rbIfrFlight = findViewById(R.id.rb_ifr_flight)

        rgNight = findViewById(R.id.rg_night)
        rbNightSkip = findViewById(R.id.rb_night_skip)
        rbNightReal = findViewById(R.id.rb_night_real)

        rgAcType = findViewById(R.id.rg_ac_type)
        rbAcTypeSkip = findViewById(R.id.rb_ac_type_skip)
        rbAcTypeInput = findViewById(R.id.rb_ac_type_input)
        actAcTypeValue = findViewById(R.id.act_ac_type_value)

        rgCategory = findViewById(R.id.rg_category)
        rbCategorySkip = findViewById(R.id.rb_category_skip)
        rbCategoryInput = findViewById(R.id.rb_category_input)
        actCategoryValue = findViewById(R.id.act_category_value)

        rgPilotFlying = findViewById(R.id.rg_pilot_flying)
        rbPfSkip = findViewById(R.id.rb_pf_skip)
        rbPfAlternate = findViewById(R.id.rb_pf_alternate)

        val app = application as LogbookApplication
        val fixedCategoryList = prefs.getCustomCategoryList()

        fun setupEnterKey(editText: EditText) {
            editText.setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_NEXT) {
                    editText.clearFocus(); true
                } else false
            }
        }
        setupEnterKey(actAcTypeValue)
        setupEnterKey(actCategoryValue)

        fun setupAutoComplete(
            autoComplete: AutoCompleteTextView,
            suggestionFunction: suspend (String) -> List<String>,
            fixedList: List<String>? = null
        ) {
            if (fixedList != null) {
                autoComplete.setOnFocusChangeListener { _, hasFocus ->
                    if (hasFocus && autoComplete.text.isEmpty()) {
                        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, fixedList)
                        autoComplete.setAdapter(adapter)
                        autoComplete.showDropDown()
                    }
                }
            }

            autoComplete.addTextChangedListener(object : TextWatcher {
                private var lastQuery = ""
                private var fetchJob: Job? = null
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    val query = s.toString()
                    if (!autoComplete.hasFocus() || autoComplete.isPerformingCompletion) return

                    if (fixedList != null && query.length < 2) {
                        val filtered = if (query.isEmpty()) fixedList else fixedList.filter { it.contains(query, ignoreCase = true) }
                        if (filtered.isNotEmpty()) {
                            val adapter = ArrayAdapter(this@LegsCorrectionPreferencesActivity, android.R.layout.simple_dropdown_item_1line, filtered)
                            autoComplete.setAdapter(adapter)
                            autoComplete.showDropDown()
                        } else {
                            autoComplete.dismissDropDown()
                        }
                        return
                    }

                    if (query.length >= 2 && query != lastQuery) {
                        lastQuery = query
                        fetchJob?.cancel()
                        fetchJob = lifecycleScope.launch {
                            try {
                                val suggestions = suggestionFunction(query)
                                if (query != lastQuery) return@launch
                                if (suggestions.isNotEmpty()) {
                                    val adapter = ArrayAdapter(this@LegsCorrectionPreferencesActivity, android.R.layout.simple_dropdown_item_1line, suggestions)
                                    autoComplete.setAdapter(adapter)
                                    autoComplete.showDropDown()
                                } else {
                                    autoComplete.dismissDropDown()
                                }
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {}
                        }
                    } else if (query.isEmpty()) {
                        lastQuery = ""
                        fetchJob?.cancel()
                        autoComplete.dismissDropDown()
                    }
                }
            })

            autoComplete.setOnItemClickListener { adapterView, _, position, _ ->
                val selected = adapterView.getItemAtPosition(position) as? String ?: return@setOnItemClickListener
                autoComplete.setText(selected)
                autoComplete.setSelection(selected.length)
                autoComplete.clearFocus()
                autoComplete.dismissDropDown()
            }
        }

        setupAutoComplete(actAcTypeValue, { query -> app.repository.searchAcType(query) })
        setupAutoComplete(actCategoryValue, { query -> app.repository.searchCategory(query) }, fixedList = fixedCategoryList)

    }

    private fun loadPreferences() {
        loadConfig(prefs.getLegsCorrectionConfig())
    }

    private fun loadConfig(config: LegsCorrectionConfig) {
        when (config.takeoffLandingMode) {
            TakeoffLandingMode.SKIP -> rbTlSkip.isChecked = true
            TakeoffLandingMode.ADD_MINUTES -> rbTlAdd.isChecked = true
        }
        etTakeoffOffset.setText(config.takeoffOffsetMinutes.toString())
        etLandingOffset.setText(config.landingOffsetMinutes.toString())
        updateMinutesVisibility()

        when (config.ifrMode) {
            IfrMode.SKIP -> rbIfrSkip.isChecked = true
            IfrMode.BLOCK_TIME -> rbIfrBlock.isChecked = true
            IfrMode.FLIGHT_TIME -> rbIfrFlight.isChecked = true
        }

        when (config.nightMode) {
            NightMode.SKIP -> rbNightSkip.isChecked = true
            NightMode.REAL_NIGHT -> rbNightReal.isChecked = true
        }

        when (config.pilotFlyingMode) {
            PilotFlyingMode.SKIP -> rbPfSkip.isChecked = true
            PilotFlyingMode.ALTERNATE -> rbPfAlternate.isChecked = true
        }

        // a/c Type
        when (config.acTypeMode) {
            AcTypeMode.SKIP -> rbAcTypeSkip.isChecked = true
            AcTypeMode.USER_INPUT -> rbAcTypeInput.isChecked = true
        }
        actAcTypeValue.setText(config.acTypeValue)

        // Category
        when (config.categoryMode) {
            CategoryMode.SKIP -> rbCategorySkip.isChecked = true
            CategoryMode.USER_INPUT -> rbCategoryInput.isChecked = true
        }
        actCategoryValue.setText(config.categoryValue)

    }

    private fun updateMinutesVisibility() {
        if (rbTlAdd.isChecked) {
            llTlMinutes.visibility = View.VISIBLE
        } else {
            llTlMinutes.visibility = View.GONE
        }
    }

    private fun savePreferences() {
        val tlMode = if (rbTlAdd.isChecked) TakeoffLandingMode.ADD_MINUTES else TakeoffLandingMode.SKIP
        val takeoffOffset = etTakeoffOffset.text.toString().toIntOrNull() ?: 15
        val landingOffset = etLandingOffset.text.toString().toIntOrNull() ?: 15

        val ifrMode = when {
            rbIfrBlock.isChecked -> IfrMode.BLOCK_TIME
            rbIfrFlight.isChecked -> IfrMode.FLIGHT_TIME
            else -> IfrMode.SKIP
        }

        val nightMode = if (rbNightReal.isChecked) NightMode.REAL_NIGHT else NightMode.SKIP

        val pfMode = if (rbPfAlternate.isChecked) PilotFlyingMode.ALTERNATE else PilotFlyingMode.SKIP

        val acTypeMode = if (rbAcTypeInput.isChecked) AcTypeMode.USER_INPUT else AcTypeMode.SKIP
        val acTypeValue = actAcTypeValue.text.toString().trim()

        val categoryMode = if (rbCategoryInput.isChecked) CategoryMode.USER_INPUT else CategoryMode.SKIP
        val categoryValue = actCategoryValue.text.toString().trim()

        val config = LegsCorrectionConfig(
            takeoffLandingMode = tlMode,
            takeoffOffsetMinutes = takeoffOffset,
            landingOffsetMinutes = landingOffset,
            ifrMode = ifrMode,
            nightMode = nightMode,
            pilotFlyingMode = pfMode,
            acTypeMode = acTypeMode,
            acTypeValue = acTypeValue,
            categoryMode = categoryMode,
            categoryValue = categoryValue
        )



        prefs.setLegsCorrectionConfig(config)
        Toast.makeText(this, "Preferences saved", Toast.LENGTH_SHORT).show()
        finish()
    }
}