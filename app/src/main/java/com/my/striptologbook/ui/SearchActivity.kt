package com.my.striptologbook.ui

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.MenuItem
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.widget.SwitchCompat
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.my.striptologbook.LogbookApplication
import com.my.striptologbook.R
import com.my.striptologbook.data.LogicOperator
import com.my.striptologbook.data.SearchCondition
import com.my.striptologbook.data.SearchField
import com.my.striptologbook.data.SearchFields
import com.my.striptologbook.data.SearchOperator
import com.my.striptologbook.utils.InsetsHelper

import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import com.my.striptologbook.utils.formatDateDigits
import com.my.striptologbook.utils.formatDateOnly
import com.my.striptologbook.utils.normalizeDuration
import com.my.striptologbook.utils.normalizeTime
import com.my.striptologbook.utils.showCustomDatePicker

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.milliseconds

import android.app.Dialog
import android.view.ViewGroup
import com.my.striptologbook.utils.formatDateDigits
import com.my.striptologbook.utils.formatDateOnly
import com.my.striptologbook.utils.normalizeDuration
import com.my.striptologbook.utils.normalizeTime
import com.my.striptologbook.utils.showCustomDatePicker

class SearchActivity : BaseActivity() {
    private lateinit var viewModel: SearchViewModel
    private lateinit var adapter: SearchConditionAdapter

