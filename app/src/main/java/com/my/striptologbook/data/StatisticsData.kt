package com.my.striptologbook.data

// For Type statistics: one row per statistic name, with values per position plus total
data class TypeStatRow(
    val statisticName: String,
    val positionTotals: Map<String, Long>,
    val total: Long
)

// For Month/Year statistics
data class YearStat(
    val year: Int,
    val blockTotal: Long,
    val blockNight: Long,
    val legCount: Int,
    val simTime: Long,
    var expanded: Boolean = false,
    val isPrior: Boolean = false
)

data class MonthStat(
    val month: Int, // 1..12
    val blockTotal: Long,
    val blockNight: Long,
    val legCount: Int,
    val simTime: Long
)