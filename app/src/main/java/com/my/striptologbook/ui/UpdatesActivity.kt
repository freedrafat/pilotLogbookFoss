package com.my.striptologbook.ui

import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.SwitchCompat
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import com.my.striptologbook.LogbookApplication
import com.my.striptologbook.R
import com.my.striptologbook.utils.BackupManager
import com.my.striptologbook.utils.FileManager
import com.my.striptologbook.utils.InsetsHelper
import com.my.striptologbook.utils.PreferencesManager
import com.my.striptologbook.utils.UpdateCheckResult
import com.my.striptologbook.utils.UpdateChecker
import com.my.striptologbook.utils.UpdateDownloader
import com.my.striptologbook.utils.UpdateInfo
import com.my.striptologbook.utils.WorkDirectoryState
import com.my.striptologbook.utils.formatDateUtc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class UpdatesActivity : BaseActivity(), UpdateAvailableDialogFragment.Listener {

    companion object {
        const val EXTRA_AUTO_UPDATE_URL      = "extra_auto_update_url"
        const val EXTRA_AUTO_UPDATE_FILENAME = "extra_auto_update_filename"
        const val EXTRA_AUTO_UPDATE_VERSION  = "extra_auto_update_version"
    }

    private lateinit var txtAppVersion: TextView
    private lateinit var tvLastCheckDate: TextView
    private lateinit var btnCheckUpdate: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var switchAutoCheck: SwitchCompat
    private lateinit var switchCheckEveryStart: SwitchCompat

    private var pendingUpdateInfo: UpdateInfo? = null
    private var autoTriggered: Boolean = false

    // Folder picker for backup (if no folder set)
    private val folderPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            if (FileManager.persistWorkDirectoryPermission(this, uri)) {
                Toast.makeText(this, "Backup folder set successfully", Toast.LENGTH_SHORT).show()
                lifecycleScope.launch {
                    performBackupAndUpdate()
                }
            } else {
                Toast.makeText(
                    this,
                    "Failed to persist permission. Please try again.",
                    Toast.LENGTH_LONG
                ).show()
                pendingUpdateInfo = null
                btnCheckUpdate.isEnabled = true
            }
        } else {
            pendingUpdateInfo = null
            Toast.makeText(this, "Update cancelled", Toast.LENGTH_SHORT).show()
            btnCheckUpdate.isEnabled = true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_updates)

        // One-time cleanup of the orphan auto_install_apk key
        PreferencesManager.getInstance(this).clearLegacyAutoInstallKey()

        // -------- Status bar spacer ------------------------------
        InsetsHelper.applyStatusBarSpacer(findViewById(R.id.status_bar_spacer))

        // -------- Toolbar -----------------------------------------
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = "Updates"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // Views
        txtAppVersion          = findViewById(R.id.txtAppVersion)
        tvLastCheckDate        = findViewById(R.id.tv_last_check_date)
        btnCheckUpdate         = findViewById(R.id.btn_check_update)
        progressBar            = findViewById(R.id.progressBar)
        switchAutoCheck        = findViewById(R.id.switch_auto_check)
        switchCheckEveryStart  = findViewById(R.id.switch_check_every_start)

        txtAppVersion.text = "App version: ${getAppVersionName()}"

        val prefs = PreferencesManager.getInstance(this)

        // ----- Automatic check for updates (master) -----
        switchAutoCheck.isChecked = prefs.isAutoCheckUpdatesEnabled()
        switchAutoCheck.setOnCheckedChangeListener { _, isChecked ->
            prefs.setAutoCheckUpdatesEnabled(isChecked)
            updateCheckEveryStartEnabledState()
        }

        // ----- Check on every app start -----
        switchCheckEveryStart.isChecked = prefs.isCheckUpdatesEveryStartEnabled()
        switchCheckEveryStart.setOnCheckedChangeListener { _, isChecked ->
            prefs.setCheckUpdatesEveryStartEnabled(isChecked)
        }

        updateCheckEveryStartEnabledState()
        refreshLastCheckDate()

        btnCheckUpdate.setOnClickListener { runManualCheck() }

        // ------------------------------------------------------------------
        // Auto-triggered mode: Home already found an update and the user
        // tapped "Download" there. Skip the network check, skip the prompt,
        // go straight to folder -> backup -> enqueue.
        // ------------------------------------------------------------------
        val autoUrl      = intent.getStringExtra(EXTRA_AUTO_UPDATE_URL)
        val autoFileName = intent.getStringExtra(EXTRA_AUTO_UPDATE_FILENAME)
        val autoVersion  = intent.getStringExtra(EXTRA_AUTO_UPDATE_VERSION)
        if (autoUrl != null && autoFileName != null) {
            autoTriggered = true
            pendingUpdateInfo = UpdateInfo(autoUrl, autoFileName, autoVersion ?: "")
            btnCheckUpdate.isEnabled = false
            lifecycleScope.launch {
                performBackupAndUpdate()
            }
        }
    }

    // ------------------------------------------------------------------------
    // Manual check flow
    // ------------------------------------------------------------------------

    private fun runManualCheck() {
        lifecycleScope.launch {
            btnCheckUpdate.isEnabled = false
            progressBar.visibility = View.VISIBLE

            val prefs = PreferencesManager.getInstance(this@UpdatesActivity)
            val result = UpdateChecker.checkForUpdate(this@UpdatesActivity)
            progressBar.visibility = View.GONE

            // Manual check: always update the timestamp, regardless of outcome.
            prefs.setLastUpdateCheck(System.currentTimeMillis())
            refreshLastCheckDate()

            when (result) {
                is UpdateCheckResult.UpdateAvailable -> {
                    pendingUpdateInfo = result.info
                    performBackupAndUpdate()
                }
                is UpdateCheckResult.NoUpdateAvailable -> {
                    Toast.makeText(
                        this@UpdatesActivity,
                        "No update available",
                        Toast.LENGTH_LONG
                    ).show()
                    btnCheckUpdate.isEnabled = true
                }
                is UpdateCheckResult.InvalidResponse -> {
                    Toast.makeText(
                        this@UpdatesActivity,
                        "Invalid updates infos",
                        Toast.LENGTH_LONG
                    ).show()
                    btnCheckUpdate.isEnabled = true
                }
                is UpdateCheckResult.NoResponse -> {
                    Toast.makeText(
                        this@UpdatesActivity,
                        "Could not reach update server",
                        Toast.LENGTH_LONG
                    ).show()
                    btnCheckUpdate.isEnabled = true
                }
            }
        }
    }

    // ------------------------------------------------------------------------
    // Backup + update flow
    // ------------------------------------------------------------------------

    private suspend fun performBackupAndUpdate() {
        val prefs = PreferencesManager.getInstance(this)

        val folder = when (val state = FileManager.resolveWorkDirectory(this)) {
            WorkDirectoryState.NotSet -> {
                Toast.makeText(this, "Please select a backup folder first", Toast.LENGTH_LONG)
                    .show()
                folderPickerLauncher.launch(FileManager.workDirectoryPickerInitialUri(this))
                return
            }

            WorkDirectoryState.Invalid -> {
                prefs.setBackupFolderUri(null)
                Toast.makeText(
                    this,
                    "Backup folder unavailable. Please select it again.",
                    Toast.LENGTH_LONG
                ).show()
                folderPickerLauncher.launch(FileManager.workDirectoryPickerInitialUri(this))
                return
            }

            is WorkDirectoryState.Ready -> state.folder
        }

        try {
            val repository = (application as LogbookApplication).repository
            val legs = repository.getAllLegsList()
            val openingBalance = repository.getAllOpeningBalancesList()

            val result = withContext(Dispatchers.IO) {
                BackupManager.createVerifiedBackup(
                    legs = legs,
                    openingBalance = openingBalance,
                    folder = folder,
                    contentResolver = contentResolver
                )
            }

            when (result) {
                is BackupManager.BackupResult.Verified -> {
                    lifecycleScope.launch(Dispatchers.IO) {
                        BackupManager.cleanupOldBackups(folder)
                    }

                    val updateInfo = pendingUpdateInfo
                    if (updateInfo == null) {
                        btnCheckUpdate.isEnabled = true
                        return
                    }

                    if (autoTriggered) {
                        // User already accepted on Home — enqueue now.
                        val id = UpdateDownloader.enqueue(
                            this@UpdatesActivity,
                            updateInfo.apkUrl,
                            updateInfo.fileName
                        )
                        if (id == null) {
                            Toast.makeText(
                                this@UpdatesActivity,
                                "Could not start the download. Please try again.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                        pendingUpdateInfo = null
                        btnCheckUpdate.isEnabled = true
                    } else {
                        // Manual flow: show the confirmation dialog.
                        pendingUpdateInfo = null
                        showUpdateAvailableDialog(updateInfo)
                        // Button re-enabled by the fragment's callbacks
                    }
                }

                is BackupManager.BackupResult.Failed -> {
                    Toast.makeText(
                        this,
                        "Backup failed: ${result.reason}",
                        Toast.LENGTH_LONG
                    ).show()
                    pendingUpdateInfo = null
                    btnCheckUpdate.isEnabled = true
                }
            }
        } catch (e: Exception) {
            Log.e("UpdatesActivity", "Backup failed", e)
            Toast.makeText(
                this,
                "Backup failed: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
            pendingUpdateInfo = null
            btnCheckUpdate.isEnabled = true
        }
    }

    // ------------------------------------------------------------------------
    // UpdateAvailableDialogFragment.Listener
    // ------------------------------------------------------------------------

    override fun onUpdateAccepted(info: UpdateInfo) {
        val id = UpdateDownloader.enqueue(this, info.apkUrl, info.fileName)
        if (id == null) {
            Toast.makeText(
                this,
                "Could not start the download. Please try again.",
                Toast.LENGTH_LONG
            ).show()
        }
        btnCheckUpdate.isEnabled = true
    }

    override fun onUpdateDeferred() {
        btnCheckUpdate.isEnabled = true
    }

    // ------------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------------

    private fun showUpdateAvailableDialog(info: UpdateInfo) {
        if (supportFragmentManager.findFragmentByTag(UpdateAvailableDialogFragment.TAG) != null) {
            return
        }
        UpdateAvailableDialogFragment
            .newInstance(info)
            .show(supportFragmentManager, UpdateAvailableDialogFragment.TAG)
    }

    private fun updateCheckEveryStartEnabledState() {
        val enabled = switchAutoCheck.isChecked
        switchCheckEveryStart.isEnabled = enabled
        findViewById<View>(R.id.row_check_every_start).alpha = if (enabled) 1f else 0.4f
    }

    private fun refreshLastCheckDate() {
        val ts = PreferencesManager.getInstance(this).getLastUpdateCheck()
        tvLastCheckDate.text = if (ts == 0L) {
            "Last check: never"
        } else {
            "Last check: ${formatDateUtc(ts)}"
        }
    }

    private fun getAppVersionName(): String {
        return try {
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            packageInfo.versionName ?: "unknown"
        } catch (_: Exception) {
            "unknown"
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}