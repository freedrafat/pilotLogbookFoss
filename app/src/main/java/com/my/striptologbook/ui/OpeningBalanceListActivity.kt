package com.my.striptologbook.ui

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import androidx.activity.viewModels
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.my.striptologbook.LogbookApplication
import com.my.striptologbook.R
import com.my.striptologbook.data.OpeningBalance
import com.my.striptologbook.data.OpeningBalanceCardData
import com.my.striptologbook.data.OpeningBalanceCardLine
import com.my.striptologbook.data.OpeningBalanceCardRenderer
import com.my.striptologbook.utils.CardRendererBinder
import com.my.striptologbook.utils.InsetsHelper
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class OpeningBalanceListActivity : BaseActivity() {

    private val viewModel: OpeningBalanceListViewModel by viewModels {
        OpeningBalanceListViewModelFactory(application as LogbookApplication)
    }

    private lateinit var adapter: OpeningBalanceListAdapter
    private lateinit var rv: RecyclerView
    private lateinit var cardMergedTotals: View
    private lateinit var mergedContainer: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_opening_balance_list)

        InsetsHelper.applyStatusBarSpacer(findViewById(R.id.status_bar_spacer))

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = "Opening Balance"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        rv = findViewById(R.id.rv_opening_balances)
        rv.layoutManager = LinearLayoutManager(this)
        adapter = OpeningBalanceListAdapter { row ->
            val intent = Intent(this, OpeningBalanceEditActivity::class.java).apply {
                putExtra("openingBalanceId", row.id)
            }
            startActivity(intent)
        }
        rv.adapter = adapter

        cardMergedTotals = findViewById(R.id.card_merged_totals)
        mergedContainer = cardMergedTotals as LinearLayout

        cardMergedTotals.setOnClickListener {
            val intent = Intent(this, OpeningBalanceEditActivity::class.java).apply {
                putExtra("mergedMode", true)
            }
            startActivity(intent)
        }

        findViewById<Button>(R.id.btn_add).setOnClickListener {
            startActivity(Intent(this, OpeningBalanceEditActivity::class.java))
        }

        lifecycleScope.launch {
            viewModel.allRows.collectLatest { rows ->
                adapter.submitList(rows)
                updateMergedCard(rows)
            }
        }
    }

    private suspend fun updateMergedCard(rows: List<OpeningBalance>) {
        val totals = (application as LogbookApplication).repository.getOpeningBalanceTotals()
        val data: OpeningBalanceCardData = OpeningBalanceCardData.fromMerged(totals, rows)
        val lines: List<OpeningBalanceCardLine> = OpeningBalanceCardRenderer.render(data)
        CardRendererBinder.bind(mergedContainer, lines)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}