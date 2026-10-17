package com.my.striptologbook.utils

import android.content.Context
import android.view.LayoutInflater
import android.widget.NumberPicker
import androidx.appcompat.app.AlertDialog
import com.my.striptologbook.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.Calendar

import android.app.TimePickerDialog
import android.util.Log


// Shows a custom date picker dialog with NumberPickers. All operations are in UTC.
fun showCustomDatePicker(context: Context, initialDate: Long?, onDateSelected: (Long) -> Unit) {
    val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_custom_date, null)
    val dayPicker = dialogView.findViewById<NumberPicker>(R.id.np_day)
    val monthPicker = dialogView.findViewById<NumberPicker>(R.id.np_month)
    val yearPicker = dialogView.findViewById<NumberPicker>(R.id.np_year)

    // Set up pickers with UTC calendar
    val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        timeInMillis = initialDate ?: System.currentTimeMillis()
    }

    val currentYear = Calendar.getInstance(TimeZone.getTimeZone("UTC")).get(Calendar.YEAR)
    val yearRange = 40

    // Year
    yearPicker.minValue = currentYear - yearRange
    yearPicker.maxValue = currentYear + yearRange
    yearPicker.value = cal.get(Calendar.YEAR)
    yearPicker.wrapSelectorWheel = false

    // Month (1-12) – with two‑digit formatter
    monthPicker.minValue = 1
    monthPicker.maxValue = 12
    monthPicker.value = cal.get(Calendar.MONTH) + 1
    monthPicker.wrapSelectorWheel = false
    monthPicker.setFormatter { value -> String.format("%02d", value) }

    // Day – with two‑digit formatter
    fun getDaysInMonth(year: Int, month: Int): Int {
        val c = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            set(year, month - 1, 1)
        }
        return c.getActualMaximum(Calendar.DAY_OF_MONTH)
    }

    fun updateDayRange() {
        val year = yearPicker.value
        val month = monthPicker.value
        val maxDay = getDaysInMonth(year, month)
        dayPicker.minValue = 1
        dayPicker.maxValue = maxDay
        if (dayPicker.value > maxDay) {
            dayPicker.value = maxDay
        }
    }

    // Initial day
    dayPicker.minValue = 1
    dayPicker.maxValue = getDaysInMonth(yearPicker.value, monthPicker.value)
    dayPicker.value = cal.get(Calendar.DAY_OF_MONTH)
    dayPicker.wrapSelectorWheel = false
    dayPicker.setFormatter { value -> String.format("%02d", value) }

    // Update day range when month or year changes
    monthPicker.setOnValueChangedListener { _, _, _ ->
        updateDayRange()
    }
    yearPicker.setOnValueChangedListener { _, _, _ ->
        updateDayRange()
    }

    Log.d("TimeDebug", "Date picker opened with initial timestamp: $initialDate")


    AlertDialog.Builder(context)
        .setTitle("Select Date (UTC)")
        .setView(dialogView)
        .setPositiveButton("OK") { _, _ ->
            val year = yearPicker.value
            val month = monthPicker.value
            val day = dayPicker.value

            val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                set(year, month - 1, day, 0, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }
            onDateSelected(utcCal.timeInMillis)
            Log.d("TimeDebug", "Date selected: year=$year, month=$month, day=$day -> UTC timestamp=${utcCal.timeInMillis}, formatted=${formatDateUtc(utcCal.timeInMillis)}")
        }
        .setNegativeButton("Cancel", null)
        .show()


}

/**
 * Shows a 24‑hour time picker dialog that works strictly in UTC.
 * The initial value (if provided) is interpreted as minutes since midnight UTC.
 * The selected time is returned as minutes since midnight UTC.
 *
 * @param context Activity context
 * @param initialMinutesSinceMidnight UTC minutes (0..1439), or null to use current UTC time
 * @param onTimeSelected Callback with the selected minutes since midnight (UTC)
 */
fun showCustomTimePicker(
    context: Context,
    initialMinutesSinceMidnight: Int? = null,
    onTimeSelected: (minutesSinceMidnight: Int) -> Unit
) {
    // Use current UTC time if no initial value given
    val initialMinutes = initialMinutesSinceMidnight ?: run {
        val now = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
    }

    val hour = initialMinutes / 60
    val minute = initialMinutes % 60

    TimePickerDialog(
        context,
        { _, hourOfDay, minuteOfHour ->
            // Treat the chosen hour/minute as UTC – no timezone conversion
            val totalMinutes = hourOfDay * 60 + minuteOfHour
            onTimeSelected(totalMinutes)
            Log.d("TimeDebug", "Time selected: hour=$hourOfDay, minute=$minuteOfHour -> UTC minutes since midnight=$totalMinutes")
        },
        hour,
        minute,
        true   // Force 24‑hour view


    ).show()
}



