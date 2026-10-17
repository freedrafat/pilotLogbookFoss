package com.my.striptologbook.utils

import android.util.Log
import com.my.striptologbook.data.AirportDataManager
import java.util.*
import kotlin.math.*

object MovingSunriseCalculator {

    data class SunEvent(
        val timeUTC: Date,
        val latitude: Double,
        val longitude: Double,
        val type: String // "Sunrise" or "Sunset"
    )

    private const val ZENITH_OFFICIAL = 90.8333 // degrees
    private const val ASTRONOMICAL_TWILIGHT_ELEVATION = -18.0 // degrees

    /**
     * Computes the solar elevation angle (in degrees) for a given position and UTC time.
     * Positive = above horizon, negative = below.
     */
    fun computeSolarElevation(latDeg: Double, lonDeg: Double, timeMillis: Long): Double {

        Log.d("TimeDebug", "computeSolarElevation: lat=$latDeg, lon=$lonDeg, timeMillis=$timeMillis, formatted=${formatDateUtc(timeMillis)}")

        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        cal.timeInMillis = timeMillis
        val dayOfYear = cal.get(Calendar.DAY_OF_YEAR)
        val hourUTC = cal.get(Calendar.HOUR_OF_DAY) + cal.get(Calendar.MINUTE) / 60.0 + cal.get(Calendar.SECOND) / 3600.0

        Log.d("TimeDebug", "  UTC components: dayOfYear=$dayOfYear, hourUTC=$hourUTC")


        // NOAA Solar Calculator algorithm
        val t = dayOfYear + (hourUTC / 24.0)
        val M = (0.9856 * t) - 3.289
        val L = M + (1.916 * sin(Math.toRadians(M))) + (0.020 * sin(Math.toRadians(2 * M))) + 282.634
        val Lnorm = (L % 360 + 360) % 360

        var RA_deg = Math.toDegrees(atan(0.91764 * tan(Math.toRadians(Lnorm))))
        RA_deg = (RA_deg % 360 + 360) % 360
        val Lquadrant = floor(Lnorm / 90) * 90
        val RAquadrant = floor(RA_deg / 90) * 90
        RA_deg += (Lquadrant - RAquadrant)

        val sinDec = 0.39782 * sin(Math.toRadians(Lnorm))
        val dec = asin(sinDec)

        val equationOfTimeDeg = Lnorm - RA_deg

        // ⚠️ CRITICAL: MUST be "+ lonDeg", not "- lonDeg"
        val hourAngleDeg = (hourUTC - 12.0) * 15.0 + lonDeg + equationOfTimeDeg

        val haRad = Math.toRadians(hourAngleDeg)
        val latRad = Math.toRadians(latDeg)
        val elevation = asin(sin(latRad) * sinDec + cos(latRad) * cos(dec) * cos(haRad))
        return Math.toDegrees(elevation)
    }

    /**
     * Interpolates the crossing point using Cartesian coordinates.
     * Returns (time, latitude, longitude) of the exact crossing.
     */
    private fun interpolateCrossing(
        t1: Long, t2: Long,
        delta1: Double, delta2: Double,
        x1: Double, y1: Double, z1: Double,
        x2: Double, y2: Double, z2: Double
    ): Triple<Long, Double, Double> {
        val ratio = -delta1 / (delta2 - delta1)
        val tCross = t1 + (ratio * (t2 - t1)).toLong()

        // Linear interpolation in Cartesian space
        val x = x1 + ratio * (x2 - x1)
        val y = y1 + ratio * (y2 - y1)
        val z = z1 + ratio * (z2 - z1)
        val norm = sqrt(x*x + y*y + z*z)
        val xNorm = x / norm
        val yNorm = y / norm
        val zNorm = z / norm

        val latCross = Math.toDegrees(atan2(zNorm, sqrt(xNorm*xNorm + yNorm*yNorm)))
        val lonCross = Math.toDegrees(atan2(yNorm, xNorm))
        return Triple(tCross, latCross, lonCross)
    }


