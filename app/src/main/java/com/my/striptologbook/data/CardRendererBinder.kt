package com.my.striptologbook.utils

import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.my.striptologbook.data.OpeningBalanceCardLine

/**
 * Writes the lines produced by OpeningBalanceCardRenderer into a
 * LinearLayout container. Used by both the row cards in the adapter
 * and the merged card in the list activity, so both always look the
 * same.
 */
object CardRendererBinder {

    fun bind(container: LinearLayout, lines: List<OpeningBalanceCardLine>) {
        container.removeAllViews()
        val ctx = container.context

        for (line in lines) {
            val tv = TextView(ctx).apply {
                text = line.text
                if (line.emphasis) {
                    setTextAppearance(com.my.striptologbook.R.style.TextAppearance_AppCompat_Medium)
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                } else {
                    setTextAppearance(com.my.striptologbook.R.style.TextAppearance_AppCompat_Medium)
                }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            container.addView(tv)
        }
    }
}