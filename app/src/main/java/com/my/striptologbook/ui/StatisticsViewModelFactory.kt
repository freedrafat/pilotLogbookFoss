package com.my.striptologbook.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.my.striptologbook.LogbookApplication

class StatisticsViewModelFactory(
    private val context: Context,
    private val filterType: String?,
    private val customStart: Long?,
    private val customEnd: Long?,
    private val mode: String
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(StatisticsViewModel::class.java)) {
            val repository = (context.applicationContext as LogbookApplication).repository
            @Suppress("UNCHECKED_CAST")
            return StatisticsViewModel(
                repository,
                filterType,
                customStart,
                customEnd,
                mode,
                context.applicationContext // pass context
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}