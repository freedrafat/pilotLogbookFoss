package com.my.striptologbook.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * One item inside one of the 4 rows of the leg card.
 *
 * Either a reference to a Leg field (with an optional renderer), or a literal
 * string the user typed. Literals carry all spacing explicitly - we never
 * inject automatic separators.
 */
@Serializable
data class LegRowItem(
    val type: String,                 // "field" | "literal"
    val fieldId: String? = null,
    val rendererId: String? = null,
    val text: String? = null
) {
    companion object {
        const val TYPE_FIELD = "field"
        const val TYPE_LITERAL = "literal"

        fun field(fieldId: String, rendererId: String? = null) =
            LegRowItem(type = TYPE_FIELD, fieldId = fieldId, rendererId = rendererId)

        fun literal(text: String) =
            LegRowItem(type = TYPE_LITERAL, text = text)
    }
}

/**
 * One named layout profile. The built-in "Default" profile is flagged with
 * builtIn = true and is treated as read-only by the editor.
 */
@Serializable
data class LegProfile(
    val name: String,
    val builtIn: Boolean = false,
    val rows: List<List<LegRowItem>>
)

/**
 * The full persisted layout: a set of named profiles plus the name of the
 * currently active one.
 */
@Serializable
data class LegItemLayout(
    val activeProfile: String,
    val profiles: Map<String, LegProfile>
) {
    companion object {
        const val DEFAULT_PROFILE_NAME = "Default"
        const val ROW_COUNT = 4
        const val MAX_LITERAL_LENGTH = 10

        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        /**
         * The built-in Default profile, identical to the original hardcoded
         * layout of item_leg.xml.
         */
        fun getDefaultProfile(): LegProfile {
            val sp = LegRowItem.literal("  ")
            return LegProfile(
                name = DEFAULT_PROFILE_NAME,
                builtIn = true,
                rows = listOf(
                    // Row 1: offBlock(date)  position  acRegistration  acType
                    listOf(
                        LegRowItem.field("offBlock", "date"),
                        sp,
                        LegRowItem.field("position"),
                        sp,
                        LegRowItem.field("acRegistration"),
                        sp,
                        LegRowItem.field("acType")
                    ),
                    // Row 2: flightNumber  icaoDeparture(icao)  offBlock(time)  onBlock(time)  icaoDestination(icao)
                    listOf(
                        LegRowItem.field("flightNumber"),
                        sp,
                        LegRowItem.field("icaoDeparture", "icao"),
                        sp,
                        LegRowItem.field("offBlock", "time"),
                        sp,
                        LegRowItem.field("onBlock", "time"),
                        sp,
                        LegRowItem.field("icaoDestination", "icao")
                    ),
                    // Row 3: "BT/night: " blockTimeTotal(duration) " / " blockTimeNight(duration)
                    listOf(
                        LegRowItem.literal("BT/night: "),
                        LegRowItem.field("blockTimeTotal", "duration"),
                        LegRowItem.literal(" / "),
                        LegRowItem.field("blockTimeNight", "duration")
                    ),
                    // Row 4: comment
                    listOf(
                        LegRowItem.field("comment")
                    )
                )
            )
        }

        fun getDefaultLayout(): LegItemLayout = LegItemLayout(
            activeProfile = DEFAULT_PROFILE_NAME,
            profiles = mapOf(DEFAULT_PROFILE_NAME to getDefaultProfile())
        )

        fun toJson(layout: LegItemLayout): String =
            json.encodeToString(LegItemLayout.serializer(), layout)

        /**
         * Parses persisted JSON. Never throws - falls back to the built-in
         * Default layout on any error. Also normalises the result so callers
         * can trust the shape of what they get back.
         */
        fun fromJson(raw: String?): LegItemLayout {
            if (raw.isNullOrBlank()) return getDefaultLayout()
            val parsed = try {
                json.decodeFromString(LegItemLayout.serializer(), raw)
            } catch (_: Exception) {
                return getDefaultLayout()
            }
            return normalize(parsed)
        }

        /**
         * Guarantees:
         *  - "Default" is always present with the built-in contents (overrides
         *    any user-made contents under that name).
         *  - Every user profile has exactly ROW_COUNT rows.
         *  - Literal items are trimmed to MAX_LITERAL_LENGTH.
         *  - Items with unknown types, or field items with a blank fieldId,
         *    are dropped.
         *  - activeProfile points to an existing profile (else Default).
         */
        private fun normalize(layout: LegItemLayout): LegItemLayout {
            val normalized = linkedMapOf<String, LegProfile>()
            normalized[DEFAULT_PROFILE_NAME] = getDefaultProfile()
            for ((name, profile) in layout.profiles) {
                if (name == DEFAULT_PROFILE_NAME) continue
                normalized[name] = normalizeProfile(profile, builtIn = false)
            }
            val active = if (layout.activeProfile in normalized) {
                layout.activeProfile
            } else {
                DEFAULT_PROFILE_NAME
            }
            return LegItemLayout(activeProfile = active, profiles = normalized)
        }

        private fun normalizeProfile(profile: LegProfile, builtIn: Boolean): LegProfile {
            val normalizedRows = (0 until ROW_COUNT).map { index ->
                profile.rows.getOrNull(index).orEmpty()
                    .mapNotNull { normalizeItem(it) }
            }
            return LegProfile(name = profile.name, builtIn = builtIn, rows = normalizedRows)
        }

        private fun normalizeItem(item: LegRowItem): LegRowItem? = when (item.type) {
            LegRowItem.TYPE_FIELD -> {
                val fieldId = item.fieldId?.takeIf { it.isNotBlank() } ?: return null
                LegRowItem(
                    type = LegRowItem.TYPE_FIELD,
                    fieldId = fieldId,
                    rendererId = item.rendererId?.takeIf { it.isNotBlank() }
                )
            }
            LegRowItem.TYPE_LITERAL -> {
                val text = item.text ?: ""
                LegRowItem(
                    type = LegRowItem.TYPE_LITERAL,
                    text = if (text.length > MAX_LITERAL_LENGTH) text.take(MAX_LITERAL_LENGTH) else text
                )
            }
            else -> null
        }
    }

    /** The currently active profile, or the built-in Default if missing. */
    fun getActiveProfile(): LegProfile =
        profiles[activeProfile] ?: getDefaultProfile()
}

/**
 * True when the profile references any field id not present in [knownFieldIds].
 * Used by the editor to show the "unknown fields" warning banner.
 */
fun LegProfile.hasUnknownFields(knownFieldIds: Set<String>): Boolean =
    rows.any { row ->
        row.any { item ->
            item.type == LegRowItem.TYPE_FIELD &&
                    item.fieldId != null &&
                    item.fieldId !in knownFieldIds
        }
    }