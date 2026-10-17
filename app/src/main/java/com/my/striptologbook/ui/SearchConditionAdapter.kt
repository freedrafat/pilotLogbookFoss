package com.my.striptologbook.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.my.striptologbook.R
import com.my.striptologbook.data.SearchCondition
import com.my.striptologbook.data.SearchFields
import com.my.striptologbook.data.SearchOperator
import com.my.striptologbook.utils.formatDuration
import com.my.striptologbook.utils.parseDuration
import com.my.striptologbook.utils.formatDateOnly
import com.my.striptologbook.utils.formatTime
import com.my.striptologbook.utils.parseDateFromDigits
import com.my.striptologbook.utils.parseTimeFromDigits

class SearchConditionAdapter(
    private val onDelete: (SearchCondition) -> Unit
) : ListAdapter<SearchCondition, SearchConditionAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_condition, parent, false)
        return ViewHolder(view, onDelete)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val condition = getItem(position)
        holder.bind(condition)
    }

    class ViewHolder(itemView: View, private val onDelete: (SearchCondition) -> Unit) :
        RecyclerView.ViewHolder(itemView) {
        private val tvCondition: TextView = itemView.findViewById(R.id.tv_condition)
        private val btnDelete: ImageButton = itemView.findViewById(R.id.btn_delete_condition)

        fun bind(condition: SearchCondition) {
            tvCondition.text = formatCondition(condition)
            btnDelete.setOnClickListener { onDelete(condition) }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<SearchCondition>() {
        override fun areItemsTheSame(oldItem: SearchCondition, newItem: SearchCondition): Boolean {
            return oldItem == newItem
        }

        override fun areContentsTheSame(oldItem: SearchCondition, newItem: SearchCondition): Boolean {
            return oldItem == newItem
        }
    }

    companion object {
        private fun formatCondition(cond: SearchCondition): String {
            val fieldDisplay = getFieldDisplayName(cond.fieldName)
            val opDisplay = when (cond.operator) {
                SearchOperator.EQ_CONTAINS -> "contains"
                SearchOperator.NOT_EQ -> "is not"
                SearchOperator.LE -> "<="
                SearchOperator.GE -> ">="
            }
            val valueDisplay = when (cond.valueType) {
                "empty" -> "empty"
                "timestamp_date" -> {
                    val v = cond.value
                    val parsed = v?.let { parseDateFromDigits(it) }
                    if (parsed != null) formatDateOnly(parsed) else (v ?: "?")
                }
                "timestamp_time" -> {
                    val v = cond.value
                    val parsed = v?.let { parseTimeFromDigits(it) }
                    if (parsed != null) formatTime(parsed) else (v ?: "?")
                }
                "timestamp" -> {
                    val v = cond.value
                    if (v == null) "?" else {
                        val parts = v.split(" ")
                        if (parts.size == 2) {
                            val d = parseDateFromDigits(parts[0])
                            val t = parseTimeFromDigits(parts[1])
                            if (d != null && t != null) "${formatDateOnly(d)} ${formatTime(t)}" else v
                        } else v
                    }
                }
                "duration" -> {
                    val minutes = cond.value?.let { parseDuration(it) }
                    if (minutes != null) formatDuration(minutes) else cond.value ?: "?"
                }
                else -> cond.value ?: "?"
            }
            return "$fieldDisplay $opDisplay $valueDisplay"
        }

        private fun getFieldDisplayName(fieldName: String): String {
            return SearchFields.allFields.find { it.name == fieldName }?.displayName ?: fieldName
        }
    }
}