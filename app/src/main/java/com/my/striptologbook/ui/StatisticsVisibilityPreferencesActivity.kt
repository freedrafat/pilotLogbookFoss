package com.my.striptologbook.ui

import android.os.Bundle
import android.view.MenuItem
import android.widget.Button
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.my.striptologbook.R
import com.my.striptologbook.utils.InsetsHelper
import com.my.striptologbook.utils.PreferencesManager

class StatisticsVisibilityPreferencesActivity : BaseActivity() {

    private lateinit var rvVisibility: RecyclerView
    private lateinit var adapter: StatisticVisibilityAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_statistics_visibility_preferences)

        // -------- Status bar spacer ------------------------------
        InsetsHelper.applyStatusBarSpacer(findViewById(R.id.status_bar_spacer))

        // -------- Toolbar -----------------------------------------
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = "Statistics Visibility"              //change title
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // RecyclerView
        rvVisibility = findViewById(R.id.rv_statistics_visibility)
        rvVisibility.layoutManager = LinearLayoutManager(this)

        val stats = StatisticNames.ALL_STATISTICS
        // ✅ Use PreferencesManager instead of PreferencesHelper
        adapter = StatisticVisibilityAdapter(stats) { statName, isChecked ->
            PreferencesManager.getInstance(this).setStatisticVisible(statName, isChecked)
        }
        rvVisibility.adapter = adapter

        // Select All / Deselect All
        findViewById<Button>(R.id.btn_select_all).setOnClickListener {
            StatisticNames.ALL_STATISTICS.forEach { statName ->
                PreferencesManager.getInstance(this).setStatisticVisible(statName, true)
            }
            adapter.notifyDataSetChanged()
        }

        findViewById<Button>(R.id.btn_deselect_all).setOnClickListener {
            StatisticNames.ALL_STATISTICS.forEach { statName ->
                PreferencesManager.getInstance(this).setStatisticVisible(statName, false)
            }
            adapter.notifyDataSetChanged()
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