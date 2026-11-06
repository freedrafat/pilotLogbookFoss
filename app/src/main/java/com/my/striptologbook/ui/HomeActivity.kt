package com.my.striptologbook.ui

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.lifecycle.lifecycleScope
import com.my.striptologbook.LogbookApplication
import com.my.striptologbook.R
import com.my.striptologbook.utils.InsetsHelper
import com.my.striptologbook.utils.PreferencesManager
import com.my.striptologbook.utils.UpdateCheckResult
import com.my.striptologbook.utils.UpdateChecker
import com.my.striptologbook.utils.UpdateInfo
import com.my.striptologbook.utils.formatDuration
import com.my.striptologbook.utils.getDateAtMidnight
import com.my.striptologbook.utils.getTodayMidnight
import java.util.Calendar
import java.util.TimeZone
import kotlinx.coroutines.launch


class HomeActivity : BaseActivity(), UpdateAvailableDialogFragment.Listener {

//    private lateinit var viewModel: ExportImportViewModel
    private lateinit var txtAppVersion: TextView
    private lateinit var rowHomeOwner: View
    private lateinit var tvHomeOwnerValue: TextView
    private lateinit var tvHomeLast7DaysValue: TextView
    private lateinit var tvHomeLast28DaysValue: TextView
    private lateinit var tvHomeActualMonthValue: TextView
    private lateinit var tvHomeActualYearValue: TextView
    private lateinit var tvHomeTotalValue: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        // -------- Status bar spacer ------------------------------
        InsetsHelper.applyStatusBarSpacer(findViewById(R.id.status_bar_spacer))

        // -------- Toolbar -----------------------------------------
//        val toolbar = findViewById<Toolbar>(R.id.toolbar)
//        setSupportActionBar(toolbar)
//        supportActionBar?.title = "Home"              //change title
//        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // Silent auto update check (with a short delay so it never blocks
        // the first frame). The gate and timestamp logic live in
        // maybeAutoCheckForUpdate().
        Handler(Looper.getMainLooper()).postDelayed({
            lifecycleScope.launch {
                maybeAutoCheckForUpdate()
            }
        }, 1000)

//        val app = application as LogbookApplication
//        viewModel = ExportImportViewModel(app.repository)

        // ---------------------------------------------------

        txtAppVersion = findViewById(R.id.txtAppVersion)
        txtAppVersion.text = "App version: ${getAppVersionName()}"

        val btnViewLogbook: Button = findViewById(R.id.btn_view_logbook)
        val btnAddLeg: Button = findViewById(R.id.btn_add_leg)
        val btnUserManual: Button = findViewById(R.id.btn_user_manual)

        rowHomeOwner = findViewById(R.id.row_home_owner)
        tvHomeOwnerValue = findViewById(R.id.tv_home_owner_value)
        tvHomeLast7DaysValue = findViewById(R.id.tv_home_last_7_days_value)
        tvHomeLast28DaysValue = findViewById(R.id.tv_home_last_28_days_value)
        tvHomeActualMonthValue = findViewById(R.id.tv_home_actual_month_value)
        tvHomeActualYearValue = findViewById(R.id.tv_home_actual_year_value)
        tvHomeTotalValue = findViewById(R.id.tv_home_total_value)

        btnViewLogbook.setOnClickListener {
            startActivity(Intent(this, LegListActivity::class.java))
        }

        btnAddLeg.setOnClickListener {
            startActivity(Intent(this, LegEditActivity::class.java))
        }

        val btnTools: Button = findViewById(R.id.btn_tools)
        btnTools.setOnClickListener {
            startActivity(Intent(this, ToolsActivity::class.java))
        }

        val btnSettings: Button = findViewById(R.id.btn_settings)
        btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        btnUserManual.setOnClickListener {
            startActivity(Intent(this, ManualActivity::class.java))
        }

        // Observe ViewModel result
//        lifecycleScope.launch {
//            viewModel.result.collect { result ->
//                when (result) {
//                    is ExportImportResult.Success -> {
//                        Toast.makeText(this@HomeActivity, "Operation completed successfully", Toast.LENGTH_SHORT).show()
//                        viewModel.consumeResult()
//                    }
//                    is ExportImportResult.Error -> {
//                        Toast.makeText(this@HomeActivity, result.message, Toast.LENGTH_LONG).show()
//                        viewModel.consumeResult()
//                    }
//                    else -> {}
//                }
//            }
//        }