fun String.toMinutesOrNull(): Int? {
    return try {
        val parts = this.split(":")
        if (parts.size == 2) {
            parts[0].toInt() * 60 + parts[1].toInt()
        } else null
    } catch (_: Exception) {
        null
    }
}

fun formatDateUtc(timestamp: Long): String {
    val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
    sdf.timeZone = TimeZone.getTimeZone("UTC")
    return sdf.format(Date(timestamp))
}

// Parse a string like "20260721_1430" into a Date (UTC).
fun parseVersionDate(version: String?): Date? {
    if (version == null) return null
    return try {
        SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).parse(version)
    } catch (@Suppress("unused") e: Exception) {
        null
    }
}


// extract from a timestamp date only as "dd.MM.yyyy" in UTC.
fun formatDateOnly(timestamp: Long): String {
    val sdf = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
    sdf.timeZone = TimeZone.getTimeZone("UTC")
    return sdf.format(Date(timestamp))
}


// extract from a timestamp time only as "HH:mm" in UTC.
fun extractTimeOnly(timestamp: Long): String {
    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
    sdf.timeZone = TimeZone.getTimeZone("UTC")
    return sdf.format(Date(timestamp))
}


// Formats a UTC timestamp as an ISO 8601 string with a 'Z' suffix "2026-05-30 02:27:00Z".
// Returns an empty string if timestamp is null.
fun formatTimestampIsoUtc(timestamp: Long?): String {
    if (timestamp == null) return ""
    val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
    sdf.timeZone = TimeZone.getTimeZone("UTC")
    return sdf.format(Date(timestamp))
}

// always two digits for hours and minutes
fun formatTime(minutes: Int): String {
    val hours = minutes / 60
    val mins = minutes % 60
    return String.format("%02d:%02d", hours, mins)
}

// Normalizes a time input string (like "1:30" or "130") to a validated
// time string in "HH:MM" format (always two‑digit hour). Returns empty string if invalid.
fun normalizeTime(input: String): String {
    if (input.isEmpty()) return ""

    if (input.contains(':')) {
        val parts = input.split(":")
        if (parts.size != 2) return "invalid"
        val hours = parts[0].toIntOrNull() ?: return "invalid"
        val minutes = parts[1].toIntOrNull() ?: return "invalid"
        if (hours !in 0..23) return "invalid"
        if (minutes !in 0..59) return "invalid"
        return formatTime(hours * 60 + minutes)
    }

    if (!input.all { it.isDigit() }) return "invalid"
    val digits = input.filter { it.isDigit() }
    if (digits.isEmpty()) return "invalid"

    val minutesPart = if (digits.length >= 2) digits.takeLast(2).toInt() else digits.toInt()
    val hoursPart = if (digits.length >= 2) digits.dropLast(2).toIntOrNull() ?: 0 else 0

    if (hoursPart !in 0..23) return "invalid"
    if (minutesPart !in 0..59) return "invalid"

    val totalMinutes = hoursPart * 60 + minutesPart
    return formatTime(totalMinutes)
}

// Normalizes a duration input string (like "1:30" or "130") to a validated
// duration string in "H:MM" format (single‑digit hour if less than 10). Returns empty string if invalid.
// Normalizes a duration input string (like "1:30" or "130") to a validated
// duration string in "H:MM" format (single‑digit hour if less than 10).
// Returns "invalid" for invalid input, "" for intentionally empty.
fun normalizeDuration(input: String): String {
    if (input.isEmpty()) return ""

    if (input.contains(':')) {
        val parts = input.split(":")
        if (parts.size != 2) return "invalid"
        val hours = parts[0].toIntOrNull() ?: return "invalid"
        val minutes = parts[1].toIntOrNull() ?: return "invalid"
        if (hours < 0) return "invalid"
        if (minutes !in 0..59) return "invalid"
        return formatDuration(hours * 60 + minutes)
    }

    if (!input.all { it.isDigit() }) return "invalid"
    val digits = input.filter { it.isDigit() }
    if (digits.isEmpty()) return "invalid"

    val minutesPart = if (digits.length >= 2) digits.takeLast(2).toInt() else digits.toInt()
    val hoursPart = if (digits.length >= 2) digits.dropLast(2).toIntOrNull() ?: 0 else 0

    if (hoursPart < 0) return "invalid"
    if (minutesPart !in 0..59) return "invalid"

    val totalMinutes = hoursPart * 60 + minutesPart
    return formatDuration(totalMinutes)
}

