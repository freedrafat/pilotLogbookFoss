package com.my.striptologbook.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable



/**
 * Represents a single flight leg (logbook entry).
 *
 * All timestamps are stored as milliseconds since epoch (UTC).
 * All durations are stored as total minutes (Int).
 * Nullable fields are optional; only offBlock and onBlock are required.
 */
@Serializable
@Entity(tableName = "legs")
data class Leg(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,                     // Auto‑generated primary key

    // Text fields (all optional, hence nullable)
    val position: String? = null,
    val picName: String? = null,
    val sicName: String? = null,
    val jumpseat1: String? = null,
    val jumpseat2: String? = null,
    val cabinCrew: String? = null,
    val acRegistration: String? = null,
    val acType: String? = null,
    val flightNumber: String? = null,
    val category: String? = null,
    val icaoDeparture: String? = null,
    val icaoDestination: String? = null,

    // Booleans (optional – use null for "not set")
    val isSimulator: Boolean? = null,
    val pilotFlying: Boolean? = null,

    // Optional timestamp fields
    val offBlock: Long?,   // UTC milliseconds
    val takeoffTime: Long? = null,
    val landingTime: Long? = null,
    val onBlock: Long?,    // UTC milliseconds
    val sunrise: Long? = null,
    val sunset: Long? = null,

    // Durations stored as total minutes (nullable)
    val blockTimeDay: Int? = null,
    val blockTimeNight: Int? = null,
    val blockTimeTotal: Int? = null,
    val flightTimeDay: Int? = null,
    val flightTimeNight: Int? = null,
    val flightTimeTotal: Int? = null,
    val simulatorTime: Int? = null,
    val ifrDay: Int? = null,
    val ifrNight: Int? = null,
    val ifrTotal: Int? = null,

    // Integer counters
    val numberToDay: Int? = null,
    val numberToNight: Int? = null,
    val numberToTotal: Int? = null,
    val numberLdgDay: Int? = null,
    val numberLdgNight: Int? = null,
    val numberLdgTotal: Int? = null,

    // Instrument approaches (ICAO/EASA + FAA)
    val numberInstrumentApproaches: Int? = null,

    // Free‑text comment
    val comment: String? = null
)