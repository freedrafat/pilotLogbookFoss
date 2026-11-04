package com.my.striptologbook.utils

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.RadioButton
import android.widget.Toast
import com.my.striptologbook.R
import com.my.striptologbook.data.PdfLayoutConfig
import com.my.striptologbook.ui.DateRange
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Shared PDF range dialog used by ExportImportActivity and LegListActivity
 * (when the "All" filter is active).
 *
 * The dialog asks for one of: All / From-To / Last N pages. On Generate,
 * the caller receives the selected range plus the current PDF preferences
 * (rows per page, owner name, signature, layout config) read from
 * PreferencesManager.
 *
 * Callers are expected to dispatch the resulting data to their own
 * ViewModel method (exportPdfToFolder for both current call sites).
 */
object ExportRangeDialogHelper {

    fun show(
        activity: Activity,
        onGenerate: (range: DateRange,
                     rowsPerPage: Int,
                     ownerName: String,
                     signature: Bitmap?,
                     config: PdfLayoutConfig) -> Unit
    ) {
        val view = activity.layoutInflater.inflate(R.layout.dialog_date_range, null)
        val rgAll        = view.findViewById<RadioButton>(R.id.rb_all)
        val rgCustom     = view.findViewById<RadioButton>(R.id.rb_custom_range)
        val rgLastPages  = view.findViewById<RadioButton>(R.id.rb_last_pages)
        val btnFrom      = view.findViewById<Button>(R.id.btn_from_date)
        val btnTo        = view.findViewById<Button>(R.id.btn_to_date)
        val datesRow     = view.findViewById<View>(R.id.layout_dates)
        val lastPagesRow = view.findViewById<View>(R.id.layout_last_pages)
        val etLastPages  = view.findViewById<EditText>(R.id.et_last_pages_count)

        var fromDate: Long? = null
        var toDate: Long? = null

        fun refreshRows() {
            datesRow.visibility     = if (rgCustom.isChecked)    View.VISIBLE else View.GONE
            lastPagesRow.visibility = if (rgLastPages.isChecked) View.VISIBLE else View.GONE
        }
        rgAll.isChecked = true
        refreshRows()
        rgAll.setOnCheckedChangeListener { _, _ -> refreshRows() }
        rgCustom.setOnCheckedChangeListener { _, _ -> refreshRows() }
        rgLastPages.setOnCheckedChangeListener { _, _ -> refreshRows() }

        btnFrom.setOnClickListener {
            showCustomDatePicker(activity, fromDate) { picked ->
                fromDate = getDateAtMidnight(picked)
                btnFrom.text = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
                    .format(Date(fromDate!!))
            }
        }
        btnTo.setOnClickListener {
            showCustomDatePicker(activity, toDate) { picked ->
                toDate = getDateAtMidnight(picked) + 86_399_000L
                btnTo.text = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
                    .format(Date(toDate!!))
            }
        }

        AlertDialog.Builder(activity)
            .setTitle("Export PDF – range")
            .setView(view)
            .setPositiveButton("Generate") { _, _ ->
                val range: DateRange = when {
                    rgLastPages.isChecked -> {
                        val n = etLastPages.text.toString().trim().toIntOrNull() ?: 0
                        if (n < 1) {
                            Toast.makeText(activity,
                                "Enter a page count of at least 1",
                                Toast.LENGTH_SHORT).show()
                            return@setPositiveButton
                        }
                        DateRange.LastPages(n)
                    }
                    rgCustom.isChecked -> {
                        val f = fromDate ?: return@setPositiveButton
                        val t = toDate ?: return@setPositiveButton
                        if (f > t) {
                            Toast.makeText(activity,
                                "From date must be before To date",
                                Toast.LENGTH_SHORT).show()
                            return@setPositiveButton
                        }
                        DateRange.Custom(f, t)
                    }
                    else -> DateRange.All
                }
                val prefs = PreferencesManager.getInstance(activity)
                val rows = prefs.getPdfRowsPerPage()
                val owner = prefs.getPdfOwnerName()
                val cfg = prefs.getPdfLayoutConfig()
                val sig = prefs.getPdfSignaturePath()?.let { path ->
                    if (File(path).exists()) BitmapFactory.decodeFile(path) else null
                }
                onGenerate(range, rows, owner, sig, cfg)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}