package com.my.striptologbook.utils

import com.my.striptologbook.data.AcTypeMode
import com.my.striptologbook.data.AirportDataManager
import com.my.striptologbook.data.CategoryMode
import com.my.striptologbook.data.IfrMode
import com.my.striptologbook.data.Leg
import com.my.striptologbook.data.LegsCorrectionConfig
import com.my.striptologbook.data.NightMode
import com.my.striptologbook.data.PilotFlyingMode
import com.my.striptologbook.data.TakeoffLandingMode
import java.util.Date

object LegsCorrectionEngine {

    data class CorrectionResult(
        val original: Leg,
        val corrected: Leg,
        val hasChanges: Boolean
    )

    fun applyCorrections(
        legs: List<Leg>,
        config: LegsCorrectionConfig,
        overrideExisting: Boolean,
        defaultSimuIfrApproach: Int,
        airportManager: AirportDataManager,
        defaultAltitude: Int
    ): List<CorrectionResult> {
        return legs.mapIndexed { index, leg ->
            val isSim = leg.isSimulator == true || (leg.simulatorTime != null && leg.simulatorTime > 0)

            if (isSim) {
                correctSimulatorLeg(leg, config, overrideExisting, defaultSimuIfrApproach)
            } else {
                correctFlightLeg(leg, index, config, overrideExisting, airportManager, defaultAltitude)
            }
        }
    }

    private fun correctSimulatorLeg(
        leg: Leg,
        config: LegsCorrectionConfig,
        overrideExisting: Boolean,
        defaultSimuIfrApproach: Int
    ): CorrectionResult {
        var correctedLeg = leg.copy(isSimulator = true)

        if (config.acTypeMode == AcTypeMode.USER_INPUT && (overrideExisting || correctedLeg.acType == null)) {
            correctedLeg = correctedLeg.copy(acType = config.acTypeValue)
        }
        if (config.categoryMode == CategoryMode.USER_INPUT && (overrideExisting || correctedLeg.category == null)) {
            correctedLeg = correctedLeg.copy(category = config.categoryValue)
        }
                if (correctedLeg.simulatorTime == null) {
            if (correctedLeg.blockTimeTotal != null) {
                correctedLeg = correctedLeg.copy(simulatorTime = correctedLeg.blockTimeTotal)
            } else if (correctedLeg.offBlock != null && correctedLeg.onBlock != null) {
                val diffMinutes = ((correctedLeg.onBlock - correctedLeg.offBlock) / 60_000L).toInt()
                correctedLeg = correctedLeg.copy(simulatorTime = diffMinutes)
            }
        }

        correctedLeg = correctedLeg.copy(
            blockTimeTotal = null, blockTimeDay = null, blockTimeNight = null,
            takeoff = null, landing = null,
            flightTimeTotal = null, flightTimeDay = null, flightTimeNight = null,
            ifrTotal = null, ifrDay = null, ifrNight = null,
            sunrise = null, sunset = null,
            isPilotFlying = null
        )

        val isPicOrSic = correctedLeg.position == "PIC" || correctedLeg.position == "SIC"
        if (isPicOrSic) {
            if (correctedLeg.numberInstrumentApproaches == null || correctedLeg.numberInstrumentApproaches == 0) {
                correctedLeg = correctedLeg.copy(numberInstrumentApproaches = defaultSimuIfrApproach)
            }
        }

        return CorrectionResult(leg, correctedLeg, leg != correctedLeg)
    }

