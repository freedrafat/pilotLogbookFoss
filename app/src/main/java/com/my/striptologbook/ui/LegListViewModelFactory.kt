package com.my.striptologbook.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.my.striptologbook.repository.LegRepository

class LegListViewModelFactory(
    private val repository: LegRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LegViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return LegViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}