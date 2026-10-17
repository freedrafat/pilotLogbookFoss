package com.my.striptologbook.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.my.striptologbook.R
import com.my.striptologbook.data.Leg
import com.my.striptologbook.data.LegProfile
import com.my.striptologbook.data.LegRowItem
import com.my.striptologbook.data.LegRowRenderer

/**
 * Renders each Leg as a 4-line card. What goes on each line is fully
 * driven by [profile] (a LegProfile coming from PreferencesManager).
 *
 * item_leg.xml itself is unchanged: we still populate tv_line1..tv_comment.
 */
class LegAdapter(
    private val profile: LegProfile,
    private val onItemClick: (Leg) -> Unit
) : ListAdapter<Leg, LegAdapter.LegViewHolder>(DiffCallback()) {

    /**
     * Pre-computed row item lists (always exactly 4 after normalization in
     * LegItemLayout). Kept as a val so we don't re-index on every bind.
     */
    private val rows: List<List<LegRowItem>> = (0 until 4).map { i ->
        profile.rows.getOrNull(i).orEmpty()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LegViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_leg, parent, false)
        return LegViewHolder(view, rows, onItemClick)
    }

    override fun onBindViewHolder(holder: LegViewHolder, position: Int) {
        val leg = getItem(position)
        holder.bind(leg)
    }

    class LegViewHolder(
        itemView: View,
        private val rows: List<List<LegRowItem>>,
        private val onItemClick: (Leg) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        private val tvLine1: TextView = itemView.findViewById(R.id.tv_line1)
        private val tvLine2: TextView = itemView.findViewById(R.id.tv_line2)
        private val tvBlock: TextView = itemView.findViewById(R.id.tv_block)
        private val tvComment: TextView = itemView.findViewById(R.id.tv_comment)

        fun bind(leg: Leg) {
            val ctx = itemView.context
            tvLine1.text   = LegRowRenderer.renderRow(rows[0], leg, ctx)
            tvLine2.text   = LegRowRenderer.renderRow(rows[1], leg, ctx)
            tvBlock.text   = LegRowRenderer.renderRow(rows[2], leg, ctx)
            tvComment.text = LegRowRenderer.renderRow(rows[3], leg, ctx)

            itemView.setOnClickListener { onItemClick(leg) }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<Leg>() {
        override fun areItemsTheSame(oldItem: Leg, newItem: Leg): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Leg, newItem: Leg): Boolean {
            return oldItem == newItem
        }
    }
}
