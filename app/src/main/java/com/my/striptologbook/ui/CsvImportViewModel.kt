package com.my.striptologbook.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.my.striptologbook.data.Leg
import com.my.striptologbook.repository.LegRepository
import com.my.striptologbook.utils.CsvData
import com.my.striptologbook.utils.CsvImporter
import com.my.striptologbook.utils.formatTimestampIsoUtc
import com.my.striptologbook.utils.getDateAtMidnight
import com.my.striptologbook.utils.toMinutesOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

// All fields that can be mapped from CSV
enum class LegField(val displayName: String) {
    IGNORE("Ignore"),
    POSITION("Position"),
    PIC_NAME("PIC Name"),
    SIC_NAME("SIC Name"),
    JUMPSEAT1("Jumpseat 1"),
    JUMPSEAT2("Jumpseat 2"),
    CABIN_CREW("Cabin Crew"),
    AC_REGISTRATION("Registration"),
    AC_TYPE("A/C Type"),
    FLIGHT_NUMBER("Flight Number"),
    CATEGORY("Category"),
    ICAO_DEPARTURE("ICAO Departure"),
    ICAO_DESTINATION("ICAO Destination"),

    // OffBlock: full timestamp + separate date/time parts
    OFF_BLOCK_DATETIME("OffBlock (full)"),
    OFF_BLOCK_DATE_ONLY("OffBlock Date"),
    OFF_BLOCK_TIME_ONLY("OffBlock Time"),

    // Takeoff: full timestamp + separate date/time parts
    TAKEOFF_DATETIME("Takeoff Time (full)"),
    TAKEOFF_DATE_ONLY("Takeoff Date"),
    TAKEOFF_TIME_ONLY("Takeoff Time"),

    // Landing: full timestamp + separate date/time parts
    LANDING_DATETIME("Landing Time (full)"),
    LANDING_DATE_ONLY("Landing Date"),
    LANDING_TIME_ONLY("Landing Time"),

    // OnBlock: full timestamp + separate date/time parts
    ON_BLOCK_DATETIME("OnBlock (full)"),
    ON_BLOCK_DATE_ONLY("OnBlock Date"),
    ON_BLOCK_TIME_ONLY("OnBlock Time"),

    IS_SIMULATOR("is Simulator"),
    SIMULATOR_TIME("Simulator Time"),

    // Sunrise: full timestamp + separate date/time parts
    SUNRISE_DATETIME("Sunrise (full)"),
    SUNRISE_DATE_ONLY("Sunrise Date"),
    SUNRISE_TIME_ONLY("Sunrise Time"),

    // Sunset: full timestamp + separate date/time parts
    SUNSET_DATETIME("Sunset (full)"),
    SUNSET_DATE_ONLY("Sunset Date"),
    SUNSET_TIME_ONLY("Sunset Time"),

    // Durations
    BLOCK_TIME_TOTAL("Block Time Total"),
    BLOCK_TIME_NIGHT("Block Time Night"),
    BLOCK_TIME_DAY("Block Time Day"),
    FLIGHT_TIME_TOTAL("Flight Time Total"),
    FLIGHT_TIME_NIGHT("Flight Time Night"),
    FLIGHT_TIME_DAY("Flight Time Day"),
    IFR_TOTAL("IFR Total"),
    IFR_NIGHT("IFR Night"),
    IFR_DAY("IFR Day"),
    NUMBER_INSTRUMENT_APPROACHES("Instrument Approaches"),

    PILOT_FLYING("Pilot Flying"),
    // Counters
    NUMBER_TO_DAY("# TO Day"),
    NUMBER_TO_NIGHT("# TO Night"),
    NUMBER_TO_TOTAL("# TO Total"),
    NUMBER_LDG_DAY("# LDG Day"),
    NUMBER_LDG_NIGHT("# LDG Night"),
    NUMBER_LDG_TOTAL("# LDG Total"),

    COMMENT("Comment")
}

data class CsvImportState(
    val fileName: String = "",
    val totalRows: Int = 0,
    val columnMappings: Map<String, LegField> = emptyMap(),
    val previewRows: List<Map<LegField, String>> = emptyList(),
    val isImporting: Boolean = false,
    val importResult: ImportResult? = null,
    val validationError: String? = null
)

private const val TAG = "CsvImportLogViewModel"

// Add this at the file level, or inside the ViewModel
data class SkipDetail(
    val rowIndex: Int,
    val offBlock: String,
    val departure: String,
    val arrival: String,
    val reason: String
)