fun formatDuration(minutes: Int): String {
    val hours = minutes / 60
    val mins = minutes % 60
    return "$hours:${String.format("%02d", mins)}"
}

fun getDateAtMidnight(timestamp: Long): Long {
    val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        timeInMillis = timestamp
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    return cal.timeInMillis
}

fun getTodayMidnight(): Long = getDateAtMidnight(System.currentTimeMillis())

// Parse date from digits like "27052025" -> midnight UTC
fun parseDateFromDigits(input: String): Long? {
    // Expect exactly 8 digits: DDMMYYYY
    if (input.length != 8 || !input.all { it.isDigit() }) return null
    val day = input.substring(0, 2).toIntOrNull() ?: return null
    val month = input.substring(2, 4).toIntOrNull() ?: return null
    val year = input.substring(4, 8).toIntOrNull() ?: return null
    return try {
        Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            set(year, month - 1, day, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    } catch (_: Exception) {
        null
    }
}

// Formats a UTC timestamp (midnight) as "DDMMYYYY" digits, the inverse of parseDateFromDigits.
fun formatDateDigits(timestamp: Long): String {
    val sdf = SimpleDateFormat("ddMMyyyy", Locale.US)
    sdf.timeZone = TimeZone.getTimeZone("UTC")
    return sdf.format(Date(timestamp))
}

// Parse time from digits like "245" -> minutes since midnight (2*60+45=165)
fun parseTimeFromDigits(input: String): Int? {
    if (input.isEmpty() || !input.all { it.isDigit() }) return null
    val totalMinutes = input.toIntOrNull() ?: return null
    // Assume hours are the first part, minutes are last two digits
    val minutesPart = totalMinutes % 100
    val hoursPart = totalMinutes / 100
    return if (minutesPart < 60) hoursPart * 60 + minutesPart else null
}

// Combine date and time into a full UTC timestamp
fun combineDateAndTime(dateMidnight: Long?, timeMinutes: Int?): Long? {
    if (dateMidnight == null && timeMinutes == null) return null
    val base = dateMidnight ?: getTodayMidnight() // fallback to today for time-only
    val minutes = timeMinutes ?: 0
    return base + minutes * 60 * 1000L
}

fun extractMinutesSinceMidnight(timestamp: Long): Int {
    val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = timestamp }
    return cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
}

// Parse duration from string, supporting H:MM or HHMM (e.g., 245 -> 2:45)
fun parseDuration(input: String): Int? {
    if (input.isEmpty()) return null
    // Try as H:MM first (using existing toMinutesOrNull)
    val colonIndex = input.indexOf(':')
    if (colonIndex != -1) {
        return input.toMinutesOrNull()
    }
    // Try as HHMM (e.g., 245 -> 2:45) using existing parseTimeFromDigits
    return parseTimeFromDigits(input)
}


