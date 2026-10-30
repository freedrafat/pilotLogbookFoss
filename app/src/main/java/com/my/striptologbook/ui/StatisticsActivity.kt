package com.my.striptologbook.ui

import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.ProgressBar
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.my.striptologbook.R
import com.my.striptologbook.data.MonthStat
import com.my.striptologbook.data.YearStat
import com.my.striptologbook.utils.InsetsHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class StatisticsActivity : BaseActivity() {

    private lateinit var progressBar: ProgressBar
    private lateinit var rvStatistics: RecyclerView

    private lateinit var viewModel: StatisticsViewModel

    // For month/year mode: cache months per year
    private val monthCache = mutableMapOf<Int, List<MonthStat>>()

    private var isFetchingMonths = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_statistics)

        // -------- Status bar spacer ------------------------------
        InsetsHelper.applyStatusBarSpacer(findViewById(R.id.status_bar_spacer))

        // -------- Toolbar -----------------------------------------
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = "Statistics"              //change title
        supportActionBar?.setDisplayHomeAsUpEnabled(true)


        progressBar = findViewById(R.id.progress_bar)
        rvStatistics = findViewById(R.id.rv_statistics)
        rvStatistics.layoutManager = LinearLayoutManager(this)

        val mode = intent.getStringExtra("mode") ?: "type"

        val header = findViewById<View>(R.id.header_statistics)
        header.visibility = if (mode == "monthyear") View.VISIBLE else View.GONE

        val filterType = intent.getStringExtra("filterType")
        val customStart = intent.getLongExtra("customStart", -1L).takeIf { it != -1L }
        val customEnd = intent.getLongExtra("customEnd", -1L).takeIf { it != -1L }

        val factory = StatisticsViewModelFactory(
            this, // context
            filterType,
            customStart,
            customEnd,
            mode
        )
        viewModel = ViewModelProvider(this, factory)[StatisticsViewModel::class.java]

        observeViewModel(mode)
    }

    override fun onResume() {
        super.onResume()
        // If we are in type mode, refresh the statistics to apply preference changes
        val mode = intent.getStringExtra("mode") ?: "type"
        if (mode == "type") {
            viewModel.refreshTypeStats()
        }
    }

    // --- Handle the back arrow ---
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun observeViewModel(mode: String) {
        lifecycleScope.launch {
            viewModel.isLoading.collect { loading ->
                progressBar.visibility = if (loading) View.VISIBLE else View.GONE
                rvStatistics.visibility = if (loading) View.GONE else View.VISIBLE
            }
        }

        when (mode) {
            "type" -> observeTypeStats()
            "monthyear" -> observeMonthYearStats()
        }
    }

    private fun observeTypeStats() {
        lifecycleScope.launch {
            viewModel.typeStats.collect { rows ->
                Log.d("Statistics", "TypeStats rows: ${rows.size}")
                val adapter = StatisticsByTypeAdapter(rows)
                rvStatistics.adapter = adapter
            }
        }
    }

    private fun observeMonthYearStats() {
        lifecycleScope.launch {
            viewModel.yearStats.collect { yearStats ->
                buildMonthYearList(yearStats)
            }
        }
    }

    private fun buildMonthYearList(yearStats: List<YearStat>) {
        // ----- 1. Build the full items list from cache -----
        val items = mutableListOf<StatisticsItem>()

        for (yearStat in yearStats) {
            items.add(StatisticsItem.Year(yearStat))

            if (yearStat.expanded) {
                // Check cache or fetch
                val months = monthCache[yearStat.year]
                if (months != null) {
                    // Already cached
                    items.addAll(months.map { StatisticsItem.Month(it, yearStat.year) })
                }
                // If months are not cached, we will fetch them later (outside this loop)
            }
        }

        // ----- 2. Update the adapter -----
        if (rvStatistics.adapter == null) {
            val adapter = StatisticsAdapter(items) { year ->
                viewModel.toggleYearExpansion(year)
                // After toggle, the collector will trigger rebuild
            }
            rvStatistics.adapter = adapter
        } else {
            (rvStatistics.adapter as StatisticsAdapter).updateItems(items)
        }

        // ----- 3. Fetch missing months for all expanded years (only once at a time) -----
        val missingYears = yearStats
            .filter { it.expanded && !monthCache.containsKey(it.year) }
            .map { it.year }

        if (missingYears.isNotEmpty() && !isFetchingMonths) {
            isFetchingMonths = true

            lifecycleScope.launch {
                try {
                    // Fetch each missing year on a background thread
                    missingYears.forEach { year ->
                        val months = viewModel.getMonthsForYear(year)
                        monthCache[year] = months
                    }
                } finally {
                    isFetchingMonths = false
                }

                // After all fetches complete, rebuild the list on the main thread
                withContext(Dispatchers.Main) {
                    // Use the current yearStats from the ViewModel (may have changed)
                    buildMonthYearList(viewModel.yearStats.value)
                }
            }
        }

    }

}