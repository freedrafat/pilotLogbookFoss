package com.my.striptologbook.utils

import android.content.Intent
import android.net.Uri
import android.provider.MediaStore

object OpenFileIntentFactory {

    fun build(mimeType: String): Intent =
        Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = mimeType
            putExtra("android.intent.extra.INITIAL_URI", downloadsUri())
        }

    private fun downloadsUri(): Uri? = try {
        MediaStore.Downloads.EXTERNAL_CONTENT_URI
    } catch (_: Exception) {
        null
    }
}