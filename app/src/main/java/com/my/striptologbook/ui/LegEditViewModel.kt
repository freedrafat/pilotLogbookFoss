package com.my.striptologbook.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.my.striptologbook.data.AirportDataManager
import com.my.striptologbook.data.DefaultFields
import com.my.striptologbook.data.Leg
import com.my.striptologbook.data.LegEditState
import com.my.striptologbook.repository.LegRepository
import com.my.striptologbook.utils.MovingSunriseCalculator
import com.my.striptologbook.utils.extractTimeOnly
import com.my.striptologbook.utils.formatDuration
import com.my.striptologbook.utils.getDateAtMidnight
import com.my.striptologbook.utils.getTodayMidnight
import com.my.striptologbook.utils.toMinutesOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Date


class LegEditViewModel(
    private val repository: LegRepository,
    private val legId: Long?,
    private val airportManager: AirportDataManager,
    private val defaultFields: DefaultFields
) : ViewModel() {

    private val _state = MutableStateFlow(createInitialState())
    val state: StateFlow<LegEditState> = _state.asStateFlow()

    private fun createInitialState(): LegEditState {
        if (legId != null) {
            return LegEditState(legId = legId, isIfr = defaultFields.isIfr)
        }

        // New leg — existing base state
        var state = LegEditState(
            baseDate = getTodayMidnight(),
            isIfr = defaultFields.isIfr,
            position = defaultFields.position
        )

        // --- Additive string defaults (empty = no prefill; copy is harmless) ---
        state = state.copy(
            picName = defaultFields.picName,
            sicName = defaultFields.sicName,
            jumpseat1 = defaultFields.jumpseat1,
            jumpseat2 = defaultFields.jumpseat2,
            cabinCrew = defaultFields.cabinCrew,
            acRegistration = defaultFields.acRegistration,
            flightNumber = defaultFields.flightNumberPrefix,
            icaoDeparture = defaultFields.icaoDeparture,
            icaoDestination = defaultFields.icaoDestination,
            comment = defaultFields.comment
        )

        // --- Tri-state: isSimulator ---
        when (defaultFields.isSimulatorDefault) {
            "true" -> {
                state = state.copy(
                    isSimulator = true,
                    numberInstrumentApproaches = if (defaultFields.simuIfrApproach > 0)
                        defaultFields.simuIfrApproach.toString() else ""
                )
            }
            "false" -> {
                state = state.copy(isSimulator = false)
            }
            // "none": leave as-is
        }

        // --- Tri-state: pilotFlying ---
        when (defaultFields.pilotFlyingDefault) {
            "true" -> state = state.copy(pilotFlying = true)
            "false" -> state = state.copy(pilotFlying = false)
            // "none": leave as-is
        }

        // --- Off-Block = now (truncated to the minute) ---
        if (defaultFields.offBlockIsNow) {
            val nowMs = System.currentTimeMillis()
            val truncatedMs = nowMs - (nowMs % 60_000L)
            state = state.copy(
                baseDate = getDateAtMidnight(truncatedMs),
                offBlock = truncatedMs,
                offBlockTime = extractTimeOnly(truncatedMs)
            )
        }

        return state
    }

    init {
        if (legId != null) {
            loadLeg(legId)
        }
    }

    private fun loadLeg(id: Long) {
        viewModelScope.launch {
            val leg = repository.getLegById(id)
            if (leg != null) {
                _state.update { LegEditState.fromLeg(leg).copy(isIfr = defaultFields.isIfr) }
            }
        }
    }

    // ---------- Field Updates (no recompute) ----------

    fun updatePosition(value: String) {
        _state.update { it.copy(position = value) }
    }

    fun updatePicName(value: String) {
        _state.update { it.copy(picName = value) }
    }

    fun updateSicName(value: String) {
        _state.update { it.copy(sicName = value) }
    }

    fun updateJumpseat1(value: String) {
        _state.update { it.copy(jumpseat1 = value) }
    }

    fun updateJumpseat2(value: String) {
        _state.update { it.copy(jumpseat2 = value) }
    }

    fun updateCabinCrew(value: String) {
        _state.update { it.copy(cabinCrew = value) }
    }

    fun updateAcRegistration(value: String) {
        _state.update { it.copy(acRegistration = value) }
    }

    fun updateAcType(value: String) {
        _state.update { it.copy(acType = value) }
    }

    fun updateFlightNumber(value: String) {
        _state.update { it.copy(flightNumber = value) }
    }

    fun updateCategory(value: String) {
        _state.update { it.copy(category = value) }
    }

    fun updateIcaoDeparture(value: String) {
        _state.update { it.copy(icaoDeparture = value) }
    }

    fun updateIcaoDestination(value: String) {
        _state.update { it.copy(icaoDestination = value) }
    }

    fun updateOffBlockTime(value: String) {
        _state.update { it.copy(offBlockTime = value) }
    }

    fun updateTakeoffTimeStr(value: String) {
        _state.update { it.copy(takeoffTimeStr = value) }
    }

    fun updateLandingTimeStr(value: String) {
        _state.update { it.copy(landingTimeStr = value) }
    }

    fun updateOnBlockTime(value: String) {
        _state.update { it.copy(onBlockTime = value) }
    }

    fun updateSunriseTimeStr(value: String) {
        _state.update { it.copy(sunriseTimeStr = value) }
    }

    fun updateSunsetTimeStr(value: String) {
        _state.update { it.copy(sunsetTimeStr = value) }
    }

    fun updateSimulatorTime(value: String) {
        _state.update { it.copy(simulatorTime = value) }
    }

    fun updateBlockTimeNight(value: String) {
        _state.update { it.copy(blockTimeNight = value) }
    }

    fun updateBlockTimeTotal(value: String) {
        _state.update { it.copy(blockTimeTotal = value) }
    }

    fun updateFlightTimeNight(value: String) {
        _state.update { it.copy(flightTimeNight = value) }
    }

    fun updateFlightTimeTotal(value: String) {
        _state.update { it.copy(flightTimeTotal = value) }
    }

    fun updateIfrNight(value: String) {
        _state.update { it.copy(ifrNight = value) }
        recalcIfrDay()
    }

    fun updateIfrTotal(value: String) {
        _state.update { it.copy(ifrTotal = value) }
        recalcIfrDay()
        autoFillInstrumentApproaches()
    }

    fun updateNumberToDay(value: String) {
        _state.update { it.copy(numberToDay = value) }
    }

    fun updateNumberToNight(value: String) {
        _state.update { it.copy(numberToNight = value) }
    }

    fun updateNumberToTotal(value: String) {
        _state.update { it.copy(numberToTotal = value) }
    }

    fun updateNumberLdgDay(value: String) {
        _state.update { it.copy(numberLdgDay = value) }
    }

    fun updateNumberLdgNight(value: String) {
        _state.update { it.copy(numberLdgNight = value) }
    }

    fun updateNumberLdgTotal(value: String) {
        _state.update { it.copy(numberLdgTotal = value) }
    }

    fun updateIsSimulator(value: Boolean?) {
        _state.update { it.copy(isSimulator = value) }
    }

    fun updatePilotFlying(value: Boolean?) {
        _state.update { it.copy(pilotFlying = value) }
        autoFillInstrumentApproaches()
    }

    fun updateComment(value: String) {
        _state.update { it.copy(comment = value) }
    }

    // ---------- Date ----------
    fun setBaseDate(date: Long) {
        _state.update { it.copy(baseDate = date) }
        recalculateTimestamps()
    }

    fun updateSunriseTimestamp(ts: Long?) {
        _state.update { it.copy(sunrise = ts) }
    }

    fun updateSunsetTimestamp(ts: Long?) {
        _state.update { it.copy(sunset = ts) }
    }

    // --- offblock from timestamp (intent from stripToCalendar)
    fun setOffBlockFromTimestamp(timestamp: Long) {
        val midnight = getDateAtMidnight(timestamp)   // extract the date part
        val timeStr = extractTimeOnly(timestamp)      // extract the time part
        _state.update {
            it.copy(
                offBlock = timestamp,                 // store the exact timestamp
                baseDate = midnight,                  // for the date picker
                offBlockTime = timeStr                // for the time field
            )
        }
    }

    // ---------- Recompute (called on focus lost) ----------

    fun recalculateTimestamps() {
        val currentState = _state.value

        val base = currentState.baseDate ?: getTodayMidnight()

        fun timeStringToMinutes(str: String): Int? {
            if (str.isEmpty()) return null
            return str.toMinutesOrNull()
        }

        // OffBlock
        val offBlockMinutes = timeStringToMinutes(currentState.offBlockTime)
        val offBlockTimestamp = if (offBlockMinutes != null) base + (offBlockMinutes * 60 * 1000L) else null

        // Takeoff
        val takeoffMinutes = timeStringToMinutes(currentState.takeoffTimeStr)
        var takeoffTimestamp = if (takeoffMinutes != null && offBlockTimestamp != null) {
            base + (takeoffMinutes * 60 * 1000L)
        } else null
        if (takeoffTimestamp != null && offBlockTimestamp != null) {
            if (takeoffTimestamp <= offBlockTimestamp) {
                takeoffTimestamp += 24 * 60 * 60 * 1000L
            }
            if (takeoffTimestamp >= offBlockTimestamp + 24 * 60 * 60 * 1000L) {
                takeoffTimestamp -= 24 * 60 * 60 * 1000L
            }
        }

        // Landing
        val landingMinutes = timeStringToMinutes(currentState.landingTimeStr)
        var landingTimestamp = if (landingMinutes != null && takeoffTimestamp != null) {
            val takeoffDate = getDateAtMidnight(takeoffTimestamp)
            takeoffDate + (landingMinutes * 60 * 1000L)
        } else null
        if (landingTimestamp != null && takeoffTimestamp != null) {
            if (landingTimestamp <= takeoffTimestamp) {
                landingTimestamp += 24 * 60 * 60 * 1000L
            }
            if (landingTimestamp >= takeoffTimestamp + 24 * 60 * 60 * 1000L) {
                landingTimestamp -= 24 * 60 * 60 * 1000L
            }
        }

        // OnBlock
        val onBlockMinutes = timeStringToMinutes(currentState.onBlockTime)
        var onBlockTimestamp: Long? = null
        if (onBlockMinutes != null && offBlockTimestamp != null) {
            val refTimestamp = if (landingTimestamp != null && landingTimestamp > offBlockTimestamp) {
                landingTimestamp
            } else {
                offBlockTimestamp
            }
            var candidate = base + (onBlockMinutes * 60 * 1000L)
            while (candidate <= refTimestamp) {
                candidate += 24 * 60 * 60 * 1000L
            }
            while (candidate >= refTimestamp + 24 * 60 * 60 * 1000L) {
                candidate -= 24 * 60 * 60 * 1000L
            }
            onBlockTimestamp = candidate
        }

        // Sunrise and sunset (string to Long)
        val sunriseMinutes = timeStringToMinutes(currentState.sunriseTimeStr)
        val sunriseTimestamp = if (sunriseMinutes != null) base + (sunriseMinutes * 60 * 1000L) else null

        val sunsetMinutes = timeStringToMinutes(currentState.sunsetTimeStr)
        val sunsetTimestamp = if (sunsetMinutes != null) base + (sunsetMinutes * 60 * 1000L) else null

        _state.update {
            it.copy(
                offBlock = offBlockTimestamp,
                takeoffTime = takeoffTimestamp,
                landingTime = landingTimestamp,
                onBlock = onBlockTimestamp,
                sunrise = sunriseTimestamp,
                sunset = sunsetTimestamp
            )
        }

        // Note: The rest of the recalculations (block, flight, night) are not called here
        // because they are triggered elsewhere. This is kept as originally.
    }

    fun recalcBlockDuration() {
        val currentState = _state.value
        val offBlock = currentState.offBlock
        val onBlock = currentState.onBlock

        if (currentState.isSimulator == true) {
            recalcSimulator()
            return
        }

        val durationString = if (offBlock != null && onBlock != null) {
            val durationMinutes = ((onBlock - offBlock) / (60 * 1000)).toInt()
            if (durationMinutes > 0) formatDuration(durationMinutes) else ""
        } else {
            ""
        }

        _state.update {
            it.copy(
                blockTimeTotal = durationString,
                simulatorTime = ""
            )
        }

        computeBlockNightTotal()
    }


    /**
     * Clears all Night-button data, so the next recalculation falls back
     * to the manual sunrise/sunset path (or the "cannot compute" result
     * when both are empty).
     *
     * Called when the user edits or erases a manual sun-time field.
     */
    fun clearSunData() {
        this.flightEvents = emptyList()
        this.offBlockIsDay = null
        this.takeoffIsDay = null
        this.landingIsDay = null
        this.onBlockIsDay = null
        this.depSunrise = null
        this.depSunset = null
        this.arrSunrise = null
        this.arrSunset = null
    }

    private fun computeNightMinutes(
        events: List<MovingSunriseCalculator.SunEvent>,
        start: Long,
        end: Long,
        isDayAtStart: Boolean?
    ): Long? {
        if (end <= start) return null

        // No events and no status → cannot compute
        if (events.isEmpty() && isDayAtStart == null) return null

        // Sweep starting state: use provided status, else default to day
        var isDay = isDayAtStart ?: true
        var nightMinutes = 0L
        var currentTime = start

        val relevant = events.filter { it.timeUTC.time in start..end }
            .sortedBy { it.timeUTC.time }

        for (event in relevant) {
            val t = event.timeUTC.time
            if (t > currentTime) {
                if (!isDay) nightMinutes += (t - currentTime) / 60_000L
            }
            isDay = event.type == "Sunrise"
            currentTime = t
        }
        if (currentTime < end && !isDay) {
            nightMinutes += (end - currentTime) / 60_000L
        }
        return nightMinutes
    }

    private fun buildBlockEvents(
        offBlock: Long,
        takeoff: Long,
        landing: Long,
        onBlock: Long
    ): List<MovingSunriseCalculator.SunEvent> {
        val groundEvents = mutableListOf<MovingSunriseCalculator.SunEvent>()

        depSunrise?.takeIf { it in offBlock..takeoff }?.let {
            groundEvents.add(MovingSunriseCalculator.SunEvent(Date(it), 0.0, 0.0, "Sunrise"))
        }
        depSunset?.takeIf { it in offBlock..takeoff }?.let {
            groundEvents.add(MovingSunriseCalculator.SunEvent(Date(it), 0.0, 0.0, "Sunset"))
        }
        arrSunrise?.takeIf { it in landing..onBlock }?.let {
            groundEvents.add(MovingSunriseCalculator.SunEvent(Date(it), 0.0, 0.0, "Sunrise"))
        }
        arrSunset?.takeIf { it in landing..onBlock }?.let {
            groundEvents.add(MovingSunriseCalculator.SunEvent(Date(it), 0.0, 0.0, "Sunset"))
        }

        val all = groundEvents + flightEvents
        return all.sortedBy { it.timeUTC.time }
    }

    private fun computeBlockNightTotal() {
        val state = _state.value
        val hasManualSunrise = state.sunriseTimeStr.isNotEmpty()
        val hasManualSunset = state.sunsetTimeStr.isNotEmpty()

        if (hasManualSunrise || hasManualSunset) {
            // Use legacy (old) block night calculation
            when {
                hasManualSunrise && hasManualSunset -> computeBlockNightTotalTwoEntrys()
                hasManualSunrise xor hasManualSunset -> computeBlockNightTotalOneEntry()
                else -> { /* both empty – do nothing */ }
            }
        } else {
            // Use new sweep-based calculation (requires data from FlightSunActivity)
            val off = state.offBlock ?: return
            val on = state.onBlock ?: return
            val takeoff = state.takeoffTime ?: return
            val landing = state.landingTime ?: return

            val fullEvents = buildBlockEvents(off, takeoff, landing, on)
            val nightMinutes = computeNightMinutes(fullEvents, off, on, offBlockIsDay)
            val nightStr = when (nightMinutes) {
                null -> ""
                0L -> "0:00"
                else -> formatDuration(nightMinutes.toInt())
            }
            _state.update { it.copy(blockTimeNight = nightStr) }
            recalcBlockDay()
        }

    }

    private fun computeBlockNightTotalTwoEntrys() {
        val currentState = _state.value
        val off = currentState.offBlock ?: return
        val on = currentState.onBlock ?: return

        val sunriseStr = currentState.sunriseTimeStr
        val sunsetStr = currentState.sunsetTimeStr
        if (sunriseStr.isEmpty() || sunsetStr.isEmpty()) {
            return
        }

        val dayMillis = 24L * 60 * 60 * 1000
        val offDay = getDateAtMidnight(off)
        val onDay = getDateAtMidnight(on)

        var currentDay = offDay - dayMillis
        val endDay = onDay + dayMillis

        var totalNightMinutes = 0L

        while (currentDay <= endDay) {
            val interval = getNightIntervalForDate(currentDay)
            if (interval != null) {
                val (sunsetTS, nextSunrise) = interval
                val overlapStart = maxOf(sunsetTS, off)
                val overlapEnd = minOf(nextSunrise, on)
                if (overlapEnd > overlapStart) {
                    totalNightMinutes += (overlapEnd - overlapStart) / 60_000L
                }
            }
            currentDay += dayMillis
        }

        val nightDurationStr = formatDuration(totalNightMinutes.toInt())
        _state.update { it.copy(blockTimeNight = nightDurationStr) }

        recalcBlockDay()
    }

    private fun computeBlockNightTotalOneEntry() {
        val currentState = _state.value
        val off = currentState.offBlock ?: return
        val on = currentState.onBlock ?: return

        val sunriseStr = currentState.sunriseTimeStr
        val sunsetStr = currentState.sunsetTimeStr
        val hasSunrise = sunriseStr.isNotEmpty()
        val hasSunset = sunsetStr.isNotEmpty()

        if (hasSunrise == hasSunset) return

        var found = false

        if (hasSunrise) {
            val sunriseMin = sunriseStr.toMinutesOrNull() ?: return
            val candidate = findFirstCandidate(off, on, sunriseMin)
            if (candidate != null) {
                val nightMinutes = (candidate - off) / 60_000L
                val nightDurationStr = formatDuration(nightMinutes.toInt())
                _state.update { it.copy(blockTimeNight = nightDurationStr) }
                found = true
            }
        } else { // hasSunset
            val sunsetMin = sunsetStr.toMinutesOrNull() ?: return
            val candidate = findFirstCandidate(off, on, sunsetMin)
            if (candidate != null) {
                val nightMinutes = (on - candidate) / 60_000L
                val nightDurationStr = formatDuration(nightMinutes.toInt())
                _state.update { it.copy(blockTimeNight = nightDurationStr) }
                found = true
            }
        }

        if (!found) {
            _state.update { it.copy(blockTimeNight = "") }
        }

        recalcBlockDay()
    }

    fun recalcBlockDay() {
        val state = _state.value
        val totalStr = state.blockTimeTotal
        val nightStr = state.blockTimeNight

        if (totalStr.isEmpty()) {
            _state.update { it.copy(blockTimeDay = "") }
            return
        }

        if (nightStr.isEmpty()) {
            // Night cannot be computed → default day = total
            _state.update { it.copy(blockTimeDay = totalStr) }
            return
        }

        val totalMin = totalStr.toMinutesOrNull()
        val nightMin = nightStr.toMinutesOrNull()
        if (totalMin != null && nightMin != null && totalMin >= nightMin) {
            _state.update { it.copy(blockTimeDay = formatDuration(totalMin - nightMin)) }
        } else {
            _state.update { it.copy(blockTimeDay = "") }
        }
    }

    fun recalcFlightDuration() {
        val currentState = _state.value
        val takeoff = currentState.takeoffTime
        val landing = currentState.landingTime

        if (takeoff == null || landing == null) {
            _state.update { it.copy(flightTimeTotal = "") }
            return
        }

        if (currentState.isSimulator == true) {
            return
        }

        val durationMinutes = ((landing - takeoff) / (60 * 1000)).toInt()
        if (durationMinutes > 0) {
            val durationString = formatDuration(durationMinutes)
            _state.update { it.copy(flightTimeTotal = durationString) }
        } else {
            _state.update { it.copy(flightTimeTotal = "") }
        }

        computeFlightNightTotal()

        // Recalc TO/LDG counts if pilot is flying
        if (currentState.pilotFlying == true) {
            autoFillTakeoffLandingCounts()
        }

        // Only recalc IFR if isIfr is true
        if (currentState.isIfr) {
            recalcIfrFromFlight()
        }
    }

    private fun computeFlightNightTotal() {
        val state = _state.value
        val hasManualSunrise = state.sunriseTimeStr.isNotEmpty()
        val hasManualSunset = state.sunsetTimeStr.isNotEmpty()

        if (hasManualSunrise || hasManualSunset) {
            // Use legacy (old) flight night calculation
            when {
                hasManualSunrise && hasManualSunset -> computeFlightNightTotalTwoEntrys()
                hasManualSunrise xor hasManualSunset -> computeFlightNightTotalOneEntry()
                else -> { /* both empty – do nothing */ }
            }
        } else {
            // Use new sweep-based calculation (requires data from FlightSunActivity)
            val takeoff = state.takeoffTime ?: return
            val landing = state.landingTime ?: return

            val nightMinutes = computeNightMinutes(flightEvents, takeoff, landing, takeoffIsDay)
            val nightStr = when (nightMinutes) {
                null -> ""
                0L -> "0:00"
                else -> formatDuration(nightMinutes.toInt())
            }
            _state.update { it.copy(flightTimeNight = nightStr) }
            recalcFlightDay()
        }
    }

    private fun computeFlightNightTotalOneEntry() {
        val currentState = _state.value
        val takeoff = currentState.takeoffTime ?: return
        val landing = currentState.landingTime ?: return

        val sunriseStr = currentState.sunriseTimeStr
        val sunsetStr = currentState.sunsetTimeStr
        val hasSunrise = sunriseStr.isNotEmpty()
        val hasSunset = sunsetStr.isNotEmpty()

        if (hasSunrise == hasSunset) return

        var found = false

        if (hasSunrise) {
            val sunriseMin = sunriseStr.toMinutesOrNull() ?: return
            val candidate = findFirstCandidate(takeoff, landing, sunriseMin)
            if (candidate != null) {
                val nightMinutes = (candidate - takeoff) / 60_000L
                val nightDurationStr = formatDuration(nightMinutes.toInt())
                _state.update { it.copy(flightTimeNight = nightDurationStr) }
                found = true
            }
        } else { // hasSunset
            val sunsetMin = sunsetStr.toMinutesOrNull() ?: return
            val candidate = findFirstCandidate(takeoff, landing, sunsetMin)
            if (candidate != null) {
                val nightMinutes = (landing - candidate) / 60_000L
                val nightDurationStr = formatDuration(nightMinutes.toInt())
                _state.update { it.copy(flightTimeNight = nightDurationStr) }
                found = true
            }
        }

        if (!found) {
            _state.update { it.copy(flightTimeNight = "") }
        }

        recalcFlightDay()
    }

    private fun computeFlightNightTotalTwoEntrys() {
        val currentState = _state.value
        val takeoff = currentState.takeoffTime ?: return
        val landing = currentState.landingTime ?: return

        val sunriseStr = currentState.sunriseTimeStr
        val sunsetStr = currentState.sunsetTimeStr
        if (sunriseStr.isEmpty() || sunsetStr.isEmpty()) {
            return
        }

        val dayMillisec = 24L * 60 * 60 * 1000
        val takeoffDay = getDateAtMidnight(takeoff)
        val landingDay = getDateAtMidnight(landing)

        var currentDay = takeoffDay - dayMillisec
        val endDay = landingDay + dayMillisec

        var totalNightMinutes = 0L

        while (currentDay <= endDay) {
            val interval = getNightIntervalForDate(currentDay)
            if (interval != null) {
                val (sunsetTS, nextSunrise) = interval
                val overlapStart = maxOf(sunsetTS, takeoff)
                val overlapEnd = minOf(nextSunrise, landing)
                if (overlapEnd > overlapStart) {
                    totalNightMinutes += (overlapEnd - overlapStart) / 60_000L
                }
            }
            currentDay += dayMillisec
        }

        val nightDurationStr = formatDuration(totalNightMinutes.toInt())
        _state.update { it.copy(flightTimeNight = nightDurationStr) }

        recalcFlightDay()
    }

    fun recalcFlightDay() {
        val state = _state.value
        val totalStr = state.flightTimeTotal
        val nightStr = state.flightTimeNight

        if (totalStr.isEmpty()) {
            _state.update { it.copy(flightTimeDay = "") }
            return
        }

        if (nightStr.isEmpty()) {
            // Night cannot be computed → default day = total
            _state.update { it.copy(flightTimeDay = totalStr) }
            return
        }

        val totalMin = totalStr.toMinutesOrNull()
        val nightMin = nightStr.toMinutesOrNull()
        if (totalMin != null && nightMin != null && totalMin >= nightMin) {
            _state.update { it.copy(flightTimeDay = formatDuration(totalMin - nightMin)) }
        } else {
            _state.update { it.copy(flightTimeDay = "") }
        }
    }

    fun recalcIfrDay() {
        val state = _state.value
        val totalStr = state.ifrTotal
        val nightStr = state.ifrNight
        if (totalStr.isNotEmpty() && nightStr.isNotEmpty()) {
            val totalMin = totalStr.toMinutesOrNull()
            val nightMin = nightStr.toMinutesOrNull()
            if (totalMin != null && nightMin != null && totalMin >= nightMin) {
                val dayMin = totalMin - nightMin
                val dayStr = formatDuration(dayMin)
                _state.update { it.copy(ifrDay = dayStr) }
                return
            }
        }
        _state.update { it.copy(ifrDay = "") }
    }

    fun autoFillInstrumentApproaches() {
        val state = _state.value
        val ifrTotalMin = state.ifrTotal.toMinutesOrNull()
        val pilotFlying = state.pilotFlying ?: false

        if (ifrTotalMin != null && ifrTotalMin > 0 && pilotFlying) {
            // Only fill when currently empty (preserve manual values)
            if (state.numberInstrumentApproaches.isEmpty()) {
                _state.update { it.copy(numberInstrumentApproaches = "1") }
            }
        } else {
            // Clear unconditionally
            _state.update { it.copy(numberInstrumentApproaches = "") }
        }
    }

    private fun recalcSimulator() {
        val currentState = _state.value
        val offBlock = currentState.offBlock
        val onBlock = currentState.onBlock

        val durationString = if (offBlock != null && onBlock != null) {
            val durationMinutes = ((onBlock - offBlock) / (60 * 1000)).toInt()
            if (durationMinutes > 0) formatDuration(durationMinutes) else ""
        } else {
            ""
        }

        val simuApproach = if (defaultFields.simuIfrApproach > 0) {
            defaultFields.simuIfrApproach.toString()
        } else {
            ""
        }

        _state.update {
            it.copy(
                simulatorTime = durationString,
                blockTimeTotal = "",
                blockTimeNight = "",
                blockTimeDay = "",
                flightTimeTotal = "",
                flightTimeNight = "",
                flightTimeDay = "",
                ifrTotal = "",
                ifrNight = "",
                ifrDay = "",
                numberInstrumentApproaches = simuApproach,
                numberToTotal = "",
                numberToDay = "",
                numberToNight = "",
                numberLdgTotal = "",
                numberLdgDay = "",
                numberLdgNight = ""
            )
        }
    }

    private fun getNightIntervalForDate(dateMidnight: Long): Pair<Long, Long>? {
        val currentState = _state.value
        val sunrise = currentState.sunrise
        val sunset = currentState.sunset
        if (sunrise == null || sunset == null) return null

        val dayMillis = 24L * 60 * 60 * 1000
        val sunriseTimeOfDay = sunrise % dayMillis
        val sunsetTimeOfDay = sunset % dayMillis

        val sunsetOnDate = dateMidnight + sunsetTimeOfDay
        val nextSunriseOnDate = if (sunriseTimeOfDay > sunsetTimeOfDay) {
            dateMidnight + sunriseTimeOfDay
        } else {
            dateMidnight + dayMillis + sunriseTimeOfDay
        }
        return sunsetOnDate to nextSunriseOnDate
    }

    private fun findFirstCandidate(start: Long, end: Long, timeOfDayMinutes: Int): Long? {
        val startDay = getDateAtMidnight(start)
        var candidate = startDay + timeOfDayMinutes * 60_000L
        while (candidate <= end) {
            if (candidate >= start) return candidate
            candidate += 24L * 60 * 60 * 1000
        }
        return null
    }

    private fun findFirstCandidateForFlight(): Long? {
        val currentState = _state.value
        val takeoff = currentState.takeoffTime ?: return null
        val landing = currentState.landingTime ?: return null

        val sunriseStr = currentState.sunriseTimeStr
        val sunsetStr = currentState.sunsetTimeStr
        val hasSunrise = sunriseStr.isNotEmpty()
        val hasSunset = sunsetStr.isNotEmpty()

        if (hasSunrise == hasSunset) return null

        return if (hasSunrise) {
            val sunriseMin = sunriseStr.toMinutesOrNull() ?: return null
            findFirstCandidate(takeoff, landing, sunriseMin)
        } else {
            val sunsetMin = sunsetStr.toMinutesOrNull() ?: return null
            findFirstCandidate(takeoff, landing, sunsetMin)
        }
    }

    private fun isEventAtNight(event: Long): Boolean {
        val dayMillis = 24L * 60 * 60 * 1000
        val eventDay = getDateAtMidnight(event)

        var currentDay = eventDay - dayMillis
        val endDay = eventDay + dayMillis

        while (currentDay <= endDay) {
            val interval = getNightIntervalForDate(currentDay)
            if (interval != null) {
                val (sunset, nextSunrise) = interval
                if (event in sunset..<nextSunrise) {
                    return true
                }
            }
            currentDay += dayMillis
        }

        return false
    }

    fun autoFillTakeoffLandingCounts() {
        val currentState = _state.value

        // --- Pre‑checks ---
        if (currentState.pilotFlying != true) {
            _state.update {
                it.copy(
                    numberToTotal = "",
                    numberToDay = "",
                    numberToNight = "",
                    numberLdgTotal = "",
                    numberLdgDay = "",
                    numberLdgNight = ""
                )
            }
            return
        }

        val takeoff = currentState.takeoffTime
        val landing = currentState.landingTime
        if (takeoff == null || landing == null) {
            _state.update {
                it.copy(
                    numberToTotal = "",
                    numberToDay = "",
                    numberToNight = "",
                    numberLdgTotal = "",
                    numberLdgDay = "",
                    numberLdgNight = ""
                )
            }
            return
        }

        // --- NEW: Use computed statuses from FlightSunActivity (if available) ---
        val toDay = takeoffIsDay
        val ldgDay = landingIsDay
        if (toDay != null && ldgDay != null) {
            _state.update {
                it.copy(
                    numberToDay = if (toDay) "1" else "",
                    numberToNight = if (!toDay) "1" else "",
                    numberLdgDay = if (ldgDay) "1" else "",
                    numberLdgNight = if (!ldgDay) "1" else ""
                )
            }
            recalcToTotal()
            recalcLdgTotal()
            return
        }

        // --- Legacy fallback (existing logic) ---
        // If both sun times are null → use airport fallback
        if (currentState.sunrise == null && currentState.sunset == null) {
            attemptAirportFallback(takeoff, landing)
            recalcToTotal()
            recalcLdgTotal()
            return
        }

        // Both sun times exist
        val sunrise = currentState.sunrise
        val sunset = currentState.sunset
        val hasBoth = sunrise != null && sunset != null

        if (hasBoth) {
            val toNight = isEventAtNight(takeoff)
            val ldgNight = isEventAtNight(landing)

            _state.update {
                it.copy(
                    numberToDay = if (toNight) "" else "1",
                    numberToNight = if (toNight) "1" else "",
                    numberLdgDay = if (ldgNight) "" else "1",
                    numberLdgNight = if (ldgNight) "1" else ""
                )
            }
            recalcToTotal()
            recalcLdgTotal()
            return
        }

        // Only one sun time exists – try to find crossing
        val candidate = findFirstCandidateForFlight()

        if (candidate == null) {
            // No crossing found → use airport fallback
            attemptAirportFallback(takeoff, landing)
            recalcToTotal()
            recalcLdgTotal()
            return
        }

        // Crossing found
        val hasSunrise = currentState.sunriseTimeStr.isNotEmpty()
        val hasSunset = currentState.sunsetTimeStr.isNotEmpty()

        if (hasSunrise) {
            _state.update {
                it.copy(
                    numberToDay = "",
                    numberToNight = "1",
                    numberLdgDay = "1",
                    numberLdgNight = ""
                )
            }
        } else if (hasSunset) {
            _state.update {
                it.copy(
                    numberToDay = "1",
                    numberToNight = "",
                    numberLdgDay = "",
                    numberLdgNight = "1"
                )
            }
        } else {
            _state.update {
                it.copy(
                    numberToDay = "",
                    numberToNight = "",
                    numberLdgDay = "",
                    numberLdgNight = ""
                )
            }
        }
        recalcToTotal()
        recalcLdgTotal()
    }

    private fun attemptAirportFallback(takeoff: Long, landing: Long) {
        val currentState = _state.value
        val depIATA = currentState.icaoDeparture
        val arrIATA = currentState.icaoDestination
        if (depIATA.isEmpty() || arrIATA.isEmpty()) {
            // Cannot determine – keep existing values
            return
        }

        val depDay = computeDayNightAtAirport(depIATA, takeoff)
        val arrDay = computeDayNightAtAirport(arrIATA, landing)
        if (depDay == null || arrDay == null) {
            // Cannot determine – keep existing values
            return
        }

        // Update day/night fields only (totals will be recalculated later)
        _state.update {
            it.copy(
                numberToDay = if (depDay) "1" else "",
                numberToNight = if (!depDay) "1" else "",
                numberLdgDay = if (arrDay) "1" else "",
                numberLdgNight = if (!arrDay) "1" else ""
            )
        }
    }

    private fun computeDayNightAtAirport(icao: String, timestamp: Long): Boolean? {
        val airport = airportManager.findAirportByCode(icao) ?: return null
        val elevationMeters = airport.elevation * 0.3048
        val horizonElevation = -0.833 - 0.0347 * kotlin.math.sqrt(elevationMeters)
        val solarElevation = MovingSunriseCalculator.computeSolarElevation(
            airport.latitude, airport.longitude, timestamp
        )
        return solarElevation > horizonElevation
    }

    fun recalcToTotal() {
        val currentState = _state.value
        val dayStr = currentState.numberToDay
        val nightStr = currentState.numberToNight

        val day = dayStr.toIntOrNull() ?: 0
        val night = nightStr.toIntOrNull() ?: 0

        val total = day + night
        _state.update { it.copy(numberToTotal = total.toString()) }
    }

    fun recalcLdgTotal() {
        val currentState = _state.value
        val dayStr = currentState.numberLdgDay
        val nightStr = currentState.numberLdgNight

        val day = dayStr.toIntOrNull() ?: 0
        val night = nightStr.toIntOrNull() ?: 0

        val total = day + night
        _state.update { it.copy(numberLdgTotal = total.toString()) }
    }

    // ---------- IFR ----------
    fun updateIsIfr(value: Boolean) {
        _state.update { it.copy(isIfr = value) }
        if (value) {
            recalcIfrFromFlight()
        } else {
            _state.update {
                it.copy(
                    ifrTotal = "",
                    ifrNight = "",
                    ifrDay = ""
                )
            }
        }
    }

    fun recalcIfrFromFlight() {
        val currentState = _state.value
        if (currentState.isIfr) {
            _state.update {
                it.copy(
                    ifrTotal = currentState.flightTimeTotal,
                    ifrNight = currentState.flightTimeNight,
                    ifrDay = currentState.flightTimeDay
                )
            }
            autoFillInstrumentApproaches()
        }
    }

    fun updateNumberInstrumentApproaches(value: String) {
        _state.update { it.copy(numberInstrumentApproaches = value) }
    }

    // ---------- Save ----------
    fun saveLeg(): Leg {
        val currentState = _state.value

        val leg = Leg(
            id = currentState.legId ?: 0,
            position = currentState.position.ifEmpty { null },
            picName = currentState.picName.ifEmpty { null },
            sicName = currentState.sicName.ifEmpty { null },
            jumpseat1 = currentState.jumpseat1.ifEmpty { null },
            jumpseat2 = currentState.jumpseat2.ifEmpty { null },
            cabinCrew = currentState.cabinCrew.ifEmpty { null },
            acRegistration = currentState.acRegistration.ifEmpty { null },
            acType = currentState.acType.ifEmpty { null },
            flightNumber = currentState.flightNumber.ifEmpty { null },
            category = currentState.category.ifEmpty { null },
            isSimulator = currentState.isSimulator,
            icaoDeparture = currentState.icaoDeparture.ifEmpty { null },
            icaoDestination = currentState.icaoDestination.ifEmpty { null },
            offBlock = currentState.offBlock,
            onBlock = currentState.onBlock,
            takeoffTime = currentState.takeoffTime,
            landingTime = currentState.landingTime,
            sunrise = currentState.sunrise,
            sunset = currentState.sunset,
            blockTimeDay = currentState.blockTimeDay.toMinutesOrNull(),
            blockTimeNight = currentState.blockTimeNight.toMinutesOrNull(),
            blockTimeTotal = currentState.blockTimeTotal.toMinutesOrNull(),
            flightTimeDay = currentState.flightTimeDay.toMinutesOrNull(),
            flightTimeNight = currentState.flightTimeNight.toMinutesOrNull(),
            flightTimeTotal = currentState.flightTimeTotal.toMinutesOrNull(),
            simulatorTime = currentState.simulatorTime.toMinutesOrNull(),
            ifrDay = currentState.ifrDay.toMinutesOrNull(),
            ifrNight = currentState.ifrNight.toMinutesOrNull(),
            ifrTotal = currentState.ifrTotal.toMinutesOrNull(),
            numberInstrumentApproaches = currentState.numberInstrumentApproaches.toIntOrNull(),
            numberToDay = currentState.numberToDay.toIntOrNull(),
            numberToNight = currentState.numberToNight.toIntOrNull(),
            numberToTotal = currentState.numberToTotal.toIntOrNull(),
            numberLdgDay = currentState.numberLdgDay.toIntOrNull(),
            numberLdgNight = currentState.numberLdgNight.toIntOrNull(),
            numberLdgTotal = currentState.numberLdgTotal.toIntOrNull(),
            pilotFlying = currentState.pilotFlying,
            comment = currentState.comment.ifEmpty { null }
        )

        viewModelScope.launch {
            if (currentState.legId != null) {
                repository.updateLeg(leg)
            } else {
                repository.insertLeg(leg.copy(id = 0))
            }
        }

        return leg
    }

    fun deleteLeg() {
        val currentState = _state.value
        val legId = currentState.legId ?: return

        viewModelScope.launch {
            val leg = repository.getLegById(legId)
            leg?.let { repository.deleteLeg(it) }
        }
    }

    fun isEditing(): Boolean = legId != null

    fun reloadLeg() {
        val id = _state.value.legId
        if (id != null) {
            loadLeg(id)
        }
    }

    // ---------- Autosuggest ----------
    suspend fun searchPosition(query: String): List<String> = repository.searchPosition(query)
    suspend fun searchPicName(query: String): List<String> = repository.searchPicName(query)
    suspend fun searchSicName(query: String): List<String> = repository.searchSicName(query)
    suspend fun searchJumpseat1(query: String): List<String> = repository.searchJumpseat1(query)
    suspend fun searchJumpseat2(query: String): List<String> = repository.searchJumpseat2(query)
    suspend fun searchCabinCrew(query: String): List<String> = repository.searchCabinCrew(query)
    suspend fun searchAcRegistration(query: String): List<String> = repository.searchAcRegistration(query)
    suspend fun searchAcType(query: String): List<String> = repository.searchAcType(query)
    suspend fun searchFlightNumber(query: String): List<String> = repository.searchFlightNumber(query)
    suspend fun searchCategory(query: String): List<String> = repository.searchCategory(query)
    suspend fun searchIcaoDeparture(query: String): List<String> = repository.searchIcaoDeparture(query)
    suspend fun searchIcaoDestination(query: String): List<String> = repository.searchIcaoDestination(query)

    suspend fun getMostRecentTypeByRegistration(registration: String): String? {
        return repository.getMostRecentTypeByRegistration(registration)
    }

    suspend fun getMostRecentCategoryByAcType(acType: String): String? {
        return repository.getMostRecentCategoryByAcType(acType)
    }


    private var depSunrise: Long? = null
    private var depSunset: Long? = null
    private var arrSunrise: Long? = null
    private var arrSunset: Long? = null

    private var flightEvents: List<MovingSunriseCalculator.SunEvent> = emptyList()
    private var offBlockIsDay: Boolean? = null
    private var takeoffIsDay: Boolean? = null
    private var landingIsDay: Boolean? = null
    private var onBlockIsDay: Boolean? = null

    fun setSunData(
        events: List<MovingSunriseCalculator.SunEvent>,
        offBlockIsDay: Boolean?,
        takeoffIsDay: Boolean?,
        landingIsDay: Boolean?,
        onBlockIsDay: Boolean?,
        depSunrise: Long?,
        depSunset: Long?,
        arrSunrise: Long?,
        arrSunset: Long?
    ) {
        this.flightEvents = events
        this.offBlockIsDay = offBlockIsDay
        this.takeoffIsDay = takeoffIsDay
        this.landingIsDay = landingIsDay
        this.onBlockIsDay = onBlockIsDay
        this.depSunrise = depSunrise
        this.depSunset = depSunset
        this.arrSunrise = arrSunrise
        this.arrSunset = arrSunset
    }


}