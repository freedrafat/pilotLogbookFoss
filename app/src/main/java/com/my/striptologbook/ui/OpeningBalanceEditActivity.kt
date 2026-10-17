package com.my.striptologbook.ui

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.my.striptologbook.LogbookApplication
import com.my.striptologbook.R
import com.my.striptologbook.utils.InsetsHelper
import com.my.striptologbook.utils.formatDateOnly
import com.my.striptologbook.utils.normalizeDuration
import com.my.striptologbook.utils.showCustomDatePicker
import kotlinx.coroutines.launch

class OpeningBalanceEditActivity : BaseActivity() {

    private lateinit var viewModel: OpeningBalanceEditViewModel

    private lateinit var tvStartDate: TextView
    private lateinit var tvEndDate: TextView
    private lateinit var actType: AutoCompleteTextView
    private lateinit var actPosition: AutoCompleteTextView
    private lateinit var actCategory: AutoCompleteTextView
    private lateinit var etBlockTotal: EditText
    private lateinit var etBlockNight: EditText
    private lateinit var etBlockDay: EditText
    private lateinit var etFlightTotal: EditText
    private lateinit var etFlightNight: EditText
    private lateinit var etFlightDay: EditText
    private lateinit var etIfrTotal: EditText
    private lateinit var etIfrNight: EditText
    private lateinit var etIfrDay: EditText
    private lateinit var etSimulatorTime: EditText
    private lateinit var etInstrumentApproaches: EditText
    private lateinit var etToDay: EditText
    private lateinit var etToNight: EditText
    private lateinit var etToTotal: EditText
    private lateinit var etLdgDay: EditText
    private lateinit var etLdgNight: EditText
    private lateinit var etLdgTotal: EditText
    private lateinit var etSinglePilotSe: EditText
    private lateinit var etSinglePilotMe: EditText
    private lateinit var etMultiPilot: EditText
    private lateinit var etPfPic: EditText
    private lateinit var etPfSic: EditText
    private lateinit var etPfDual: EditText
    private lateinit var etPfInstructor: EditText
    private lateinit var etComment: EditText

    private lateinit var btnEdit: Button
    private lateinit var btnSave: Button
    private lateinit var btnCancel: Button
    private lateinit var btnDelete: Button

    private val invalidFields = mutableSetOf<EditText>()
    private val defaultBackgrounds = mutableMapOf<EditText, android.graphics.drawable.Drawable>()

    private var isUpdatingFromViewModel = false
    private var isEditing = false
    private var mergedMode = false

