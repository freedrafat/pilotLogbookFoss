package com.my.striptologbook.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import androidx.recyclerview.widget.RecyclerView
import com.my.striptologbook.R
import com.my.striptologbook.data.SearchField

class PdfColumnAdapter(
    private val items: MutableList<Pair<SearchField, Boolean>>,
    private val onToggle: (() -> Unit)? = null
) : RecyclerView.Adapter<PdfColumnAdapter.VH>() {

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val cb: CheckBox = view.findViewById(R.id.cb_column)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_pdf_column, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val (field, checked) = items[position]
        holder.cb.setOnCheckedChangeListener(null)
        holder.cb.text = field.displayName
        holder.cb.isChecked = checked
        holder.cb.setOnCheckedChangeListener { _, isChecked ->
            items[position] = field to isChecked
            onToggle?.invoke()
        }
    }

    override fun getItemCount() = items.size

    fun setChecked(fieldNames: Collection<String>) {
        for (i in items.indices) {
            val (field, _) = items[i]
            items[i] = field to (field.name in fieldNames)
        }
        notifyDataSetChanged()
    }

    fun selectedFieldNames(): List<String> =
        items.filter { it.second }.map { it.first.name }
}