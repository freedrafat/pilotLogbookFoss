package com.my.striptologbook.data

import com.my.striptologbook.utils.formatDateOnly
import com.my.striptologbook.utils.formatDuration

/** One line on a card, with a flag for whether it should be emphasised. */
data class OpeningBalanceCardLine(
    val text: String,
    val emphasis: Boolean = false
)

/**
 * The single place where card content is decided. Everything that
 * appears on an Opening Balance card — the row card in the list and
 * the merged card at the bottom of the list — is produced here.
 *
 * To add a field to the card, add a line to the list returned by
 * render(). Nothing else needs to change.
 */
object OpeningBalanceCardRenderer {

    fun render(data: OpeningBalanceCardData): List<OpeningBalanceCardLine> {
        return listOf(
            OpeningBalanceCardLine(label(data), emphasis = true),
            OpeningBalanceCardLine(dates(data)),
            OpeningBalanceCardLine(block(data))
        )
    }

    private fun label(data: OpeningBalanceCardData): String {
        if (data.isMerged) return "Merged Totals"
        val parts = listOfNotNull(
            data.acType?.takeIf { it.isNotBlank() },
            data.position?.takeIf { it.isNotBlank() },
            data.category?.takeIf { it.isNotBlank() }
        )
        return parts.joinToString(" • ").ifEmpty { "Opening Balance" }
    }

    private fun dates(data: OpeningBalanceCardData): String {
        val start = data.startDate?.let { formatDateOnly(it) } ?: "NA"
        val end = data.endDate?.let { formatDateOnly(it) } ?: "NA"
        return "$start – $end"
    }

    private fun block(data: OpeningBalanceCardData): String {
        val minutes = data.blockTimeTotal ?: 0
        return "Total Block: " + formatDuration(minutes)
    }
}