    private fun correctFlightLeg(
        leg: Leg,
        index: Int,
        config: LegsCorrectionConfig,
        overrideExisting: Boolean,
        airportManager: AirportDataManager,
        defaultAltitude: Int
    ): CorrectionResult {
        var correctedLeg = leg

        // --- AcType & Category ---
        if (config.acTypeMode == AcTypeMode.USER_INPUT && (overrideExisting || correctedLeg.acType == null)) {
            correctedLeg = correctedLeg.copy(acType = config.acTypeValue)
        }
        if (config.categoryMode == CategoryMode.USER_INPUT && (overrideExisting || correctedLeg.category == null)) {
            correctedLeg = correctedLeg.copy(category = config.categoryValue)
        }

        // 1. Takeoff / Landing (ADD_MINUTES)
        if (config.takeoffLandingMode == TakeoffLandingMode.ADD_MINUTES) {
            if (overrideExisting || (leg.takeoff == null && leg.landing == null)) {
                val offBlock = leg.offBlock
                val onBlock = leg.onBlock
                val blockTotal = leg.blockTimeTotal ?: if (offBlock != null && onBlock != null) {
                    ((onBlock - offBlock) / 60_000L).toInt()
                } else null

                if (offBlock != null && onBlock != null && blockTotal != null) {
                    val requiredMinutes = config.takeoffOffsetMinutes + config.landingOffsetMinutes
                    if (blockTotal > requiredMinutes) {
                        val newTakeoff = offBlock + (config.takeoffOffsetMinutes * 60_000L)
                        val newLanding = onBlock - (config.landingOffsetMinutes * 60_000L)
                        val flightTotal = ((newLanding - newTakeoff) / 60_000L).toInt()

                        correctedLeg = correctedLeg.copy(
                            takeoff = newTakeoff,
                            landing = newLanding,
                            flightTimeTotal = flightTotal,
                            blockTimeTotal = blockTotal
                        )
                    } else {
                        val newRemarks = if (leg.remarks.isNullOrBlank()) {
                            "flight time not corrected"
                        } else {
                            "${leg.remarks}, flight time not corrected"
                        }
                        correctedLeg = correctedLeg.copy(remarks = newRemarks)
                    }
                }
            }
        }

        // 2. Pilot Flying (ALTERNATE)
        if (config.pilotFlyingMode == PilotFlyingMode.ALTERNATE) {
            if (overrideExisting || leg.isPilotFlying == null) {
                val isAlternateTrue = (index % 2 == 0)
                correctedLeg = correctedLeg.copy(isPilotFlying = isAlternateTrue)
            }
        }

        // 3. Instructor / SELF Overrides
        if (correctedLeg.position == "Instructor") {
            if (correctedLeg.isPilotFlying != false) {
                correctedLeg = correctedLeg.copy(isPilotFlying = false)
            }
        }
        val isSelfInJumpseat = correctedLeg.jumpseat1?.contains("SELF", ignoreCase = true) == true ||
                correctedLeg.jumpseat2?.contains("SELF", ignoreCase = true) == true
        if (isSelfInJumpseat) {
            if (correctedLeg.isPilotFlying != false) {
                correctedLeg = correctedLeg.copy(isPilotFlying = false)
            }
        }

        // 4. IFR Approaches
        if (config.ifrMode != IfrMode.SKIP && correctedLeg.isPilotFlying == true) {
            if (overrideExisting || correctedLeg.numberInstrumentApproaches == null) {
                correctedLeg = correctedLeg.copy(numberInstrumentApproaches = 1)
            }
        }

        // 5. Night (REAL_NIGHT)
        if (config.nightMode == NightMode.REAL_NIGHT) {
            if (overrideExisting || correctedLeg.blockTimeNight == null) {
                val res = calculateRealNight(correctedLeg, airportManager, defaultAltitude)

                if (res.blockNightMinutes != null) {
                    correctedLeg = correctedLeg.copy(blockTimeNight = res.blockNightMinutes)
                }
                if (res.flightNightMinutes != null) {
                    correctedLeg = correctedLeg.copy(flightTimeNight = res.flightNightMinutes)
                }
                if (res.sunrise != null || res.sunset != null) {
                    correctedLeg = correctedLeg.copy(sunrise = res.sunrise, sunset = res.sunset)
                }
            }
        }

        // 6. IFR
        if (config.ifrMode != IfrMode.SKIP) {
            if (overrideExisting || leg.ifrTotal == null) {
                if (config.ifrMode == IfrMode.BLOCK_TIME && correctedLeg.blockTimeTotal != null) {
                    correctedLeg = correctedLeg.copy(
                        ifrTotal = correctedLeg.blockTimeTotal,
                        ifrDay = correctedLeg.blockTimeDay,
                        ifrNight = correctedLeg.blockTimeNight
                    )
                } else if (config.ifrMode == IfrMode.FLIGHT_TIME && correctedLeg.flightTimeTotal != null) {
                    correctedLeg = correctedLeg.copy(
                        ifrTotal = correctedLeg.flightTimeTotal,
                        ifrDay = correctedLeg.flightTimeDay,
                        ifrNight = correctedLeg.flightTimeNight
                    )
                }
            }
        }

        // 7. Deduce Day Fields (Total - Night)
        if (correctedLeg.blockTimeTotal != null && correctedLeg.blockTimeNight != null) {
            if (overrideExisting || correctedLeg.blockTimeDay == null) {
                correctedLeg = correctedLeg.copy(blockTimeDay = correctedLeg.blockTimeTotal - correctedLeg.blockTimeNight)
            }
        }
        if (correctedLeg.flightTimeTotal != null && correctedLeg.flightTimeNight != null) {
            if (overrideExisting || correctedLeg.flightTimeDay == null) {
                correctedLeg = correctedLeg.copy(flightTimeDay = correctedLeg.flightTimeTotal - correctedLeg.flightTimeNight)
            }
        }
        if (correctedLeg.ifrTotal != null && correctedLeg.ifrNight != null) {
            if (overrideExisting || correctedLeg.ifrDay == null) {
                correctedLeg = correctedLeg.copy(ifrDay = correctedLeg.ifrTotal - correctedLeg.ifrNight)
            }
        }

        // 8. Fill Counters if isPilotFlying is true
        if (correctedLeg.isPilotFlying == true) {
            val legTakeoff = correctedLeg.takeoff
            val legLanding = correctedLeg.landing
            val depAirport = correctedLeg.icaoDeparture?.let { airportManager.findAirportByCode(it) }
            val arrAirport = correctedLeg.icaoDestination?.let { airportManager.findAirportByCode(it) }

            if (config.nightMode != NightMode.SKIP && legTakeoff != null && legLanding != null && depAirport != null && arrAirport != null) {
                val depElevM = depAirport.elevation * 0.3048
                val arrElevM = arrAirport.elevation * 0.3048

                val isToNight = isNight(depAirport.latitude, depAirport.longitude, depElevM, legTakeoff)
                val isLdgNight = isNight(arrAirport.latitude, arrAirport.longitude, arrElevM, legLanding)

                if (overrideExisting || correctedLeg.numberToTotal == null) {
                    correctedLeg = correctedLeg.copy(
                        numberToDay = if (!isToNight) 1 else null,
                        numberToNight = if (isToNight) 1 else null,
                        numberToTotal = 1
                    )
                }
                if (overrideExisting || correctedLeg.numberLdgTotal == null) {
                    correctedLeg = correctedLeg.copy(
                        numberLdgDay = if (!isLdgNight) 1 else null,
                        numberLdgNight = if (isLdgNight) 1 else null,
                        numberLdgTotal = 1
                    )
                }
            } else {
                if (overrideExisting || correctedLeg.numberToTotal == null) {
                    correctedLeg = correctedLeg.copy(numberToTotal = 1)
                }
                if (overrideExisting || correctedLeg.numberLdgTotal == null) {
                    correctedLeg = correctedLeg.copy(numberLdgTotal = 1)
                }
            }
        }

        return CorrectionResult(leg, correctedLeg, leg != correctedLeg)
    }

