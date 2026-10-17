package com.my.striptologbook.repository

import com.my.striptologbook.data.Leg
import com.my.striptologbook.data.LegDao
import com.my.striptologbook.data.LegTotals
import com.my.striptologbook.data.OpeningBalance
import com.my.striptologbook.data.OpeningBalanceDao
import kotlinx.coroutines.flow.Flow

/**
 * Repository that provides CRUD operations for Leg and OpeningBalance entities.
 * Wraps the DAOs and exposes a clean API for the ViewModels.
 */
class LegRepository(
    private val legDao: LegDao,
    private val openingBalanceDao: OpeningBalanceDao
) {

    // ---------------- Legs ----------------

    fun getAllLegs(): Flow<List<Leg>> = legDao.getAllLegs()

    suspend fun getLegById(id: Long): Leg? = legDao.getLegById(id)

    suspend fun insertLeg(leg: Leg) {
        legDao.insertLeg(leg)
    }

    suspend fun updateLeg(leg: Leg) {
        legDao.updateLeg(leg)
    }

    suspend fun deleteLeg(leg: Leg) {
        legDao.deleteLeg(leg)
    }

    suspend fun clearAllLegs() = legDao.clearAll()

    suspend fun insertAll(legs: List<Leg>) = legDao.insertAll(legs)

    suspend fun getAllLegsList(): List<Leg> = legDao.getAllLegsList()

    // ---------------- Autosuggest ----------------

    suspend fun searchPosition(query: String): List<String> = legDao.searchPosition(query)
    suspend fun searchPicName(query: String): List<String> = legDao.searchPicName(query)
    suspend fun searchSicName(query: String): List<String> = legDao.searchSicName(query)
    suspend fun searchJumpseat1(query: String): List<String> = legDao.searchJumpseat1(query)
    suspend fun searchJumpseat2(query: String): List<String> = legDao.searchJumpseat2(query)
    suspend fun searchCabinCrew(query: String): List<String> = legDao.searchCabinCrew(query)
    suspend fun searchAcRegistration(query: String): List<String> = legDao.searchAcRegistration(query)
    suspend fun searchAcType(query: String): List<String> = legDao.searchAcType(query)
    suspend fun searchFlightNumber(query: String): List<String> = legDao.searchFlightNumber(query)
    suspend fun searchCategory(query: String): List<String> = legDao.searchCategory(query)
    suspend fun searchIcaoDeparture(query: String): List<String> = legDao.searchIcaoDeparture(query)
    suspend fun searchIcaoDestination(query: String): List<String> = legDao.searchIcaoDestination(query)

    suspend fun getMostRecentTypeByRegistration(registration: String): String? =
        legDao.getMostRecentTypeByRegistration(registration)

    suspend fun getMostRecentCategoryByAcType(acType: String): String? =
        legDao.getMostRecentCategoryByAcType(acType)

    // ---------------- PDF ----------------

    suspend fun getLegsBetweenDates(start: Long, end: Long): List<Leg> =
        legDao.getLegsBetweenDates(start, end)

    /**
     * Grand totals of all numeric columns for every leg whose offBlock <= endDate,
     * PLUS the summed opening balance.
     *
     * The opening balance is added here so that the PDF's "Total from previous page"
     * on the first rendered page reflects the prior experience naturally.
     * getLegsBetweenDates is intentionally left unchanged, so no opening balance
     * row ever appears in the PDF table.
     */
    suspend fun getTotalsUpTo(endDate: Long): LegTotals {
        val legsTotal = legDao.getTotalsUpTo(endDate) ?: LegTotals()
        val balanceTotal = openingBalanceDao.getTotals() ?: LegTotals()
        return legsTotal + balanceTotal
    }

    // ---------------- Opening balance ----------------

    fun getAllOpeningBalances(): Flow<List<OpeningBalance>> = openingBalanceDao.getAll()

    suspend fun getAllOpeningBalancesList(): List<OpeningBalance> = openingBalanceDao.getAllList()

    suspend fun getOpeningBalanceById(id: Long): OpeningBalance? =
        openingBalanceDao.getById(id)

    suspend fun insertOpeningBalance(row: OpeningBalance) {
        openingBalanceDao.insert(row)
    }

    suspend fun updateOpeningBalance(row: OpeningBalance) {
        openingBalanceDao.update(row)
    }

    suspend fun deleteOpeningBalance(row: OpeningBalance) {
        openingBalanceDao.delete(row)
    }

    suspend fun clearAllOpeningBalances() = openingBalanceDao.clearAll()

    /**
     * Summed opening balance as a single LegTotals.
     * Returns an empty LegTotals when there are no rows.
     */
    suspend fun getOpeningBalanceTotals(): LegTotals =
        openingBalanceDao.getTotals() ?: LegTotals()
}