package com.my.striptologbook.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.my.striptologbook.LogbookApplication

class OpeningBalanceListViewModelFactory(
    private val app: LogbookApplication
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(OpeningBalanceListViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return OpeningBalanceListViewModel(app) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}