    private data class NightResult(
        val blockNightMinutes: Int?,
        val flightNightMinutes: Int?,
        val sunrise: Long?,
        val sunset: Long?
    )

    private fun calculateRealNight(
        leg: Leg,
        airportManager: AirportDataManager,
        defaultAltitude: Int
    ): NightResult {
        val offBlock = leg.offBlock ?: return NightResult(null, null, null, null)
        val onBlock = leg.onBlock ?: return NightResult(null, null, null, null)
        val depIcao = leg.icaoDeparture ?: return NightResult(null, null, null, null)
        val arrIcao = leg.icaoDestination ?: return NightResult(null, null, null, null)

        val depAirport = airportManager.findAirportByCode(depIcao) ?: return NightResult(null, null, null, null)
        val arrAirport = airportManager.findAirportByCode(arrIcao) ?: return NightResult(null, null, null, null)

        val depElevM = depAirport.elevation * 0.3048
        val arrElevM = arrAirport.elevation * 0.3048
        val altM = defaultAltitude * 0.3048

        val takeoff = leg.takeoff
        val landing = leg.landing
        val isSimulatedTakeoff = takeoff == null
        val actualTakeoff = takeoff ?: offBlock
        val actualLanding = landing ?: onBlock

        var nightTimeDeparture = 0L
        var flightTimeNight = 0L
        var nightTimeDestination = 0L
        val allEvents = mutableListOf<MovingSunriseCalculator.SunEvent>()

        // --- 1. DEPARTURE ---
        val isOffBlockNight = isNight(depAirport.latitude, depAirport.longitude, depElevM, offBlock)

        if (!isSimulatedTakeoff) {
            // Determine takeoff at elevation
            val isTakeoffNightElev = isNight(depAirport.latitude, depAirport.longitude, depElevM, actualTakeoff)

            // If offblock and takeoff are opposite
            if (isOffBlockNight != isTakeoffNightElev) {
                val crossTime = findCrossingTime(depAirport.latitude, depAirport.longitude, depElevM, offBlock, actualTakeoff)
                val type = if (isOffBlockNight) "Sunrise" else "Sunset"
                allEvents.add(MovingSunriseCalculator.SunEvent(Date(crossTime), depAirport.latitude, depAirport.longitude, type))
                nightTimeDeparture = if (isOffBlockNight) crossTime - offBlock else actualTakeoff - crossTime
            } else {
                // No crossing
                if (isOffBlockNight) {
                    nightTimeDeparture = actualTakeoff - offBlock
                }
            }
        }

        // --- 2. FLIGHT ---
        val events = try {
            MovingSunriseCalculator.computeEvents(airportManager, depIcao, arrIcao, actualTakeoff, actualLanding, altM)
        } catch (e: Exception) {
            emptyList()
        }
        val validEvents = events.filter { it.timeUTC.time in actualTakeoff..actualLanding }
        allEvents.addAll(validEvents)

        if (!isSimulatedTakeoff) {
            // Determine takeoff at cruise altitude
            val isTakeoffNightCruise = isNight(depAirport.latitude, depAirport.longitude, altM, actualTakeoff)
            val isTakeoffNightElev = isNight(depAirport.latitude, depAirport.longitude, depElevM, actualTakeoff)

            // If opposite, consider sun event occurs on takeoff time
            if (isTakeoffNightElev != isTakeoffNightCruise) {
                val type = if (isTakeoffNightElev) "Sunrise" else "Sunset"
                allEvents.add(MovingSunriseCalculator.SunEvent(Date(actualTakeoff), depAirport.latitude, depAirport.longitude, type))
            }
        }

        flightTimeNight = calculateNightDuration(
            actualTakeoff, actualLanding, validEvents,
            isNight(depAirport.latitude, depAirport.longitude, altM, actualTakeoff)
        )

        // --- 3. DESTINATION ---
        if (!isSimulatedTakeoff) {
            val isLandingNightElev = isNight(arrAirport.latitude, arrAirport.longitude, arrElevM, actualLanding)
            val isLandingNightCruise = isNight(arrAirport.latitude, arrAirport.longitude, altM, actualLanding)

            // If opposite, consider sun event occurs on landing time
            if (isLandingNightElev != isLandingNightCruise) {
                val type = if (isLandingNightElev) "Sunrise" else "Sunset"
                allEvents.add(MovingSunriseCalculator.SunEvent(Date(actualLanding), arrAirport.latitude, arrAirport.longitude, type))
            }

            val isOnBlockNight = isNight(arrAirport.latitude, arrAirport.longitude, arrElevM, onBlock)

            // If onblock and landing are opposite
            if (isLandingNightElev != isOnBlockNight) {
                val crossTime = findCrossingTime(arrAirport.latitude, arrAirport.longitude, arrElevM, actualLanding, onBlock)
                val type = if (isLandingNightElev) "Sunrise" else "Sunset"
                allEvents.add(MovingSunriseCalculator.SunEvent(Date(crossTime), arrAirport.latitude, arrAirport.longitude, type))
                nightTimeDestination = if (isLandingNightElev) crossTime - actualLanding else onBlock - crossTime
            } else {
                if (isLandingNightElev) {
                    nightTimeDestination = onBlock - actualLanding
                }
            }
        }

        // --- 4. TOTAL ---
        val totalBlockNightMs = if (isSimulatedTakeoff) {
            flightTimeNight
        } else {
            nightTimeDeparture + flightTimeNight + nightTimeDestination
        }
        val blockNightMins = (totalBlockNightMs / 60_000L).toInt()

        var flightNightMins: Int? = null
        if (!isSimulatedTakeoff) {
            flightNightMins = (flightTimeNight / 60_000L).toInt()
        }

        // --- 5. SUNRISE/SUNSET FILL ---
        var newSunrise: Long? = null
        var newSunset: Long? = null
        if (allEvents.size == 1) {
            val e = allEvents[0]
            if (e.type == "Sunrise") newSunrise = e.timeUTC.time
            else if (e.type == "Sunset") newSunset = e.timeUTC.time
        } else if (allEvents.size == 2) {
            val sunrise = allEvents.find { it.type == "Sunrise" }
            val sunset = allEvents.find { it.type == "Sunset" }
            if (sunrise != null && sunset != null) {
                newSunrise = sunrise.timeUTC.time
                newSunset = sunset.timeUTC.time
            }
        }

        return NightResult(blockNightMins, flightNightMins, newSunrise, newSunset)
    }

