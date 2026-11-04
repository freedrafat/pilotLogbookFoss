package com.my.striptologbook.ui

import android.os.Bundle
import android.view.MenuItem
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.widget.Toolbar
import com.my.striptologbook.R
import com.my.striptologbook.data.CsvDateFormat
import com.my.striptologbook.data.CsvDurationFormat
import com.my.striptologbook.data.CsvExportFormat
import com.my.striptologbook.data.CsvFormatType
import com.my.striptologbook.utils.InsetsHelper
import com.my.striptologbook.utils.PreferencesManager

class CsvFormatActivity : BaseActivity() {

    private val dateFormatValues = CsvDateFormat.values()
    private val dateFormatLabels = listOf(
        "dd.MM.yyyy",
        "DDMMYYYY",
        "yyyy-MM-dd",
        "dd/MM/yyyy",
        "MM/dd/yyyy",
        "d MMM yyyy",
        "yyyyMMdd"
    )

    private val durationFormatValues = CsvDurationFormat.values()
    private val durationFormatLabels = listOf(
        "H:MM",
        "Minutes",
        "Decimal hours (2 decimals)",
        "HH:mm:ss"
    )

    private lateinit var rgFormatType: RadioGroup
    private lateinit var spDateFormat: Spinner
    private lateinit var spDurationFormat: Spinner
    private lateinit var cbIncludeSimulator: CheckBox
    private lateinit var allColumnCheckboxes: List<CheckBox>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_csv_format)

        InsetsHelper.applyStatusBarSpacer(findViewById(R.id.status_bar_spacer))

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = "CSV format"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val scrollView = findViewById<ScrollView>(R.id.scroll_view)
        InsetsHelper.attachScrollToFocusedView(this, scrollView)
        InsetsHelper.applyImeBottomPaddingToRoot(this)

        val prefs = PreferencesManager.getInstance(this)

        rgFormatType = findViewById(R.id.rg_format_type)
        spDateFormat = findViewById(R.id.sp_date_format)
        spDurationFormat = findViewById(R.id.sp_duration_format)
        cbIncludeSimulator = findViewById(R.id.cb_include_simulator)

        allColumnCheckboxes = listOf(
            findViewById(R.id.cb_include_takeoff_landing),
            findViewById(R.id.cb_include_day),
            findViewById(R.id.cb_include_number_to_day),
            findViewById(R.id.cb_include_number_to_night),
            findViewById(R.id.cb_include_number_to_total),
            findViewById(R.id.cb_include_number_ldg_day),
            findViewById(R.id.cb_include_number_ldg_night),
            findViewById(R.id.cb_include_number_ldg_total),
            findViewById(R.id.cb_include_number_instrument_approaches),
            findViewById(R.id.cb_include_is_pilot_flying),
            findViewById(R.id.cb_include_remarks)
        )

        spDateFormat.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            dateFormatLabels
        )
        spDurationFormat.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            durationFormatLabels
        )

        val cfg = prefs.getCsvExportFormat()
        bindConfigToFields(cfg)

        rgFormatType.setOnCheckedChangeListener { _, _ -> applyEnabledState() }
        applyEnabledState()

        findViewById<Button>(R.id.btn_save_csv_format).setOnClickListener {
            val newCfg = readConfigFromFields()
            prefs.setCsvExportFormat(newCfg)
            Toast.makeText(this, "CSV format saved", Toast.LENGTH_SHORT).show()
            finish()
        }

        findViewById<Button>(R.id.btn_restore_defaults_csv_format).setOnClickListener {
            bindConfigToFields(CsvExportFormat.DEFAULT)
            applyEnabledState()
            Toast.makeText(this, "Defaults restored (not yet saved)",
                Toast.LENGTH_SHORT).show()
        }
    }

    private fun bindConfigToFields(cfg: CsvExportFormat) {
        when (cfg.formatType) {
            CsvFormatType.PDF_COLUMNS -> rgFormatType.check(R.id.rb_format_pdf_columns)
            CsvFormatType.RAW -> rgFormatType.check(R.id.rb_format_raw)
            CsvFormatType.FORMATTED -> rgFormatType.check(R.id.rb_format_formatted)
        }

        val dIdx = dateFormatValues.indexOf(cfg.dateFormat).let { if (it >= 0) it else 0 }
        spDateFormat.setSelection(dIdx)

        val uIdx = durationFormatValues.indexOf(cfg.durationFormat).let { if (it >= 0) it else 0 }
        spDurationFormat.setSelection(uIdx)

        cbIncludeSimulator.isChecked = cfg.includeSimulator

        val cbs = allColumnCheckboxes
        cbs[0].isChecked = cfg.includeTakeoffLanding
        cbs[1].isChecked = cfg.includeDay
        cbs[2].isChecked = cfg.includeNumberToDay
        cbs[3].isChecked = cfg.includeNumberToNight
        cbs[4].isChecked = cfg.includeNumberToTotal
        cbs[5].isChecked = cfg.includeNumberLdgDay
        cbs[6].isChecked = cfg.includeNumberLdgNight
        cbs[7].isChecked = cfg.includeNumberLdgTotal
        cbs[8].isChecked = cfg.includeNumberInstrumentApproaches
        cbs[9].isChecked = cfg.includeIsPilotFlying
        cbs[10].isChecked = cfg.includeRemarks
    }

    private fun readConfigFromFields(): CsvExportFormat {
        val formatType = when (rgFormatType.checkedRadioButtonId) {
            R.id.rb_format_pdf_columns -> CsvFormatType.PDF_COLUMNS
            R.id.rb_format_raw -> CsvFormatType.RAW
            else -> CsvFormatType.FORMATTED
        }
        val cbs = allColumnCheckboxes
        return CsvExportFormat(
            formatType = formatType,
            dateFormat = dateFormatValues[spDateFormat.selectedItemPosition],
            durationFormat = durationFormatValues[spDurationFormat.selectedItemPosition],
            includeSimulator = cbIncludeSimulator.isChecked,
            includeTakeoffLanding = cbs[0].isChecked,
            includeDay = cbs[1].isChecked,
            includeNumberToDay = cbs[2].isChecked,
            includeNumberToNight = cbs[3].isChecked,
            includeNumberToTotal = cbs[4].isChecked,
            includeNumberLdgDay = cbs[5].isChecked,
            includeNumberLdgNight = cbs[6].isChecked,
            includeNumberLdgTotal = cbs[7].isChecked,
            includeNumberInstrumentApproaches = cbs[8].isChecked,
            includeIsPilotFlying = cbs[9].isChecked,
            includeRemarks = cbs[10].isChecked
        )
    }

    private fun applyEnabledState() {
        val format = when (rgFormatType.checkedRadioButtonId) {
            R.id.rb_format_raw -> CsvFormatType.RAW
            R.id.rb_format_pdf_columns -> CsvFormatType.PDF_COLUMNS
            else -> CsvFormatType.FORMATTED
        }
        // Date, duration, simulator: enabled for PDF_COLUMNS and FORMATTED
        val metaEnabled = format != CsvFormatType.RAW
        spDateFormat.isEnabled = metaEnabled
        spDurationFormat.isEnabled = metaEnabled
        cbIncludeSimulator.isEnabled = metaEnabled

        // Column checkboxes: enabled only for FORMATTED
        val colsEnabled = format == CsvFormatType.FORMATTED
        for (cb in allColumnCheckboxes) cb.isEnabled = colsEnabled
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}