    private lateinit var btnEqContains: Button
    private lateinit var btnNotEq: Button
    private lateinit var btnLe: Button
    private lateinit var btnGe: Button
    private lateinit var rvConditions: RecyclerView
    private lateinit var swLogic: SwitchCompat
    private lateinit var tvLogicLabel: TextView
    private lateinit var btnSearch: Button
    private lateinit var tvConditionsTitle: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search)

        // -------- Status bar spacer ------------------------------
        InsetsHelper.applyStatusBarSpacer(findViewById(R.id.status_bar_spacer))

        // -------- Toolbar -----------------------------------------
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = "Search"              //change title
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finish()
            }
        })

        val repository = (application as LogbookApplication).repository
        val factory = SearchViewModelFactory(repository)
        viewModel = ViewModelProvider(this, factory)[SearchViewModel::class.java]

        // Initialize views
        btnEqContains = findViewById(R.id.btn_eq_contains)
        btnNotEq = findViewById(R.id.btn_not_eq)
        btnLe = findViewById(R.id.btn_le)
        btnGe = findViewById(R.id.btn_ge)
        rvConditions = findViewById(R.id.rv_conditions)
        swLogic = findViewById(R.id.sw_logic)
        tvLogicLabel = findViewById(R.id.tv_logic_label)
        btnSearch = findViewById(R.id.btn_search)
        tvConditionsTitle = findViewById(R.id.tv_conditions_title)

        // Setup RecyclerView
        rvConditions.layoutManager = LinearLayoutManager(this)
        adapter = SearchConditionAdapter { condition ->
            viewModel.removeCondition(condition)
        }
        rvConditions.adapter = adapter

        // Operator button clicks
        btnEqContains.setOnClickListener {
            showFieldSelectionDialog(SearchOperator.EQ_CONTAINS)
        }
        btnNotEq.setOnClickListener {
            showFieldSelectionDialog(SearchOperator.NOT_EQ)
        }
        btnLe.setOnClickListener {
            showFieldSelectionDialog(SearchOperator.LE)
        }
        btnGe.setOnClickListener {
            showFieldSelectionDialog(SearchOperator.GE)
        }

        // Logic switch
        swLogic.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setLogicOperator(if (isChecked) LogicOperator.AND else LogicOperator.OR)
            updateLogicLabel()
        }

        // Search button
        btnSearch.setOnClickListener {
            val conditions = viewModel.conditions.value
            val logicOperator = viewModel.logicOperator.value
            val query = com.my.striptologbook.data.SearchQuery(conditions, logicOperator)
            val json = Json.encodeToString(query)
            val intent = Intent(this, LegListActivity::class.java).apply {
                putExtra("searchQuery", json)
            }
            startActivity(intent)
//            finish()
        }

        // Observe ViewModel state
        lifecycleScope.launch {
            viewModel.conditions.collect { conditions ->
                adapter.submitList(conditions)
                btnSearch.isEnabled = conditions.isNotEmpty()
                tvConditionsTitle.text = "Conditions (${conditions.size})"
            }
        }

        // Initially set logic label
        updateLogicLabel()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun showFieldSelectionDialog(operator: SearchOperator) {
        val availableFields = SearchFields.getFieldsForOperator(operator)
        val fieldNames = availableFields.map { it.displayName }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Select Field")
            .setItems(fieldNames) { _, which ->
                val field = availableFields[which]
                showValueInputDialog(field, operator)
            }
            .setNegativeButton("Cancel") { _, _ -> }
            .show()
    }

    private fun showValueInputDialog(field: SearchField, operator: SearchOperator) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_value_input, null)
        val autoComplete = dialogView.findViewById<AutoCompleteTextView>(R.id.et_value)
        val timestampLayout = dialogView.findViewById<LinearLayout>(R.id.timestamp_layout)
        val tvDate = dialogView.findViewById<TextView>(R.id.tv_date)
        val etTime = dialogView.findViewById<EditText>(R.id.et_time)
        val btnClear = dialogView.findViewById<Button>(R.id.btn_clear)
        val btnCancel = dialogView.findViewById<Button>(R.id.btn_cancel)
        val btnOk = dialogView.findViewById<Button>(R.id.btn_save_ok)

        val isTimestamp = field.type == "timestamp"
        val isDuration = field.type == "duration"
        val isBoolean = field.type == "boolean"

        if (isTimestamp) {
            autoComplete.visibility = View.GONE
            timestampLayout.visibility = View.VISIBLE
            btnClear.visibility = View.VISIBLE
        } else {
            autoComplete.visibility = View.VISIBLE
            timestampLayout.visibility = View.GONE
            btnClear.visibility = View.GONE
        }

        // ---------- Timestamp branch state ----------
        var selectedDate: Long? = null
        val defaultTimeBackground = etTime.background
        fun clearTimeWarning() { etTime.background = defaultTimeBackground }
        fun markTimeWarning() { etTime.setBackgroundColor(ContextCompat.getColor(this, R.color.warning)) }

        if (isTimestamp) {
            tvDate.setOnClickListener {
                showCustomDatePicker(this, selectedDate) { picked ->
                    selectedDate = picked
                    tvDate.text = formatDateOnly(picked)
                }
            }

            etTime.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    clearTimeWarning()
                } else {
                    val raw = etTime.text.toString()
                    if (raw.isBlank()) {
                        clearTimeWarning()
                        return@setOnFocusChangeListener
                    }
                    val normalized = normalizeTime(raw)
                    if (normalized == "invalid") {
                        etTime.setText("")
                        markTimeWarning()
                    } else {
                        if (normalized != raw) etTime.setText(normalized)
                        clearTimeWarning()
                    }
                }
            }
            etTime.setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_NEXT) {
                    etTime.clearFocus(); true
                } else false
            }

            btnClear.setOnClickListener {
                selectedDate = null
                tvDate.text = ""
                etTime.setText("")
                clearTimeWarning()
            }
        }

        // ---------- Duration branch ----------
        val defaultAutoCompleteBackground = autoComplete.background
        fun clearDurationWarning() { autoComplete.background = defaultAutoCompleteBackground }
        fun markDurationWarning() { autoComplete.setBackgroundColor(ContextCompat.getColor(this, R.color.warning)) }

        if (isDuration) {
            autoComplete.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    clearDurationWarning()
                } else {
                    val raw = autoComplete.text.toString()
                    if (raw.isBlank()) {
                        clearDurationWarning()
                        return@setOnFocusChangeListener
                    }
                    val normalized = normalizeDuration(raw)
                    if (normalized == "invalid") {
                        autoComplete.setText("")
                        markDurationWarning()
                    } else {
                        if (normalized != raw) autoComplete.setText(normalized)
                        clearDurationWarning()
                    }
                }
            }
            autoComplete.setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_NEXT) {
                    autoComplete.clearFocus(); true
                } else false
            }
            autoComplete.threshold = Int.MAX_VALUE
        }

        // ---------- Boolean branch: fixed true / false dropdown ----------
        if (isBoolean) {
            val boolList = listOf("true", "false")
            autoComplete.threshold = 0

            autoComplete.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus && autoComplete.text.isEmpty()) {
                    val adapter = ArrayAdapter(
                        this@SearchActivity,
                        android.R.layout.simple_dropdown_item_1line,
                        boolList
                    )
                    autoComplete.setAdapter(adapter)
                    autoComplete.showDropDown()
                }
            }

            autoComplete.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    if (!autoComplete.hasFocus()) return
                    if (autoComplete.isPerformingCompletion) return
                    val q = s.toString()
                    val filtered = if (q.isEmpty()) boolList
                    else boolList.filter { it.contains(q, ignoreCase = true) }
                    if (filtered.isNotEmpty()) {
                        val adapter = ArrayAdapter(
                            this@SearchActivity,
                            android.R.layout.simple_dropdown_item_1line,
                            filtered
                        )
                        autoComplete.setAdapter(adapter)
                        autoComplete.showDropDown()
                    } else {
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

            autoComplete.setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_NEXT) {
                    autoComplete.clearFocus(); true
                } else false
            }
        }

        // ---------- String + = / <> : dynamic suggestions ----------
        val isStringField = field.type == "string"
        val isTextOperator = operator == SearchOperator.EQ_CONTAINS || operator == SearchOperator.NOT_EQ
        val enableSuggestions = isStringField && isTextOperator

        var fetchVersion = 0
        var lastSelectedText: String? = null

        if (enableSuggestions) {
            autoComplete.threshold = 2

            autoComplete.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    if (!autoComplete.hasFocus()) return
                    if (autoComplete.isPerformingCompletion) return
                    val query = s.toString()
                    if (query == lastSelectedText) return

                    val thisVersion = ++fetchVersion
                    lifecycleScope.launch {
                        delay(300.milliseconds)
                        if (thisVersion != fetchVersion) return@launch
                        val suggestions = viewModel.getSuggestions(field.name, query)
                        if (thisVersion != fetchVersion) return@launch
                        if (suggestions.isNotEmpty()) {
                            val adapter = ArrayAdapter(
                                this@SearchActivity,
                                android.R.layout.simple_dropdown_item_1line,
                                suggestions
                            )
                            autoComplete.setAdapter(adapter)
                            autoComplete.showDropDown()
                        } else {
                            autoComplete.dismissDropDown()
                        }
                    }
                }
            })

            autoComplete.setOnItemClickListener { adapterView, _, position, _ ->
                val selected = adapterView.getItemAtPosition(position) as? String ?: return@setOnItemClickListener
                lastSelectedText = selected
                fetchVersion++
                autoComplete.setText(selected)
                autoComplete.setSelection(selected.length)
                autoComplete.clearFocus()
                autoComplete.dismissDropDown()
            }

            autoComplete.setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_NEXT) {
                    autoComplete.clearFocus(); true
                } else false
            }
        }

        // ---------- Number branch ----------
        if (!isTimestamp && !isDuration && !isBoolean && !enableSuggestions) {
            autoComplete.threshold = Int.MAX_VALUE
            autoComplete.setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_NEXT) {
                    autoComplete.clearFocus(); true
                } else false
            }
        }

        // ---------- Dialog ----------
        val dialog = Dialog(this)
        dialog.setContentView(dialogView)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        val widthPx = (resources.displayMetrics.widthPixels * 0.92f).toInt()
        dialog.window?.setLayout(widthPx, ViewGroup.LayoutParams.WRAP_CONTENT)

        btnCancel.setOnClickListener { dialog.dismiss() }

        btnOk.setOnClickListener {
            when {
                isTimestamp -> {
                    val rawTime = etTime.text.toString().trim()
                    var timeDigits: String? = null
                    if (rawTime.isNotBlank()) {
                        val normalized = normalizeTime(rawTime)
                        if (normalized == "invalid") {
                            etTime.setText("")
                            markTimeWarning()
                            return@setOnClickListener
                        }
                        timeDigits = normalized.replace(":", "")
                    }
                    val dateDigits = selectedDate?.let { formatDateDigits(it) }

                    if (dateDigits == null && timeDigits == null) {
                        if (operator == SearchOperator.EQ_CONTAINS || operator == SearchOperator.NOT_EQ) {
                            viewModel.addCondition(
                                SearchCondition(
                                    fieldName = field.name,
                                    operator = operator,
                                    value = null,
                                    valueType = "empty"
                                )
                            )
                        }
                    } else {
                        val value = when {
                            dateDigits != null && timeDigits != null -> "$dateDigits $timeDigits"
                            dateDigits != null -> dateDigits
                            else -> timeDigits!!
                        }
                        val valueType = when {
                            dateDigits != null && timeDigits != null -> "timestamp"
                            dateDigits != null -> "timestamp_date"
                            else -> "timestamp_time"
                        }
                        viewModel.addCondition(
                            SearchCondition(
                                fieldName = field.name,
                                operator = operator,
                                value = value,
                                valueType = valueType
                            )
                        )
                    }
                    dialog.dismiss()
                }
                isDuration -> {
                    val raw = autoComplete.text.toString().trim()
                    var stored: String? = null
                    if (raw.isNotBlank()) {
                        val normalized = normalizeDuration(raw)
                        if (normalized == "invalid") {
                            autoComplete.setText("")
                            markDurationWarning()
                            return@setOnClickListener
                        }
                        stored = normalized
                    }
                    if (stored == null) {
                        if (operator == SearchOperator.EQ_CONTAINS || operator == SearchOperator.NOT_EQ) {
                            viewModel.addCondition(
                                SearchCondition(
                                    fieldName = field.name,
                                    operator = operator,
                                    value = null,
                                    valueType = "empty"
                                )
                            )
                        }
                    } else {
                        viewModel.addCondition(
                            SearchCondition(
                                fieldName = field.name,
                                operator = operator,
                                value = stored,
                                valueType = "duration"
                            )
                        )
                    }
                    dialog.dismiss()
                }
                else -> {
                    val inputText = autoComplete.text.toString()
                    if (inputText.isBlank() && (operator == SearchOperator.EQ_CONTAINS || operator == SearchOperator.NOT_EQ)) {
                        viewModel.addCondition(
                            SearchCondition(
                                fieldName = field.name,
                                operator = operator,
                                value = null,
                                valueType = "empty"
                            )
                        )
                    } else if (inputText.isNotBlank()) {
                        val valueType = when (field.type) {
                            "string" -> "string"
                            "boolean" -> "boolean"
                            "number" -> "number"
                            else -> "string"
                        }
                        viewModel.addCondition(
                            SearchCondition(
                                fieldName = field.name,
                                operator = operator,
                                value = inputText,
                                valueType = valueType
                            )
                        )
                    }
                    dialog.dismiss()
                }
            }
        }

        dialog.show()
    }


    private fun updateLogicLabel() {
        tvLogicLabel.text = if (swLogic.isChecked) "AND" else "OR"
    }
}