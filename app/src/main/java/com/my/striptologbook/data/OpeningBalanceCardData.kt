package com.my.striptologbook.data

/**
 * The single data shape that every Opening Balance card is built from,
 * whether it represents one row or the merged view of every row.
 *
 * The two producers at the bottom of the companion object are the only
 * places where a row or a merge is turned into card data. Everything
 * downstream — the renderer, the adapter, the merged card — works on
 * this shape only.
 */
data class OpeningBalanceCardData(
    val acType: String?,
    val position: String?,
    val category: String?,
    val comment: String?,
    val startDate: Long?,
    val endDate: Long?,

    val blockTimeTotal: Int?,
    val blockTimeNight: Int?,
    val blockTimeDay: Int?,

    val flightTimeTotal: Int?,
    val flightTimeNight: Int?,
    val flightTimeDay: Int?,

    val ifrTotal: Int?,
    val ifrNight: Int?,
    val ifrDay: Int?,

    val simulatorTime: Int?,

    val numberToDay: Int?,
    val numberToNight: Int?,
    val numberToTotal: Int?,

    val numberLdgDay: Int?,
    val numberLdgNight: Int?,
    val numberLdgTotal: Int?,

    val numberInstrumentApproaches: Int?,

    val singlePilotSeTime: Int?,
    val singlePilotMeTime: Int?,
    val multiPilotTime: Int?,
    val pilotFunctionPicTime: Int?,
    val pilotFunctionSicTime: Int?,
    val pilotFunctionDualTime: Int?,
    val pilotFunctionInstructorTime: Int?,

    val isMerged: Boolean
) {
    companion object {

        /** Producer for a real row. Copies every field as stored. */
        fun fromRow(row: OpeningBalance): OpeningBalanceCardData =
            OpeningBalanceCardData(
                acType = row.acType,
                position = row.position,
                category = row.category,
                comment = row.comment,
                startDate = row.startDate,
                endDate = row.endDate,
                blockTimeTotal = row.blockTimeTotal,
                blockTimeNight = row.blockTimeNight,
                blockTimeDay = row.blockTimeDay,
                flightTimeTotal = row.flightTimeTotal,
                flightTimeNight = row.flightTimeNight,
                flightTimeDay = row.flightTimeDay,
                ifrTotal = row.ifrTotal,
                ifrNight = row.ifrNight,
                ifrDay = row.ifrDay,
                simulatorTime = row.simulatorTime,
                numberToDay = row.numberToDay,
                numberToNight = row.numberToNight,
                numberToTotal = row.numberToTotal,
                numberLdgDay = row.numberLdgDay,
                numberLdgNight = row.numberLdgNight,
                numberLdgTotal = row.numberLdgTotal,
                numberInstrumentApproaches = row.numberInstrumentApproaches,
                singlePilotSeTime = row.singlePilotSeTime,
                singlePilotMeTime = row.singlePilotMeTime,
                multiPilotTime = row.multiPilotTime,
                pilotFunctionPicTime = row.pilotFunctionPicTime,
                pilotFunctionSicTime = row.pilotFunctionSicTime,
                pilotFunctionDualTime = row.pilotFunctionDualTime,
                pilotFunctionInstructorTime = row.pilotFunctionInstructorTime,
                isMerged = false
            )

        /**
         * Producer for the merged view.
         *
         * Numeric fields come from the summed LegTotals; a zero sum is
         * stored as null so the renderer treats it as empty, matching the
         * way a row stores an empty numeric field.
         *
         * The four text fields use the rule: if every row has the same
         * value, keep it; otherwise empty.
         *
         * The dates use the earliest start date and the latest end date
         * across all rows.
         */
        fun fromMerged(
            totals: LegTotals,
            rows: List<OpeningBalance>
        ): OpeningBalanceCardData {

            fun common(values: List<String?>): String? {
                if (values.isEmpty()) return null
                val first = values.first()
                return if (values.all { it == first }) first else null
            }

            fun nonZero(v: Int): Int? = if (v == 0) null else v

            val earliestStart = rows.mapNotNull { it.startDate }.minOrNull()
            val latestEnd = rows.mapNotNull { it.endDate }.maxOrNull()

            return OpeningBalanceCardData(
                acType = common(rows.map { it.acType }),
                position = common(rows.map { it.position }),
                category = common(rows.map { it.category }),
                comment = common(rows.map { it.comment }),
                startDate = earliestStart,
                endDate = latestEnd,
                blockTimeTotal = nonZero(totals.blockTimeTotal),
                blockTimeNight = nonZero(totals.blockTimeNight),
                blockTimeDay = nonZero(totals.blockTimeDay),
                flightTimeTotal = nonZero(totals.flightTimeTotal),
                flightTimeNight = nonZero(totals.flightTimeNight),
                flightTimeDay = nonZero(totals.flightTimeDay),
                ifrTotal = nonZero(totals.ifrTotal),
                ifrNight = nonZero(totals.ifrNight),
                ifrDay = nonZero(totals.ifrDay),
                simulatorTime = nonZero(totals.simulatorTime),
                numberToDay = nonZero(totals.numberToDay),
                numberToNight = nonZero(totals.numberToNight),
                numberToTotal = nonZero(totals.numberToTotal),
                numberLdgDay = nonZero(totals.numberLdgDay),
                numberLdgNight = nonZero(totals.numberLdgNight),
                numberLdgTotal = nonZero(totals.numberLdgTotal),
                numberInstrumentApproaches = nonZero(totals.numberInstrumentApproaches),
                singlePilotSeTime = nonZero(totals.singlePilotSeTime),
                singlePilotMeTime = nonZero(totals.singlePilotMeTime),
                multiPilotTime = nonZero(totals.multiPilotTime),
                pilotFunctionPicTime = nonZero(totals.pilotFunctionPicTime),
                pilotFunctionSicTime = nonZero(totals.pilotFunctionSicTime),
                pilotFunctionDualTime = nonZero(totals.pilotFunctionDualTime),
                pilotFunctionInstructorTime = nonZero(totals.pilotFunctionInstructorTime),
                isMerged = true
            )
        }
    }
}