package com.my.striptologbook.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.widget.SwitchCompat
import androidx.recyclerview.widget.RecyclerView
import com.my.striptologbook.R
import com.my.striptologbook.utils.PreferencesManager

class StatisticVisibilityAdapter(
    private val items: List<String>,
    private val onToggle: (String, Boolean) -> Unit
) : RecyclerView.Adapter<StatisticVisibilityAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_statistic_visibility, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val statName = items[position]
        holder.bind(statName, onToggle)
    }

    override fun getItemCount(): Int = items.size

    class ViewHolder(itemView: android.view.View) : RecyclerView.ViewHolder(itemView) {
        private val tvStatName = itemView.findViewById<android.widget.TextView>(R.id.tv_stat_name)
        private val swVisibility = itemView.findViewById<SwitchCompat>(R.id.sw_stat_visibility)

        fun bind(statName: String, onToggle: (String, Boolean) -> Unit) {
            tvStatName.text = statName
            val context = itemView.context
            // 🔥 Use PreferencesManager instead of PreferencesHelper
            val isVisible = PreferencesManager.getInstance(context).isStatisticVisible(statName)
            swVisibility.isChecked = isVisible
            swVisibility.setOnCheckedChangeListener { _, isChecked ->
                onToggle(statName, isChecked)
            }
        }
    }
}