package com.my.striptologbook.ui

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import com.my.striptologbook.R
import com.my.striptologbook.utils.InsetsHelper
import java.io.BufferedReader
import java.io.InputStreamReader

class ManualActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manual)

        // Setup Toolbar
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = "User Manual"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        InsetsHelper.applyStatusBarSpacer(findViewById(R.id.status_bar_spacer))

        val tvManualContent = findViewById<TextView>(R.id.tvManualContent)

        // Read the text file from assets
        try {
            val inputStream = assets.open("user_manual.txt")
            val reader = BufferedReader(InputStreamReader(inputStream))
            val stringBuilder = StringBuilder()
            var line: String?

            while (reader.readLine().also { line = it } != null) {
                stringBuilder.append(line).append("\n")
            }
            reader.close()

            tvManualContent.text = stringBuilder.toString()
        } catch (e: Exception) {
            tvManualContent.text = "Error loading manual: ${e.message}"
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}