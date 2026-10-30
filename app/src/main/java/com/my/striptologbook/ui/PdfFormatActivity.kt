package com.my.striptologbook.ui

import android.app.AlertDialog
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.MenuItem
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.Toolbar
import com.my.striptologbook.R
import com.my.striptologbook.data.PDF_PAGE_WIDTH
import com.my.striptologbook.data.PdfLayoutConfig
import com.my.striptologbook.utils.InsetsHelper
import com.my.striptologbook.utils.PreferencesManager
import com.my.striptologbook.utils.SignatureView
import java.io.File
import java.util.Locale

class PdfFormatActivity : BaseActivity() {

    private lateinit var spRows: Spinner
    private lateinit var spFontFamily: Spinner
    private lateinit var etOwner: EditText
    private lateinit var imgSignaturePreview: ImageView
    private lateinit var tvWidthSummary: TextView

    private val presetRows = listOf(13, 15, 18, 20, 25)

    // Order matters — indexes map to fontFamilyKeys below.
    private val fontLabels = listOf("Sans-serif", "Serif", "Monospace")
    private val fontFamilyKeys = listOf("SANS_SERIF", "SERIF", "MONOSPACE")

    private var currentSignaturePath: String? = null

    private lateinit var etMarginLeft: EditText
    private lateinit var etMarginRight: EditText
    private lateinit var etMarginTop: EditText
    private lateinit var etMarginBottom: EditText
    private lateinit var etTitleHeight: EditText
    private lateinit var etHeaderRowHeight: EditText
    private lateinit var etPageNumArea: EditText

    private lateinit var etWidthDate: EditText
    private lateinit var etWidthPlace: EditText
    private lateinit var etWidthTime: EditText
    private lateinit var etWidthAircraftModel: EditText
    private lateinit var etWidthAircraftReg: EditText
    private lateinit var etWidthDuration: EditText
    private lateinit var etWidthNamePic: EditText
    private lateinit var etWidthCount: EditText
    private lateinit var etWidthRemarks: EditText

    private lateinit var etTsTitle: EditText
    private lateinit var etTsHeader: EditText
    private lateinit var etTsBody: EditText
    private lateinit var etTsRemarks: EditText
    private lateinit var etTsCert: EditText
    private lateinit var etTsOwner: EditText
    private lateinit var cbAlternateRows: CheckBox

