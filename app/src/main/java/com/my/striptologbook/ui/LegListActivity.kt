package com.my.striptologbook.ui

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.my.striptologbook.LogbookApplication
import com.my.striptologbook.R
import com.my.striptologbook.data.*
import com.my.striptologbook.utils.InsetsHelper
import com.my.striptologbook.utils.PreferencesManager
import com.my.striptologbook.utils.formatDateOnly
import com.my.striptologbook.utils.formatDuration
import com.my.striptologbook.utils.getDateAtMidnight
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.TimeZone

class LegListActivity : BaseActivity() {

    private lateinit var viewModel: LegViewModel
    private lateinit var adapter: LegAdapter
    private lateinit var rvLegs: RecyclerView
    private lateinit var tvBlockTotal: TextView
    private lateinit var tvBlockNight: TextView
    private lateinit var tvLegCount: TextView
    private lateinit var tvSimTime: TextView
    private lateinit var btnFilter: Button
    private lateinit var btnSearch: ImageButton
    private lateinit var btnStatistics: ImageButton
    private lateinit var filterRow: View

    private var searchQuery: String? = null
    private var isSearchMode = false
    private var customStart: Long? = null
    private var customEnd: Long? = null

    private var toolbarTitle: String? = "Legs List"
    private var toolbarSearchTitle: String? = "Search Results"


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_leg_list)

        // -------- Status bar spacer ------------------------------
        val spacer = findViewById<View>(R.id.status_bar_spacer)
        InsetsHelper.applyStatusBarSpacer(spacer)

        // -------- Toolbar -----------------------------------------
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = if (isSearchMode) toolbarSearchTitle else toolbarTitle

        // plus button click listener
