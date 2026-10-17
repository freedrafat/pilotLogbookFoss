package com.my.striptologbook.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.Toolbar
import com.my.striptologbook.R
import com.my.striptologbook.utils.InsetsHelper
import com.my.striptologbook.utils.PreferencesManager

class CustomListsPreferencesActivity : BaseActivity() {

    private lateinit var preferencesManager: PreferencesManager
    private lateinit var containerPosition: LinearLayout
    private lateinit var containerCategory: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_custom_lists_preferences)

        preferencesManager = PreferencesManager.getInstance(this)

        // -------- Toolbar ------------
        val spacer = findViewById<View>(R.id.status_bar_spacer)
        InsetsHelper.applyStatusBarSpacer(spacer)

        val scrollView = findViewById<ScrollView>(R.id.scroll_view)
        InsetsHelper.attachScrollToFocusedView(this, scrollView)
        InsetsHelper.applyImeBottomPaddingToRoot(this)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = "Custom Lists"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        // -------- Toolbar ------------


        containerPosition = findViewById(R.id.container_position)
        containerCategory = findViewById(R.id.container_category)

        // Populate from preferences
        preferencesManager.getCustomPositionList().forEach { addRow(containerPosition, it) }
        preferencesManager.getCustomCategoryList().forEach { addRow(containerCategory, it) }

        // Add buttons
        findViewById<Button>(R.id.btn_add_position).setOnClickListener {
            addRow(containerPosition, "")
        }
        findViewById<Button>(R.id.btn_add_category).setOnClickListener {
            addRow(containerCategory, "")
        }

        // Reset buttons
        findViewById<Button>(R.id.btn_reset_position).setOnClickListener {
            showResetDialog(
                title = "Reset Position list?",
                message = "This will replace the current Position list with the defaults.",
                onConfirm = {
                    clearRows(containerPosition)
                    PreferencesManager.DEFAULT_POSITION_LIST.forEach {
                        addRow(containerPosition, it)
                    }
                }
            )
        }
        findViewById<Button>(R.id.btn_reset_category).setOnClickListener {
            showResetDialog(
                title = "Reset Category list?",
                message = "This will replace the current Category list with the defaults.",
                onConfirm = {
                    clearRows(containerCategory)
                    PreferencesManager.DEFAULT_CATEGORY_LIST.forEach {
                        addRow(containerCategory, it)
                    }
                }
            )
        }

        // Save
        findViewById<Button>(R.id.btn_save_custom_lists).setOnClickListener {
            val positionRaw = readRows(containerPosition)
            val categoryRaw = readRows(containerCategory)

            // Reject if any entry contains '|'
            val hasPipe = (positionRaw + categoryRaw).any { it.contains("|") }
            if (hasPipe) {
                Toast.makeText(
                    this,
                    "Entries cannot contain the '|' character.",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            val position = normalize(positionRaw)
            val category = normalize(categoryRaw)

            preferencesManager.setCustomPositionList(position)
            preferencesManager.setCustomCategoryList(category)

            Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun addRow(container: LinearLayout, value: String) {
        val row = LayoutInflater.from(this)
            .inflate(R.layout.item_custom_list_row, container, false)

        val etValue = row.findViewById<EditText>(R.id.et_row_value)
        etValue.setText(value)

        row.findViewById<Button>(R.id.btn_row_remove).setOnClickListener {
            container.removeView(row)
        }

        container.addView(row)
    }

    private fun clearRows(container: LinearLayout) {
        container.removeAllViews()
    }

    private fun readRows(container: LinearLayout): List<String> {
        val result = mutableListOf<String>()
        for (i in 0 until container.childCount) {
            val row = container.getChildAt(i)
            val etValue = row.findViewById<EditText>(R.id.et_row_value)
            result.add(etValue.text.toString())
        }
        return result
    }

    private fun normalize(raw: List<String>): List<String> {
        val result = mutableListOf<String>()
        val seen = mutableSetOf<String>()
        for (entry in raw) {
            val trimmed = entry.trim()
            if (trimmed.isEmpty()) continue
            if (!seen.add(trimmed)) continue
            result.add(trimmed)
        }
        return result
    }

    private fun showResetDialog(title: String, message: String, onConfirm: () -> Unit) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Reset") { _, _ -> onConfirm() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}