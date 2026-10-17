package com.my.striptologbook.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.my.striptologbook.data.Leg
import com.my.striptologbook.repository.LegRepository
import com.my.striptologbook.utils.getDateAtMidnight
import com.my.striptologbook.utils.getTodayMidnight
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.TimeZone

enum class FilterType {
    ALL,
    LAST_7_DAYS,
    LAST_28_DAYS,
    ACTUAL_MONTH,
    PREVIOUS_MONTH,
    ACTUAL_YEAR,
    CUSTOM
}

class LegViewModel(private val repository: LegRepository) : ViewModel() {

    private val _legs = MutableStateFlow<List<Leg>>(emptyList())
    val legs: StateFlow<List<Leg>> = _legs.asStateFlow()

    private val _currentFilter = MutableStateFlow(FilterType.ACTUAL_MONTH)
    val currentFilter: StateFlow<FilterType> = _currentFilter.asStateFlow()

    private val _customStartDate = MutableStateFlow<Long?>(null)
    val customStartDate: StateFlow<Long?> = _customStartDate.asStateFlow()
    private val _customEndDate = MutableStateFlow<Long?>(null)
    val customEndDate: StateFlow<Long?> = _customEndDate.asStateFlow()

    private val _filteredLegs = MutableStateFlow<List<Leg>>(emptyList())
    val filteredLegs: StateFlow<List<Leg>> = _filteredLegs.asStateFlow()

    data class Summary(
        val blockTotalMinutes: Int,
        val blockNightMinutes: Int,
        val simulatorMinutes: Int,
        val legCount: Int
    )
    private val _summary = MutableStateFlow(Summary(0, 0, 0, 0))
//    val summary: StateFlow<Summary> = _summary.asStateFlow()

    // Flag to prevent automatic default from overriding a user‑selected filter
    private var isUserFilterChanged = false

    init {
        viewModelScope.launch {
            repository.getAllLegs().collect { legList ->
                _legs.value = legList
                if (!isUserFilterChanged) {
                    applySmartDefault(legList)
                } else {
                    // Otherwise just refresh the filtered list with the current filter
                    updateFilteredAndSummary()
                }            }
        }
    }

    /**
     * Selects the first filter (in order: ACTUAL_MONTH, LAST_28_DAYS, ALL)
     * that yields at least one leg. Sets it as the current filter.
     */
    private fun applySmartDefault(legs: List<Leg>) {
        val candidates = listOf(
            FilterType.ACTUAL_MONTH,
            FilterType.LAST_28_DAYS,
            FilterType.ALL
        )
        val chosen = candidates.firstOrNull { filter ->
            filterLegs(legs, filter).isNotEmpty()
        } ?: FilterType.ALL

        // Set the filter without marking it as a user change
        _currentFilter.value = chosen
        updateFilteredAndSummary()
        // Mark that we've applied the default, so we won't run this again
        isUserFilterChanged = true
    }


//    fun insertLeg(leg: Leg) {
//        viewModelScope.launch { repository.insertLeg(leg) }
//    }
//
//    fun updateLeg(leg: Leg) {
//        viewModelScope.launch { repository.updateLeg(leg) }
//    }
//
//    fun deleteLeg(leg: Leg) {
//        viewModelScope.launch { repository.deleteLeg(leg) }
//    }
//
//    suspend fun getLegById(id: Long): Leg? = repository.getLegById(id)

    private fun updateFilteredAndSummary() {
        val all = _legs.value
        val filtered = filterLegs(
            all,
            _currentFilter.value,
            _customStartDate.value,
            _customEndDate.value
        )
        _filteredLegs.value = filtered
        _summary.value = computeSummary(filtered)
    }

    private fun filterLegs(
        legs: List<Leg>,
        filter: FilterType,
        customStart: Long? = null,
        customEnd: Long? = null
    ): List<Leg> {
        if (filter == FilterType.ALL) return legs

        val (start, end) = when (filter) {
            FilterType.CUSTOM -> customStart to customEnd
            else -> getDateRangeForFilter(filter)   // returns (Long, Long)
        }

        return legs.filter { leg ->
            val offBlock = leg.offBlock ?: return@filter false
            val dateMidnight = getDateAtMidnight(offBlock)
            val startOk = start == null || dateMidnight >= start
            val endOk = end == null || dateMidnight <= end   // safe now
            startOk && endOk
        }
    }

    /**
     * Computes the date range (start, end) for a non‑CUSTOM filter.
     * All times are in UTC milliseconds at midnight.
     */
    private fun getDateRangeForFilter(filter: FilterType): Pair<Long?, Long?> {
        val todayMidnight = getTodayMidnight()
        return when (filter) {
            FilterType.LAST_7_DAYS -> todayMidnight - 6 * 24 * 60 * 60 * 1000L to todayMidnight
            FilterType.LAST_28_DAYS -> todayMidnight - 27 * 24 * 60 * 60 * 1000L to todayMidnight
            FilterType.ACTUAL_MONTH -> {
                val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                    timeInMillis = System.currentTimeMillis()
                    set(Calendar.DAY_OF_MONTH, 1)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                cal.timeInMillis to todayMidnight
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
                val start = cal.timeInMillis
                val currentMonthFirst = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                    timeInMillis = System.currentTimeMillis()
                    set(Calendar.DAY_OF_MONTH, 1)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                start to (currentMonthFirst - 1)
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
                cal.timeInMillis to todayMidnight
            }
            else -> null to null // ALL, CUSTOM handled separately
        }
    }

    private fun computeSummary(legs: List<Leg>): Summary {
        var blockTotal = 0
        var blockNight = 0
        var simulator = 0
        var count = 0
        for (leg in legs) {
            blockTotal += leg.blockTimeTotal ?: 0
            blockNight += leg.blockTimeNight ?: 0
            simulator += leg.simulatorTime ?: 0
            count++
        }
        return Summary(blockTotal, blockNight, simulator, count)
    }

    fun setFilter(filter: FilterType) {
        isUserFilterChanged = true
        _currentFilter.value = filter
        updateFilteredAndSummary()
    }

    fun setCustomDateRange(start: Long?, end: Long?) {
        _customStartDate.value = start
        _customEndDate.value = end
        if (_currentFilter.value == FilterType.CUSTOM) {
            updateFilteredAndSummary()
        }
    }

}