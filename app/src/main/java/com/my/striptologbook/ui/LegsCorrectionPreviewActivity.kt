package com.my.striptologbook.ui

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.my.striptologbook.LogbookApplication
import com.my.striptologbook.R
import com.my.striptologbook.data.AirportDataManager
import com.my.striptologbook.utils.InsetsHelper
import com.my.striptologbook.utils.LegsCorrectionEngine
import com.my.striptologbook.utils.PreferencesManager
import com.my.striptologbook.utils.VerifiedBackupFlow
import com.my.striptologbook.utils.WorkFolderGateFlow
import com.my.striptologbook.utils.formatDateOnly
import com.my.striptologbook.utils.extractTimeOnly
import com.my.striptologbook.utils.formatDuration
import kotlinx.coroutines.launch

class LegsCorrectionPreviewActivity : BaseActivity() {

    private lateinit var adapter: PreviewAdapter
    private lateinit var btnApply: Button
    private lateinit var workDirGate: WorkFolderGateFlow
    private var correctedResults: List<LegsCorrectionEngine.CorrectionResult> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_legs_correction_preview)

        InsetsHelper.applyStatusBarSpacer(findViewById(R.id.status_bar_spacer))
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = "Correction Preview"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val legIds = intent.getLongArrayExtra("legIds") ?: longArrayOf()
        val overrideExisting = intent.getBooleanExtra("overrideExisting", false)
        val prefs = PreferencesManager.getInstance(this)
        val config = prefs.getLegsCorrectionConfig()
        val repo = (application as LogbookApplication).repository
        val defaultSimuIfrApproach = prefs.getDefaultSimuIfrApproach()
        val defaultAltitude = prefs.getDefaultAltitude()
        val airportManager = AirportDataManager.getInstance(this@LegsCorrectionPreviewActivity)

        workDirGate = WorkFolderGateFlow(this)

        val rv = findViewById<RecyclerView>(R.id.rv_preview)
        rv.layoutManager = LinearLayoutManager(this)
        adapter = PreviewAdapter()
        rv.adapter = adapter

        btnApply = findViewById(R.id.btn_apply)
        findViewById<Button>(R.id.btn_cancel).setOnClickListener { finish() }

        lifecycleScope.launch {
            val allLegs = repo.getAllLegsList()
            // Filter the database legs by the IDs passed from the search results
            val legsToCorrect = allLegs.filter { it.id in legIds }

            correctedResults = LegsCorrectionEngine.applyCorrections(
                legs = legsToCorrect,
                config = config,
                overrideExisting = overrideExisting,
                defaultSimuIfrApproach = defaultSimuIfrApproach,
                airportManager = airportManager,
                defaultAltitude = defaultAltitude
            )

            val itemsToShow = correctedResults.filter { it.hasChanges }
            adapter.submitList(itemsToShow)

            if (itemsToShow.isEmpty()) {
                Toast.makeText(this@LegsCorrectionPreviewActivity, "No fields to correct based on current preferences.", Toast.LENGTH_LONG).show()
                btnApply.isEnabled = false
            }
        }

        btnApply.setOnClickListener {
            workDirGate.runOrPick { folder ->
                lifecycleScope.launch {
                    val ok = VerifiedBackupFlow.backupThenProceed(
                        activity = this@LegsCorrectionPreviewActivity,
                        scope = lifecycleScope,
                        repository = repo,
                        folder = folder,
                        contentResolver = contentResolver
                    )
                    if (ok) {
                        val toUpdate = correctedResults.filter { it.hasChanges }.map { it.corrected }
                        repo.updateLegs(toUpdate)
                        Toast.makeText(this@LegsCorrectionPreviewActivity, "Legs updated successfully", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                }
            }
        }
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        if (item.itemId == android.R.id.home) { finish(); return true }
        return super.onOptionsItemSelected(item)
    }

    inner class PreviewAdapter : RecyclerView.Adapter<PreviewAdapter.ViewHolder>() {
        private var items: List<LegsCorrectionEngine.CorrectionResult> = emptyList()

        fun submitList(newItems: List<LegsCorrectionEngine.CorrectionResult>) {
            items = newItems
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
            val view = layoutInflater.inflate(R.layout.item_correction_preview, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(items[position])
        }

        override fun getItemCount() = items.size

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            private val tvHeader: TextView = view.findViewById(R.id.tv_leg_header)
            private val tvChanges: TextView = view.findViewById(R.id.tv_changes)

            fun bind(result: LegsCorrectionEngine.CorrectionResult) {
                val original = result.original
                val corrected = result.corrected

                val dateStr = original.offBlock?.let { formatDateOnly(it) } ?: "No Date"

                // Added acType and category to the header
                val typeStr = original.acType ?: "N/A"
                val catStr = original.category ?: "N/A"
                tvHeader.text = "$dateStr | ${original.flightNumber ?: "No Flight"} | $typeStr | $catStr"

                val sb = StringBuilder()

                fun appendField(name: String, origVal: Any?, corrVal: Any?, formatter: (Any) -> String = { it.toString() }) {
                    val origStr = origVal?.let { formatter(it) } ?: "N/A"
                    val corrStr = corrVal?.let { formatter(it) } ?: "N/A"
                    if (origVal != corrVal) {
                        sb.append("$name: $origStr -> $corrStr\n")
                    } else {
                        sb.append("$name: $origStr (unchanged)\n")
                    }
                }

                appendField("a/c Type", original.acType, corrected.acType)
                appendField("Category", original.category, corrected.category)

                appendField("Block Total", original.blockTimeTotal, corrected.blockTimeTotal) { formatDuration(it as Int) }
                appendField("Block Night", original.blockTimeNight, corrected.blockTimeNight) { formatDuration(it as Int) }
                appendField("Block Day", original.blockTimeDay, corrected.blockTimeDay) { formatDuration(it as Int) }

                appendField("Flight Total", original.flightTimeTotal, corrected.flightTimeTotal) { formatDuration(it as Int) }
                appendField("Flight Night", original.flightTimeNight, corrected.flightTimeNight) { formatDuration(it as Int) }
                appendField("Flight Day", original.flightTimeDay, corrected.flightTimeDay) { formatDuration(it as Int) }

                appendField("Takeoff", original.takeoff, corrected.takeoff) { extractTimeOnly(it as Long) }
                appendField("Landing", original.landing, corrected.landing) { extractTimeOnly(it as Long) }

                appendField("Sunrise", original.sunrise, corrected.sunrise) { extractTimeOnly(it as Long) }
                appendField("Sunset", original.sunset, corrected.sunset) { extractTimeOnly(it as Long) }

                appendField("IFR Total", original.ifrTotal, corrected.ifrTotal) { formatDuration(it as Int) }
                appendField("IFR Night", original.ifrNight, corrected.ifrNight) { formatDuration(it as Int) }
                appendField("IFR Day", original.ifrDay, corrected.ifrDay) { formatDuration(it as Int) }

                appendField("Approaches", original.numberInstrumentApproaches, corrected.numberInstrumentApproaches)
                appendField("Pilot Flying", original.isPilotFlying, corrected.isPilotFlying)

                appendField("TO Day", original.numberToDay, corrected.numberToDay)
                appendField("TO Night", original.numberToNight, corrected.numberToNight)
                appendField("TO Total", original.numberToTotal, corrected.numberToTotal)

                appendField("LDG Day", original.numberLdgDay, corrected.numberLdgDay)
                appendField("LDG Night", original.numberLdgNight, corrected.numberLdgNight)
                appendField("LDG Total", original.numberLdgTotal, corrected.numberLdgTotal)

                appendField("Remarks", original.remarks, corrected.remarks)

                tvChanges.text = sb.toString().trimEnd()
            }

        }
    }
}