//        val btnAddLeg: ImageButton = findViewById(R.id.btn_add_leg)
//        btnAddLeg.setOnClickListener {
//            startActivity(Intent(this, LegEditActivity::class.java))
//        }


        ViewCompat.setOnApplyWindowInsetsListener(spacer) { view, insets ->
            val statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top
            if (statusBarHeight > 0) {
                val params = view.layoutParams
                params.height = statusBarHeight
                view.requestLayout()
            }
            insets
        }

        // Views
        rvLegs = findViewById(R.id.rv_legs)
        btnFilter = findViewById(R.id.btn_filter)
        btnSearch = findViewById(R.id.btn_search)
        btnStatistics = findViewById(R.id.btn_statistics)
        filterRow = findViewById(R.id.filter_row)

        tvBlockTotal = findViewById(R.id.tv_block_total)
        tvBlockNight = findViewById(R.id.tv_block_night)
        tvLegCount = findViewById(R.id.tv_leg_count)
        tvSimTime = findViewById(R.id.tv_sim_time)

        // Setup RecyclerView
        rvLegs.layoutManager = LinearLayoutManager(this)
        val activeProfile = PreferencesManager.getInstance(this)
            .getLegItemLayout()
            .getActiveProfile()
        adapter = LegAdapter(activeProfile) { leg ->
            val intent = Intent(this, LegEditActivity::class.java).apply {
                putExtra("legId", leg.id)
            }
            startActivity(intent)
        }
        rvLegs.adapter = adapter

        // ViewModel
        val app = application as LogbookApplication
        val factory = LegListViewModelFactory(app.repository)
        viewModel = ViewModelProvider(this, factory)[LegViewModel::class.java]

        // Intent extras
        searchQuery = intent.getStringExtra("searchQuery")
        isSearchMode = !searchQuery.isNullOrEmpty()

        // Update UI for search mode
        if (isSearchMode) {
            supportActionBar?.title = toolbarSearchTitle
            filterRow.visibility = View.GONE
        } else {
            supportActionBar?.title = toolbarTitle
            filterRow.visibility = View.VISIBLE
        }

        // Listeners
        btnFilter.setOnClickListener { showFilterDropdown() }
        btnSearch.setOnClickListener {
            startActivity(Intent(this, SearchActivity::class.java))
        }
        btnStatistics.setOnClickListener { showStatisticsDialog() }

        // Observe ViewModel
        lifecycleScope.launch {
            viewModel.legs.collectLatest { allLegs ->
                updateList(allLegs)
            }
        }

        lifecycleScope.launch {
            viewModel.filteredLegs.collectLatest { filtered ->
                if (!isSearchMode) {
                    adapter.submitList(filtered)
                    updateSummary(filtered)
                }
            }
        }

        lifecycleScope.launch {
            viewModel.currentFilter.collectLatest { filter ->
                if (!isSearchMode) {
                    updateFilterButton(filter)
                }
            }
        }

        lifecycleScope.launch {
            viewModel.customStartDate.collectLatest { start ->
                customStart = start
                if (viewModel.currentFilter.value == FilterType.CUSTOM) {
                    updateFilterButton(FilterType.CUSTOM)
                }
            }
        }

        lifecycleScope.launch {
            viewModel.customEndDate.collectLatest { end ->
                customEnd = end
                if (viewModel.currentFilter.value == FilterType.CUSTOM) {
                    updateFilterButton(FilterType.CUSTOM)
                }
            }
        }

        // Initial filter label
        updateFilterButton(viewModel.currentFilter.value)

        // Back button handling via OnBackPressedDispatcher
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finish()
            }
        })
    }

    private suspend fun updateList(allLegs: List<Leg>) {
        if (isSearchMode && !searchQuery.isNullOrEmpty()) {
            val query = try {
                kotlinx.serialization.json.Json.decodeFromString<SearchQuery>(searchQuery!!)
            } catch (_: Exception) {
                null
            }
            val filtered = if (query != null) {
                applySearch(allLegs, query)
            } else {
                allLegs
            }
            adapter.submitList(filtered)
            updateSummary(filtered)
        }
    }

    private fun applySearch(legs: List<Leg>, query: SearchQuery): List<Leg> {
        return legs.filter { leg ->
            val evaluations = query.conditions.map { cond ->
                evaluateCondition(leg, cond)
            }
            when (query.logicOperator) {
                LogicOperator.AND -> evaluations.all { it }
                LogicOperator.OR -> evaluations.any { it }
            }
        }
    }

    private fun evaluateCondition(leg: Leg, condition: SearchCondition): Boolean {
        val field = SearchFields.allFields.find { it.name == condition.fieldName } ?: return false
        val legValue = field.getValue(leg)
        if (condition.valueType == "empty") {
            val isEmpty = when (field.type) {
                "string" -> {
                    val str = legValue as? String
                    str.isNullOrEmpty()
                }
                "boolean", "timestamp", "duration", "number" -> legValue == null
                else -> false
            }
            return if (condition.operator == SearchOperator.NOT_EQ) !isEmpty else isEmpty
        }

        val isNegated = condition.operator == SearchOperator.NOT_EQ
        val matches = when (condition.operator) {
            SearchOperator.EQ_CONTAINS, SearchOperator.NOT_EQ -> {
                when (field.type) {
                    "string" -> {
                        val strValue = legValue as? String ?: return false
                        val searchValue = condition.value ?: return false
                        strValue.contains(searchValue, ignoreCase = true)
                    }
                    "boolean" -> {
                        val boolValue = legValue as? Boolean ?: return false
                        val searchBool = condition.value?.toBooleanStrictOrNull() ?: return false
                        boolValue == searchBool
                    }
                    "timestamp" -> {
                        val ts = legValue as? Long ?: return false
                        when (val parsed = parseTimestampValue(condition.value)) {
                            is FullTimestamp -> ts == parsed.timestamp
                            is DateOnly -> {
                                val legDate = getDateAtMidnight(ts)
                                legDate in parsed.start..parsed.end
                            }
                            is TimeOnly -> {
                                val legTime = extractTimeOfDay(ts)
                                legTime == parsed.minutes
                            }
                            null -> false
                        }
                    }
                    "duration" -> {
                        val dur = legValue as? Int ?: return false
                        val searchDur = condition.value?.let { parseDuration(it) } ?: return false
                        dur == searchDur
                    }
                    "number" -> {
                        val num = legValue as? Int ?: return false
                        val searchNum = condition.value?.toIntOrNull() ?: return false
                        num == searchNum
                    }
                    else -> false
                }
            }
            SearchOperator.LE -> {
                when (field.type) {
                    "timestamp" -> {
                        val ts = legValue as? Long ?: return false
                        when (val parsed = parseTimestampValue(condition.value)) {
                            is FullTimestamp -> ts <= parsed.timestamp
                            is DateOnly -> ts <= parsed.end
                            is TimeOnly -> extractTimeOfDay(ts) <= parsed.minutes
                            null -> false
                        }
                    }
                    "duration" -> {
                        val dur = legValue as? Int ?: return false
                        val searchDur = condition.value?.let { parseDuration(it) } ?: return false
                        dur <= searchDur
                    }
                    "number" -> {
                        val num = legValue as? Int ?: return false
                        val searchNum = condition.value?.toIntOrNull() ?: return false
                        num <= searchNum
                    }
                    else -> false
                }
            }
            SearchOperator.GE -> {
                when (field.type) {
                    "timestamp" -> {
                        val ts = legValue as? Long ?: return false
                        when (val parsed = parseTimestampValue(condition.value)) {
                            is FullTimestamp -> ts >= parsed.timestamp
                            is DateOnly -> ts >= parsed.start
                            is TimeOnly -> extractTimeOfDay(ts) >= parsed.minutes
                            null -> false
                        }
                    }
                    "duration" -> {
                        val dur = legValue as? Int ?: return false
                        val searchDur = condition.value?.let { parseDuration(it) } ?: return false
                        dur >= searchDur
                    }
                    "number" -> {
                        val num = legValue as? Int ?: return false
                        val searchNum = condition.value?.toIntOrNull() ?: return false
                        num >= searchNum
                    }
                    else -> false
                }
            }
        }
        return if (isNegated) !matches else matches
    }

    // Helper classes and functions
    private sealed class ParsedTimestamp
    private data class FullTimestamp(val timestamp: Long) : ParsedTimestamp()
    private data class DateOnly(val start: Long, val end: Long) : ParsedTimestamp()
    private data class TimeOnly(val minutes: Int) : ParsedTimestamp()

    private fun parseTimestampValue(value: String?): ParsedTimestamp? {
        if (value.isNullOrBlank()) return null
        val parts = value.split(" ")
        if (parts.size == 2) {
            val dateStr = parts[0]
            val timeStr = parts[1]
            val date = com.my.striptologbook.utils.parseDateFromDigits(dateStr)
            val time = com.my.striptologbook.utils.parseTimeFromDigits(timeStr)
            if (date != null && time != null) {
                val ts = com.my.striptologbook.utils.combineDateAndTime(date, time)
                if (ts != null) return FullTimestamp(ts)
            }
        }
        val dateOnly = com.my.striptologbook.utils.parseDateFromDigits(value)
        if (dateOnly != null) {
            val end = dateOnly + 24 * 60 * 60 * 1000L - 1
            return DateOnly(dateOnly, end)
        }
        val timeOnly = com.my.striptologbook.utils.parseTimeFromDigits(value)
        if (timeOnly != null) {
            return TimeOnly(timeOnly)
        }
        return null
    }

    private fun extractTimeOfDay(timestamp: Long): Int {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = timestamp }
        return cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
    }

    private fun parseDuration(value: String): Int? {
        return com.my.striptologbook.utils.parseDuration(value)
    }

    private fun updateFilterButton(filter: FilterType) {
        val label = when (filter) {
            FilterType.ALL -> "All"
            FilterType.LAST_7_DAYS -> "Last 7 days"
            FilterType.LAST_28_DAYS -> "Last 28 days"
            FilterType.ACTUAL_MONTH -> "Actual Month"
            FilterType.PREVIOUS_MONTH -> "Previous Month"
            FilterType.ACTUAL_YEAR -> "Actual Year"
            FilterType.CUSTOM -> {
                when {
                    customStart != null && customEnd != null ->
                        "From ${formatDateOnly(customStart!!)} to ${formatDateOnly(customEnd!!)}"
                    customStart != null ->
                        "From ${formatDateOnly(customStart!!)}"
                    customEnd != null ->
                        "Till ${formatDateOnly(customEnd!!)}"
                    else -> "Custom (incomplete)"
                }
            }
        }
        btnFilter.text = label
//        val isActive = filter != FilterType.ALL
//        btnFilter.setBackgroundColor(
//            if (isActive) getColor(android.R.color.holo_blue_light)
//            else getColor(android.R.color.transparent)
//        )
    }

    private fun showFilterDropdown() {
        val items = arrayOf(
            "All",
            "Last 7 days",
            "Last 28 days",
            "Actual Month",
            "Previous Month",
            "Actual Year",
            "Custom..."
        )
        android.app.AlertDialog.Builder(this)
            .setTitle("Filter")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> viewModel.setFilter(FilterType.ALL)
                    1 -> viewModel.setFilter(FilterType.LAST_7_DAYS)
                    2 -> viewModel.setFilter(FilterType.LAST_28_DAYS)
                    3 -> viewModel.setFilter(FilterType.ACTUAL_MONTH)
                    4 -> viewModel.setFilter(FilterType.PREVIOUS_MONTH)
                    5 -> viewModel.setFilter(FilterType.ACTUAL_YEAR)
                    6 -> showCustomDateDialog()
                }
            }
            .show()
    }

    private fun showCustomDateDialog() {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        DatePickerDialog(
            this,
            { _, year, month, day ->
                val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                    set(year, month, day, 0, 0, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val start = cal.timeInMillis
                DatePickerDialog(
                    this,
                    { _, y, m, d ->
                        val cal2 = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                            set(y, m, d, 0, 0, 0)
                            set(Calendar.MILLISECOND, 0)
                        }
                        val end = cal2.timeInMillis
                        viewModel.setCustomDateRange(start, end)
                        viewModel.setFilter(FilterType.CUSTOM)
                    },
                    calendar.get(Calendar.YEAR),
                    calendar.get(Calendar.MONTH),
                    calendar.get(Calendar.DAY_OF_MONTH)
                ).show()
            },
            calendar[Calendar.YEAR],
            calendar[Calendar.MONTH],
            calendar[Calendar.DAY_OF_MONTH]
        ).show()
    }

    private fun showStatisticsDialog() {
        android.app.AlertDialog.Builder(this)
            .setTitle("Select Statistics Mode")
            .setItems(arrayOf("By Type", "By Month/Year")) { _, which ->
                if (which == 0) {
                    val filterType = viewModel.currentFilter.value
                    val intent = Intent(this, StatisticsActivity::class.java).apply {
                        putExtra("mode", "type")
                        putExtra("filterType", filterType.name)
                        if (filterType == FilterType.CUSTOM) {
                            putExtra("customStart", viewModel.customStartDate.value)
                            putExtra("customEnd", viewModel.customEndDate.value)
                        }
                    }
                    startActivity(intent)
                } else {
                    val intent = Intent(this, StatisticsActivity::class.java).apply {
                        putExtra("mode", "monthyear")
                    }
                    startActivity(intent)
                }
            }
            .show()
    }

    private suspend fun updateSummary(legs: List<Leg>) {
        var blockTotal = 0
        var blockNight = 0
        var simTime = 0
        for (leg in legs) {
            blockTotal += leg.blockTimeTotal ?: 0
            blockNight += leg.blockTimeNight ?: 0
            simTime += leg.simulatorTime ?: 0
        }

        // Opening balance is added only when the current filter is ALL.
        if (viewModel.currentFilter.value == FilterType.ALL) {
            val balance = (application as LogbookApplication).repository.getOpeningBalanceTotals()
            blockTotal += balance.blockTimeTotal
            blockNight += balance.blockTimeNight
            simTime += balance.simulatorTime
        }

        tvBlockTotal.text = formatDuration(blockTotal)
        tvBlockNight.text = formatDuration(blockNight)
        tvLegCount.text = legs.size.toString()
        tvSimTime.text = formatDuration(simTime)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                finish()
                return true
            }
//            R.id.action_add -> {
//                startActivity(Intent(this, LegEditActivity::class.java))
//                return true
//            }
        }
        return super.onOptionsItemSelected(item)
    }
}