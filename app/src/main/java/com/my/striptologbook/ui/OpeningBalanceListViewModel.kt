package com.my.striptologbook.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.my.striptologbook.LogbookApplication
import com.my.striptologbook.data.OpeningBalance
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class OpeningBalanceListViewModel(
    private val app: LogbookApplication
) : ViewModel() {

    val allRows: StateFlow<List<OpeningBalance>> =
        app.repository.getAllOpeningBalances()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun delete(row: OpeningBalance) {
        viewModelScope.launch {
            app.repository.deleteOpeningBalance(row)
        }
    }
}