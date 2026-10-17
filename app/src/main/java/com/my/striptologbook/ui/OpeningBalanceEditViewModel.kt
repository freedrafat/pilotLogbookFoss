package com.my.striptologbook.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.my.striptologbook.data.LegTotals
import com.my.striptologbook.data.OpeningBalance
import com.my.striptologbook.repository.LegRepository
import com.my.striptologbook.utils.formatDuration
import com.my.striptologbook.utils.toMinutesOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OpeningBalanceEditState(
    val id: Long? = null,
    val startDate: Long? = null,
    val endDate: Long? = null,
    val acType: String = "",
    val position: String = "",
    val category: String = "",
    val comment: String = "",
    val blockTimeTotal: String = "",
    val blockTimeNight: String = "",
    val blockTimeDay: String = "",
    val flightTimeTotal: String = "",
    val flightTimeNight: String = "",
    val flightTimeDay: String = "",
    val ifrTotal: String = "",
    val ifrNight: String = "",
    val ifrDay: String = "",
    val simulatorTime: String = "",
    val numberToDay: String = "",
    val numberToNight: String = "",
    val numberToTotal: String = "",
    val numberLdgDay: String = "",
    val numberLdgNight: String = "",
    val numberLdgTotal: String = "",
    val numberInstrumentApproaches: String = "",
    val singlePilotSeTime: String = "",
    val singlePilotMeTime: String = "",
    val multiPilotTime: String = "",
    val pilotFunctionPicTime: String = "",
    val pilotFunctionSicTime: String = "",
    val pilotFunctionDualTime: String = "",
    val pilotFunctionInstructorTime: String = ""
)

