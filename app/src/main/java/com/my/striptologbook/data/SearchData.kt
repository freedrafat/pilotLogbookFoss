package com.my.striptologbook.data

import kotlinx.serialization.Serializable

enum class SearchOperator {
    EQ_CONTAINS, // = / contains
    NOT_EQ,      // <> is not
    LE,          // <=
    GE           // >=
}

enum class LogicOperator {
    AND,
    OR
}

@Serializable
data class SearchCondition(
    val fieldName: String,
    val operator: SearchOperator,
    val value: String? = null,
    val valueType: String // "string", "boolean", "timestamp", "duration", "number", "empty"
)

@Serializable
data class SearchQuery(
    val conditions: List<SearchCondition>,
    val logicOperator: LogicOperator
)