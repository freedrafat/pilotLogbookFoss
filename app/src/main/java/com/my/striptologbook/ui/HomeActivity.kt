package com.my.striptologbook.ui

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.lifecycle.lifecycleScope
import com.my.striptologbook.LogbookApplication
import com.my.striptologbook.R
import com.my.striptologbook.utils.PreferencesManager
import com.my.striptologbook.utils.UpdateCheckResult
import com.my.striptologbook.utils.UpdateChecker
import com.my.striptologbook.utils.UpdateInfo
import kotlinx.coroutines.launch

class HomeActivity : BaseActivity(), UpdateAvailableDialogFragment.Listener {

    private lateinit var viewModel: ExportImportViewModel
    private lateinit var txtAppVersion: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        // Silent auto update check (with a short delay so it never blocks
        // the first frame). The gate and timestamp logic live in
        // maybeAutoCheckForUpdate().
        Handler(Looper.getMainLooper()).postDelayed({
            lifecycleScope.launch {
                maybeAutoCheckForUpdate()
            }
        }, 1000)

        val app = application as LogbookApplication
        viewModel = ExportImportViewModel(app.repository)

        // ---------------------------------------------------

        txtAppVersion = findViewById(R.id.txtAppVersion)
        txtAppVersion.text = "App version: ${getAppVersionName()}"

        val btnViewLogbook: Button = findViewById(R.id.btn_view_logbook)
        val btnAddLeg: Button = findViewById(R.id.btn_add_leg)
        val btnUserManual: Button = findViewById(R.id.btn_user_manual)

        btnViewLogbook.setOnClickListener {
            startActivity(Intent(this, LegListActivity::class.java))
        }

        btnAddLeg.setOnClickListener {
            startActivity(Intent(this, LegEditActivity::class.java))
        }

        val btnFlightSun: Button = findViewById(R.id.btn_flight_sun)
        btnFlightSun.setOnClickListener {
            startActivity(Intent(this, FlightSunActivity::class.java))
        }

        val btnSettings: Button = findViewById(R.id.btn_settings)
        btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        btnUserManual.setOnClickListener {
            startActivity(Intent(this, ManualActivity::class.java))
        }

        // Observe ViewModel result
        lifecycleScope.launch {
            viewModel.result.collect { result ->
                when (result) {
                    is ExportImportResult.Success -> {
                        Toast.makeText(this@HomeActivity, "Operation completed successfully", Toast.LENGTH_SHORT).show()
                        viewModel.consumeResult()
                    }
                    is ExportImportResult.Error -> {
                        Toast.makeText(this@HomeActivity, result.message, Toast.LENGTH_LONG).show()
                        viewModel.consumeResult()
                    }
                    else -> {}
                }
            }
        }

        // Back button
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finish()
            }
        })
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