class OpeningBalanceEditViewModel(
    private val repository: LegRepository,
    private val balanceId: Long?,
    private val mergedMode: Boolean = false
) : ViewModel() {

    private val _state = MutableStateFlow(
        OpeningBalanceEditState(id = if (mergedMode) null else balanceId)
    )
    val state: StateFlow<OpeningBalanceEditState> = _state.asStateFlow()

    init {
        if (mergedMode) {
            viewModelScope.launch {
                val totals = repository.getOpeningBalanceTotals()
                val rows = repository.getAllOpeningBalancesList()
                _state.value = toEditStateFromMerged(totals, rows)
            }
        } else if (balanceId != null) {
            viewModelScope.launch {
                val row = repository.getOpeningBalanceById(balanceId)
                if (row != null) _state.value = toEditState(row)
            }
        }
    }

    fun reloadRow() {
        if (mergedMode) return
        val id = balanceId ?: return
        viewModelScope.launch {
            val row = repository.getOpeningBalanceById(id)
            if (row != null) _state.value = toEditState(row)
        }
    }

    // ---------------- Dates ----------------

    fun setStartDate(millis: Long) {
        _state.update { it.copy(startDate = millis) }
    }

    fun setEndDate(millis: Long) {
        _state.update { it.copy(endDate = millis) }
    }

    // ---------------- Text fields ----------------

    fun updateAcType(value: String) { _state.update { it.copy(acType = value) } }

    fun updatePosition(value: String) {
        _state.update { it.copy(position = value) }
        recalcPilotFunctions()
    }

    fun updateCategory(value: String) {
        _state.update { it.copy(category = value) }
        recalcSinglePilot()
    }

    fun updateComment(value: String) { _state.update { it.copy(comment = value) } }

    // ---------------- Block group ----------------

    fun updateBlockTotal(value: String) {
        val s = _state.value
        if (value.isEmpty()) {
            _state.update { it.copy(blockTimeTotal = "", blockTimeDay = "") }
            recalcAll7(); return
        }
        val (t, n, d) = resolveGroup(value, s.blockTimeNight, s.blockTimeDay, EnteredField.TOTAL)
        _state.update { it.copy(blockTimeTotal = t, blockTimeNight = n, blockTimeDay = d) }
        recalcAll7()
    }

    fun updateBlockNight(value: String) {
        val s = _state.value
        if (value.isEmpty()) {
            _state.update { it.copy(blockTimeNight = "", blockTimeTotal = s.blockTimeDay) }
            recalcAll7(); return
        }
        val (t, n, d) = resolveGroup(s.blockTimeTotal, value, s.blockTimeDay, EnteredField.NIGHT)
        _state.update { it.copy(blockTimeTotal = t, blockTimeNight = n, blockTimeDay = d) }
        recalcAll7()
    }

    fun updateBlockDay(value: String) {
        val s = _state.value
        if (value.isEmpty()) {
            _state.update { it.copy(blockTimeDay = "", blockTimeTotal = s.blockTimeNight) }
            recalcAll7(); return
        }
        val (t, n, d) = resolveGroup(s.blockTimeTotal, s.blockTimeNight, value, EnteredField.DAY)
        _state.update { it.copy(blockTimeTotal = t, blockTimeNight = n, blockTimeDay = d) }
        recalcAll7()
    }

    // ---------------- Flight group ----------------

    fun updateFlightTotal(value: String) {
        val s = _state.value
        if (value.isEmpty()) {
            _state.update { it.copy(flightTimeTotal = "", flightTimeDay = "") }; return
        }
        val (t, n, d) = resolveGroup(value, s.flightTimeNight, s.flightTimeDay, EnteredField.TOTAL)
        _state.update { it.copy(flightTimeTotal = t, flightTimeNight = n, flightTimeDay = d) }
    }

    fun updateFlightNight(value: String) {
        val s = _state.value
        if (value.isEmpty()) {
            _state.update { it.copy(flightTimeNight = "", flightTimeTotal = s.flightTimeDay) }; return
        }
        val (t, n, d) = resolveGroup(s.flightTimeTotal, value, s.flightTimeDay, EnteredField.NIGHT)
        _state.update { it.copy(flightTimeTotal = t, flightTimeNight = n, flightTimeDay = d) }
    }

    fun updateFlightDay(value: String) {
        val s = _state.value
        if (value.isEmpty()) {
            _state.update { it.copy(flightTimeDay = "", flightTimeTotal = s.flightTimeNight) }; return
        }
        val (t, n, d) = resolveGroup(s.flightTimeTotal, s.flightTimeNight, value, EnteredField.DAY)
        _state.update { it.copy(flightTimeTotal = t, flightTimeNight = n, flightTimeDay = d) }
    }

    // ---------------- IFR group ----------------

    fun updateIfrTotal(value: String) {
        val s = _state.value
        if (value.isEmpty()) {
            _state.update { it.copy(ifrTotal = "", ifrDay = "") }; return
        }
        val (t, n, d) = resolveGroup(value, s.ifrNight, s.ifrDay, EnteredField.TOTAL)
        _state.update { it.copy(ifrTotal = t, ifrNight = n, ifrDay = d) }
    }

    fun updateIfrNight(value: String) {
        val s = _state.value
        if (value.isEmpty()) {
            _state.update { it.copy(ifrNight = "", ifrTotal = s.ifrDay) }; return
        }
        val (t, n, d) = resolveGroup(s.ifrTotal, value, s.ifrDay, EnteredField.NIGHT)
        _state.update { it.copy(ifrTotal = t, ifrNight = n, ifrDay = d) }
    }

    fun updateIfrDay(value: String) {
        val s = _state.value
        if (value.isEmpty()) {
            _state.update { it.copy(ifrDay = "", ifrTotal = s.ifrNight) }; return
        }
        val (t, n, d) = resolveGroup(s.ifrTotal, s.ifrNight, value, EnteredField.DAY)
        _state.update { it.copy(ifrTotal = t, ifrNight = n, ifrDay = d) }
    }

    // ---------------- Simulator & Approaches ----------------

    fun updateSimulatorTime(value: String) { _state.update { it.copy(simulatorTime = value) } }
    fun updateNumberInstrumentApproaches(value: String) { _state.update { it.copy(numberInstrumentApproaches = value) } }

    // ---------------- Takeoff group ----------------

    fun updateToDay(value: String) {
        val s = _state.value
        if (value.isEmpty()) { _state.update { it.copy(numberToDay = "", numberToTotal = s.numberToNight) }; return }
        val (d, n, t) = resolveGroupCounters(value, s.numberToNight, s.numberToTotal, EnteredField.DAY)
        _state.update { it.copy(numberToDay = d, numberToNight = n, numberToTotal = t) }
    }

    fun updateToNight(value: String) {
        val s = _state.value
        if (value.isEmpty()) { _state.update { it.copy(numberToNight = "", numberToTotal = s.numberToDay) }; return }
        val (d, n, t) = resolveGroupCounters(s.numberToDay, value, s.numberToTotal, EnteredField.NIGHT)
        _state.update { it.copy(numberToDay = d, numberToNight = n, numberToTotal = t) }
    }

    fun updateToTotal(value: String) {
        val s = _state.value
        if (value.isEmpty()) { _state.update { it.copy(numberToTotal = "", numberToDay = "") }; return }
        val (d, n, t) = resolveGroupCounters(s.numberToDay, s.numberToNight, value, EnteredField.TOTAL)
        _state.update { it.copy(numberToDay = d, numberToNight = n, numberToTotal = t) }
    }

    // ---------------- Landing group ----------------

    fun updateLdgDay(value: String) {
        val s = _state.value
        if (value.isEmpty()) { _state.update { it.copy(numberLdgDay = "", numberLdgTotal = s.numberLdgNight) }; return }
        val (d, n, t) = resolveGroupCounters(value, s.numberLdgNight, s.numberLdgTotal, EnteredField.DAY)
        _state.update { it.copy(numberLdgDay = d, numberLdgNight = n, numberLdgTotal = t) }
    }

    fun updateLdgNight(value: String) {
        val s = _state.value
        if (value.isEmpty()) { _state.update { it.copy(numberLdgNight = "", numberLdgTotal = s.numberLdgDay) }; return }
        val (d, n, t) = resolveGroupCounters(s.numberLdgDay, value, s.numberLdgTotal, EnteredField.NIGHT)
        _state.update { it.copy(numberLdgDay = d, numberLdgNight = n, numberLdgTotal = t) }
    }

    fun updateLdgTotal(value: String) {
        val s = _state.value
        if (value.isEmpty()) { _state.update { it.copy(numberLdgTotal = "", numberLdgDay = "") }; return }
        val (d, n, t) = resolveGroupCounters(s.numberLdgDay, s.numberLdgNight, value, EnteredField.TOTAL)
        _state.update { it.copy(numberLdgDay = d, numberLdgNight = n, numberLdgTotal = t) }
    }

    // ---------------- 7 direct fields ----------------

    fun updateSinglePilotSe(v: String)      { _state.update { it.copy(singlePilotSeTime = v) } }
    fun updateSinglePilotMe(v: String)      { _state.update { it.copy(singlePilotMeTime = v) } }
    fun updateMultiPilot(v: String)         { _state.update { it.copy(multiPilotTime = v) } }
    fun updatePilotFunctionPic(v: String)   { _state.update { it.copy(pilotFunctionPicTime = v) } }
    fun updatePilotFunctionSic(v: String)   { _state.update { it.copy(pilotFunctionSicTime = v) } }
    fun updatePilotFunctionDual(v: String)  { _state.update { it.copy(pilotFunctionDualTime = v) } }
    fun updatePilotFunctionInstructor(v: String) { _state.update { it.copy(pilotFunctionInstructorTime = v) } }

    // ---------------- Save / Delete ----------------

    fun saveRow(onDone: () -> Unit) {
        if (mergedMode) { onDone(); return }
        val s = _state.value
        val row = OpeningBalance(
            id = s.id ?: 0,
            startDate = s.startDate,
            endDate = s.endDate,
            acType = s.acType.ifEmpty { null },
            position = s.position.ifEmpty { null },
            category = s.category.ifEmpty { null },
            comment = s.comment.ifEmpty { null },
            blockTimeTotal = s.blockTimeTotal.toMinutesOrNull(),
            blockTimeNight = s.blockTimeNight.toMinutesOrNull(),
            blockTimeDay = s.blockTimeDay.toMinutesOrNull(),
            flightTimeTotal = s.flightTimeTotal.toMinutesOrNull(),
            flightTimeNight = s.flightTimeNight.toMinutesOrNull(),
            flightTimeDay = s.flightTimeDay.toMinutesOrNull(),
            ifrTotal = s.ifrTotal.toMinutesOrNull(),
            ifrNight = s.ifrNight.toMinutesOrNull(),
            ifrDay = s.ifrDay.toMinutesOrNull(),
            simulatorTime = s.simulatorTime.toMinutesOrNull(),
            numberToDay = s.numberToDay.toIntOrNull(),
            numberToNight = s.numberToNight.toIntOrNull(),
            numberToTotal = s.numberToTotal.toIntOrNull(),
            numberLdgDay = s.numberLdgDay.toIntOrNull(),
            numberLdgNight = s.numberLdgNight.toIntOrNull(),
            numberLdgTotal = s.numberLdgTotal.toIntOrNull(),
            numberInstrumentApproaches = s.numberInstrumentApproaches.toIntOrNull(),
            singlePilotSeTime = s.singlePilotSeTime.toMinutesOrNull(),
            singlePilotMeTime = s.singlePilotMeTime.toMinutesOrNull(),
            multiPilotTime = s.multiPilotTime.toMinutesOrNull(),
            pilotFunctionPicTime = s.pilotFunctionPicTime.toMinutesOrNull(),
            pilotFunctionSicTime = s.pilotFunctionSicTime.toMinutesOrNull(),
            pilotFunctionDualTime = s.pilotFunctionDualTime.toMinutesOrNull(),
            pilotFunctionInstructorTime = s.pilotFunctionInstructorTime.toMinutesOrNull()
        )
        viewModelScope.launch {
            if (s.id != null) repository.updateOpeningBalance(row)
            else repository.insertOpeningBalance(row)
            onDone()
        }
    }

    fun deleteRow(onDone: () -> Unit) {
        if (mergedMode) { onDone(); return }
        val id = _state.value.id ?: return
        viewModelScope.launch {
            val row = repository.getOpeningBalanceById(id)
            row?.let { repository.deleteOpeningBalance(it) }
            onDone()
        }
    }

    // ---------------- Helpers ----------------

    private enum class EnteredField { TOTAL, NIGHT, DAY }

    private fun resolveGroup(
        total: String, night: String, day: String, entered: EnteredField
    ): Triple<String, String, String> {
        val t = total.toMinutesOrNull()
        val n = night.toMinutesOrNull()
        val d = day.toMinutesOrNull()
        val count = listOf(t, n, d).count { it != null }

        if (count <= 1) return Triple(total, night, day)

        if (count == 2) {
            return when {
                t != null && n != null -> Triple(total, night, if (t >= n) formatDuration(t - n) else "")
                t != null && d != null -> Triple(total, if (t >= d) formatDuration(t - d) else "", day)
                n != null && d != null -> Triple(formatDuration(n + d), night, day)
                else -> Triple(total, night, day)
            }
        }

        return when (entered) {
            EnteredField.TOTAL, EnteredField.NIGHT ->
                Triple(total, night, if (t != null && n != null && t >= n) formatDuration(t - n) else "")
            EnteredField.DAY ->
                Triple(if (n != null && d != null) formatDuration(n + d) else "", night, day)
        }
    }

    private fun resolveGroupCounters(
        day: String, night: String, total: String, entered: EnteredField
    ): Triple<String, String, String> {
        val d = day.toIntOrNull()
        val n = night.toIntOrNull()
        val t = total.toIntOrNull()
        val count = listOf(d, n, t).count { it != null }

        if (count <= 1) return Triple(day, night, total)

        if (count == 2) {
            return when {
                d != null && n != null -> Triple(day, night, (d + n).toString())
                d != null && t != null -> Triple(day, if (t >= d) (t - d).toString() else "", total)
                n != null && t != null -> Triple(if (t >= n) (t - n).toString() else "", night, total)
                else -> Triple(day, night, total)
            }
        }

        return when (entered) {
            EnteredField.TOTAL, EnteredField.NIGHT ->
                Triple(if (t != null && n != null && t >= n) (t - n).toString() else "", night, total)
            EnteredField.DAY ->
                Triple(day, night, if (d != null && n != null) (d + n).toString() else "")
        }
    }

    private fun recalcAll7() {
        recalcSinglePilot()
        recalcPilotFunctions()
    }

    private fun recalcSinglePilot() {
        val s = _state.value
        val block = s.blockTimeTotal.toMinutesOrNull() ?: return
        val cat = s.category
        if (cat.isEmpty()) return

        val se = if (cat == "Single Pilot SE") formatDuration(block) else ""
        val me = if (cat == "Single Pilot ME") formatDuration(block) else ""
        val multi = if (cat == "Multi Pilot") formatDuration(block) else ""

        _state.update {
            it.copy(singlePilotSeTime = se, singlePilotMeTime = me, multiPilotTime = multi)
        }
    }

    private fun recalcPilotFunctions() {
        val s = _state.value
        val block = s.blockTimeTotal.toMinutesOrNull() ?: return
        val pos = s.position
        if (pos.isEmpty()) return

        val pic = if (pos in setOf("PIC", "SPIC", "PICUS")) formatDuration(block) else ""
        val sic = if (pos == "SIC") formatDuration(block) else ""
        val dual = if (pos == "Dual") formatDuration(block) else ""
        val instr = if (pos == "Instructor") formatDuration(block) else ""

        _state.update {
            it.copy(
                pilotFunctionPicTime = pic,
                pilotFunctionSicTime = sic,
                pilotFunctionDualTime = dual,
                pilotFunctionInstructorTime = instr
            )
        }
    }

    private fun toEditState(row: OpeningBalance) = OpeningBalanceEditState(
        id = row.id,
        startDate = row.startDate,
        endDate = row.endDate,
        acType = row.acType ?: "",
        position = row.position ?: "",
        category = row.category ?: "",
        comment = row.comment ?: "",
        blockTimeTotal = row.blockTimeTotal?.let { formatDuration(it) } ?: "",
        blockTimeNight = row.blockTimeNight?.let { formatDuration(it) } ?: "",
        blockTimeDay = row.blockTimeDay?.let { formatDuration(it) } ?: "",
        flightTimeTotal = row.flightTimeTotal?.let { formatDuration(it) } ?: "",
        flightTimeNight = row.flightTimeNight?.let { formatDuration(it) } ?: "",
        flightTimeDay = row.flightTimeDay?.let { formatDuration(it) } ?: "",
        ifrTotal = row.ifrTotal?.let { formatDuration(it) } ?: "",
        ifrNight = row.ifrNight?.let { formatDuration(it) } ?: "",
        ifrDay = row.ifrDay?.let { formatDuration(it) } ?: "",
        simulatorTime = row.simulatorTime?.let { formatDuration(it) } ?: "",
        numberToDay = row.numberToDay?.toString() ?: "",
        numberToNight = row.numberToNight?.toString() ?: "",
        numberToTotal = row.numberToTotal?.toString() ?: "",
        numberLdgDay = row.numberLdgDay?.toString() ?: "",
        numberLdgNight = row.numberLdgNight?.toString() ?: "",
        numberLdgTotal = row.numberLdgTotal?.toString() ?: "",
        numberInstrumentApproaches = row.numberInstrumentApproaches?.toString() ?: "",
        singlePilotSeTime = row.singlePilotSeTime?.let { formatDuration(it) } ?: "",
        singlePilotMeTime = row.singlePilotMeTime?.let { formatDuration(it) } ?: "",
        multiPilotTime = row.multiPilotTime?.let { formatDuration(it) } ?: "",
        pilotFunctionPicTime = row.pilotFunctionPicTime?.let { formatDuration(it) } ?: "",
        pilotFunctionSicTime = row.pilotFunctionSicTime?.let { formatDuration(it) } ?: "",
        pilotFunctionDualTime = row.pilotFunctionDualTime?.let { formatDuration(it) } ?: "",
        pilotFunctionInstructorTime = row.pilotFunctionInstructorTime?.let { formatDuration(it) } ?: ""
    )

    private fun toEditStateFromMerged(
        t: LegTotals,
        rows: List<OpeningBalance>
    ): OpeningBalanceEditState {

        fun commonValue(values: List<String?>): String {
            if (values.isEmpty()) return ""
            val first = values.first()
            return if (values.all { it == first }) first ?: "" else ""
        }

        val earliestStart = rows.mapNotNull { it.startDate }.minOrNull()
        val latestEnd = rows.mapNotNull { it.endDate }.maxOrNull()

        return OpeningBalanceEditState(
            id = null,
            startDate = earliestStart,
            endDate = latestEnd,
            acType = commonValue(rows.map { it.acType }),
            position = commonValue(rows.map { it.position }),
            category = commonValue(rows.map { it.category }),
            comment = commonValue(rows.map { it.comment }),
            blockTimeTotal = if (t.blockTimeTotal != 0) formatDuration(t.blockTimeTotal) else "",
            blockTimeNight = if (t.blockTimeNight != 0) formatDuration(t.blockTimeNight) else "",
            blockTimeDay = if (t.blockTimeDay != 0) formatDuration(t.blockTimeDay) else "",
            flightTimeTotal = if (t.flightTimeTotal != 0) formatDuration(t.flightTimeTotal) else "",
            flightTimeNight = if (t.flightTimeNight != 0) formatDuration(t.flightTimeNight) else "",
            flightTimeDay = if (t.flightTimeDay != 0) formatDuration(t.flightTimeDay) else "",
            ifrTotal = if (t.ifrTotal != 0) formatDuration(t.ifrTotal) else "",
            ifrNight = if (t.ifrNight != 0) formatDuration(t.ifrNight) else "",
            ifrDay = if (t.ifrDay != 0) formatDuration(t.ifrDay) else "",
            simulatorTime = if (t.simulatorTime != 0) formatDuration(t.simulatorTime) else "",
            numberToDay = if (t.numberToDay != 0) t.numberToDay.toString() else "",
            numberToNight = if (t.numberToNight != 0) t.numberToNight.toString() else "",
            numberToTotal = if (t.numberToTotal != 0) t.numberToTotal.toString() else "",
            numberLdgDay = if (t.numberLdgDay != 0) t.numberLdgDay.toString() else "",
            numberLdgNight = if (t.numberLdgNight != 0) t.numberLdgNight.toString() else "",
            numberLdgTotal = if (t.numberLdgTotal != 0) t.numberLdgTotal.toString() else "",
            numberInstrumentApproaches = if (t.numberInstrumentApproaches != 0) t.numberInstrumentApproaches.toString() else "",
            singlePilotSeTime = if (t.singlePilotSeTime != 0) formatDuration(t.singlePilotSeTime) else "",
            singlePilotMeTime = if (t.singlePilotMeTime != 0) formatDuration(t.singlePilotMeTime) else "",
            multiPilotTime = if (t.multiPilotTime != 0) formatDuration(t.multiPilotTime) else "",
            pilotFunctionPicTime = if (t.pilotFunctionPicTime != 0) formatDuration(t.pilotFunctionPicTime) else "",
            pilotFunctionSicTime = if (t.pilotFunctionSicTime != 0) formatDuration(t.pilotFunctionSicTime) else "",
            pilotFunctionDualTime = if (t.pilotFunctionDualTime != 0) formatDuration(t.pilotFunctionDualTime) else "",
            pilotFunctionInstructorTime = if (t.pilotFunctionInstructorTime != 0) formatDuration(t.pilotFunctionInstructorTime) else ""
        )
    }
    suspend fun searchAcType(query: String) = repository.searchAcType(query)
    suspend fun searchPosition(query: String) = repository.searchPosition(query)
    suspend fun searchCategory(query: String) = repository.searchCategory(query)
}