    /**
     * Computes sunrise/sunset events along a route defined by departure, arrival,
     * and optional intermediate waypoints.
     *
     * @param airportManager        for airport coordinates
     * @param depIATA               departure airport code
     * @param arrIATA               arrival airport code
     * @param depUTC                departure time (ms)
     * @param arrUTC                arrival time (ms)
     * @param elevationMeters       cruise altitude (m)
     * @param waypoints             list of user‑entered waypoints (order = intended sequence)
     * @return list of SunEvent sorted by time
     */
    fun computeEventsWithWaypoints(
        airportManager: AirportDataManager,
        depIATA: String,
        arrIATA: String,
        depUTC: Long,
        arrUTC: Long,
        elevationMeters: Double,
        waypoints: List<Waypoint>
    ): List<SunEvent> {
        // 1. Get airport data
        val depAirport = airportManager.findAirportByCode(depIATA)
        val arrAirport = airportManager.findAirportByCode(arrIATA)
        if (depAirport == null || arrAirport == null) return emptyList()

        // 2. Build the master route: fixed endpoints + waypoints (in UI order)
        val routePoints = mutableListOf<Waypoint>()
        // Departure as a complete waypoint
        routePoints.add(Waypoint(depAirport.latitude, depAirport.longitude, depUTC))
        // Add user waypoints (filter out completely empty rows)
        routePoints.addAll(waypoints.filter { it.latitude != null || it.longitude != null || it.timeUTC != null })
        // Arrival as a complete waypoint
        routePoints.add(Waypoint(arrAirport.latitude, arrAirport.longitude, arrUTC))

        // 3. Fill missing times based on distance proportion (respect UI order)
        fillMissingTimes(routePoints)

        // 4. Fill missing coordinates using sorted‑by‑time interpolation
        fillMissingCoordinates(routePoints)

        // 5. Validate: all waypoints must be complete and in strictly increasing time order
        if (!validateRoute(routePoints, depUTC, arrUTC)) {
            return emptyList()  // or throw an exception with a user‑friendly message
        }

        // 6. Sort by time for the final route (this is the chronological order)
        val sortedRoute = routePoints.sortedBy { it.timeUTC }

        Log.d("TimeDebug", "Final sorted route (${sortedRoute.size} points):")
        sortedRoute.forEachIndexed { idx, wp ->
            Log.d("TimeDebug", "  $idx: lat=${wp.latitude}, lon=${wp.longitude}, time=${wp.timeUTC} (${formatDateUtc(wp.timeUTC!!)})")
        }

        // 7. Run the minute‑by‑minute scanning loop over the segmented path
        return computeEventsOnRoute(sortedRoute, elevationMeters)
    }

    // ----------------------------------------------------------------
    // Helper functions for waypoint processing
    // ----------------------------------------------------------------

