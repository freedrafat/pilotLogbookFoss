package com.my.striptologbook.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.my.striptologbook.R
import com.my.striptologbook.data.OpeningBalance
import com.my.striptologbook.data.OpeningBalanceCardData
import com.my.striptologbook.data.OpeningBalanceCardLine
import com.my.striptologbook.data.OpeningBalanceCardRenderer
import com.my.striptologbook.utils.CardRendererBinder

class OpeningBalanceListAdapter(
    private val onClick: (OpeningBalance) -> Unit
) : ListAdapter<OpeningBalance, OpeningBalanceListAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_opening_balance, parent, false)
        return ViewHolder(view, onClick)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(
        itemView: View,
        private val onClick: (OpeningBalance) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {

        private val container: LinearLayout = itemView.findViewById(R.id.card_lines_container)

        fun bind(row: OpeningBalance) {
            val data: OpeningBalanceCardData = OpeningBalanceCardData.fromRow(row)
            val lines: List<OpeningBalanceCardLine> = OpeningBalanceCardRenderer.render(data)
            CardRendererBinder.bind(container, lines)
            itemView.setOnClickListener { onClick(row) }
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<OpeningBalance>() {
        override fun areItemsTheSame(oldItem: OpeningBalance, newItem: OpeningBalance): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: OpeningBalance, newItem: OpeningBalance): Boolean =
            oldItem == newItem
    }
}