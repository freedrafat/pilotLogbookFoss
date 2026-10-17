package com.my.striptologbook.data

/**
 * Accumulates every numeric field of the Leg entity, plus the derived
 * per‑category and per‑position flight times used by the PDF logbook.
 *
 * A leg contributes to exactly one of singlePilotSeTime / singlePilotMeTime /
 * multiPilotTime (based on category) and to exactly one of pilotFunction*
 * (based on position). The other derived fields stay at 0 for that leg.
 */
data class LegTotals(
    val blockTimeDay: Int = 0,
    val blockTimeNight: Int = 0,
    val blockTimeTotal: Int = 0,
    val flightTimeDay: Int = 0,
    val flightTimeNight: Int = 0,
    val flightTimeTotal: Int = 0,
    val simulatorTime: Int = 0,
    val ifrDay: Int = 0,
    val ifrNight: Int = 0,
    val ifrTotal: Int = 0,
    val numberToDay: Int = 0,
    val numberToNight: Int = 0,
    val numberToTotal: Int = 0,
    val numberLdgDay: Int = 0,
    val numberLdgNight: Int = 0,
    val numberLdgTotal: Int = 0,
    val numberInstrumentApproaches: Int = 0,
    // Derived per‑category / per‑position flight times
    val singlePilotSeTime: Int = 0,
    val singlePilotMeTime: Int = 0,
    val multiPilotTime: Int = 0,
    val pilotFunctionPicTime: Int = 0,
    val pilotFunctionSicTime: Int = 0,
    val pilotFunctionDualTime: Int = 0,
    val pilotFunctionInstructorTime: Int = 0
) {
    operator fun plus(other: LegTotals) = LegTotals(
        blockTimeDay    = blockTimeDay    + other.blockTimeDay,
        blockTimeNight  = blockTimeNight  + other.blockTimeNight,
        blockTimeTotal  = blockTimeTotal  + other.blockTimeTotal,
        flightTimeDay   = flightTimeDay   + other.flightTimeDay,
        flightTimeNight = flightTimeNight + other.flightTimeNight,
        flightTimeTotal = flightTimeTotal + other.flightTimeTotal,
        simulatorTime   = simulatorTime   + other.simulatorTime,
        ifrDay          = ifrDay          + other.ifrDay,
        ifrNight        = ifrNight        + other.ifrNight,
        ifrTotal        = ifrTotal        + other.ifrTotal,
        numberToDay     = numberToDay     + other.numberToDay,
        numberToNight   = numberToNight   + other.numberToNight,
        numberToTotal   = numberToTotal   + other.numberToTotal,
        numberLdgDay    = numberLdgDay    + other.numberLdgDay,
        numberLdgNight  = numberLdgNight  + other.numberLdgNight,
        numberLdgTotal  = numberLdgTotal  + other.numberLdgTotal,
        numberInstrumentApproaches = numberInstrumentApproaches + other.numberInstrumentApproaches,
        singlePilotSeTime           = singlePilotSeTime           + other.singlePilotSeTime,
        singlePilotMeTime           = singlePilotMeTime           + other.singlePilotMeTime,
        multiPilotTime              = multiPilotTime              + other.multiPilotTime,
        pilotFunctionPicTime        = pilotFunctionPicTime        + other.pilotFunctionPicTime,
        pilotFunctionSicTime        = pilotFunctionSicTime        + other.pilotFunctionSicTime,
        pilotFunctionDualTime       = pilotFunctionDualTime       + other.pilotFunctionDualTime,
        pilotFunctionInstructorTime = pilotFunctionInstructorTime + other.pilotFunctionInstructorTime
    )

    /** Component‑wise subtraction (assumes result is non‑negative). */
    operator fun minus(other: LegTotals) = LegTotals(
        blockTimeDay    = blockTimeDay    - other.blockTimeDay,
        blockTimeNight  = blockTimeNight  - other.blockTimeNight,
        blockTimeTotal  = blockTimeTotal  - other.blockTimeTotal,
        flightTimeDay   = flightTimeDay   - other.flightTimeDay,
        flightTimeNight = flightTimeNight - other.flightTimeNight,
        flightTimeTotal = flightTimeTotal - other.flightTimeTotal,
        simulatorTime   = simulatorTime   - other.simulatorTime,
        ifrDay          = ifrDay          - other.ifrDay,
        ifrNight        = ifrNight        - other.ifrNight,
        ifrTotal        = ifrTotal        - other.ifrTotal,
        numberToDay     = numberToDay     - other.numberToDay,
        numberToNight   = numberToNight   - other.numberToNight,
        numberToTotal   = numberToTotal   - other.numberToTotal,
        numberLdgDay    = numberLdgDay    - other.numberLdgDay,
        numberLdgNight  = numberLdgNight  - other.numberLdgNight,
        numberLdgTotal  = numberLdgTotal  - other.numberLdgTotal,
        numberInstrumentApproaches = numberInstrumentApproaches - other.numberInstrumentApproaches,
        singlePilotSeTime           = singlePilotSeTime           - other.singlePilotSeTime,
        singlePilotMeTime           = singlePilotMeTime           - other.singlePilotMeTime,
        multiPilotTime              = multiPilotTime              - other.multiPilotTime,
        pilotFunctionPicTime        = pilotFunctionPicTime        - other.pilotFunctionPicTime,
        pilotFunctionSicTime        = pilotFunctionSicTime        - other.pilotFunctionSicTime,
        pilotFunctionDualTime       = pilotFunctionDualTime       - other.pilotFunctionDualTime,
        pilotFunctionInstructorTime = pilotFunctionInstructorTime - other.pilotFunctionInstructorTime
    )

    companion object {
        fun fromLeg(leg: Leg): LegTotals {
            val block    = leg.blockTimeTotal ?: 0
            val category = leg.category
            val position = leg.position

            return LegTotals(
                blockTimeDay    = leg.blockTimeDay    ?: 0,
                blockTimeNight  = leg.blockTimeNight  ?: 0,
                blockTimeTotal  = leg.blockTimeTotal  ?: 0,
                flightTimeDay   = leg.flightTimeDay   ?: 0,
                flightTimeNight = leg.flightTimeNight ?: 0,
                flightTimeTotal = leg.flightTimeTotal ?: 0,
                simulatorTime   = leg.simulatorTime   ?: 0,
                ifrDay          = leg.ifrDay          ?: 0,
                ifrNight        = leg.ifrNight        ?: 0,
                ifrTotal        = leg.ifrTotal        ?: 0,
                numberToDay     = leg.numberToDay     ?: 0,
                numberToNight   = leg.numberToNight   ?: 0,
                numberToTotal   = leg.numberToTotal   ?: 0,
                numberLdgDay    = leg.numberLdgDay    ?: 0,
                numberLdgNight  = leg.numberLdgNight  ?: 0,
                numberLdgTotal  = leg.numberLdgTotal  ?: 0,
                numberInstrumentApproaches = leg.numberInstrumentApproaches ?: 0,
                singlePilotSeTime = if (category == "Single Pilot SE") block else 0,
                singlePilotMeTime = if (category == "Single Pilot ME") block else 0,
                multiPilotTime    = if (category == "Multi Pilot")     block else 0,
                pilotFunctionPicTime = if (position in setOf("PIC", "SPIC", "PICUS")) block else 0,
                pilotFunctionSicTime = if (position == "SIC")        block else 0,
                pilotFunctionDualTime = if (position == "Dual")      block else 0,
                pilotFunctionInstructorTime = if (position == "Instructor") block else 0
            )
        }
    }
}