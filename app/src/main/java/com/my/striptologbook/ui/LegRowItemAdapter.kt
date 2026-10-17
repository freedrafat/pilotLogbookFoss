package com.my.striptologbook.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.my.striptologbook.R
import com.my.striptologbook.data.LegItemFields
import com.my.striptologbook.data.LegRowItem

/**
 * Shows the items of ONE row of the current profile.
 *
 * Deliberately a plain RecyclerView.Adapter (not ListAdapter): the item
 * counts are tiny (a handful per row) and the list is mutated constantly
 * during editing, so DiffUtil brings no benefit and only complicates the
 * enable/disable logic.
 */
class LegRowItemAdapter(
    private val isEditableProvider: () -> Boolean,
    private val onMoveUp: (position: Int) -> Unit,
    private val onMoveDown: (position: Int) -> Unit,
    private val onDelete: (position: Int) -> Unit,
    private val onEdit: (position: Int) -> Unit
) : RecyclerView.Adapter<LegRowItemAdapter.VH>() {

    private val items = mutableListOf<LegRowItem>()

    fun submit(newItems: List<LegRowItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_leg_row_item, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(items[position], position, items.size)
    }

    inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvLabel: TextView = itemView.findViewById(R.id.tv_item_label)
        private val btnMoveUp: ImageButton = itemView.findViewById(R.id.btn_move_up)
        private val btnMoveDown: ImageButton = itemView.findViewById(R.id.btn_move_down)
        private val btnDelete: ImageButton = itemView.findViewById(R.id.btn_delete)

        fun bind(item: LegRowItem, position: Int, total: Int) {
            tvLabel.text = labelFor(item)

            val editable = isEditableProvider()
            btnMoveUp.isEnabled = editable && position > 0
            btnMoveDown.isEnabled = editable && position < total - 1
            btnDelete.isEnabled = editable
            itemView.isClickable = editable

            btnMoveUp.setOnClickListener {
                val p = bindingAdapterPosition
                if (p != RecyclerView.NO_POSITION) onMoveUp(p)
            }
            btnMoveDown.setOnClickListener {
                val p = bindingAdapterPosition
                if (p != RecyclerView.NO_POSITION) onMoveDown(p)
            }
            btnDelete.setOnClickListener {
                val p = bindingAdapterPosition
                if (p != RecyclerView.NO_POSITION) onDelete(p)
            }
            itemView.setOnClickListener {
                val p = bindingAdapterPosition
                if (p != RecyclerView.NO_POSITION) onEdit(p)
            }
        }
    }

    companion object {
        fun labelFor(item: LegRowItem): String = when (item.type) {
            LegRowItem.TYPE_FIELD -> {
                val field = LegItemFields.getById(item.fieldId)
                val fieldName = field?.displayName ?: (item.fieldId ?: "?")
                val rendererName = item.rendererId?.let { rid ->
                    field?.renderers?.find { it.id == rid }?.displayName ?: rid
                }
                if (rendererName != null) "$fieldName ($rendererName)" else fieldName
            }
            LegRowItem.TYPE_LITERAL -> "Text: \"${item.text ?: ""}\""
            else -> "?"
        }
    }
}