    fun fillMissingTimes(route: MutableList<Waypoint>) {
        // We need to process gaps between complete time anchors.
        // We'll iterate and find segments where start and end have time, and intermediate have no time.
        var i = 0
        while (i < route.size) {
            // Find next waypoint that has a time (anchor)
            var anchorIndex = i
            while (anchorIndex < route.size && route[anchorIndex].timeUTC == null) {
                anchorIndex++
            }
            if (anchorIndex == route.size) break
            // Now we have a complete anchor at anchorIndex.
            // The previous anchor with time (if any) is before i.
            // We'll fill missing times between previous anchor and this anchor.
            var prevAnchorIndex = i - 1
            while (prevAnchorIndex >= 0 && route[prevAnchorIndex].timeUTC == null) {
                prevAnchorIndex--
            }
            if (prevAnchorIndex >= 0 && i < anchorIndex) {
                // We have a gap from prevAnchorIndex+1 to anchorIndex-1 with missing times.
                // And we have coordinates for all points (maybe some are missing coordinates, but that's handled later)
                // Compute the total great‑circle distance from prev anchor to current anchor,
                // summing distances between consecutive points that have coordinates.
                val startIdx = prevAnchorIndex
                val endIdx = anchorIndex
                val startTime = route[startIdx].timeUTC!!
                val endTime = route[endIdx].timeUTC!!

                // Compute cumulative distances along the sequence
                val distances = mutableListOf<Double>()
                var totalDist = 0.0
                for (k in startIdx until endIdx) {
                    val p1 = route[k]
                    val p2 = route[k + 1]
                    if (p1.hasCoordinates() && p2.hasCoordinates()) {
                        val dist = greatCircleDistance(p1.latitude!!, p1.longitude!!, p2.latitude!!, p2.longitude!!)
                        totalDist += dist
                        distances.add(dist)
                    } else {
                        // If some coordinates are missing, we cannot compute distance yet.
                        // We'll skip this gap and let the coordinate filling happen first.
                        // But that would be a circular dependency.
                        // We'll assume that when we fill missing times, all points have coordinates,
                        // but that's not guaranteed. Actually, some may have no coords (time only).
                        // We need to handle this carefully: we can only fill times if we have coordinates.
                        // If not, we skip filling times for now and let coordinate filling happen later.
                        // However, coordinate filling needs times to interpolate.
                        // So we need to break this circular dependency by filling missing times using
                        // only the anchors with complete data, but if a point has no coordinates,
                        // we cannot compute its position, but we can still use the anchor times to
                        // assign a time proportionally. However, if there are points with time only,
                        // they will get coordinates in the next step; they don't need times.
                        // So we can simply skip filling missing times for points that lack coordinates.
                        // But then they'd have no time, and coordinate filling would need times.
                        // So we must fill times for all points, even if they lack coordinates,
                        // by assuming constant speed along the great circle between the two anchors.
                        // But we don't know the path if intermediate coordinates are missing.
                        // The user might have entered a waypoint with time only—that's fine because we
                        // will later interpolate its coordinates using Slerp between adjacent anchors.
                        // So for time filling, we can just linearly interpolate time based on index order,
                        // not distance, because we don't have coordinates.
                        // However, if the user enters a point with coordinates but no time, we want to
                        // compute time based on distance, not index.
                        // So we need to differentiate: if the point has coordinates, we use distance;
                        // if it doesn't, we fall back to linear time between anchors by index.
                        // This is a reasonable simplification.
                    }
                }

                // Now assign times to each point in the gap
                if (totalDist > 0) {
                    // We have valid coordinates for all intermediate points, so we can use distance
                    var cumDist = 0.0
                    for (k in (startIdx + 1) until endIdx) {
                        val point = route[k]
                        if (point.hasCoordinates()) {
                            // Distance from previous point
                            val prevPoint = route[k - 1]
                            if (prevPoint.hasCoordinates()) {
                                val dist = greatCircleDistance(prevPoint.latitude!!, prevPoint.longitude!!,
                                    point.latitude!!, point.longitude!!)
                                cumDist += dist
                                val fraction = cumDist / totalDist
                                val time = startTime + (fraction * (endTime - startTime)).toLong()
                                point.timeUTC = time
                            }
                        }
                    }
                } else {
                    // Fallback: assign times linearly by index
                    val count = endIdx - startIdx
                    for (k in (startIdx + 1) until endIdx) {
                        val fraction = (k - startIdx).toDouble() / count
                        val time = startTime + (fraction * (endTime - startTime)).toLong()
                        route[k].timeUTC = time
                    }
                }
            }
            i = anchorIndex + 1
        }
    }