// Parse any supported format to Double, or null if invalid.
// A leading '-' or '+' is treated as a hemisphere sign (S/W or N/E).
// Contradictions between a leading sign and a hemisphere letter are rejected.
fun parseCoordinate(input: String?, isLatitude: Boolean): Double? {
    if (input.isNullOrBlank()) return null
    var s = input.trim().uppercase()

    // 1. Extract leading sign
    var signChar: Char? = null
    if (s.startsWith('-') || s.startsWith('+')) {
        signChar = s[0]
        s = s.substring(1).trim()
        // Reject multiple leading signs
        if (s.startsWith('-') || s.startsWith('+')) return null
    }

    // 2. Extract hemisphere if present
    var hem: Char? = null
    for (ch in setOf('N','S','E','W')) {
        if (s.contains(ch)) {
            hem = ch
            s = s.replace(ch.toString(), "").trim()
            break
        }
    }

    // 3. Check contradiction between leading sign and hemisphere
    if (signChar != null && hem != null) {
        val signIsNegative = signChar == '-'
        val hemIsNegative = hem in setOf('S','W')
        if (signIsNegative != hemIsNegative) return null
    }

    // 4. If hemisphere present, remaining string must not start with a sign
    if (hem != null && (s.startsWith('-') || s.startsWith('+'))) return null

    // 5. Replace comma with dot, collapse spaces
    s = s.replace(',', '.').replace(Regex("\\s+"), " ").trim()

    var magnitude: Double? = null

    // ----- 1. Try D°M.m or D M.m (space) -----
    val dmRegex = Regex("""^(\d+)\s*[°\s]\s*(\d+\.?\d*)$""")
    dmRegex.find(s)?.let {
        val deg = it.groupValues[1].toDoubleOrNull() ?: return null
        val min = it.groupValues[2].toDoubleOrNull() ?: 0.0
        magnitude = deg + min / 60.0
    }

    // ----- 2. Try D°M'S" or D M S (space) -----
    if (magnitude == null) {
        val dmsRegex = Regex("""^(\d+)\s*[°\s]\s*(\d+)\s*['\s]\s*(\d+\.?\d*)\s*["\s]?$""")
        dmsRegex.find(s)?.let {
            val deg = it.groupValues[1].toDoubleOrNull() ?: return null
            val min = it.groupValues[2].toDoubleOrNull() ?: 0.0
            val sec = it.groupValues[3].toDoubleOrNull() ?: 0.0
            magnitude = deg + min / 60.0 + sec / 3600.0
        }
    }

    // ----- 3. Try compact with decimal point: DDMM.m or DDDMM.m -----
    if (magnitude == null) {
        val compactDotRegex = Regex("""^(\d{2,3})\.(\d+)$""")
        compactDotRegex.find(s)?.let {
            val degStr = it.groupValues[1]
            val minStr = it.groupValues[2]
            val deg = degStr.toDoubleOrNull() ?: return null
            val min = minStr.toDoubleOrNull() ?: return null
            magnitude = deg + min / 60.0
        }
    }

    // ----- 4. Try compact without decimal: DDMMm or DDDMMm -----
    if (magnitude == null && s.matches(Regex("^\\d+$"))) {
        val len = s.length
        val minLen = if (isLatitude) 3 else 4
        if (len >= minLen) {
            val degStr = if (isLatitude) s.take(2) else s.take(3)
            val minStr = s.drop(degStr.length)
            if (minStr.isNotEmpty()) {
                var minutes: Double
                if (minStr.length == 3) {
                    val whole = minStr.take(2).toDoubleOrNull() ?: return null
                    val tenths = minStr.last().toString().toDoubleOrNull() ?: 0.0
                    minutes = whole + tenths / 10.0
                } else {
                    minutes = minStr.toDoubleOrNull() ?: return null
                }
                val deg = degStr.toDoubleOrNull() ?: return null
                magnitude = deg + minutes / 60.0
            }
        }
    }

    // ----- 5. Finally, try plain decimal (signed) -----
    if (magnitude == null) {
        s.toDoubleOrNull()?.let {
            magnitude = kotlin.math.abs(it)
        }
    }

    if (magnitude == null) return null

    // Apply sign from leading sign or hemisphere
    val isNegative = (signChar == '-') || (hem in setOf('S', 'W'))
    val result = if (isNegative) -magnitude else magnitude

    return if (isValidRange(result, isLatitude)) result else null
}

// Validate range based on type
private fun isValidRange(value: Double, isLatitude: Boolean): Boolean {
    return if (isLatitude) value in -90.0..90.0 else value in -180.0..180.0
}

// Format a Double to display string
fun formatCoordinate(decimalDeg: Double, isLatitude: Boolean): String {
    val hem = when {
        isLatitude && decimalDeg >= 0 -> "N"
        isLatitude && decimalDeg < 0  -> "S"
        !isLatitude && decimalDeg >= 0 -> "E"
        else -> "W"
    }
    val absVal = kotlin.math.abs(decimalDeg)
    var deg = absVal.toInt()
    val min = (absVal - deg) * 60.0
    var minRounded = kotlin.math.round(min * 10) / 10.0
    if (minRounded >= 60.0) {
        deg += 1
        minRounded = 0.0
    }
    val degPadded = if (isLatitude) String.format(Locale.US, "%02d", deg) else String.format(Locale.US, "%03d", deg)
    return "$hem$degPadded°${String.format(Locale.US, "%04.1f", minRounded)}"
}
