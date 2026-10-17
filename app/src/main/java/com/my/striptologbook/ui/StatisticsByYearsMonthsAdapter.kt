package com.my.striptologbook.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.my.striptologbook.R
import com.my.striptologbook.data.MonthStat
import com.my.striptologbook.data.YearStat
import com.my.striptologbook.utils.formatDuration

sealed class StatisticsItem {
    data class Year(val yearStat: YearStat) : StatisticsItem()
    data class Month(val monthStat: MonthStat, val year: Int) : StatisticsItem()
}

class StatisticsAdapter(
    private var items: List<StatisticsItem>,
    private val onYearClick: (Int) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is StatisticsItem.Year -> VIEW_TYPE_YEAR
            is StatisticsItem.Month -> VIEW_TYPE_MONTH
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_YEAR -> YearViewHolder(inflater.inflate(R.layout.item_statistics_year_header, parent, false))
            else -> MonthViewHolder(inflater.inflate(R.layout.item_statistics_month_row, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is StatisticsItem.Year -> (holder as YearViewHolder).bind(item.yearStat, onYearClick)
            is StatisticsItem.Month -> (holder as MonthViewHolder).bind(item.monthStat)
        }
    }

    override fun getItemCount(): Int = items.size

    fun updateItems(newItems: List<StatisticsItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    // -------- ViewHolders --------

    private class YearViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvYear: TextView = itemView.findViewById(R.id.tv_year)
        private val tvBlockTotal: TextView = itemView.findViewById(R.id.tv_block_total)
        private val tvBlockNight: TextView = itemView.findViewById(R.id.tv_block_night)
        private val tvLegCount: TextView = itemView.findViewById(R.id.tv_leg_count)
        private val tvSimTime: TextView = itemView.findViewById(R.id.tv_sim_time)

        fun bind(yearStat: YearStat, onYearClick: (Int) -> Unit) {
            tvYear.text = if (yearStat.isPrior) "Prior" else yearStat.year.toString()
            tvBlockTotal.text = formatDuration(yearStat.blockTotal.toInt())
            tvBlockNight.text = formatDuration(yearStat.blockNight.toInt())
            tvLegCount.text = yearStat.legCount.toString()
            tvSimTime.text = formatDuration(yearStat.simTime.toInt())

            if (yearStat.isPrior) {
                // Prior row is not expandable and not clickable.
                itemView.setOnClickListener(null)
                itemView.isClickable = false
            } else {
                itemView.isClickable = true
                itemView.setOnClickListener { onYearClick(yearStat.year) }
            }
        }
    }

    private class MonthViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvMonthName: TextView = itemView.findViewById(R.id.tv_month_name)
        private val tvBlockTotal: TextView = itemView.findViewById(R.id.tv_block_total)
        private val tvBlockNight: TextView = itemView.findViewById(R.id.tv_block_night)
        private val tvLegCount: TextView = itemView.findViewById(R.id.tv_leg_count)
        private val tvSimTime: TextView = itemView.findViewById(R.id.tv_sim_time)

        fun bind(monthStat: MonthStat) {
            val monthName = when (monthStat.month) {
                1 -> "Jan"
                2 -> "Feb"
                3 -> "Mar"
                4 -> "Apr"
                5 -> "May"
                6 -> "Jun"
                7 -> "Jul"
                8 -> "Aug"
                9 -> "Sep"
                10 -> "Oct"
                11 -> "Nov"
                12 -> "Dec"
                else -> ""
            }
            tvMonthName.text = monthName
            tvBlockTotal.text = formatDuration(monthStat.blockTotal.toInt())
            tvBlockNight.text = formatDuration(monthStat.blockNight.toInt())
            tvLegCount.text = monthStat.legCount.toString()
            tvSimTime.text = formatDuration(monthStat.simTime.toInt())
        }
    }

    companion object {
        private const val VIEW_TYPE_YEAR = 0
        private const val VIEW_TYPE_MONTH = 1
    }
}