package com.my.striptologbook.data

import kotlinx.serialization.Serializable

enum class TakeoffLandingMode { SKIP, ADD_MINUTES }
enum class IfrMode { SKIP, BLOCK_TIME, FLIGHT_TIME }
enum class NightMode { SKIP, REAL_NIGHT }
enum class PilotFlyingMode { SKIP, ALTERNATE }
enum class AcTypeMode { SKIP, USER_INPUT }
enum class CategoryMode { SKIP, USER_INPUT }

@Serializable
data class LegsCorrectionConfig(
    val takeoffLandingMode: TakeoffLandingMode = TakeoffLandingMode.ADD_MINUTES,
    val takeoffOffsetMinutes: Int = 15,
    val landingOffsetMinutes: Int = 10,

    val ifrMode: IfrMode = IfrMode.FLIGHT_TIME,
    val nightMode: NightMode = NightMode.REAL_NIGHT,
    val pilotFlyingMode: PilotFlyingMode = PilotFlyingMode.ALTERNATE,

    val acTypeMode: AcTypeMode = AcTypeMode.SKIP,
    val acTypeValue: String = "",
    val categoryMode: CategoryMode = CategoryMode.USER_INPUT,
    val categoryValue: String = ""
) {
    companion object {
        val DEFAULT = LegsCorrectionConfig()
    }
}