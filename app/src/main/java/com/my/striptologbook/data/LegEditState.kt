package com.my.striptologbook.data

import com.my.striptologbook.utils.getDateAtMidnight

/**
 * UI state for the Leg Edit screen.
 * All timestamps are stored as milliseconds since epoch (UTC).
 * Time strings are stored as "HH:MM" format.
 */
data class LegEditState(
    // Edit mode
    val legId: Long? = null,

    // Base date (OffBlock date at midnight UTC)
    val baseDate: Long? = null,

    // Text fields
    val position: String = "",
    val picName: String = "",
    val sicName: String = "",
    val jumpseat1: String = "",
    val jumpseat2: String = "",
    val cabinCrew: String = "",
    val acRegistration: String = "",
    val acType: String = "",
    val flightNumber: String = "",
    val category: String = "",
    val icaoDeparture: String = "",
    val icaoDestination: String = "",

    // Time strings (HH:MM format)
    val offBlockTime: String = "",
    val takeoffTime: String = "",
    val landingTime: String = "",
    val onBlockTime: String = "",
    val sunriseTime: String = "",
    val sunsetTime: String = "",

    // Computed timestamps (read-only in UI)
    val offBlock: Long? = null,
    val takeoff: Long? = null,
    val landing: Long? = null,
    val onBlock: Long? = null,
    val sunrise: Long? = null,
    val sunset: Long? = null,

    // Duration fields (strings for UI, will be parsed later)
    val blockTimeDay: String = "",
    val blockTimeNight: String = "",
    val blockTimeTotal: String = "",
    val flightTimeDay: String = "",
    val flightTimeNight: String = "",
    val flightTimeTotal: String = "",
    val simulatorTime: String = "",
    val ifrDay: String = "",
    val ifrNight: String = "",
    val ifrTotal: String = "",

    // Number fields (strings for UI)
    val numberInstrumentApproaches: String = "",
    val numberToDay: String = "",
    val numberToNight: String = "",
    val numberToTotal: String = "",
    val numberLdgDay: String = "",
    val numberLdgNight: String = "",
    val numberLdgTotal: String = "",

    // Boolean fields
    val isSimulator: Boolean? = null,
    val isPilotFlying: Boolean? = null,
    val isIfr: Boolean = true,  // Default true, UI only, not stored in DB

    // Comment
    val remarks: String = ""
) {
    companion object {
        fun fromLeg(leg: Leg): LegEditState {
            val baseDate = leg.offBlock?.let { getDateAtMidnight(it) }

            return LegEditState(
                legId = leg.id,
                baseDate = baseDate,
                position = leg.position ?: "",
                picName = leg.picName ?: "",
                sicName = leg.sicName ?: "",
                jumpseat1 = leg.jumpseat1 ?: "",
                jumpseat2 = leg.jumpseat2 ?: "",
                cabinCrew = leg.cabinCrew ?: "",
                acRegistration = leg.acRegistration ?: "",
                acType = leg.acType ?: "",
                flightNumber = leg.flightNumber ?: "",
                category = leg.category ?: "",
                icaoDeparture = leg.icaoDeparture ?: "",
                icaoDestination = leg.icaoDestination ?: "",
                offBlockTime = extractTimeString(leg.offBlock),
                takeoffTime = extractTimeString(leg.takeoff),
                landingTime = extractTimeString(leg.landing),
                onBlockTime = extractTimeString(leg.onBlock),
                sunriseTime = extractTimeString(leg.sunrise),
                sunsetTime = extractTimeString(leg.sunset),
                offBlock = leg.offBlock,
                takeoff = leg.takeoff,
                landing = leg.landing,
                onBlock = leg.onBlock,
                sunrise = leg.sunrise,
                sunset = leg.sunset,
                blockTimeDay = leg.blockTimeDay?.let { formatDurationString(it) } ?: "",
                blockTimeNight = leg.blockTimeNight?.let { formatDurationString(it) } ?: "",
                blockTimeTotal = leg.blockTimeTotal?.let { formatDurationString(it) } ?: "",
                flightTimeDay = leg.flightTimeDay?.let { formatDurationString(it) } ?: "",
                flightTimeNight = leg.flightTimeNight?.let { formatDurationString(it) } ?: "",
                flightTimeTotal = leg.flightTimeTotal?.let { formatDurationString(it) } ?: "",
                simulatorTime = leg.simulatorTime?.let { formatDurationString(it) } ?: "",
                ifrDay = leg.ifrDay?.let { formatDurationString(it) } ?: "",
                ifrNight = leg.ifrNight?.let { formatDurationString(it) } ?: "",
                ifrTotal = leg.ifrTotal?.let { formatDurationString(it) } ?: "",
                numberInstrumentApproaches = leg.numberInstrumentApproaches?.toString() ?: "",
                numberToDay = leg.numberToDay?.toString() ?: "",
                numberToNight = leg.numberToNight?.toString() ?: "",
                numberToTotal = leg.numberToTotal?.toString() ?: "",
                numberLdgDay = leg.numberLdgDay?.toString() ?: "",
                numberLdgNight = leg.numberLdgNight?.toString() ?: "",
                numberLdgTotal = leg.numberLdgTotal?.toString() ?: "",
                isSimulator = leg.isSimulator,
                isPilotFlying = leg.isPilotFlying,
                isIfr = leg.ifrTotal != null || leg.ifrNight != null || leg.ifrDay != null,
                remarks = leg.remarks ?: ""
            )
        }

        private fun extractTimeString(timestamp: Long?): String {
            if (timestamp == null) return ""
            // Extract HH:MM from timestamp
            val minutes = ((timestamp % (24 * 60 * 60 * 1000L)) / (60 * 1000L)).toInt()
            val hours = minutes / 60
            val mins = minutes % 60
            return String.format("%02d:%02d", hours, mins)
        }

        private fun formatDurationString(minutes: Int): String {
            val hours = minutes / 60
            val mins = minutes % 60
            return "$hours:${String.format("%02d", mins)}"
        }
    }
}