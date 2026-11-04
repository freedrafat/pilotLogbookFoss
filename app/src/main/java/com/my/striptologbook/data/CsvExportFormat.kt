package com.my.striptologbook.data

import kotlinx.serialization.Serializable

enum class CsvFormatType {
    PDF_COLUMNS,
    RAW,
    FORMATTED
}

enum class CsvDateFormat {
    DD_MM_YYYY,
    DDMMYYYY,
    YYYY_MM_DD,
    DD_SLASH_MM_SLASH_YYYY,
    MM_SLASH_DD_SLASH_YYYY,
    D_MMM_YYYY,
    YYYYMMDD
}

enum class CsvDurationFormat {
    H_MM,
    MINUTES,
    DECIMAL_HOURS,
    HH_MM_SS
}

@Serializable
data class CsvExportFormat(
    val formatType: CsvFormatType = CsvFormatType.FORMATTED,
    val dateFormat: CsvDateFormat = CsvDateFormat.DD_MM_YYYY,
    val durationFormat: CsvDurationFormat = CsvDurationFormat.H_MM,
    val includeSimulator: Boolean = false,
    val includeTakeoffLanding: Boolean = true,
    val includeDay: Boolean = true,
    val includeNumberToDay: Boolean = true,
    val includeNumberToNight: Boolean = true,
    val includeNumberToTotal: Boolean = true,
    val includeNumberLdgDay: Boolean = true,
    val includeNumberLdgNight: Boolean = true,
    val includeNumberLdgTotal: Boolean = true,
    val includeNumberInstrumentApproaches: Boolean = true,
    val includeIsPilotFlying: Boolean = true,
    val includeRemarks: Boolean = true
) {
    companion object {
        val DEFAULT = CsvExportFormat()
    }
}