sealed class ImportResult {
    object Success : ImportResult()
    data class Partial(
        val imported: Int,
        val skipped: Int,
        val errors: Int,
        val skipDetails: List<SkipDetail> = emptyList() // <-- Added
    ) : ImportResult()
    data class Error(val message: String) : ImportResult()
}

private val APP_EXPORT_HEADER_TO_FIELD: Map<String, LegField> = mapOf(
    "id"                         to LegField.IGNORE,
    "position"                   to LegField.POSITION,
    "picName"                    to LegField.PIC_NAME,
    "sicName"                    to LegField.SIC_NAME,
    "jumpseat1"                  to LegField.JUMPSEAT1,
    "jumpseat2"                  to LegField.JUMPSEAT2,
    "cabinCrew"                  to LegField.CABIN_CREW,
    "acRegistration"             to LegField.AC_REGISTRATION,
    "acType"                     to LegField.AC_TYPE,
    "flightNumber"               to LegField.FLIGHT_NUMBER,
    "category"                   to LegField.CATEGORY,
    "isSimulator"                to LegField.IS_SIMULATOR,
    "icaoDeparture"              to LegField.ICAO_DEPARTURE,
    "icaoDestination"            to LegField.ICAO_DESTINATION,
    "offBlock"                   to LegField.OFF_BLOCK_DATETIME,
    "takeoffTime"                to LegField.TAKEOFF_DATETIME,
    "landingTime"                to LegField.LANDING_DATETIME,
    "onBlock"                    to LegField.ON_BLOCK_DATETIME,
    "sunrise"                    to LegField.SUNRISE_DATETIME,
    "sunset"                     to LegField.SUNSET_DATETIME,
    "blockTimeDay"               to LegField.BLOCK_TIME_DAY,
    "blockTimeNight"             to LegField.BLOCK_TIME_NIGHT,
    "blockTimeTotal"             to LegField.BLOCK_TIME_TOTAL,
    "flightTimeDay"              to LegField.FLIGHT_TIME_DAY,
    "flightTimeNight"            to LegField.FLIGHT_TIME_NIGHT,
    "flightTimeTotal"            to LegField.FLIGHT_TIME_TOTAL,
    "simulatorTime"              to LegField.SIMULATOR_TIME,
    "ifrDay"                     to LegField.IFR_DAY,
    "ifrNight"                   to LegField.IFR_NIGHT,
    "ifrTotal"                   to LegField.IFR_TOTAL,
    "numberToDay"                to LegField.NUMBER_TO_DAY,
    "numberToNight"              to LegField.NUMBER_TO_NIGHT,
    "numberToTotal"              to LegField.NUMBER_TO_TOTAL,
    "numberLdgDay"               to LegField.NUMBER_LDG_DAY,
    "numberLdgNight"             to LegField.NUMBER_LDG_NIGHT,
    "numberLdgTotal"             to LegField.NUMBER_LDG_TOTAL,
    "numberInstrumentApproaches" to LegField.NUMBER_INSTRUMENT_APPROACHES,
    "pilotFlying"                to LegField.PILOT_FLYING,
    "comment"                    to LegField.COMMENT
)


