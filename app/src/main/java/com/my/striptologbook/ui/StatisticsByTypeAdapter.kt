package com.my.striptologbook.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.my.striptologbook.R
import com.my.striptologbook.data.TypeStatRow
import com.my.striptologbook.utils.formatDuration

class StatisticsByTypeAdapter(
    private val rows: List<TypeStatRow>
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val positions = rows.firstOrNull()?.positionTotals?.keys?.toList()?.sorted() ?: emptyList()
    private val totalWeight = 1f + (positions.size * 0.8f) + 0.8f // statistic + positions + total

    override fun getItemViewType(position: Int): Int {
        return if (position == 0) HEADER else ITEM
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            HEADER -> {
                val view = inflater.inflate(R.layout.item_statistics_type_header, parent, false)
                HeaderViewHolder(view, positions, totalWeight)
            }
            else -> {
                val view = inflater.inflate(R.layout.item_statistics_type_row, parent, false)
                RowViewHolder(view, positions, totalWeight)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is HeaderViewHolder -> holder.bind()
            is RowViewHolder -> holder.bind(rows[position - 1]) // -1 because header is first
        }
    }

    override fun getItemCount(): Int = rows.size + 1 // +1 for header

    // -------- ViewHolders --------

    private class HeaderViewHolder(
        itemView: View,
        private val positions: List<String>,
        private val totalWeight: Float
    ) : RecyclerView.ViewHolder(itemView) {
        fun bind() {
            val container = itemView as LinearLayout
            container.removeAllViews()
            container.weightSum = totalWeight

            addTextView(container, "Statistic", bold = true, weight = 1f)
            positions.forEach {
                addTextView(container, it, bold = true, weight = 0.8f)
            }
            addTextView(container, "Total", bold = true, weight = 0.8f)
        }

        private fun addTextView(parent: LinearLayout, text: String, bold: Boolean, weight: Float) {
            val tv = TextView(parent.context).apply {
                this.text = text
                if (bold) setTypeface(null, android.graphics.Typeface.BOLD)
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, weight)
            }
            parent.addView(tv)
        }
    }

    private class RowViewHolder(
        itemView: View,
        private val positions: List<String>,
        private val totalWeight: Float
    ) : RecyclerView.ViewHolder(itemView) {
        fun bind(row: TypeStatRow) {
            val container = itemView as LinearLayout
            container.removeAllViews()
            container.weightSum = totalWeight

            // Statistic name
            addTextView(container, row.statisticName, bold = false, weight = 1f)

            // Position values
            positions.forEach { pos ->
                val value = row.positionTotals[pos] ?: 0L
                val display = if (row.statisticName.startsWith("#")) {
                    value.toString()
                } else {
                    formatDuration(value.toInt())
                }
                addTextView(container, display, bold = false, weight = 0.8f)
            }

            // Total
            val totalDisplay = if (row.statisticName.startsWith("#")) {
                row.total.toString()
            } else {
                formatDuration(row.total.toInt())
            }
            addTextView(container, totalDisplay, bold = true, weight = 0.8f)
        }

        private fun addTextView(parent: LinearLayout, text: String, bold: Boolean, weight: Float) {
            val tv = TextView(parent.context).apply {
                this.text = text
                if (bold) setTypeface(null, android.graphics.Typeface.BOLD)
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, weight)
            }
            parent.addView(tv)
        }
    }

    companion object {
        private const val HEADER = 1
        private const val ITEM = 0
    }
}