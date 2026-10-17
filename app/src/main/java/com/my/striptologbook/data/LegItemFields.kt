package com.my.striptologbook.data

/**
 * One selectable renderer for a field in the Leg Items editor.
 * Fields with an empty renderer list have a single fixed rendering
 * (see LegRowRenderer) and are shown without a renderer dropdown.
 */
data class RendererOption(
    val id: String,
    val displayName: String
)

/**
 * Decorates a SearchField with presentation metadata:
 *  - which renderers the user can choose from (empty = no choice),
 *  - the value accessor (delegated to SearchField).
 *
 * This registry is a 1:1 mapping over SearchFields.allFields - no extra
 * fields, no derived/combination fields.
 */
data class LegItemField(
    val id: String,
    val displayName: String,
    val type: String,                 // "string" | "boolean" | "timestamp" | "duration" | "number"
    val renderers: List<RendererOption>,
    val getValue: (Leg) -> Any?
)

object LegItemFields {

    private val TIMESTAMP_RENDERERS = listOf(
        RendererOption("date",     "Date"),
        RendererOption("time",     "Time"),
        RendererOption("datetime", "Date + Time"),
        RendererOption("iso",      "ISO 8601")
    )

    private val DURATION_RENDERERS = listOf(
        RendererOption("duration",     "H:MM"),
        RendererOption("decimalHours", "Decimal hours"),
        RendererOption("minutes",      "Minutes")
    )

    /**
     * All fields, in the same order as SearchFields.allFields.
     * The editor's field picker reads this list directly.
     *
     * Note: airport fields (icaoDeparture / icaoDestination) intentionally
     * have no renderer choice. The database stores ICAO codes and there is
     * no IATA fallback in the Leg Items feature.
     */
    val all: List<LegItemField> = SearchFields.allFields.map { sf ->
        val renderers = when (sf.type) {
            "timestamp" -> TIMESTAMP_RENDERERS
            "duration"  -> DURATION_RENDERERS
            else        -> emptyList()
        }
        LegItemField(
            id = sf.name,
            displayName = sf.displayName,
            type = sf.type,
            renderers = renderers,
            getValue = { leg -> sf.getValue(leg) }
        )
    }

    private val byId: Map<String, LegItemField> = all.associateBy { it.id }

    fun getById(id: String?): LegItemField? = id?.let { byId[it] }

    /** Convenience for the editor's unknown-field warning banner. */
    val knownIds: Set<String> = byId.keys
}