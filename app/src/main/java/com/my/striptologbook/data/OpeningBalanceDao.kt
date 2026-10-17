package com.my.striptologbook.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface OpeningBalanceDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(row: OpeningBalance)

    @Update
    suspend fun update(row: OpeningBalance)

    @Delete
    suspend fun delete(row: OpeningBalance)

    @Query("SELECT * FROM opening_balances ORDER BY startDate ASC")
    fun getAll(): Flow<List<OpeningBalance>>

    @Query("SELECT * FROM opening_balances ORDER BY startDate ASC")
    suspend fun getAllList(): List<OpeningBalance>

    @Query("SELECT * FROM opening_balances WHERE id = :id")
    suspend fun getById(id: Long): OpeningBalance?

    @Query("DELETE FROM opening_balances")
    suspend fun clearAll()

    /**
     * Sums every numeric column of every opening balance row.
     * Returns null when the table is empty.
     *
     * The 7 pilot-function/category fields are summed as stored.
     * Day/Total fields are summed as stored; no derivation is done here.
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
        COALESCE(SUM(singlePilotSeTime),           0) AS singlePilotSeTime,
        COALESCE(SUM(singlePilotMeTime),           0) AS singlePilotMeTime,
        COALESCE(SUM(multiPilotTime),              0) AS multiPilotTime,
        COALESCE(SUM(pilotFunctionPicTime),        0) AS pilotFunctionPicTime,
        COALESCE(SUM(pilotFunctionSicTime),        0) AS pilotFunctionSicTime,
        COALESCE(SUM(pilotFunctionDualTime),       0) AS pilotFunctionDualTime,
        COALESCE(SUM(pilotFunctionInstructorTime), 0) AS pilotFunctionInstructorTime
    FROM opening_balances
""")
    suspend fun getTotals(): LegTotals?
}