        // Back button
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finish()
            }
        })

        refreshBlockSummary()

    }

    override fun onResume() {
        super.onResume()
        refreshBlockSummary()
    }

    // ------------------------------------------------------------------------
    // Automatic update check
    // ------------------------------------------------------------------------

    /**
     * Runs a silent automatic update check if the user's preferences and
     * the 7-day throttle (when applicable) allow it.
     *
     * On UpdateAvailable / NoUpdateAvailable / InvalidResponse the timestamp
     * is written (the server was reached). On NoResponse it is not.
     *
     * Only UpdateAvailable produces UI: the "Update Available" prompt is
     * shown on top of Home. All other outcomes stay silent.
     */
    private suspend fun maybeAutoCheckForUpdate() {
        val prefs = PreferencesManager.getInstance(this)

        if (!prefs.isAutoCheckUpdatesEnabled()) return

        val now = System.currentTimeMillis()
        if (!prefs.isCheckUpdatesEveryStartEnabled()) {
            val sevenDaysMs = 7L * 24 * 60 * 60 * 1000
            val last = prefs.getLastUpdateCheck()
            if (last != 0L && now - last < sevenDaysMs) return
        }

        when (val result = UpdateChecker.checkForUpdate(this)) {
            is UpdateCheckResult.UpdateAvailable -> {
                prefs.setLastUpdateCheck(now)
                showUpdateAvailableDialog(result.info)
            }
            is UpdateCheckResult.NoUpdateAvailable -> prefs.setLastUpdateCheck(now)
            is UpdateCheckResult.InvalidResponse   -> prefs.setLastUpdateCheck(now)
            is UpdateCheckResult.NoResponse        -> { /* leave timestamp as is */ }
        }
    }

    /**
     * Shows the prompt via FragmentManager so it survives rotation.
     * Re-entry guard: if the fragment is already present (e.g. the check
     * somehow runs twice), we do not add a second instance.
     */
    private fun showUpdateAvailableDialog(info: UpdateInfo) {
        if (supportFragmentManager.findFragmentByTag(UpdateAvailableDialogFragment.TAG) != null) {
            return
        }
        UpdateAvailableDialogFragment
            .newInstance(info)
            .show(supportFragmentManager, UpdateAvailableDialogFragment.TAG)
    }

    // ------------------------------------------------------------------------
    // UpdateAvailableDialogFragment.Listener
    // ------------------------------------------------------------------------

    override fun onUpdateAccepted(info: UpdateInfo) {
        val intent = Intent(this, UpdatesActivity::class.java).apply {
            putExtra(UpdatesActivity.EXTRA_AUTO_UPDATE_URL, info.apkUrl)
            putExtra(UpdatesActivity.EXTRA_AUTO_UPDATE_FILENAME, info.fileName)
            putExtra(UpdatesActivity.EXTRA_AUTO_UPDATE_VERSION, info.versionName)
        }
        startActivity(intent)
    }

    private fun refreshBlockSummary() {
        val app = application as LogbookApplication
        val prefs = PreferencesManager.getInstance(this)

        val ownerName = prefs.getPdfOwnerName()

        lifecycleScope.launch {
            try {
                val allLegs = app.repository.getAllLegsList()
                val balanceTotals = app.repository.getOpeningBalanceTotals()

                val todayMidnight = getTodayMidnight()
                val last7Start = todayMidnight - 6L * 24 * 60 * 60 * 1000
                val last28Start = todayMidnight - 27L * 24 * 60 * 60 * 1000

                val monthStart = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                    timeInMillis = System.currentTimeMillis()
                    set(Calendar.DAY_OF_MONTH, 1)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis

                val yearStart = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                    timeInMillis = System.currentTimeMillis()
                    set(Calendar.DAY_OF_YEAR, 1)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis

                fun sumBlockInRange(start: Long, end: Long): Int {
                    var total = 0
                    for (leg in allLegs) {
                        val off = leg.offBlock ?: continue
                        val dateMidnight = getDateAtMidnight(off)
                        if (dateMidnight in start..end) {
                            total += leg.blockTimeTotal ?: 0
                        }
                    }
                    return total
                }

                val last7 = sumBlockInRange(last7Start, todayMidnight)
                val last28 = sumBlockInRange(last28Start, todayMidnight)
                val month = sumBlockInRange(monthStart, todayMidnight)
                val year = sumBlockInRange(yearStart, todayMidnight)

                var totalBlock = 0
                for (leg in allLegs) totalBlock += leg.blockTimeTotal ?: 0
                totalBlock += balanceTotals.blockTimeTotal

                if (ownerName.isBlank()) {
                    rowHomeOwner.visibility = View.GONE
                } else {
                    rowHomeOwner.visibility = View.VISIBLE
                    tvHomeOwnerValue.text = ownerName
                }
                tvHomeLast7DaysValue.text = formatDuration(last7)
                tvHomeLast28DaysValue.text = formatDuration(last28)
                tvHomeActualMonthValue.text = formatDuration(month)
                tvHomeActualYearValue.text = formatDuration(year)
                tvHomeTotalValue.text = formatDuration(totalBlock)
            } catch (_: Exception) {
                // Silent — the card keeps whatever values it last showed.
            }
        }
    }

    override fun onUpdateDeferred() {
        // "Later" — nothing to do. The timestamp was already written by
        // the check itself; the gate decides when the prompt comes back.
    }

    // ------------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------------

    private fun getAppVersionName(): String {
        return try {
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            packageInfo.versionName ?: "unknown"
        } catch (_: Exception) {
            "unknown"
        }
    }


}