    fun fillMissingCoordinates(route: MutableList<Waypoint>) {
        // Sort by time (assuming all times are now filled)
        val sorted = route.sortedBy { it.timeUTC }
        // For each point with missing coordinates, interpolate between adjacent complete anchors
        for (i in sorted.indices) {
            val point = sorted[i]
            if (!point.hasCoordinates()) {
                // Find nearest anchor with coordinates before and after
                var beforeIdx = i - 1
                while (beforeIdx >= 0 && !sorted[beforeIdx].hasCoordinates()) {
                    beforeIdx--
                }
                var afterIdx = i + 1
                while (afterIdx < sorted.size && !sorted[afterIdx].hasCoordinates()) {
                    afterIdx++
                }
                if (beforeIdx >= 0 && afterIdx < sorted.size) {
                    val before = sorted[beforeIdx]
                    val after = sorted[afterIdx]
                    val tBefore = before.timeUTC!!
                    val tAfter = after.timeUTC!!
                    val tPoint = point.timeUTC!!
                    val fraction = (tPoint - tBefore).toDouble() / (tAfter - tBefore)
                    // Slerp between before and after coordinates
                    val (lat, lon) = slerp(before.latitude!!, before.longitude!!,
                        after.latitude!!, after.longitude!!, fraction)
                    point.latitude = lat
                    point.longitude = lon
                }
            }
        }
    }

    private fun validateRoute(route: List<Waypoint>, depUTC: Long, arrUTC: Long): Boolean {
        // All must be complete
        if (route.any { !it.isComplete() }) {
            // Could log error: "Incomplete waypoint"
            return false
        }
        // Must be strictly increasing times and within bounds
        val times = route.map { it.timeUTC!! }
        for (i in 0 until times.size - 1) {
            if (times[i] >= times[i + 1]) {
                return false
            }
        }
        // First must be depUTC, last arrUTC (they are, but check)
        return !(times.first() != depUTC || times.last() != arrUTC)
    }

    fun computeEventsWithWaypointsAndFilledRoute(
        airportManager: AirportDataManager,
        depIATA: String,
        arrIATA: String,
        depUTC: Long,
        arrUTC: Long,
        elevationMeters: Double,
        waypoints: List<Waypoint>
    ): RouteResult {
        // 1. Build the master route and fill missing data (same as computeEventsWithWaypoints)
        val depAirport = airportManager.findAirportByCode(depIATA)
        val arrAirport = airportManager.findAirportByCode(arrIATA)
        if (depAirport == null || arrAirport == null) return RouteResult(emptyList(), emptyList())

        val routePoints = mutableListOf<Waypoint>()
        routePoints.add(Waypoint(depAirport.latitude, depAirport.longitude, depUTC))
        routePoints.addAll(waypoints.filter { it.latitude != null || it.longitude != null || it.timeUTC != null })
        routePoints.add(Waypoint(arrAirport.latitude, arrAirport.longitude, arrUTC))

        fillMissingTimes(routePoints)
        fillMissingCoordinates(routePoints)

        if (!validateRoute(routePoints, depUTC, arrUTC)) {
            return RouteResult(emptyList(), emptyList())
        }

        val sortedRoute = routePoints.sortedBy { it.timeUTC }

        // 2. Compute events on the sorted route
        val events = computeEventsOnRoute(sortedRoute, elevationMeters)

        // 3. Return both
        return RouteResult(events, sortedRoute)
    }

