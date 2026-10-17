package com.my.striptologbook.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.my.striptologbook.LogbookApplication

class OpeningBalanceEditViewModelFactory(
    private val app: LogbookApplication,
    private val balanceId: Long?,
    private val mergedMode: Boolean = false
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(OpeningBalanceEditViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return OpeningBalanceEditViewModel(app.repository, balanceId, mergedMode) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}