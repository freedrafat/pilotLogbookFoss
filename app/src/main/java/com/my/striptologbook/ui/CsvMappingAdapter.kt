package com.my.striptologbook.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.my.striptologbook.R

class CsvMappingAdapter(
    private var mappings: List<Pair<String, LegField>>,
    private val allFields: List<LegField>,
    private val onFieldSelected: (header: String, field: LegField) -> Unit
) : RecyclerView.Adapter<CsvMappingAdapter.ViewHolder>() {

    // ---------- Companion object for static data ----------
    companion object {
        private enum class TimeGroup { OFF_BLOCK, TAKEOFF, LANDING, ON_BLOCK }
        private enum class Variant { FULL, DATE, TIME }

        private val specialFieldMap = mapOf(
            // OffBlock
            LegField.OFF_BLOCK_DATETIME to (TimeGroup.OFF_BLOCK to Variant.FULL),
            LegField.OFF_BLOCK_DATE_ONLY to (TimeGroup.OFF_BLOCK to Variant.DATE),
            LegField.OFF_BLOCK_TIME_ONLY to (TimeGroup.OFF_BLOCK to Variant.TIME),

            // Takeoff
            LegField.TAKEOFF_DATETIME to (TimeGroup.TAKEOFF to Variant.FULL),
            LegField.TAKEOFF_DATE_ONLY to (TimeGroup.TAKEOFF to Variant.DATE),
            LegField.TAKEOFF_TIME_ONLY to (TimeGroup.TAKEOFF to Variant.TIME),

            // Landing
            LegField.LANDING_DATETIME to (TimeGroup.LANDING to Variant.FULL),
            LegField.LANDING_DATE_ONLY to (TimeGroup.LANDING to Variant.DATE),
            LegField.LANDING_TIME_ONLY to (TimeGroup.LANDING to Variant.TIME),

            // OnBlock
            LegField.ON_BLOCK_DATETIME to (TimeGroup.ON_BLOCK to Variant.FULL),
            LegField.ON_BLOCK_DATE_ONLY to (TimeGroup.ON_BLOCK to Variant.DATE),
            LegField.ON_BLOCK_TIME_ONLY to (TimeGroup.ON_BLOCK to Variant.TIME)
        )
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_csv_mapping, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val (header, selectedField) = mappings[position]
        holder.bind(header, selectedField, allFields, mappings, onFieldSelected)
    }

    override fun getItemCount(): Int = mappings.size

    fun updateMappings(newMappings: List<Pair<String, LegField>>) {
        mappings = newMappings
        notifyDataSetChanged()
    }

    // ---------- ViewHolder ----------
    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvHeader: TextView = itemView.findViewById(R.id.tv_header)
        private val spinner: Spinner = itemView.findViewById(R.id.spinner_field)

        fun bind(
            header: String,
            selectedField: LegField,
            allFields: List<LegField>,
            allMappings: List<Pair<String, LegField>>,
            onFieldSelected: (String, LegField) -> Unit
        ) {
            tvHeader.text = header

            // Fields taken by other columns
            val otherSelected = allMappings.filter { it.first != header }.map { it.second }.toSet()

            // Build the available list with special group logic
            val available = allFields.filter { field ->
                // 1. Always allow IGNORE and the currently selected field for this column
                if (field == LegField.IGNORE || field == selectedField) {
                    return@filter true
                }

                // 2. Check if this field belongs to the 4 special groups
                val groupInfo = specialFieldMap[field]
                    ?: // Not in a special group → use standard uniqueness rule
                    return@filter !otherSelected.contains(field)

                val (group, variant) = groupInfo

                // 3. Determine what variants of this group are already taken by other columns
                val fullTaken = otherSelected.any { otherField ->
                    specialFieldMap[otherField]?.first == group && specialFieldMap[otherField]?.second == Variant.FULL
                }
                val dateTaken = otherSelected.any { otherField ->
                    specialFieldMap[otherField]?.first == group && specialFieldMap[otherField]?.second == Variant.DATE
                }
                val timeTaken = otherSelected.any { otherField ->
                    specialFieldMap[otherField]?.first == group && specialFieldMap[otherField]?.second == Variant.TIME
                }

                // 4. Apply the exact rules per variant
                when (variant) {
                    Variant.FULL -> !fullTaken && !dateTaken && !timeTaken
                    Variant.DATE -> !fullTaken && !dateTaken
                    Variant.TIME -> !fullTaken && !timeTaken
                }
            }

            // Spinner setup
            val adapter = ArrayAdapter(
                spinner.context,
                android.R.layout.simple_spinner_item,
                available
            ).apply {
                setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }

            spinner.adapter = adapter
            val index = available.indexOf(selectedField)
            if (index >= 0) spinner.setSelection(index)

            spinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: android.widget.AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    val newField = available[position]
                    if (newField != selectedField) {
                        onFieldSelected(header, newField)
                    }
                }

                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            }
        }
    }

}