    // Great‑circle distance in radians (or degrees, we just need proportion)
    private fun greatCircleDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val deltaLambda = Math.toRadians(lon2 - lon1)
        val d = 2 * asin(sqrt(
            sin((phi1 - phi2) / 2).pow(2) +
                    cos(phi1) * cos(phi2) * sin(deltaLambda / 2).pow(2)
        ))
        return d // radians
    }

    // Spherical linear interpolation, returns (latDeg, lonDeg)
    private fun slerp(lat1: Double, lon1: Double, lat2: Double, lon2: Double, fraction: Double): Pair<Double, Double> {
        val phi1 = Math.toRadians(lat1)
        val lambda1 = Math.toRadians(lon1)
        val phi2 = Math.toRadians(lat2)
        val lambda2 = Math.toRadians(lon2)

        val x1 = cos(phi1) * cos(lambda1)
        val y1 = cos(phi1) * sin(lambda1)
        val z1 = sin(phi1)

        val x2 = cos(phi2) * cos(lambda2)
        val y2 = cos(phi2) * sin(lambda2)
        val z2 = sin(phi2)

        val dot = x1*x2 + y1*y2 + z1*z2
        val d = acos(max(-1.0, min(1.0, dot)))
        val eps = 1e-12

        val (a, b) = if (abs(sin(d)) < eps) {
            (1.0 - fraction to fraction)
        } else {
            (sin((1 - fraction) * d) / sin(d) to sin(fraction * d) / sin(d))
        }

        val x = a * x1 + b * x2
        val y = a * y1 + b * y2
        val z = a * z1 + b * z2
        val norm = sqrt(x*x + y*y + z*z)
        val xNorm = x / norm
        val yNorm = y / norm
        val zNorm = z / norm

        val latDeg = Math.toDegrees(atan2(zNorm, sqrt(xNorm*xNorm + yNorm*yNorm)))
        var lonDeg = Math.toDegrees(atan2(yNorm, xNorm))
        if (lonDeg > 180) lonDeg -= 360
        if (lonDeg < -180) lonDeg += 360
        return Pair(latDeg, lonDeg)
    }

    // The minute‑by‑minute scanning over a pre‑sorted route
    private fun computeEventsOnRoute(
        route: List<Waypoint>,  // must have at least 2 points, sorted by time
        elevationMeters: Double
    ): List<SunEvent> {
        val totalDuration = route.last().timeUTC!! - route.first().timeUTC!!
        if (totalDuration <= 0) return emptyList()

        val stepMillis = 60_000L
        val events = mutableListOf<SunEvent>()

        var prevElevation: Double? = null
        var prevX = 0.0
        var prevY = 0.0
        var prevZ = 0.0
        var prevTime = route.first().timeUTC!!

        // Pre‑compute Cartesian coordinates for each waypoint
        val cartesianPoints = route.map { wp ->
            val latRad = Math.toRadians(wp.latitude!!)
            val lonRad = Math.toRadians(wp.longitude!!)
            Triple(cos(latRad) * cos(lonRad), cos(latRad) * sin(lonRad), sin(latRad))
        }

        val effectiveElevation = max(0.0, elevationMeters)
        val elevationAdjustment = 0.0347 * sqrt(effectiveElevation)
        val horizonElevation = -0.833 - elevationAdjustment
        val twilightElevation = ASTRONOMICAL_TWILIGHT_ELEVATION

        // Define thresholds with event names (all checked in the same loop)
        data class Threshold(val elevation: Double, val riseName: String, val setName: String)

        val thresholds = listOf(
            Threshold(horizonElevation, "Sunrise", "Sunset"),
            Threshold(twilightElevation, "Astronomical Dawn", "Astronomical Dusk")
        )

        var t = route.first().timeUTC!!
        var segmentIdx = 0

        Log.d("TimeDebug", "computeEventsOnRoute: totalDuration=$totalDuration ms, firstTime=${route.first().timeUTC} (${formatDateUtc(route.first().timeUTC!!)}), lastTime=${route.last().timeUTC} (${formatDateUtc(route.last().timeUTC!!)})")

        while (t <= route.last().timeUTC!!) {
            // Advance segment index if needed
            while (segmentIdx < route.size - 1 && t > route[segmentIdx + 1].timeUTC!!) {
                segmentIdx++
            }
            val startWp = route[segmentIdx]
            val endWp = route[segmentIdx + 1]
            val startTime = startWp.timeUTC!!
            val endTime = endWp.timeUTC!!
            val localFraction = if (endTime > startTime) {
                (t - startTime).toDouble() / (endTime - startTime)
            } else {
                0.0
            }
            val frac = max(0.0, min(1.0, localFraction))

            // Interpolate position along this segment using Slerp (Cartesian)
            val (x1, y1, z1) = cartesianPoints[segmentIdx]
            val (x2, y2, z2) = cartesianPoints[segmentIdx + 1]

            val eps = 1e-12
            val dot = x1*x2 + y1*y2 + z1*z2
            val d = acos(max(-1.0, min(1.0, dot)))
            val (a, b) = if (abs(sin(d)) < eps) {
                (1.0 - frac to frac)
            } else {
                (sin((1 - frac) * d) / sin(d) to sin(frac * d) / sin(d))
            }
            val x = a * x1 + b * x2
            val y = a * y1 + b * y2
            val z = a * z1 + b * z2
            val norm = sqrt(x*x + y*y + z*z)
            val latDeg = Math.toDegrees(atan2(z/norm, sqrt((x*x + y*y) / (norm*norm))))
            var lonDeg = Math.toDegrees(atan2(y, x))
            if (lonDeg > 180) lonDeg -= 360
            if (lonDeg < -180) lonDeg += 360

            val elevation = computeSolarElevation(latDeg, lonDeg, t)

            if (prevElevation != null) {
                // Check each threshold for a crossing
                for (th in thresholds) {
                    if ((prevElevation < th.elevation && elevation > th.elevation) ||
                        (prevElevation > th.elevation && elevation < th.elevation)) {
                        // Interpolate crossing using Cartesian coordinates
                        val (crossTime, crossLat, crossLon) = interpolateCrossingCartesian(
                            prevTime, t,
                            prevElevation - th.elevation,
                            elevation - th.elevation,
                            prevX, prevY, prevZ,
                            x/norm, y/norm, z/norm
                        )
                        val type = if (prevElevation < th.elevation) th.riseName else th.setName
                        events.add(SunEvent(Date(crossTime), crossLat, crossLon, type))
                    }
                }
            }

            // Store current step for next iteration
            prevElevation = elevation
            prevX = x/norm
            prevY = y/norm
            prevZ = z/norm
            prevTime = t
            t += stepMillis
        }

        return events.sortedBy { it.timeUTC.time }
    }

    // Interpolate crossing with Cartesian coordinates
    private fun interpolateCrossingCartesian(
        t1: Long, t2: Long,
        delta1: Double, delta2: Double,
        x1: Double, y1: Double, z1: Double,
        x2: Double, y2: Double, z2: Double
    ): Triple<Long, Double, Double> {
        val ratio = -delta1 / (delta2 - delta1)
        val tCross = t1 + (ratio * (t2 - t1)).toLong()

        val x = x1 + ratio * (x2 - x1)
        val y = y1 + ratio * (y2 - y1)
        val z = z1 + ratio * (z2 - z1)
        val norm = sqrt(x*x + y*y + z*z)
        val latCross = Math.toDegrees(atan2(z/norm, sqrt((x*x + y*y) / (norm*norm))))
        var lonCross = Math.toDegrees(atan2(y, x))
        if (lonCross > 180) lonCross -= 360
        if (lonCross < -180) lonCross += 360
        return Triple(tCross, latCross, lonCross)
    }

    // Public old method kept for compatibility
    fun computeEvents(
        airportManager: AirportDataManager,
        depIATA: String,
        arrIATA: String,
        depUTC: Long,
        arrUTC: Long,
        elevationMeters: Double
    ): List<SunEvent> {
        // Just call the waypoint version with empty list
        return computeEventsWithWaypoints(airportManager, depIATA, arrIATA, depUTC, arrUTC, elevationMeters, emptyList())
    }



}

data class Waypoint(
    var latitude: Double? = null,   // in degrees
    var longitude: Double? = null,  // in degrees
    var timeUTC: Long? = null       // milliseconds since epoch
) {
    fun isComplete(): Boolean = latitude != null && longitude != null && timeUTC != null
    fun hasCoordinates(): Boolean = latitude != null && longitude != null
    fun hasTime(): Boolean = timeUTC != null
}

data class RouteResult(
    val events: List<MovingSunriseCalculator.SunEvent>,
    val filledWaypoints: List<Waypoint>
)