package com.my.striptologbook.ui

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.widget.Toolbar
import com.my.striptologbook.R
import com.my.striptologbook.utils.InsetsHelper
import com.my.striptologbook.utils.PreferencesManager

import android.text.Editable
import android.text.TextWatcher
import android.view.inputmethod.EditorInfo
import android.widget.AutoCompleteTextView
import androidx.lifecycle.lifecycleScope
import com.my.striptologbook.LogbookApplication
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class DefaultFieldsPreferencesActivity : BaseActivity() {

    private companion object {
        private val TRI_LABELS = arrayOf("No default", "True", "False")
        private val TRI_VALUES = arrayOf("none", "true", "false")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_default_fields_preferences)

        // -------- Toolbar ------------
        val spacer = findViewById<View>(R.id.status_bar_spacer)
        InsetsHelper.applyStatusBarSpacer(spacer)

        val scrollView = findViewById<ScrollView>(R.id.scroll_view)
        InsetsHelper.attachScrollToFocusedView(this, scrollView)
        InsetsHelper.applyImeBottomPaddingToRoot(this)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = "Default Fields"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        // -------- Toolbar ------------

        val preferencesManager = PreferencesManager.getInstance(this)

        // ---- Existing views ----
        val cbDefaultIsIfr = findViewById<CheckBox>(R.id.cb_default_is_ifr)
        val etDefaultSimuIfrApproach = findViewById<EditText>(R.id.et_default_simu_ifr_approach)
        val rgDefaultPosition = findViewById<RadioGroup>(R.id.rg_default_position)
        val rbPositionPic = findViewById<RadioButton>(R.id.rb_position_pic)
        val rbPositionSic = findViewById<RadioButton>(R.id.rb_position_sic)
        val rbPositionDual = findViewById<RadioButton>(R.id.rb_position_dual)
        val rbPositionInstructor = findViewById<RadioButton>(R.id.rb_position_instructor)
        val rbPositionExaminator = findViewById<RadioButton>(R.id.rb_position_examinator)
        val rbPositionCustom = findViewById<RadioButton>(R.id.rb_position_custom)
        val etCustomPosition = findViewById<EditText>(R.id.et_custom_position)

        // ---- New views ----
        val etDefaultPicName = findViewById<EditText>(R.id.et_default_pic_name)
        val etDefaultSicName = findViewById<EditText>(R.id.et_default_sic_name)
        val etDefaultJumpseat1 = findViewById<EditText>(R.id.et_default_jumpseat1)
        val etDefaultJumpseat2 = findViewById<EditText>(R.id.et_default_jumpseat2)
        val etDefaultCabinCrew = findViewById<EditText>(R.id.et_default_cabin_crew)
        val etDefaultAcRegistration = findViewById<EditText>(R.id.et_default_ac_registration)
        val actDefaultAcType = findViewById<AutoCompleteTextView>(R.id.act_default_ac_type)
        val actDefaultCategory = findViewById<AutoCompleteTextView>(R.id.act_default_category)
        val etDefaultFlightNumberPrefix = findViewById<EditText>(R.id.et_default_flight_number_prefix)
        val etDefaultIcaoDeparture = findViewById<EditText>(R.id.et_default_icao_departure)
        val etDefaultIcaoDestination = findViewById<EditText>(R.id.et_default_icao_destination)
        val etDefaultComment = findViewById<EditText>(R.id.et_default_comment)
        val cbDefaultOffblockNow = findViewById<CheckBox>(R.id.cb_default_offblock_now)
        val spDefaultIsSimulator = findViewById<Spinner>(R.id.sp_default_is_simulator)
        val spDefaultPilotFlying = findViewById<Spinner>(R.id.sp_default_pilot_flying)

        // Spinner adapters
        val triAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            TRI_LABELS
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        spDefaultIsSimulator.adapter = triAdapter
        spDefaultPilotFlying.adapter = triAdapter

        // ---- Load existing values ----
        cbDefaultIsIfr.isChecked = preferencesManager.getDefaultIsIfr()
        etDefaultSimuIfrApproach.setText(preferencesManager.getDefaultSimuIfrApproach().toString())

        when (val storedPosition = preferencesManager.getDefaultPosition()) {
            "PIC" -> rbPositionPic.isChecked = true
            "SIC" -> rbPositionSic.isChecked = true
            "Dual" -> rbPositionDual.isChecked = true
            "Instructor" -> rbPositionInstructor.isChecked = true
            "Examinator" -> rbPositionExaminator.isChecked = true
            else -> {
                rbPositionCustom.isChecked = true
                etCustomPosition.setText(storedPosition)
            }
        }

        // ---- Load new values ----
        etDefaultPicName.setText(preferencesManager.getDefaultPicName())
        etDefaultSicName.setText(preferencesManager.getDefaultSicName())
        etDefaultJumpseat1.setText(preferencesManager.getDefaultJumpseat1())
        etDefaultJumpseat2.setText(preferencesManager.getDefaultJumpseat2())
        etDefaultCabinCrew.setText(preferencesManager.getDefaultCabinCrew())
        etDefaultAcRegistration.setText(preferencesManager.getDefaultAcRegistration())
        actDefaultAcType.setText(preferencesManager.getDefaultAcType())
        actDefaultCategory.setText(preferencesManager.getDefaultCategory())
        etDefaultFlightNumberPrefix.setText(preferencesManager.getDefaultFlightNumberPrefix())
        etDefaultIcaoDeparture.setText(preferencesManager.getDefaultIcaoDeparture())
        etDefaultIcaoDestination.setText(preferencesManager.getDefaultIcaoDestination())
        etDefaultComment.setText(preferencesManager.getDefaultComment())
        cbDefaultOffblockNow.isChecked = preferencesManager.getDefaultOffBlockNow()

        spDefaultIsSimulator.setSelection(
            TRI_VALUES.indexOf(preferencesManager.getDefaultIsSimulator()).coerceAtLeast(0)
        )
        spDefaultPilotFlying.setSelection(
            TRI_VALUES.indexOf(preferencesManager.getDefaultPilotFlying()).coerceAtLeast(0)
        )

        // ---- Custom position visibility ----
        etCustomPosition.visibility = if (rbPositionCustom.isChecked) View.VISIBLE else View.GONE
        rgDefaultPosition.setOnCheckedChangeListener { _, _ ->
            etCustomPosition.visibility = if (rbPositionCustom.isChecked) View.VISIBLE else View.GONE
        }

        // ---- Autosuggest Setup ----
        val app = application as LogbookApplication
        val fixedCategoryList = preferencesManager.getCustomCategoryList()

        fun setupEnterKeyToClearFocus(editText: EditText) {
            editText.setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_NEXT) {
                    editText.clearFocus()
                    true
                } else false
            }
        }
        setupEnterKeyToClearFocus(actDefaultAcType)
        setupEnterKeyToClearFocus(actDefaultCategory)

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
                    if (!autoComplete.hasFocus()) return
                    if (autoComplete.isPerformingCompletion) return

                    if (fixedList != null && query.length < 2) {
                        val filtered = if (query.isEmpty()) fixedList else fixedList.filter { it.contains(query, ignoreCase = true) }
                        if (filtered.isNotEmpty()) {
                            val adapter = ArrayAdapter(this@DefaultFieldsPreferencesActivity, android.R.layout.simple_dropdown_item_1line, filtered)
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
                                    val adapter = ArrayAdapter(this@DefaultFieldsPreferencesActivity, android.R.layout.simple_dropdown_item_1line, suggestions)
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

        setupAutoComplete(actDefaultAcType, { query -> app.repository.searchAcType(query) })
        setupAutoComplete(actDefaultCategory, { query -> app.repository.searchCategory(query) }, fixedList = fixedCategoryList)

        // ---- Save ----
        findViewById<Button>(R.id.btn_save_default_fields).setOnClickListener {
            // Existing
            preferencesManager.setDefaultIsIfr(cbDefaultIsIfr.isChecked)

            val simuApproach = etDefaultSimuIfrApproach.text.toString().toIntOrNull() ?: 0
            preferencesManager.setDefaultSimuIfrApproach(simuApproach)

            val position = when {
                rbPositionPic.isChecked -> "PIC"
                rbPositionSic.isChecked -> "SIC"
                rbPositionDual.isChecked -> "Dual"
                rbPositionInstructor.isChecked -> "Instructor"
                rbPositionInstructor.isChecked -> "Examinator"
                rbPositionCustom.isChecked -> etCustomPosition.text.toString()
                else -> ""
            }
            preferencesManager.setDefaultPosition(position)

            // New
            preferencesManager.setDefaultPicName(etDefaultPicName.text.toString())
            preferencesManager.setDefaultSicName(etDefaultSicName.text.toString())
            preferencesManager.setDefaultJumpseat1(etDefaultJumpseat1.text.toString())
            preferencesManager.setDefaultJumpseat2(etDefaultJumpseat2.text.toString())
            preferencesManager.setDefaultCabinCrew(etDefaultCabinCrew.text.toString())
            preferencesManager.setDefaultAcRegistration(etDefaultAcRegistration.text.toString())
            preferencesManager.setDefaultAcType(actDefaultAcType.text.toString())
            preferencesManager.setDefaultCategory(actDefaultCategory.text.toString())
            preferencesManager.setDefaultFlightNumberPrefix(etDefaultFlightNumberPrefix.text.toString())
            preferencesManager.setDefaultIcaoDeparture(etDefaultIcaoDeparture.text.toString())
            preferencesManager.setDefaultIcaoDestination(etDefaultIcaoDestination.text.toString())
            preferencesManager.setDefaultComment(etDefaultComment.text.toString())
            preferencesManager.setDefaultOffBlockNow(cbDefaultOffblockNow.isChecked)

            val simSel = TRI_VALUES.getOrElse(spDefaultIsSimulator.selectedItemPosition) { "none" }
            preferencesManager.setDefaultIsSimulator(simSel)

            val pfSel = TRI_VALUES.getOrElse(spDefaultPilotFlying.selectedItemPosition) { "none" }
            preferencesManager.setDefaultPilotFlying(pfSel)

            Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
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