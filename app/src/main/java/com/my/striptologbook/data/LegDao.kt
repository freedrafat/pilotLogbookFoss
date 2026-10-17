package com.my.striptologbook.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface LegDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLeg(leg: Leg)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(legs: List<Leg>)   // for CSV import later, but added now

    @Update
    suspend fun updateLeg(leg: Leg)

    @Delete
    suspend fun deleteLeg(leg: Leg)

    @Query("SELECT * FROM legs ORDER BY offBlock DESC")
    fun getAllLegs(): Flow<List<Leg>>

    @Query("SELECT * FROM legs ORDER BY offBlock DESC")
    suspend fun getAllLegsList(): List<Leg>  // for sync operations

    @Query("SELECT * FROM legs WHERE id = :id")
    suspend fun getLegById(id: Long): Leg?

    @Query("DELETE FROM legs")
    suspend fun clearAll()

    // ---------- Autosuggest queries ----------
    @Query("SELECT DISTINCT position FROM legs WHERE position IS NOT NULL AND position LIKE '%' || :query || '%' COLLATE NOCASE ORDER BY position")
    suspend fun searchPosition(query: String): List<String>

    @Query("SELECT DISTINCT picName FROM legs WHERE picName IS NOT NULL AND picName LIKE '%' || :query || '%' COLLATE NOCASE ORDER BY picName")
    suspend fun searchPicName(query: String): List<String>

    @Query("SELECT DISTINCT sicName FROM legs WHERE sicName IS NOT NULL AND sicName LIKE '%' || :query || '%' COLLATE NOCASE ORDER BY sicName")
    suspend fun searchSicName(query: String): List<String>

    @Query("SELECT DISTINCT jumpseat1 FROM legs WHERE jumpseat1 IS NOT NULL AND jumpseat1 LIKE '%' || :query || '%' COLLATE NOCASE ORDER BY jumpseat1")
    suspend fun searchJumpseat1(query: String): List<String>

    @Query("SELECT DISTINCT jumpseat2 FROM legs WHERE jumpseat2 IS NOT NULL AND jumpseat2 LIKE '%' || :query || '%' COLLATE NOCASE ORDER BY jumpseat2")
    suspend fun searchJumpseat2(query: String): List<String>

    @Query("SELECT DISTINCT cabinCrew FROM legs WHERE cabinCrew IS NOT NULL AND cabinCrew LIKE '%' || :query || '%' COLLATE NOCASE ORDER BY cabinCrew")
    suspend fun searchCabinCrew(query: String): List<String>

    @Query("SELECT DISTINCT acRegistration FROM legs WHERE acRegistration IS NOT NULL AND acRegistration LIKE '%' || :query || '%' COLLATE NOCASE ORDER BY acRegistration")
    suspend fun searchAcRegistration(query: String): List<String>

    @Query("SELECT DISTINCT acType FROM legs WHERE acType IS NOT NULL AND acType LIKE '%' || :query || '%' COLLATE NOCASE ORDER BY acType")
    suspend fun searchAcType(query: String): List<String>

    @Query("SELECT DISTINCT flightNumber FROM legs WHERE flightNumber IS NOT NULL AND flightNumber LIKE '%' || :query || '%' COLLATE NOCASE ORDER BY flightNumber")
    suspend fun searchFlightNumber(query: String): List<String>

    @Query("SELECT DISTINCT category FROM legs WHERE category IS NOT NULL AND category LIKE '%' || :query || '%' COLLATE NOCASE ORDER BY category")
    suspend fun searchCategory(query: String): List<String>

    @Query("SELECT DISTINCT icaoDeparture FROM legs WHERE icaoDeparture IS NOT NULL AND icaoDeparture LIKE '%' || :query || '%' COLLATE NOCASE ORDER BY icaoDeparture")
    suspend fun searchIcaoDeparture(query: String): List<String>

    @Query("SELECT DISTINCT icaoDestination FROM legs WHERE icaoDestination IS NOT NULL AND icaoDestination LIKE '%' || :query || '%' COLLATE NOCASE ORDER BY icaoDestination")
    suspend fun searchIcaoDestination(query: String): List<String>

    @Query("SELECT acType FROM legs WHERE acRegistration = :registration AND acType IS NOT NULL ORDER BY offBlock DESC LIMIT 1")
    suspend fun getMostRecentTypeByRegistration(registration: String): String?

    @Query("SELECT category FROM legs WHERE acType = :acType AND category IS NOT NULL ORDER BY offBlock DESC LIMIT 1")
    suspend fun getMostRecentCategoryByAcType(acType: String): String?

    // ---------- PDF export queries ----------

    @Query("SELECT * FROM legs WHERE offBlock IS NOT NULL AND offBlock >= :start AND offBlock <= :end ORDER BY offBlock ASC")
    suspend fun getLegsBetweenDates(start: Long, end: Long): List<Leg>

    /**
     * Grand totals of all numeric columns for every leg whose offBlock <= endDate.
     * If the caller wants the entire career, pass Long.MAX_VALUE.
     */
    @Query("""
    SELECT
        COALESCE(SUM(blockTimeDay),    0) AS blockTimeDay,
        COALESCE(SUM(blockTimeNight),  0) AS blockTimeNight,
        COALESCE(SUM(blockTimeTotal),  0) AS blockTimeTotal,
        COALESCE(SUM(flightTimeDay),   0) AS flightTimeDay,
        COALESCE(SUM(flightTimeNight), 0) AS flightTimeNight,
        COALESCE(SUM(flightTimeTotal), 0) AS flightTimeTotal,
        COALESCE(SUM(simulatorTime),   0) AS simulatorTime,
        COALESCE(SUM(ifrDay),          0) AS ifrDay,
        COALESCE(SUM(ifrNight),        0) AS ifrNight,
        COALESCE(SUM(ifrTotal),        0) AS ifrTotal,
        COALESCE(SUM(numberToDay),     0) AS numberToDay,
        COALESCE(SUM(numberToNight),   0) AS numberToNight,
        COALESCE(SUM(numberToTotal),   0) AS numberToTotal,
        COALESCE(SUM(numberLdgDay),    0) AS numberLdgDay,
        COALESCE(SUM(numberLdgNight),  0) AS numberLdgNight,
        COALESCE(SUM(numberLdgTotal),  0) AS numberLdgTotal,
        COALESCE(SUM(numberInstrumentApproaches), 0) AS numberInstrumentApproaches,
        COALESCE(SUM(CASE WHEN category = 'Single Pilot SE' THEN blockTimeTotal ELSE 0 END), 0) AS singlePilotSeTime,
        COALESCE(SUM(CASE WHEN category = 'Single Pilot ME' THEN blockTimeTotal ELSE 0 END), 0) AS singlePilotMeTime,
        COALESCE(SUM(CASE WHEN category = 'Multi Pilot'     THEN blockTimeTotal ELSE 0 END), 0) AS multiPilotTime,
        COALESCE(SUM(CASE WHEN position IN ('PIC','SPIC','PICUS') THEN blockTimeTotal ELSE 0 END), 0) AS pilotFunctionPicTime,
        COALESCE(SUM(CASE WHEN position = 'SIC' THEN blockTimeTotal ELSE 0 END), 0) AS pilotFunctionSicTime,
        COALESCE(SUM(CASE WHEN position = 'Dual' THEN blockTimeTotal ELSE 0 END), 0) AS pilotFunctionDualTime,
        COALESCE(SUM(CASE WHEN position = 'Instructor' THEN blockTimeTotal ELSE 0 END), 0) AS pilotFunctionInstructorTime
    FROM legs WHERE offBlock IS NOT NULL AND offBlock <= :endDate
""")
    suspend fun getTotalsUpTo(endDate: Long): LegTotals?

}