    private val widthWatchers = mutableListOf<EditText>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pdf_format)

        // -------- Status bar spacer ------------------------------
        InsetsHelper.applyStatusBarSpacer(findViewById(R.id.status_bar_spacer))

        // -------- Toolbar -----------------------------------------
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = "PDF format"              //change title
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // -------- ScrollView and focus / IME handling -------------
        val scrollView = findViewById<ScrollView>(R.id.scroll_view)
        InsetsHelper.attachScrollToFocusedView(this, scrollView)
        InsetsHelper.applyImeBottomPaddingToRoot(this)


        spRows = findViewById(R.id.sp_rows_per_page)
        spFontFamily = findViewById(R.id.sp_font_family)
        etOwner = findViewById(R.id.et_owner_name)
        imgSignaturePreview = findViewById(R.id.img_signature_preview)
        tvWidthSummary = findViewById(R.id.tv_width_summary)

        etMarginLeft       = findViewById(R.id.et_margin_left)
        etMarginRight      = findViewById(R.id.et_margin_right)
        etMarginTop        = findViewById(R.id.et_margin_top)
        etMarginBottom     = findViewById(R.id.et_margin_bottom)
        etTitleHeight      = findViewById(R.id.et_title_height)
        etHeaderRowHeight  = findViewById(R.id.et_header_row_height)
        etPageNumArea      = findViewById(R.id.et_page_num_area)

        etWidthDate          = findViewById(R.id.et_width_date)
        etWidthPlace         = findViewById(R.id.et_width_place)
        etWidthTime          = findViewById(R.id.et_width_time)
        etWidthAircraftModel = findViewById(R.id.et_width_aircraft_model)
        etWidthAircraftReg   = findViewById(R.id.et_width_aircraft_reg)
        etWidthDuration      = findViewById(R.id.et_width_duration)
        etWidthNamePic       = findViewById(R.id.et_width_name_pic)
        etWidthCount         = findViewById(R.id.et_width_count)
        etWidthRemarks       = findViewById(R.id.et_width_remarks)

        etTsTitle   = findViewById(R.id.et_ts_title)
        etTsHeader  = findViewById(R.id.et_ts_header)
        etTsBody    = findViewById(R.id.et_ts_body)
        etTsRemarks = findViewById(R.id.et_ts_remarks)
        etTsCert    = findViewById(R.id.et_ts_cert)
        etTsOwner   = findViewById(R.id.et_ts_owner)
        cbAlternateRows = findViewById(R.id.cb_alternate_rows)

        val prefs = PreferencesManager.getInstance(this)

        val cfg = prefs.getPdfLayoutConfig()
        bindConfigToFields(cfg)

        etOwner.setText(prefs.getPdfOwnerName())

        // Rows per page
        spRows.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            presetRows.map { it.toString() }
        )
        val current = prefs.getPdfRowsPerPage()
        val idx = presetRows.indexOf(current).let { if (it >= 0) it else presetRows.indexOf(18) }
        spRows.setSelection(idx)

        // Font family
        spFontFamily.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            fontLabels
        )
        val fontIdx = fontFamilyKeys.indexOf(cfg.fontFamily).let { if (it >= 0) it else 0 }
        spFontFamily.setSelection(fontIdx)
        cbAlternateRows.isChecked = cfg.alternateRowBackground

        currentSignaturePath = prefs.getPdfSignaturePath()
        updateSignaturePreview()

        widthWatchers.addAll(
            listOf(
                etWidthDate, etWidthPlace, etWidthTime,
                etWidthAircraftModel, etWidthAircraftReg,
                etWidthDuration, etWidthNamePic, etWidthCount,
                etWidthRemarks, etMarginLeft, etMarginRight
            )
        )
        widthWatchers.forEach { attachWidthWatcher(it) }
        updateWidthSummary()

        findViewById<Button>(R.id.btn_set_signature).setOnClickListener {
            showSignatureDialog()
        }

        findViewById<Button>(R.id.btn_set_defaults).setOnClickListener {
            bindConfigToFields(PdfLayoutConfig.DEFAULT)
            spFontFamily.setSelection(0)
            updateWidthSummary()
            Toast.makeText(this, "Defaults restored (not yet saved)", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.btn_save_format).setOnClickListener {
            val baseCfg = readConfigFromFields() ?: return@setOnClickListener
            val cfg = baseCfg.copy(
                fontFamily = fontFamilyKeys[spFontFamily.selectedItemPosition]
            )
            val rows = presetRows[spRows.selectedItemPosition]
            val owner = etOwner.text.toString().trim()
            prefs.setPdfLayoutConfig(cfg)
            prefs.setPdfRowsPerPage(rows)
            prefs.setPdfOwnerName(owner)
            Toast.makeText(this, "Format saved", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    // ------------------------------------------------------------
    // Config <-> fields
    // ------------------------------------------------------------

    private fun bindConfigToFields(c: PdfLayoutConfig) {
        etMarginLeft.setText(fmt(c.marginLeft))
        etMarginRight.setText(fmt(c.marginRight))
        etMarginTop.setText(fmt(c.marginTop))
        etMarginBottom.setText(fmt(c.marginBottom))
        etTitleHeight.setText(fmt(c.titleHeight))
        etHeaderRowHeight.setText(fmt(c.headerRowHeight))
        etPageNumArea.setText(fmt(c.pageNumArea))

        etWidthDate.setText(fmt(c.widthDate))
        etWidthPlace.setText(fmt(c.widthPlace))
        etWidthTime.setText(fmt(c.widthTime))
        etWidthAircraftModel.setText(fmt(c.widthAircraftModel))
        etWidthAircraftReg.setText(fmt(c.widthAircraftReg))
        etWidthDuration.setText(fmt(c.widthDuration))
        etWidthNamePic.setText(fmt(c.widthNamePic))
        etWidthCount.setText(fmt(c.widthCount))
        etWidthRemarks.setText(fmt(c.widthRemarks))

        etTsTitle.setText(fmt(c.titleTextSize))
        etTsHeader.setText(fmt(c.headerTextSize))
        etTsBody.setText(fmt(c.bodyTextSize))
        etTsRemarks.setText(fmt(c.remarksTextSize))
        etTsCert.setText(fmt(c.certTextSize))
        etTsOwner.setText(fmt(c.ownerTextSize))
        cbAlternateRows.isChecked = c.alternateRowBackground
    }

    private fun readConfigFromFields(): PdfLayoutConfig? {
        fun readF(et: EditText, name: String): Float? {
            val v = et.text.toString().trim().replace(',', '.').toFloatOrNull()
            if (v == null || v <= 0f) {
                Toast.makeText(this, "Invalid value for $name", Toast.LENGTH_SHORT).show()
                return null
            }
            return v
        }

        return try {
            PdfLayoutConfig(
                widthDate          = readF(etWidthDate, "Date")!!,
                widthPlace         = readF(etWidthPlace, "Places")!!,
                widthTime          = readF(etWidthTime, "Times")!!,
                widthAircraftModel = readF(etWidthAircraftModel, "Aircraft model")!!,
                widthAircraftReg   = readF(etWidthAircraftReg, "Aircraft reg")!!,
                widthDuration      = readF(etWidthDuration, "Duration")!!,
                widthNamePic       = readF(etWidthNamePic, "Name PIC")!!,
                widthCount         = readF(etWidthCount, "Count")!!,
                widthRemarks       = readF(etWidthRemarks, "Remarks")!!,

                marginLeft     = readF(etMarginLeft, "Margin left")!!,
                marginRight    = readF(etMarginRight, "Margin right")!!,
                marginTop      = readF(etMarginTop, "Margin top")!!,
                marginBottom   = readF(etMarginBottom, "Margin bottom")!!,
                titleHeight    = readF(etTitleHeight, "Title height")!!,
                headerRowHeight = readF(etHeaderRowHeight, "Header row height")!!,
                pageNumArea    = readF(etPageNumArea, "Page number area")!!,

                titleTextSize   = readF(etTsTitle, "Title text")!!,
                headerTextSize  = readF(etTsHeader, "Header text")!!,
                bodyTextSize    = readF(etTsBody, "Body text")!!,
                remarksTextSize = readF(etTsRemarks, "Remarks text")!!,
                certTextSize    = readF(etTsCert, "Cert text")!!,
                ownerTextSize   = readF(etTsOwner, "Owner text")!!,
                alternateRowBackground = cbAlternateRows.isChecked
                // fontFamily is set by the caller from the spinner
            )
        } catch (e: NullPointerException) {
            null
        }
    }

    private fun fmt(v: Float): String = if (v == v.toInt().toFloat())
        v.toInt().toString() else v.toString()

    // ------------------------------------------------------------
    // Live sum
    // ------------------------------------------------------------

    private fun attachWidthWatcher(et: EditText) {
        et.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) { updateWidthSummary() }
        })
    }

    private fun updateWidthSummary() {
        fun f(et: EditText): Float =
            et.text.toString().trim().replace(',', '.').toFloatOrNull() ?: 0f

        val cols = f(etWidthDate) +
                f(etWidthPlace) * 2f +
                f(etWidthTime) * 2f +
                f(etWidthAircraftModel) +
                f(etWidthAircraftReg) +
                f(etWidthDuration) * 11f +
                f(etWidthNamePic) +
                f(etWidthCount) * 4f +
                f(etWidthRemarks)

        val contentWidth = PDF_PAGE_WIDTH - f(etMarginLeft) - f(etMarginRight)
        val scale = if (cols > 0f) contentWidth / cols else 0f

        tvWidthSummary.text = String.format(
            Locale.US,
            "Sum: %.1f / Content width: %.1f  (scale %.3f)",
            cols, contentWidth, scale
        )
    }

    // ------------------------------------------------------------
    // Signature
    // ------------------------------------------------------------

    private fun updateSignaturePreview() {
        val path = currentSignaturePath
        if (path.isNullOrEmpty() || !File(path).exists()) {
            imgSignaturePreview.setImageDrawable(null)
            imgSignaturePreview.visibility = View.GONE
            return
        }
        val bmp = BitmapFactory.decodeFile(path)
        if (bmp != null) {
            imgSignaturePreview.setImageBitmap(bmp)
            imgSignaturePreview.visibility = View.VISIBLE
        } else imgSignaturePreview.visibility = View.GONE
    }

    private fun showSignatureDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_signature, null)
        val sigView = dialogView.findViewById<SignatureView>(R.id.signature_view)

        dialogView.findViewById<Button>(R.id.btn_clear_signature).setOnClickListener {
            sigView.clear()
        }

        AlertDialog.Builder(this)
            .setTitle("Set Signature")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                if (sigView.isEmpty()) {
                    Toast.makeText(this, "Signature is empty", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val bmp: Bitmap = sigView.toBitmap() ?: return@setPositiveButton
                val outFile = File(filesDir, "pdf_signature.png")
                outFile.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                currentSignaturePath = outFile.absolutePath
                PreferencesManager.getInstance(this).setPdfSignaturePath(currentSignaturePath)
                updateSignaturePreview()
                Toast.makeText(this, "Signature saved", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .setNeutralButton("Remove") { _, _ ->
                currentSignaturePath = null
                PreferencesManager.getInstance(this).setPdfSignaturePath(null)
                updateSignaturePreview()
                Toast.makeText(this, "Signature removed", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) { finish(); return true }
        return super.onOptionsItemSelected(item)
    }
}