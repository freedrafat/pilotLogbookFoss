package com.my.striptologbook.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.my.striptologbook.data.*
import com.my.striptologbook.repository.LegRepository
import com.my.striptologbook.utils.PreferencesManager
import com.my.striptologbook.utils.getDateAtMidnight
import com.my.striptologbook.utils.getTodayMidnight
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.TimeZone

class StatisticsViewModel(
    private val repository: LegRepository,
    private val filterType: String?,
    private val customStart: Long?,
    private val customEnd: Long?,
    private val mode: String,
    private val appContext: Context
) : ViewModel() {

    private val _typeStats = MutableStateFlow<List<TypeStatRow>>(emptyList())
    val typeStats: StateFlow<List<TypeStatRow>> = _typeStats.asStateFlow()

    private val _yearStats = MutableStateFlow<List<YearStat>>(emptyList())
    val yearStats: StateFlow<List<YearStat>> = _yearStats.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private data class StatDef(
        val name: String,
        val legExtractor: (Leg) -> Int,
        val balanceExtractor: (OpeningBalance) -> Int
    )

    init {
        viewModelScope.launch {
            if (mode == "type") {
                computeTypeStats()
            } else {
                computeMonthYearStats()
            }
            _isLoading.value = false
        }
    }

    private suspend fun computeTypeStats() {
        val allLegs = repository.getAllLegsList()
        val filteredLegs = applyFilter(allLegs)
        val openingBalances = repository.getAllOpeningBalancesList()

        if (filteredLegs.isEmpty() && openingBalances.isEmpty()) {
            _typeStats.value = emptyList()
            return
        }

        // Positions from filtered legs (no position -> "Unknown")
        val legPositions = filteredLegs.map { it.position ?: "Unknown" }
        // Positions from opening balances that have a position
        val balancePositions = openingBalances
            .mapNotNull { it.position?.takeIf { p -> p.isNotBlank() } }
        // Opening balance rows without a position go to a separate "Prior" column
        val hasPositionlessBalance = openingBalances.any { it.position.isNullOrBlank() }

        val positions = (legPositions + balancePositions).distinct().sorted().toMutableList()
        if (hasPositionlessBalance) {
            positions.add("Prior")
        }

        val stats = listOf(
            StatDef("Block",
                { it.blockTimeTotal ?: 0 },
                { it.blockTimeTotal ?: 0 }),
            StatDef("Block Night",
                { it.blockTimeNight ?: 0 },
                { it.blockTimeNight ?: 0 }),
            StatDef("Block Day",
                { it.blockTimeDay ?: 0 },
                { it.blockTimeDay ?: 0 }),
            StatDef("Flight Time",
                { it.flightTimeTotal ?: 0 },
                { it.flightTimeTotal ?: 0 }),
            StatDef("Flight Night",
                { it.flightTimeNight ?: 0 },
                { it.flightTimeNight ?: 0 }),
            StatDef("Flight Day",
                { it.flightTimeDay ?: 0 },
                { it.flightTimeDay ?: 0 }),
            StatDef("VFR",
                { (it.flightTimeTotal ?: 0) - (it.ifrTotal ?: 0) },
                { (it.flightTimeTotal ?: 0) - (it.ifrTotal ?: 0) }),
            StatDef("VFR Night",
                { (it.flightTimeNight ?: 0) - (it.ifrNight ?: 0) },
                { (it.flightTimeNight ?: 0) - (it.ifrNight ?: 0) }),
            StatDef("VFR Day",
                { (it.flightTimeDay ?: 0) - (it.ifrDay ?: 0) },
                { (it.flightTimeDay ?: 0) - (it.ifrDay ?: 0) }),
            StatDef("IFR",
                { it.ifrTotal ?: 0 },
                { it.ifrTotal ?: 0 }),
            StatDef("IFR Night",
                { it.ifrNight ?: 0 },
                { it.ifrNight ?: 0 }),
            StatDef("IFR Day",
                { it.ifrDay ?: 0 },
                { it.ifrDay ?: 0 }),
            StatDef("Simulator",
                { it.simulatorTime ?: 0 },
                { it.simulatorTime ?: 0 }),
            StatDef("# T/O",
                { it.numberToTotal ?: 0 },
                { it.numberToTotal ?: 0 }),
            StatDef("# LDG",
                { it.numberLdgTotal ?: 0 },
                { it.numberLdgTotal ?: 0 }),
            StatDef("# T/O Night",
                { it.numberToNight ?: 0 },
                { it.numberToNight ?: 0 }),
            StatDef("# LDG Night",
                { it.numberLdgNight ?: 0 },
                { it.numberLdgNight ?: 0 }),
            StatDef("# T/O Day",
                { it.numberToDay ?: 0 },
                { it.numberToDay ?: 0 }),
            StatDef("# LDG Day",
                { it.numberLdgDay ?: 0 },
                { it.numberLdgDay ?: 0 })
        )

        val allRows = stats.map { def ->
            val posTotals = positions.associateWith { pos ->
                val fromLegs = filteredLegs
                    .filter { (it.position ?: "Unknown") == pos }
                    .sumOf { def.legExtractor(it).toLong() }
                val fromBalance = openingBalances
                    .filter { ob ->
                        when (pos) {
                            "Prior" -> ob.position.isNullOrBlank()
                            else -> ob.position == pos
                        }
                    }
                    .sumOf { def.balanceExtractor(it).toLong() }
                fromLegs + fromBalance
            }
            val total = posTotals.values.sum()
            TypeStatRow(def.name, posTotals, total)
        }

        val prefs = PreferencesManager.getInstance(appContext)
        val visibleRows = allRows.filter { row ->
            prefs.isStatisticVisible(row.statisticName)
        }

        _typeStats.value = visibleRows
    }

    fun refreshTypeStats() {
        viewModelScope.launch {
            computeTypeStats()
        }
    }

    // ----- FILTER (unchanged) -----
    private fun applyFilter(legs: List<Leg>): List<Leg> {
        val filter = try { FilterType.valueOf(filterType ?: "ALL") } catch (_: Exception) { FilterType.ALL }
        if (filter == FilterType.ALL) return legs

        val todayMidnight = getTodayMidnight()
        var start: Long? = null
        var end: Long? = null

        when (filter) {
            FilterType.LAST_7_DAYS -> {
                start = todayMidnight - 6 * 24 * 60 * 60 * 1000L
                end = todayMidnight
            }
            FilterType.LAST_28_DAYS -> {
                start = todayMidnight - 27 * 24 * 60 * 60 * 1000L
                end = todayMidnight
            }
            FilterType.ACTUAL_MONTH -> {
                val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                    timeInMillis = System.currentTimeMillis()
                    set(Calendar.DAY_OF_MONTH, 1)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                start = cal.timeInMillis
                end = todayMidnight
            }
            FilterType.PREVIOUS_MONTH -> {
                val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                    timeInMillis = System.currentTimeMillis()
                    add(Calendar.MONTH, -1)
                    set(Calendar.DAY_OF_MONTH, 1)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                start = cal.timeInMillis
                val currentMonthFirst = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                    timeInMillis = System.currentTimeMillis()
                    set(Calendar.DAY_OF_MONTH, 1)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                end = currentMonthFirst - 1
            }
            FilterType.ACTUAL_YEAR -> {
                val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                    timeInMillis = System.currentTimeMillis()
                    set(Calendar.DAY_OF_YEAR, 1)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                start = cal.timeInMillis
                end = todayMidnight
            }
            FilterType.CUSTOM -> {
                start = customStart
                end = customEnd ?: todayMidnight
            }
            FilterType.ALL -> {
                start = null
                end = null
            }
        }

        return legs.filter { leg ->
            val offBlock = leg.offBlock ?: return@filter false
            val dateMidnight = getDateAtMidnight(offBlock)
            val startOk = start == null || dateMidnight >= start
            val endOk = end == null || dateMidnight <= end
            startOk && endOk
        }
    }

    // ----- Month/Year logic (updated) -----
    private suspend fun computeMonthYearStats() {
        val allLegs = repository.getAllLegsList()
        val openingBalances = repository.getAllOpeningBalancesList()

        if (allLegs.isEmpty() && openingBalances.isEmpty()) {
            _yearStats.value = emptyList()
            return
        }

        val yearMap = mutableMapOf<Int, MutableList<Leg>>()
        for (leg in allLegs) {
            val offBlock = leg.offBlock ?: continue
            val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = offBlock }
            val year = cal.get(Calendar.YEAR)
            yearMap.getOrPut(year) { mutableListOf() }.add(leg)
        }

        val sortedYears = yearMap.keys.sortedDescending()
        val yearStatsList = sortedYears.map { year ->
            val yearLegs = yearMap[year] ?: emptyList()
            val blockTotal = yearLegs.sumOf { it.blockTimeTotal ?: 0 }.toLong()
            val blockNight = yearLegs.sumOf { it.blockTimeNight ?: 0 }.toLong()
            val simTime = yearLegs.sumOf { it.simulatorTime ?: 0 }.toLong()
            val count = yearLegs.count { it.isSimulator != true }
            YearStat(year, blockTotal, blockNight, count, simTime, expanded = false)
        }.toMutableList()

        // Prior row (Option A: inserted at the position of its earliest startDate)
        if (openingBalances.isNotEmpty()) {
            val priorBlockTotal = openingBalances.sumOf { it.blockTimeTotal ?: 0 }.toLong()
            val priorBlockNight = openingBalances.sumOf { it.blockTimeNight ?: 0 }.toLong()
            val priorSimTime    = openingBalances.sumOf { it.simulatorTime ?: 0 }.toLong()

            val earliestStart = openingBalances
                .mapNotNull { it.startDate }
                .minOrNull()

            val priorRow = YearStat(
                year = 0,
                blockTotal = priorBlockTotal,
                blockNight = priorBlockNight,
                legCount = 0,
                simTime = priorSimTime,
                expanded = false,
                isPrior = true
            )

            if (earliestStart != null) {
                val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                    timeInMillis = earliestStart
                }
                val priorYear = cal.get(Calendar.YEAR)
                var insertIdx = yearStatsList.size
                for (i in yearStatsList.indices) {
                    if (yearStatsList[i].year < priorYear) {
                        insertIdx = i
                        break
                    }
                }
                yearStatsList.add(insertIdx, priorRow)
            } else {
                // No start date on any row — place at the bottom
                yearStatsList.add(priorRow)
            }
        }

        _yearStats.value = yearStatsList
    }

    suspend fun getMonthsForYear(year: Int): List<MonthStat> {
        val allLegs = repository.getAllLegsList()
        val yearLegs = allLegs.filter { leg ->
            val offBlock = leg.offBlock ?: return@filter false
            val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = offBlock }
            cal.get(Calendar.YEAR) == year
        }
        val monthMap = mutableMapOf<Int, MutableList<Leg>>()
        for (leg in yearLegs) {
            val offBlock = leg.offBlock ?: continue
            val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = offBlock }
            val month = cal.get(Calendar.MONTH) + 1
            monthMap.getOrPut(month) { mutableListOf() }.add(leg)
        }
        val months = (12 downTo 1).mapNotNull { month ->
            val legs = monthMap[month] ?: return@mapNotNull null
            val blockTotal = legs.sumOf { it.blockTimeTotal ?: 0 }.toLong()
            val blockNight = legs.sumOf { it.blockTimeNight ?: 0 }.toLong()
            val simTime = legs.sumOf { it.simulatorTime ?: 0 }.toLong()
            val count = legs.count { it.isSimulator != true }
            MonthStat(month, blockTotal, blockNight, count, simTime)
        }
        return months
    }

    fun toggleYearExpansion(year: Int) {
        val current = _yearStats.value
        val updated = current.map { stat ->
            if (stat.year == year && !stat.isPrior) stat.copy(expanded = !stat.expanded) else stat
        }
        _yearStats.value = updated
    }
}