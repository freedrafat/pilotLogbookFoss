package com.my.striptologbook.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * Represents a block of flight experience accumulated BEFORE the app was
 * first used ("opening balance"). It is not a leg.
 *
 * A row may describe experience for one aircraft type / position / category
 * combination, or a single "last total line" copied from a paper logbook.
 *
 * Multiple rows are allowed. All rows are summed into one LegTotals when
 * an overall balance is needed (Leg List summary card with ALL filter,
 * PDF grand totals, Statistics Month/Year "Prior" row).
 *
 * All timestamps are stored as milliseconds since epoch (UTC).
 * All durations are stored as total minutes (Int?).
 * All fields are optional and editable.
 */
@Serializable
@Entity(tableName = "opening_balances")
data class OpeningBalance(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    // Period covered by this row (UTC midnight). Used for Month/Year sorting.
    val startDate: Long? = null,
    val endDate: Long? = null,

    // Identification
    val acType: String? = null,
    val position: String? = null,
    val category: String? = null,
    val comment: String? = null,

    // Block durations (minutes)
    val blockTimeTotal: Int? = null,
    val blockTimeNight: Int? = null,
    val blockTimeDay: Int? = null,

    // Flight durations (minutes)
    val flightTimeTotal: Int? = null,
    val flightTimeNight: Int? = null,
    val flightTimeDay: Int? = null,

    // IFR durations (minutes)
    val ifrTotal: Int? = null,
    val ifrNight: Int? = null,
    val ifrDay: Int? = null,

    // Simulator time (minutes)
    val simulatorTime: Int? = null,

    // Takeoffs
    val numberToDay: Int? = null,
    val numberToNight: Int? = null,
    val numberToTotal: Int? = null,

    // Landings
    val numberLdgDay: Int? = null,
    val numberLdgNight: Int? = null,
    val numberLdgTotal: Int? = null,

    // Instrument approaches
    val numberInstrumentApproaches: Int? = null,

    // Direct pilot-function / category times (minutes).
    // Used as-is when the row has no position/category (e.g. "last total line" entry).
    // Otherwise recalculated from blockTimeTotal + position/category on entry.
    val singlePilotSeTime: Int? = null,
    val singlePilotMeTime: Int? = null,
    val multiPilotTime: Int? = null,
    val pilotFunctionPicTime: Int? = null,
    val pilotFunctionSicTime: Int? = null,
    val pilotFunctionDualTime: Int? = null,
    val pilotFunctionInstructorTime: Int? = null
)