package com.my.striptologbook.utils

import android.app.Activity
import android.view.View
import android.view.ViewTreeObserver
import android.widget.AutoCompleteTextView
import android.widget.EditText
import android.widget.ScrollView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * Reusable helpers for two common UI concerns:
 *  1. Making a top spacer view match the status bar height.
 *  2. Applying IME (keyboard) bottom padding to a ScrollView and keeping the
 *     focused editable field centered and visible.
 *
 * All functions are null-tolerant and no-op when the relevant view is absent.
 * Only [EditText] and [AutoCompleteTextView] are treated as "focusable editable"
 * for centering purposes, so activities with only buttons are unaffected.
 */
object InsetsHelper {

    /** Null-tolerant. Resizes the spacer to the status bar height. */
    fun applyStatusBarSpacer(spacerView: View?) {
        if (spacerView == null) return
        ViewCompat.setOnApplyWindowInsetsListener(spacerView) { view, insets ->
            val statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top
            if (statusBarHeight > 0 && view.layoutParams.height != statusBarHeight) {
                val params = view.layoutParams
                params.height = statusBarHeight
                view.requestLayout()
            }
            insets
        }
    }

    fun applyImeBottomPaddingToRoot(activity: Activity) {
        val rootView = activity.findViewById<View>(android.R.id.content) ?: return
        ViewCompat.setOnApplyWindowInsetsListener(rootView) { view, insets ->
            val imeBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            if (view.paddingBottom != imeBottom) {
                view.setPadding(
                    view.paddingLeft,
                    view.paddingTop,
                    view.paddingRight,
                    imeBottom
                )
            }
            insets
        }
        ViewCompat.requestApplyInsets(rootView)
    }


    /**
     * Null-tolerant. Applies IME bottom padding to the ScrollView and
     * re-centers the currently focused editable when the keyboard opens/closes.
     * No-op if the ScrollView is null or the activity has no content view.
     */
//    fun applyImeBottomPaddingToScrollView(activity: Activity, scrollView: ScrollView?) {
//        if (scrollView == null) return
//        val rootView = activity.findViewById<View>(android.R.id.content) ?: return
//
//        ViewCompat.setOnApplyWindowInsetsListener(rootView) { _, insets ->
//            val imeBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
//            if (scrollView.paddingBottom != imeBottom) {
//                scrollView.setPadding(
//                    scrollView.paddingLeft,
//                    scrollView.paddingTop,
//                    scrollView.paddingRight,
//                    imeBottom
//                )
//                centerCurrentFocus(activity, scrollView, animate = true)
//            }
//            insets
//        }
//        ViewCompat.requestApplyInsets(rootView)
//    }

    /**
     * Null-tolerant. Centers the currently focused editable inside the ScrollView
     * whenever focus changes. Only reacts to EditText / AutoCompleteTextView,
     * so buttons and other focusable views never trigger scrolling.
     */
    fun attachScrollToFocusedView(activity: Activity, scrollView: ScrollView?) {
        if (scrollView == null) return
        val rootView = activity.findViewById<View>(android.R.id.content) ?: return
        rootView.viewTreeObserver.addOnGlobalFocusChangeListener { _, newFocus ->
            if (isEditable(newFocus) && newFocus!!.isEnabled) {
                centerCurrentFocus(activity, scrollView, animate = true)
            }
        }
    }

    // ---------- internals ----------

    private fun isEditable(view: View?): Boolean =
        view is EditText || view is AutoCompleteTextView

    private fun centerCurrentFocus(activity: Activity, scrollView: ScrollView, animate: Boolean) {
        val focused = activity.currentFocus ?: return
        if (!isEditable(focused)) return

        scrollView.post {
            scrollView.viewTreeObserver.addOnPreDrawListener(
                object : ViewTreeObserver.OnPreDrawListener {
                    override fun onPreDraw(): Boolean {
                        scrollView.viewTreeObserver.removeOnPreDrawListener(this)

                        val location = IntArray(2)
                        focused.getLocationInWindow(location)
                        val scrollViewLoc = IntArray(2)
                        scrollView.getLocationInWindow(scrollViewLoc)

                        val offsetY = location[1] - scrollViewLoc[1] + scrollView.scrollY
                        val visibleHeight = scrollView.height -
                                scrollView.paddingTop - scrollView.paddingBottom
                        if (visibleHeight <= 0) return true

                        val targetScrollY = offsetY - (visibleHeight / 2f) +
                                (focused.height / 2f)
                        val contentHeight = scrollView.getChildAt(0)?.height ?: 0
                        val maxScrollY = (contentHeight - visibleHeight).coerceAtLeast(0)
                        val clampedTarget = targetScrollY.coerceIn(0f, maxScrollY.toFloat())

                        if (clampedTarget > 0) {
                            if (animate) scrollView.smoothScrollTo(0, clampedTarget.toInt())
                            else scrollView.scrollTo(0, clampedTarget.toInt())
                        }
                        return true
                    }
                }
            )
        }
    }
}