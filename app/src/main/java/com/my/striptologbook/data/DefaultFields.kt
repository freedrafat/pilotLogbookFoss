package com.my.striptologbook.data

/**
 * Values that a new, empty leg starts with.
 *
 * Existing fields (isIfr, simuIfrApproach, position) are preserved unchanged.
 * All new fields are additive and default to “no prefill”.
 */
data class DefaultFields(
    val isIfr: Boolean,
    val simuIfrApproach: Int,
    val position: String,

    val picName: String = "",
    val sicName: String = "",
    val jumpseat1: String = "",
    val jumpseat2: String = "",
    val cabinCrew: String = "",
    val acRegistration: String = "",
    val flightNumberPrefix: String = "",
    val icaoDeparture: String = "",
    val icaoDestination: String = "",
    val comment: String = "",
    val offBlockIsNow: Boolean = false,
    val isSimulatorDefault: String = "none",   // "none" | "true" | "false"
    val pilotFlyingDefault: String = "none"    // "none" | "true" | "false"
)