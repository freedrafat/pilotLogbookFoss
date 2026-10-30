package com.my.striptologbook.ui

import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import androidx.annotation.LayoutRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.my.striptologbook.R

abstract class BaseActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Disable edge-to-edge on Android 15+ to avoid content overlapping status bar
        WindowCompat.setDecorFitsSystemWindows(window, true)
    }

    // -------- Toolbar ------------
    /**
     * Wires the shared toolbar's home button (if present) after the child
     * activity has inflated its content. Safe to call from any activity:
     * activities whose layout does not include the shared toolbar simply
     * get a no-op because findViewById returns null.
     */
    override fun setContentView(@LayoutRes layoutResID: Int) {
        super.setContentView(layoutResID)
        wireHomeButton()
    }

    private fun wireHomeButton() {
        findViewById<ImageButton>(R.id.btn_home)?.setOnClickListener {
            startActivity(
                Intent(this, HomeActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
            )
        }
    }
    // -------- Toolbar ------------

}