class CsvImportViewModel(
    private val repository: LegRepository
) : ViewModel() {

    private val _state = MutableStateFlow(CsvImportState())
    val state: StateFlow<CsvImportState> = _state.asStateFlow()

    private var csvData: CsvData? = null

    companion object {
        private const val DAY_MILLIS = 24L * 60 * 60 * 1000

        private val TIMESTAMP_FIELDS = setOf(
            // OffBlock
            LegField.OFF_BLOCK_DATETIME,
            LegField.OFF_BLOCK_DATE_ONLY,
            LegField.OFF_BLOCK_TIME_ONLY,
            // Takeoff
            LegField.TAKEOFF_DATETIME,
            LegField.TAKEOFF_DATE_ONLY,
            LegField.TAKEOFF_TIME_ONLY,
            // Landing
            LegField.LANDING_DATETIME,
            LegField.LANDING_DATE_ONLY,
            LegField.LANDING_TIME_ONLY,
            // OnBlock
            LegField.ON_BLOCK_DATETIME,
            LegField.ON_BLOCK_DATE_ONLY,
            LegField.ON_BLOCK_TIME_ONLY,
            // Sunrise
            LegField.SUNRISE_DATETIME,
            LegField.SUNRISE_DATE_ONLY,
            LegField.SUNRISE_TIME_ONLY,
            // Sunset
            LegField.SUNSET_DATETIME,
            LegField.SUNSET_DATE_ONLY,
            LegField.SUNSET_TIME_ONLY
        )

    }

    /**
     * Checks if the given raw value can be parsed according to the LegField type.
     * Returns true if the value is empty or parseable.
     */
    private fun isValueParseable(field: LegField, value: String): Boolean {
        return value.isEmpty() || when (field) {
            // Full timestamp or Date‑only: try parseTimestamp (covers both)
            in setOf(
                LegField.OFF_BLOCK_DATETIME,
                LegField.TAKEOFF_DATETIME,
                LegField.LANDING_DATETIME,
                LegField.ON_BLOCK_DATETIME,
                LegField.SUNRISE_DATETIME,
                LegField.SUNSET_DATETIME,
                LegField.OFF_BLOCK_DATE_ONLY,
                LegField.TAKEOFF_DATE_ONLY,
                LegField.LANDING_DATE_ONLY,
                LegField.ON_BLOCK_DATE_ONLY,
                LegField.SUNRISE_DATE_ONLY,
                LegField.SUNSET_DATE_ONLY
            ) -> parseTimestamp(value) != null

            // Time‑only: try toMinutesOrNull
            in setOf(
                LegField.OFF_BLOCK_TIME_ONLY,
                LegField.TAKEOFF_TIME_ONLY,
                LegField.LANDING_TIME_ONLY,
                LegField.ON_BLOCK_TIME_ONLY,
                LegField.SUNRISE_TIME_ONLY,
                LegField.SUNSET_TIME_ONLY
            ) -> value.toMinutesOrNull() != null

            // Other fields (durations, ints, booleans, text) – we skip parseability checks for now
            else -> true
        }
    }

    fun loadCsvFile(inputStream: InputStream, fileName: String) {
        Log.d(TAG, "loadCsvFile: fileName=$fileName")

        viewModelScope.launch {
            try {
                val data = CsvImporter.readCsv(inputStream)
                csvData = data

                Log.d(TAG, "CSV parsed: headers=${data.headers.size}, rows=${data.rows.size}")

                val initialMappings = data.headers.associateWith { header ->
                    APP_EXPORT_HEADER_TO_FIELD[header] ?: LegField.IGNORE
                }

                _state.update {
                    it.copy(
                        fileName = fileName,
                        totalRows = data.rows.size,
                        columnMappings = initialMappings,
                        validationError = null
                    )
                }
                validateAllRows()
            } catch (e: Exception) {
                Log.e(TAG, "loadCsvFile failed", e)
                _state.update {
                    it.copy(
                        importResult = ImportResult.Error("Failed to read CSV: ${e.message}")
                    )
                }
            }
        }
    }

    fun updateMapping(columnHeader: String, legField: LegField) {
        Log.d(TAG, "updateMapping: header=$columnHeader, field=$legField")

        _state.update { state ->
            val newMappings = state.columnMappings.toMutableMap()
            newMappings[columnHeader] = legField
            state.copy(columnMappings = newMappings)
        }
        validateAllRows()
    }

    private fun validateAllRows() {
        val data = csvData ?: return
        val mappings = _state.value.columnMappings

        // Only check timestamp‑related fields (explicitly mapped)
        val fieldsToCheck = mappings.values.filter { it in TIMESTAMP_FIELDS }.toSet()
        if (fieldsToCheck.isEmpty()) {
            // No timestamp columns mapped – nothing to validate
            _state.update { it.copy(validationError = null, previewRows = emptyList()) }
            return
        }

        var firstError: String? = null

        for ((index, row) in data.rows.withIndex()) {
            for ((header, field) in mappings) {
                if (field !in TIMESTAMP_FIELDS) continue
                val colIndex = data.headers.indexOf(header)
                val rawValue = if (colIndex < row.size) row[colIndex].trim() else ""

                if (!isValueParseable(field, rawValue)) {
                    firstError = when (field) {
                        in setOf(
                            LegField.OFF_BLOCK_DATETIME,
                            LegField.TAKEOFF_DATETIME,
                            LegField.LANDING_DATETIME,
                            LegField.ON_BLOCK_DATETIME,
                            LegField.SUNRISE_DATETIME,
                            LegField.SUNSET_DATETIME
                        ) -> "Row ${index + 2}, column '$header': value '$rawValue' is not a valid timestamp"

                        in setOf(
                            LegField.OFF_BLOCK_DATE_ONLY,
                            LegField.TAKEOFF_DATE_ONLY,
                            LegField.LANDING_DATE_ONLY,
                            LegField.ON_BLOCK_DATE_ONLY,
                            LegField.SUNRISE_DATE_ONLY,
                            LegField.SUNSET_DATE_ONLY
                        ) -> "Row ${index + 2}, column '$header': value '$rawValue' is not a valid date"

                        in setOf(
                            LegField.OFF_BLOCK_TIME_ONLY,
                            LegField.TAKEOFF_TIME_ONLY,
                            LegField.LANDING_TIME_ONLY,
                            LegField.ON_BLOCK_TIME_ONLY,
                            LegField.SUNRISE_TIME_ONLY,
                            LegField.SUNSET_TIME_ONLY
                        ) -> "Row ${index + 2}, column '$header': value '$rawValue' is not a valid time (HH:MM)"

                        else -> "Row ${index + 2}, column '$header': invalid value"
                    }
                    break // stop at first error
                }
            }
            if (firstError != null) break
        }

        _state.update {
            if (firstError != null) {
                it.copy(validationError = firstError, previewRows = emptyList())
            } else {
                // Validation passed – keep preview rows (if needed)
                val previewRows = data.rows.take(5).map { row ->
                    // For preview, we can just map raw values; no need to parse
                    data.headers.mapIndexed { idx, header ->
                        val field = mappings[header] ?: LegField.IGNORE
                        val value = if (idx < row.size) row[idx] else ""
                        field to value
                    }.toMap()
                }
                it.copy(validationError = null, previewRows = previewRows)
            }
        }
    }

    /**
     * Parses a row and returns Pair<Leg?, String?> with error message if any.
     */
    private fun parseRowToLegWithError(
        row: List<String>,
        headers: List<String>,
        mappings: Map<String, LegField>,
        rowIndex: Int
    ): Pair<Leg?, String?> {
        try {
            // 1. Map all columns to raw string values
            val fieldValues = mutableMapOf<LegField, String>()
            for ((idx, header) in headers.withIndex()) {
                val field = mappings[header] ?: LegField.IGNORE
                if (field != LegField.IGNORE) {
                    val value = if (idx < row.size) row[idx] else ""
                    fieldValues[field] = value
                }
            }

            // Helper to get column name for error reporting
            fun getColumnName(field: LegField): String {
                for ((header, mappedField) in mappings) {
                    if (mappedField == field) return header
                }
                return field.displayName
            }

            // Helper to parse a duration with error
            fun parseDurationWithError(value: String?, field: LegField): Int? {
                if (value.isNullOrEmpty()) return null
                val minutesFromHMM = value.toMinutesOrNull()
                if (minutesFromHMM != null) return minutesFromHMM
                val minutesFromInt = value.toIntOrNull()
                if (minutesFromInt != null) return minutesFromInt
                throw IllegalArgumentException(
                    "Row $rowIndex, column '${getColumnName(field)}': value '$value' is not a valid duration (expected H:MM or minutes integer)"
                )
            }

            // Helper to parse boolean
            fun parseBooleanWithError(value: String?): Boolean? {
                if (value.isNullOrEmpty()) return null
                return when (value.uppercase()) {
                    "TRUE", "1", "YES" -> true
                    "FALSE", "0", "NO" -> false
                    else -> null
                }
            }

            // Helper to parse integer
            fun parseIntWithError(value: String?, field: LegField): Int? {
                if (value.isNullOrEmpty()) return null
                return try {
                    value.toInt()
                } catch (_: NumberFormatException) {
                    throw IllegalArgumentException("Row $rowIndex, column '${getColumnName(field)}': value '$value' is not a valid integer")
                }
            }

            fun getRaw(field: LegField): String? = fieldValues[field]

            // ---------- OffBlock parsing (no base date fallback) ----------
            val offBlockDateStr = getRaw(LegField.OFF_BLOCK_DATE_ONLY)
            val offBlockTimeStr = getRaw(LegField.OFF_BLOCK_TIME_ONLY)
            val offBlockFullStr = getRaw(LegField.OFF_BLOCK_DATETIME)

            // Priority: Full first, then Date+Time
            var offBlockExplicit = false
            val offBlockTs = when {
                !offBlockFullStr.isNullOrEmpty() -> {
                    offBlockExplicit = true
                    parseTimestamp(offBlockFullStr) ?: throw IllegalArgumentException(
                        "Row $rowIndex, column '${getColumnName(LegField.OFF_BLOCK_DATETIME)}': value '$offBlockFullStr' is not a valid timestamp"
                    )
                }
                !offBlockDateStr.isNullOrEmpty() && !offBlockTimeStr.isNullOrEmpty() -> {
                    offBlockExplicit = true
                    val combined = "$offBlockDateStr $offBlockTimeStr"
                    parseTimestamp(combined) ?: throw IllegalArgumentException(
                        "Row $rowIndex: combined value '$combined' (from columns '${getColumnName(LegField.OFF_BLOCK_DATE_ONLY)}' and '${getColumnName(LegField.OFF_BLOCK_TIME_ONLY)}') is not a valid timestamp"
                    )
                }
                else -> null
            }

            val offBlockDate = if (offBlockTs != null) getDateAtMidnight(offBlockTs) else null

            // ---------- Helper to compute a timestamp for other events ----------
            // Returns Pair(timestamp, explicitFlag)
            // Priority: Full first, then Date+Time, then Time-only + baseDate (implicit).
            // Time-only values are adjusted against referenceTs before being returned.
            fun computeTimestampWithExplicit(
                dateField: LegField,
                timeField: LegField,
                fullField: LegField,
                baseDate: Long?,
                referenceTs: Long? = null,
                isSunriseSunset: Boolean = false
            ): Pair<Long?, Boolean> {
                val dateStr = getRaw(dateField)
                val timeStr = getRaw(timeField)
                val fullStr = getRaw(fullField)

                // Priority 1: Full timestamp
                if (!fullStr.isNullOrEmpty()) {
                    val ts = parseTimestamp(fullStr) ?: throw IllegalArgumentException(
                        "Row $rowIndex, column '${getColumnName(fullField)}': value '$fullStr' is not a valid timestamp"
                    )
                    return Pair(ts, true)
                }

                // Priority 2: Date+Time pair
                if (!dateStr.isNullOrEmpty() && !timeStr.isNullOrEmpty()) {
                    val combined = "$dateStr $timeStr"
                    val ts = parseTimestamp(combined) ?: throw IllegalArgumentException(
                        "Row $rowIndex: combined value '$combined' (from columns '${getColumnName(dateField)}' and '${getColumnName(timeField)}') is not a valid timestamp"
                    )
                    return Pair(ts, true)
                }

                // Priority 3: Time-only + base date (implicit)
                if (!timeStr.isNullOrEmpty() && baseDate != null) {
                    val minutes = timeStr.toMinutesOrNull() ?: throw IllegalArgumentException(
                        "Row $rowIndex, column '${getColumnName(timeField)}': value '$timeStr' is not a valid time (HH:MM)"
                    )
                    val raw = baseDate + (minutes * 60_000L)
                    if (referenceTs != null && !isSunriseSunset) {
                        return Pair(adjustTimestampRelativeToReference(raw, referenceTs), false)
                    }
                    return Pair(raw, false)
                }

                return Pair(null, false)
            }

            // Compute takeoff, landing and onBlock. Implicit (time-only)
            // values are adjusted here, during construction, against the
            // previous event in the timeline.
            val (takeoffFinal, takeoffExplicit) = computeTimestampWithExplicit(
                dateField = LegField.TAKEOFF_DATE_ONLY,
                timeField = LegField.TAKEOFF_TIME_ONLY,
                fullField = LegField.TAKEOFF_DATETIME,
                baseDate = offBlockDate,
                referenceTs = offBlockTs
            )

            val (landingFinal, landingExplicit) = computeTimestampWithExplicit(
                dateField = LegField.LANDING_DATE_ONLY,
                timeField = LegField.LANDING_TIME_ONLY,
                fullField = LegField.LANDING_DATETIME,
                baseDate = offBlockDate,
                referenceTs = takeoffFinal ?: offBlockTs
            )

            val (onBlockFinal, onBlockExplicit) = computeTimestampWithExplicit(
                dateField = LegField.ON_BLOCK_DATE_ONLY,
                timeField = LegField.ON_BLOCK_TIME_ONLY,
                fullField = LegField.ON_BLOCK_DATETIME,
                baseDate = offBlockDate,
                referenceTs = when {
                    landingFinal != null && offBlockTs != null && landingFinal > offBlockTs -> landingFinal
                    else -> offBlockTs
                }
            )

            // Sunrise and Sunset are always explicit (no cross‑midnight adjustment) – we parse them with the same function
            val (sunriseTs, _) = computeTimestampWithExplicit(
                dateField = LegField.SUNRISE_DATE_ONLY,
                timeField = LegField.SUNRISE_TIME_ONLY,
                fullField = LegField.SUNRISE_DATETIME,
                baseDate = offBlockDate,
                isSunriseSunset = true
            )

            val (sunsetTs, _) = computeTimestampWithExplicit(
                dateField = LegField.SUNSET_DATE_ONLY,
                timeField = LegField.SUNSET_TIME_ONLY,
                fullField = LegField.SUNSET_DATETIME,
                baseDate = offBlockDate,
                isSunriseSunset = true
            )

            // ---------- Strict validation for explicit timestamps ----------
            // Runs on the final (already-adjusted) values.
            fun validateExplicitTimestamps(): String? {
                val hasOB = offBlockTs != null
                val hasON = onBlockFinal != null

                // Rule 1: OffBlock and OnBlock must be both present or both absent
                if (hasOB && !hasON) {
                    return "Row $rowIndex: OffBlock is provided but OnBlock is missing"
                }
                if (!hasOB && hasON) {
                    return "Row $rowIndex: OnBlock is provided but OffBlock is missing"
                }

                // If both are missing → skip all further checks
                if (!hasOB && !hasON) {
                    return null
                }

                // From here, both OffBlock and OnBlock are present
                val hasTO = takeoffFinal != null
                val hasLD = landingFinal != null

                // Rule 2: Takeoff and Landing must be both present or both absent
                if (hasTO && !hasLD) {
                    return "Row $rowIndex: Takeoff is provided but Landing is missing"
                }
                if (!hasTO && hasLD) {
                    return "Row $rowIndex: Landing is provided but Takeoff is missing"
                }

                // Rule 3: If both Takeoff and Landing are missing → verify only OffBlock < OnBlock
                if (!hasTO && !hasLD) {
                    if (offBlockTs!! >= onBlockFinal!!) {
                        return "Row $rowIndex: OffBlock must be strictly before OnBlock (OffBlock=${
                            formatTimestampIsoUtc(
                                offBlockTs
                            )
                        }, OnBlock=${formatTimestampIsoUtc(onBlockFinal)})"
                    }
                    return null
                }

                // Rule 4: All four are present → verify full chain: OB < TO < LD < ON
                if (offBlockTs!! >= takeoffFinal!!) {
                    return "Row $rowIndex: OffBlock must be before Takeoff (OffBlock=${
                        formatTimestampIsoUtc(
                            offBlockTs
                        )
                    }, Takeoff=${formatTimestampIsoUtc(takeoffFinal)})"
                }
                if (takeoffFinal >= landingFinal!!) {
                    return "Row $rowIndex: Takeoff must be before Landing (Takeoff=${
                        formatTimestampIsoUtc(
                            takeoffFinal
                        )
                    }, Landing=${formatTimestampIsoUtc(landingFinal)})"
                }
                if (landingFinal >= onBlockFinal!!) {
                    return "Row $rowIndex: Landing must be before OnBlock (Landing=${
                        formatTimestampIsoUtc(
                            landingFinal
                        )
                    }, OnBlock=${formatTimestampIsoUtc(onBlockFinal)})"
                }

                return null
            }

            // Run validation – only if any of the four timestamps are explicit.
            if (offBlockExplicit || takeoffExplicit || landingExplicit || onBlockExplicit) {
                val validationError = validateExplicitTimestamps()
                if (validationError != null) {
                    return Pair(null, validationError)
                }
            }

            // ---------- Build the Leg object ----------
            val leg = Leg(
                id = 0,
                position = fieldValues[LegField.POSITION]?.ifEmpty { null },
                picName = fieldValues[LegField.PIC_NAME]?.ifEmpty { null },
                sicName = fieldValues[LegField.SIC_NAME]?.ifEmpty { null },
                jumpseat1 = fieldValues[LegField.JUMPSEAT1]?.ifEmpty { null },
                jumpseat2 = fieldValues[LegField.JUMPSEAT2]?.ifEmpty { null },
                cabinCrew = fieldValues[LegField.CABIN_CREW]?.ifEmpty { null },

                acRegistration = fieldValues[LegField.AC_REGISTRATION]?.ifEmpty { null },
                acType = fieldValues[LegField.AC_TYPE]?.ifEmpty { null },
                category = fieldValues[LegField.CATEGORY]?.ifEmpty { null },

                flightNumber = fieldValues[LegField.FLIGHT_NUMBER]?.ifEmpty { null },
                icaoDeparture = fieldValues[LegField.ICAO_DEPARTURE]?.ifEmpty { null },
                icaoDestination = fieldValues[LegField.ICAO_DESTINATION]?.ifEmpty { null },

                offBlock = offBlockTs,
                onBlock = onBlockFinal,
                takeoffTime = takeoffFinal,
                landingTime = landingFinal,

                sunrise = sunriseTs,
                sunset = sunsetTs,

                blockTimeTotal = parseDurationWithError(fieldValues[LegField.BLOCK_TIME_TOTAL], LegField.BLOCK_TIME_TOTAL),
                blockTimeNight = parseDurationWithError(fieldValues[LegField.BLOCK_TIME_NIGHT], LegField.BLOCK_TIME_NIGHT),
                blockTimeDay = parseDurationWithError(fieldValues[LegField.BLOCK_TIME_DAY], LegField.BLOCK_TIME_DAY),

                flightTimeTotal = parseDurationWithError(fieldValues[LegField.FLIGHT_TIME_TOTAL], LegField.FLIGHT_TIME_TOTAL),
                flightTimeNight = parseDurationWithError(fieldValues[LegField.FLIGHT_TIME_NIGHT], LegField.FLIGHT_TIME_NIGHT),
                flightTimeDay = parseDurationWithError(fieldValues[LegField.FLIGHT_TIME_DAY], LegField.FLIGHT_TIME_DAY),

                isSimulator = parseBooleanWithError(fieldValues[LegField.IS_SIMULATOR]),
                simulatorTime = parseDurationWithError(fieldValues[LegField.SIMULATOR_TIME], LegField.SIMULATOR_TIME),

                ifrTotal = parseDurationWithError(fieldValues[LegField.IFR_TOTAL], LegField.IFR_TOTAL),
                ifrNight = parseDurationWithError(fieldValues[LegField.IFR_NIGHT], LegField.IFR_NIGHT),
                ifrDay = parseDurationWithError(fieldValues[LegField.IFR_DAY], LegField.IFR_DAY),

                numberInstrumentApproaches = parseIntWithError(
                    fieldValues[LegField.NUMBER_INSTRUMENT_APPROACHES],
                    LegField.NUMBER_INSTRUMENT_APPROACHES
                ),

                pilotFlying = parseBooleanWithError(fieldValues[LegField.PILOT_FLYING]),
                numberToDay = parseIntWithError(fieldValues[LegField.NUMBER_TO_DAY], LegField.NUMBER_TO_DAY),
                numberToNight = parseIntWithError(fieldValues[LegField.NUMBER_TO_NIGHT], LegField.NUMBER_TO_NIGHT),
                numberToTotal = parseIntWithError(fieldValues[LegField.NUMBER_TO_TOTAL], LegField.NUMBER_TO_TOTAL),
                numberLdgDay = parseIntWithError(fieldValues[LegField.NUMBER_LDG_DAY], LegField.NUMBER_LDG_DAY),
                numberLdgNight = parseIntWithError(fieldValues[LegField.NUMBER_LDG_NIGHT], LegField.NUMBER_LDG_NIGHT),
                numberLdgTotal = parseIntWithError(fieldValues[LegField.NUMBER_LDG_TOTAL], LegField.NUMBER_LDG_TOTAL),
                comment = fieldValues[LegField.COMMENT]?.ifEmpty { null }
            )

            return Pair(leg, null)

        } catch (e: Exception) {
            Log.e(TAG, "parseRowToLegWithError: error at row $rowIndex", e)

            return Pair(null, e.message ?: "Row $rowIndex: Unknown error")
        }
    }

    // Helper to adjust a timestamp relative to a reference (cross‑midnight logic)
    private fun adjustTimestampRelativeToReference(candidate: Long, reference: Long): Long {
        var adjusted = candidate
        while (adjusted < reference) {
            adjusted += DAY_MILLIS
        }
        while (adjusted >= reference + DAY_MILLIS) {
            adjusted -= DAY_MILLIS
        }
        return adjusted
    }

    // ---------- Parsing functions ----------
    private fun parseTimestamp(value: String?): Long? {
        if (value.isNullOrEmpty()) return null
        try { return value.toLong() } catch (_: NumberFormatException) {}

        // Formats with explicit offset
        val offsetFormats = listOf(
            "yyyy-MM-dd HH:mm Z",
            "yyyy-MM-dd'T'HH:mm:ssZ",
            "dd.MM.yyyy HH:mm Z",
            "yyyy-MM-dd HH:mm:ss Z",
            "yyyy-MM-dd'T'HH:mm:ssX",
            "dd.MM.yyyy HH:mm:ss Z"
        )
        for (pattern in offsetFormats) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.US)
                return sdf.parse(value)?.time
            } catch (_: Exception) { }
        }

        // Formats without offset (assume UTC)
        val utcFormats = listOf(
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd HH:mm:ss'Z'",
            "yyyy-MM-dd HH:mm",
            "yyyy-MM-dd'T'HH:mm",
            "dd.MM.yyyy HH:mm",
            "yyyy-MM-dd",
            "dd.MM.yyyy"
        )

        for (pattern in utcFormats) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.US)
                sdf.timeZone = TimeZone.getTimeZone("UTC")
                return sdf.parse(value)?.time
            } catch (_: Exception) { }
        }
        return null
    }

    // ---------- Import ----------
    fun performImport() {
        val data = csvData ?: return
        val mappings = _state.value.columnMappings

        if (_state.value.validationError != null) {
            _state.update {
                it.copy(importResult = ImportResult.Error("Cannot import: validation error"))
            }
            return
        }

        _state.update { it.copy(isImporting = true) }

        viewModelScope.launch {
            try {
                var imported = 0
                var skipped = 0
                var errors = 0
                val skipDetails = mutableListOf<SkipDetail>() // <-- New list

                for ((index, row) in data.rows.withIndex()) {
                    try {
                        val (leg, error) = parseRowToLegWithError(row, data.headers, mappings, index + 2)
                        if (leg != null) {
                            repository.insertLeg(leg.copy(id = 0))
                            imported++
                        } else {
                            // ----- Extract details for the skipped row -----
                            val rowIndex = index + 2

                            // Helper to get raw value from a specific column
                            fun getRawValue(field: LegField): String {
                                val header = mappings.entries.find { it.value == field }?.key
                                if (header == null) return "N/A"
                                val colIdx = data.headers.indexOf(header)
                                if (colIdx == -1 || colIdx >= row.size) return "N/A"
                                return row[colIdx].trim().ifEmpty { "N/A" }
                            }

                            // Extract OffBlock display (Date/Time depending on mapping)
                            val offBlockDisplay = when {
                                mappings.containsValue(LegField.OFF_BLOCK_DATETIME) -> getRawValue(LegField.OFF_BLOCK_DATETIME)
                                mappings.containsValue(LegField.OFF_BLOCK_DATE_ONLY) -> getRawValue(LegField.OFF_BLOCK_DATE_ONLY)
                                mappings.containsValue(LegField.OFF_BLOCK_TIME_ONLY) -> "Time: ${getRawValue(LegField.OFF_BLOCK_TIME_ONLY)}"
                                else -> "N/A"
                            }

                            val depIcao = getRawValue(LegField.ICAO_DEPARTURE)
                            val arrIcao = getRawValue(LegField.ICAO_DESTINATION)

                            skipDetails.add(
                                SkipDetail(
                                    rowIndex = rowIndex,
                                    offBlock = offBlockDisplay,
                                    departure = depIcao,
                                    arrival = arrIcao,
                                    reason = error ?: "Unknown error"
                                )
                            )

                            skipped++
                            Log.w(TAG, "performImport: skipped row $rowIndex: $error")
                        }
                    } catch (e: Exception) {
                        skipped++
                        errors++
                        Log.e(TAG, "performImport: Error parsing row ${index + 2}", e)

                        // Optionally add exception details to skip list
                        skipDetails.add(
                            SkipDetail(
                                rowIndex = index + 2,
                                offBlock = "N/A",
                                departure = "N/A",
                                arrival = "N/A",
                                reason = "Exception: ${e.message ?: "Unknown"}"
                            )
                        )
                    }
                }

                _state.update {
                    it.copy(
                        isImporting = false,
                        importResult = ImportResult.Partial(imported, skipped, errors, skipDetails)
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isImporting = false,
                        importResult = ImportResult.Error("Import failed: ${e.message}")
                    )
                }
            }
        }
    }

    fun consumeResult() {
        _state.update { it.copy(importResult = null) }
    }

    fun resetState() {
        csvData = null
        _state.value = CsvImportState()
    }
}