    private lateinit var fixedPositionList: List<String>
    private lateinit var fixedCategoryList: List<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_opening_balance_edit)

        InsetsHelper.applyStatusBarSpacer(findViewById(R.id.status_bar_spacer))

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        val balanceId = intent.getLongExtra("openingBalanceId", -1).takeIf { it != -1L }
        mergedMode = intent.getBooleanExtra("mergedMode", false)

        supportActionBar?.title = when {
            mergedMode -> "Merged Totals"
            balanceId == null -> "Add Opening Balance"
            else -> "Edit Opening Balance"
        }
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val scrollView = findViewById<ScrollView>(R.id.scroll_view)
        InsetsHelper.attachScrollToFocusedView(this, scrollView)
        InsetsHelper.applyImeBottomPaddingToRoot(this)

        val app = application as LogbookApplication
        val factory = OpeningBalanceEditViewModelFactory(app, balanceId, mergedMode)
        val prefs = com.my.striptologbook.utils.PreferencesManager.getInstance(this)
        fixedPositionList = prefs.getCustomPositionList()
        fixedCategoryList = prefs.getCustomCategoryList()
        viewModel = ViewModelProvider(this, factory)[OpeningBalanceEditViewModel::class.java]

        initViews()
        setupListeners()
        setupAutosuggest()
        observeState()

        // New row → edit directly. Existing row → view mode until Edit is tapped.
        // Merged mode → always view mode.
        isEditing = !mergedMode && (balanceId == null)

        updateUiForMode()

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
        tvStartDate = findViewById(R.id.tv_start_date)
        tvEndDate = findViewById(R.id.tv_end_date)

        actType = findViewById(R.id.act_type)
        actPosition = findViewById(R.id.act_position)
        actCategory = findViewById(R.id.act_category)

        etBlockTotal = findViewById(R.id.et_block_total)
        etBlockNight = findViewById(R.id.et_block_night)
        etBlockDay = findViewById(R.id.et_block_day)

        etFlightTotal = findViewById(R.id.et_flight_total)
        etFlightNight = findViewById(R.id.et_flight_night)
        etFlightDay = findViewById(R.id.et_flight_day)

        etIfrTotal = findViewById(R.id.et_ifr_total)
        etIfrNight = findViewById(R.id.et_ifr_night)
        etIfrDay = findViewById(R.id.et_ifr_day)

        etSimulatorTime = findViewById(R.id.et_simulator_time)
        etInstrumentApproaches = findViewById(R.id.et_instrument_approaches)

        etToDay = findViewById(R.id.et_to_day)
        etToNight = findViewById(R.id.et_to_night)
        etToTotal = findViewById(R.id.et_to_total)

        etLdgDay = findViewById(R.id.et_ldg_day)
        etLdgNight = findViewById(R.id.et_ldg_night)
        etLdgTotal = findViewById(R.id.et_ldg_total)

        etSinglePilotSe = findViewById(R.id.et_single_pilot_se)
        etSinglePilotMe = findViewById(R.id.et_single_pilot_me)
        etMultiPilot = findViewById(R.id.et_multi_pilot)

        etPfPic = findViewById(R.id.et_pf_pic)
        etPfSic = findViewById(R.id.et_pf_sic)
        etPfDual = findViewById(R.id.et_pf_dual)
        etPfInstructor = findViewById(R.id.et_pf_instructor)

        etComment = findViewById(R.id.et_comment)

        btnEdit = findViewById(R.id.btn_edit)
        btnSave = findViewById(R.id.btn_save)
        btnCancel = findViewById(R.id.btn_cancel)
        btnDelete = findViewById(R.id.btn_delete)

        val durations = listOf(
            etBlockTotal, etBlockNight, etBlockDay,
            etFlightTotal, etFlightNight, etFlightDay,
            etIfrTotal, etIfrNight, etIfrDay,
            etSimulatorTime,
            etSinglePilotSe, etSinglePilotMe, etMultiPilot,
            etPfPic, etPfSic, etPfDual, etPfInstructor
        )
        for (f in durations) defaultBackgrounds[f] = f.background
    }

    private fun setupListeners() {
        fun setupEnterKeyToClearFocus(editText: EditText) {
            editText.setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_NEXT) {
                    editText.clearFocus(); true
                } else false
            }
        }

        val allEditable: List<View> = listOf(
            actType, actPosition, actCategory,
            etBlockTotal, etBlockNight, etBlockDay,
            etFlightTotal, etFlightNight, etFlightDay,
            etIfrTotal, etIfrNight, etIfrDay,
            etSimulatorTime, etInstrumentApproaches,
            etToDay, etToNight, etToTotal,
            etLdgDay, etLdgNight, etLdgTotal,
            etSinglePilotSe, etSinglePilotMe, etMultiPilot,
            etPfPic, etPfSic, etPfDual, etPfInstructor,
            etComment
        )
        for (v in allEditable) if (v is EditText) setupEnterKeyToClearFocus(v)

        tvStartDate.setOnClickListener {
            if (!isEditing) return@setOnClickListener
            showCustomDatePicker(this, viewModel.state.value.startDate) {
                viewModel.setStartDate(it)
            }
        }
        tvEndDate.setOnClickListener {
            if (!isEditing) return@setOnClickListener
            showCustomDatePicker(this, viewModel.state.value.endDate) {
                viewModel.setEndDate(it)
            }
        }

        fun attachDuration(field: EditText, currentValue: () -> String, update: (String) -> Unit) {
            field.setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) {
                    val raw = field.text.toString()
                    val normalized = normalizeDuration(raw)
                    val current = currentValue()
                    if (normalized == "invalid") {
                        field.setText(current); markFieldAsWarning(field, true)
                    } else {
                        if (normalized != raw) field.setText(normalized)
                        markFieldAsWarning(field, false)
                        if (normalized != current) update(normalized)
                    }
                } else markFieldAsWarning(field, false)
            }
        }

        attachDuration(etBlockTotal, { viewModel.state.value.blockTimeTotal }, viewModel::updateBlockTotal)
        attachDuration(etBlockNight, { viewModel.state.value.blockTimeNight }, viewModel::updateBlockNight)
        attachDuration(etBlockDay, { viewModel.state.value.blockTimeDay }, viewModel::updateBlockDay)
        attachDuration(etFlightTotal, { viewModel.state.value.flightTimeTotal }, viewModel::updateFlightTotal)
        attachDuration(etFlightNight, { viewModel.state.value.flightTimeNight }, viewModel::updateFlightNight)
        attachDuration(etFlightDay, { viewModel.state.value.flightTimeDay }, viewModel::updateFlightDay)
        attachDuration(etIfrTotal, { viewModel.state.value.ifrTotal }, viewModel::updateIfrTotal)
        attachDuration(etIfrNight, { viewModel.state.value.ifrNight }, viewModel::updateIfrNight)
        attachDuration(etIfrDay, { viewModel.state.value.ifrDay }, viewModel::updateIfrDay)
        attachDuration(etSimulatorTime, { viewModel.state.value.simulatorTime }, viewModel::updateSimulatorTime)
        attachDuration(etSinglePilotSe, { viewModel.state.value.singlePilotSeTime }, viewModel::updateSinglePilotSe)
        attachDuration(etSinglePilotMe, { viewModel.state.value.singlePilotMeTime }, viewModel::updateSinglePilotMe)
        attachDuration(etMultiPilot, { viewModel.state.value.multiPilotTime }, viewModel::updateMultiPilot)
        attachDuration(etPfPic, { viewModel.state.value.pilotFunctionPicTime }, viewModel::updatePilotFunctionPic)
        attachDuration(etPfSic, { viewModel.state.value.pilotFunctionSicTime }, viewModel::updatePilotFunctionSic)
        attachDuration(etPfDual, { viewModel.state.value.pilotFunctionDualTime }, viewModel::updatePilotFunctionDual)
        attachDuration(etPfInstructor, { viewModel.state.value.pilotFunctionInstructorTime }, viewModel::updatePilotFunctionInstructor)

        fun attachCounter(field: EditText, update: (String) -> Unit) {
            field.setOnFocusChangeListener { _, hasFocus -> if (!hasFocus) update(field.text.toString()) }
        }
        attachCounter(etInstrumentApproaches, viewModel::updateNumberInstrumentApproaches)
        attachCounter(etToDay, viewModel::updateToDay)
        attachCounter(etToNight, viewModel::updateToNight)
        attachCounter(etToTotal, viewModel::updateToTotal)
        attachCounter(etLdgDay, viewModel::updateLdgDay)
        attachCounter(etLdgNight, viewModel::updateLdgNight)
        attachCounter(etLdgTotal, viewModel::updateLdgTotal)

        actType.setOnFocusChangeListener { _, hasFocus -> if (!hasFocus) viewModel.updateAcType(actType.text.toString()) }
        actPosition.setOnFocusChangeListener { _, hasFocus -> if (!hasFocus) viewModel.updatePosition(actPosition.text.toString()) }
        actCategory.setOnFocusChangeListener { _, hasFocus -> if (!hasFocus) viewModel.updateCategory(actCategory.text.toString()) }
        etComment.setOnFocusChangeListener { _, hasFocus -> if (!hasFocus) viewModel.updateComment(etComment.text.toString()) }

        btnEdit.setOnClickListener { isEditing = true; updateUiForMode() }

        btnSave.setOnClickListener {
            currentFocus?.clearFocus()
            if (invalidFields.isNotEmpty()) {
                Toast.makeText(this, "Please correct the marked fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            viewModel.saveRow { finish() }
        }

        btnCancel.setOnClickListener {
            if (viewModel.state.value.id != null) {
                viewModel.reloadRow(); isEditing = false; updateUiForMode()
            } else {
                showDiscardDialog { finish() }
            }
        }

        btnDelete.setOnClickListener {
            AlertDialog.Builder(this, R.style.DeleteDialogTheme)
                .setTitle("Delete Opening Balance")
                .setMessage("Are you sure you want to delete this row? This action cannot be undone.")
                .setPositiveButton("Delete") { _, _ -> viewModel.deleteRow { finish() } }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun updateUiForMode() {
        val allInputs: List<View> = listOf(
            actType, actPosition, actCategory,
            etBlockTotal, etBlockNight, etBlockDay,
            etFlightTotal, etFlightNight, etFlightDay,
            etIfrTotal, etIfrNight, etIfrDay,
            etSimulatorTime, etInstrumentApproaches,
            etToDay, etToNight, etToTotal,
            etLdgDay, etLdgNight, etLdgTotal,
            etSinglePilotSe, etSinglePilotMe, etMultiPilot,
            etPfPic, etPfSic, etPfDual, etPfInstructor,
            etComment
        )
        for (v in allInputs) v.isEnabled = isEditing

        tvStartDate.isEnabled = isEditing
        tvEndDate.isEnabled = isEditing

        if (mergedMode) {
            // Read-only merged view: no Edit, Save, Cancel, Delete.
            btnEdit.visibility = View.GONE
            btnSave.visibility = View.GONE
            btnCancel.visibility = View.GONE
            btnDelete.visibility = View.GONE
            return
        }

        btnSave.isEnabled = isEditing
        btnSave.visibility = if (isEditing) View.VISIBLE else View.GONE

        btnDelete.isEnabled = isEditing && viewModel.state.value.id != null
        btnDelete.visibility = if (isEditing && viewModel.state.value.id != null) View.VISIBLE else View.GONE

        btnEdit.visibility = if (isEditing) View.GONE else View.VISIBLE

        btnCancel.visibility = if (isEditing) View.VISIBLE else View.GONE
    }

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

            if (fixedList != null) {
                autoComplete.setOnFocusChangeListener { _, hasFocus ->
                    if (hasFocus && autoComplete.text.isEmpty()) {
                        val adapter = android.widget.ArrayAdapter(
                            this@OpeningBalanceEditActivity,
                            android.R.layout.simple_dropdown_item_1line,
                            fixedList
                        )
                        autoComplete.setAdapter(adapter)
                        autoComplete.showDropDown()
                    }
                }
            }

            autoComplete.addTextChangedListener(object : android.text.TextWatcher {
                private var lastQuery = ""
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: android.text.Editable?) {
                    val query = s.toString()
                    if (!autoComplete.hasFocus()) return
                    if (autoComplete.isPerformingCompletion) {
                        updateFunction(query); updatedFlag = true; return
                    }

                    if (fixedList != null && query.length < 2) {
                        val filtered = if (query.isEmpty()) fixedList
                        else fixedList.filter { it.contains(query, ignoreCase = true) }
                        if (filtered.isNotEmpty()) {
                            val adapter = android.widget.ArrayAdapter(
                                this@OpeningBalanceEditActivity,
                                android.R.layout.simple_dropdown_item_1line,
                                filtered
                            )
                            autoComplete.setAdapter(adapter)
                            autoComplete.showDropDown()
                        } else autoComplete.dismissDropDown()
                        updateFunction(query); updatedFlag = true; return
                    }

                    if (query.length >= 2 && query != lastQuery) {
                        lastQuery = query
                        lifecycleScope.launch {
                            try {
                                val suggestions = suggestionFunction(query)
                                if (suggestions.isNotEmpty()) {
                                    val adapter = android.widget.ArrayAdapter(
                                        this@OpeningBalanceEditActivity,
                                        android.R.layout.simple_dropdown_item_1line,
                                        suggestions
                                    )
                                    autoComplete.setAdapter(adapter)
                                    autoComplete.showDropDown()
                                } else autoComplete.dismissDropDown()
                            } catch (_: Exception) {}
                        }
                    } else if (query.isEmpty()) autoComplete.dismissDropDown()
                    updateFunction(query); updatedFlag = true
                }
            })

            autoComplete.setOnItemClickListener { adapterView, _, position, _ ->
                val selected = adapterView.getItemAtPosition(position) as? String ?: return@setOnItemClickListener
                if (!updatedFlag) updateFunction(selected)
                updatedFlag = false
                autoComplete.clearFocus()
                autoComplete.dismissDropDown()
            }
        }

        setupAutoComplete(actType, { viewModel.searchAcType(it) }, { viewModel.updateAcType(it) })
        setupAutoComplete(actPosition, { viewModel.searchPosition(it) }, { viewModel.updatePosition(it) }, fixedList = fixedPositionList)
        setupAutoComplete(actCategory, { viewModel.searchCategory(it) }, { viewModel.updateCategory(it) }, fixedList = fixedCategoryList)
    }

    private fun observeState() {
        lifecycleScope.launch {
            viewModel.state.collect { state ->
                isUpdatingFromViewModel = true

                tvStartDate.text = state.startDate?.let { formatDateOnly(it) } ?: "Select date"
                tvEndDate.text = state.endDate?.let { formatDateOnly(it) } ?: "Select date"

                fun setIfNotFocused(field: EditText, value: String) { if (!field.hasFocus()) field.setText(value) }
                fun setIfNotFocused(field: AutoCompleteTextView, value: String) { if (!field.hasFocus()) field.setText(value) }

                setIfNotFocused(actType, state.acType)
                setIfNotFocused(actPosition, state.position)
                setIfNotFocused(actCategory, state.category)
                setIfNotFocused(etBlockTotal, state.blockTimeTotal)
                setIfNotFocused(etBlockNight, state.blockTimeNight)
                setIfNotFocused(etBlockDay, state.blockTimeDay)
                setIfNotFocused(etFlightTotal, state.flightTimeTotal)
                setIfNotFocused(etFlightNight, state.flightTimeNight)
                setIfNotFocused(etFlightDay, state.flightTimeDay)
                setIfNotFocused(etIfrTotal, state.ifrTotal)
                setIfNotFocused(etIfrNight, state.ifrNight)
                setIfNotFocused(etIfrDay, state.ifrDay)
                setIfNotFocused(etSimulatorTime, state.simulatorTime)
                setIfNotFocused(etInstrumentApproaches, state.numberInstrumentApproaches)
                setIfNotFocused(etToDay, state.numberToDay)
                setIfNotFocused(etToNight, state.numberToNight)
                setIfNotFocused(etToTotal, state.numberToTotal)
                setIfNotFocused(etLdgDay, state.numberLdgDay)
                setIfNotFocused(etLdgNight, state.numberLdgNight)
                setIfNotFocused(etLdgTotal, state.numberLdgTotal)
                setIfNotFocused(etSinglePilotSe, state.singlePilotSeTime)
                setIfNotFocused(etSinglePilotMe, state.singlePilotMeTime)
                setIfNotFocused(etMultiPilot, state.multiPilotTime)
                setIfNotFocused(etPfPic, state.pilotFunctionPicTime)
                setIfNotFocused(etPfSic, state.pilotFunctionSicTime)
                setIfNotFocused(etPfDual, state.pilotFunctionDualTime)
                setIfNotFocused(etPfInstructor, state.pilotFunctionInstructorTime)
                setIfNotFocused(etComment, state.comment)

                isUpdatingFromViewModel = false
                updateUiForMode()
            }
        }
    }

    private fun markFieldAsWarning(field: EditText, warning: Boolean) {
        if (warning) {
            field.setBackgroundColor(androidx.core.content.ContextCompat.getColor(this, R.color.warning))
            invalidFields.add(field)
        } else {
            defaultBackgrounds[field]?.let { field.background = it }
            invalidFields.remove(field)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            if (isEditing) showDiscardDialog { finish() } else finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}