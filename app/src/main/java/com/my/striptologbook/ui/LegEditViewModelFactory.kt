package com.my.striptologbook.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.my.striptologbook.data.AirportDataManager
import com.my.striptologbook.data.DefaultFields
import com.my.striptologbook.repository.LegRepository

class LegEditViewModelFactory(
    private val repository: LegRepository,
    private val legId: Long?,
    private val airportManager: AirportDataManager,
    private val defaultFields: DefaultFields
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LegEditViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return LegEditViewModel(repository, legId, airportManager, defaultFields) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}