    private fun isNight(lat: Double, lon: Double, elevationMeters: Double, timeMillis: Long): Boolean {
        val solarElev = MovingSunriseCalculator.computeSolarElevation(lat, lon, timeMillis)
        val horizon = -0.833 - 0.0322 * kotlin.math.sqrt(kotlin.math.max(0.0, elevationMeters))
        return solarElev < horizon
    }

    private fun findCrossingTime(lat: Double, lon: Double, elevationMeters: Double, t1: Long, t2: Long): Long {
        var low = t1
        var high = t2
        val isT1Night = isNight(lat, lon, elevationMeters, t1)
        while (high - low > 60_000L) { // 1-minute precision
            val mid = low + (high - low) / 2
            if (isNight(lat, lon, elevationMeters, mid) == isT1Night) {
                low = mid
            } else {
                high = mid
            }
        }
        return (low + high) / 2
    }

    private fun calculateNightDuration(t1: Long, t2: Long, events: List<MovingSunriseCalculator.SunEvent>, t1IsNight: Boolean): Long {
        var nightDuration = 0L
        var currentStart = t1
        var currentIsNight = t1IsNight
        for (event in events) {
            val eventTime = event.timeUTC.time
            if (currentIsNight) {
                nightDuration += (eventTime - currentStart)
            }
            currentStart = eventTime
            currentIsNight = !currentIsNight
        }
        if (currentIsNight) {
            nightDuration += (t2 - currentStart)
        }
        return nightDuration
    }
}