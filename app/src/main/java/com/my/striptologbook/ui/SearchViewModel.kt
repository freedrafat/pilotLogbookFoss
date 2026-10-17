package com.my.striptologbook.ui

import androidx.lifecycle.ViewModel
import com.my.striptologbook.data.*
import com.my.striptologbook.repository.LegRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SearchViewModel(
    private val repository: LegRepository
) : ViewModel() {
    private val _conditions = MutableStateFlow<List<SearchCondition>>(emptyList())
    val conditions: StateFlow<List<SearchCondition>> = _conditions.asStateFlow()

    private val _logicOperator = MutableStateFlow(LogicOperator.AND)
    val logicOperator: StateFlow<LogicOperator> = _logicOperator.asStateFlow()

    fun addCondition(condition: SearchCondition) {
        _conditions.value += condition
    }

    fun removeCondition(condition: SearchCondition) {
        _conditions.value -= condition
    }

    fun setLogicOperator(operator: LogicOperator) {
        _logicOperator.value = operator
    }

    // get suggestions for a given field and query
    suspend fun getSuggestions(fieldName: String, query: String): List<String> {
        if (query.length < 2) return emptyList()

        return when (fieldName) {
            "flightNumber" -> repository.searchFlightNumber(query)
            "position" -> repository.searchPosition(query)
            "picName" -> repository.searchPicName(query)
            "sicName" -> repository.searchSicName(query)
            "jumpseat1" -> repository.searchJumpseat1(query)
            "jumpseat2" -> repository.searchJumpseat2(query)
            "cabinCrew" -> repository.searchCabinCrew(query)
            "acRegistration" -> repository.searchAcRegistration(query)
            "acType" -> repository.searchAcType(query)
            "category" -> repository.searchCategory(query)
            "icaoDeparture" -> repository.searchIcaoDeparture(query)
            "icaoDestination" -> repository.searchIcaoDestination(query)
            else -> emptyList()
        }
    }
}