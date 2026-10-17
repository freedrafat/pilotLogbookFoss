package com.my.striptologbook.data

import android.content.Context
import com.my.striptologbook.utils.extractTimeOnly
import com.my.striptologbook.utils.formatDateOnly
import com.my.striptologbook.utils.formatDateUtc
import com.my.striptologbook.utils.formatDuration
import com.my.striptologbook.utils.formatTimestampIsoUtc
import java.util.Locale

/**
 * Renders one of the 4 rows of a leg card into a display string.
 *
 * Shared by:
 *  - LegAdapter (list rendering),
 *  - the Leg Items editor's preview card.
 *
 * Rules:
 *  - Literal items append their text verbatim (they carry all spacing).
 *  - Field items resolve their value through LegItemFields and format it
 *    with the renderer id stored on the item.
 *  - Null value -> "" (adjacent literals are untouched).
 *  - Unknown field id -> "" (the editor shows a warning banner instead).
 *  - Booleans use a fixed per-field rendering (no user choice).
 *  - Strings and numbers render as-is. Airport fields store ICAO codes
 *    and render them verbatim; no IATA lookup is performed.
 */
object LegRowRenderer {

    @Suppress("UNUSED_PARAMETER")
    fun renderRow(row: List<LegRowItem>, leg: Leg, context: Context): String {
        val sb = StringBuilder()
        for (item in row) {
            when (item.type) {
                LegRowItem.TYPE_FIELD   -> sb.append(renderField(item, leg))
                LegRowItem.TYPE_LITERAL -> sb.append(item.text ?: "")
                // Unknown types are silently skipped (already filtered by
                // LegItemLayout.normalize, but be defensive).
            }
        }
        return sb.toString()
    }

    // ---------------------------------------------------------------- fields

    private fun renderField(item: LegRowItem, leg: Leg): String {
        val field = LegItemFields.getById(item.fieldId) ?: return ""

        // Booleans: fixed rendering by field id, ignores any stored rendererId.
        if (field.type == "boolean") {
            return renderBoolean(item.fieldId, field.getValue(leg))
        }

        val value = field.getValue(leg) ?: return ""

        return when (field.type) {
            "string"    -> value as? String ?: ""
            "timestamp" -> renderTimestamp(value as? Long ?: return "", item.rendererId)
            "duration"  -> renderDuration(value as? Int ?: return "", item.rendererId)
            "number"    -> value.toString()
            else        -> ""
        }
    }

    // ---------------------------------------------------------------- boolean

    /**
     * Fixed rendering for the two Boolean fields on Leg. Null is treated
     * as false (agreed in the design).
     */
    private fun renderBoolean(fieldId: String?, value: Any?): String {
        val b = value as? Boolean ?: false
        return when (fieldId) {
            "isSimulator" -> if (b) "Simu." else ""
            "pilotFlying" -> if (b) "PF"    else "PM"
            else          -> ""
        }
    }

    // ---------------------------------------------------------------- timestamp

    private fun renderTimestamp(ts: Long, rendererId: String?): String = when (rendererId) {
        "date"     -> formatDateOnly(ts)
        "time"     -> extractTimeOnly(ts)
        "datetime" -> formatDateUtc(ts)
        "iso"      -> formatTimestampIsoUtc(ts)
        else       -> formatDateOnly(ts)   // safe default when unspecified
    }

    // ---------------------------------------------------------------- duration

    private fun renderDuration(minutes: Int, rendererId: String?): String = when (rendererId) {
        "decimalHours" -> formatDecimalHours(minutes)
        "minutes"      -> minutes.toString()
        else           -> formatDuration(minutes)   // "duration" / unspecified
    }

    /**
     * Formats a minute count as decimal hours, using %.2f then trimming
     * trailing zeros and the dot. Examples:
     *   105 -> "1.75"
     *   120 -> "2"
     *    30 -> "0.5"
     *     0 -> "0"
     */
    private fun formatDecimalHours(minutes: Int): String {
        val hours = minutes / 60.0
        val formatted = String.format(Locale.US, "%.2f", hours)
        val trimmed = formatted.trimEnd('0').trimEnd('.')
        return trimmed.ifEmpty { "0" }
    }
}