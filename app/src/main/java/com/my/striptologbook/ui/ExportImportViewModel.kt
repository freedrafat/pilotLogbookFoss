package com.my.striptologbook.ui

import android.content.ContentResolver
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.my.striptologbook.data.Leg
import com.my.striptologbook.repository.LegRepository
import com.my.striptologbook.utils.FileManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.SerializationException
import java.io.IOException

import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import com.my.striptologbook.utils.PdfGenerator

sealed class ExportImportResult {
    object Success : ExportImportResult()
    data class Error(val message: String) : ExportImportResult()
    data class PdfReady(val bytes: ByteArray, val suggestedName: String) : ExportImportResult() {
        override fun equals(other: Any?) = this === other
        override fun hashCode() = System.identityHashCode(this)
    }
}

class ExportImportViewModel(private val repository: LegRepository) : ViewModel() {

    private val _result = MutableStateFlow<ExportImportResult?>(null)
    val result: StateFlow<ExportImportResult?> = _result.asStateFlow()

    private fun clearResult() {
        _result.value = null
    }

    // ------------------- Backup (JSON) -------------------
    fun backupToJson(contentResolver: ContentResolver, uri: Uri) {
        viewModelScope.launch {
            try {
                val legs = repository.getAllLegsList()
                val openingBalance = repository.getAllOpeningBalancesList()
                if (legs.isEmpty() && openingBalance.isEmpty()) {
                    _result.value = ExportImportResult.Error("Nothing to backup")
                    return@launch
                }
                val json = FileManager.serializeBackupToJson(legs, openingBalance)
                FileManager.writeJsonToUri(contentResolver, uri, json)
                _result.value = ExportImportResult.Success
            } catch (e: Exception) {
                _result.value = ExportImportResult.Error("Backup failed: ${e.message}")
            }
        }
    }

    // ------------------- Restore (JSON) -------------------
    fun restoreFromJson(contentResolver: ContentResolver, uri: Uri) {
        Log.d("RestoreDebug", "✅ restoreFromJson called with URI: $uri")
        viewModelScope.launch {
            try {
                Log.d("RestoreDebug", "📂 Reading file...")
                val jsonString = FileManager.readJsonFromUri(contentResolver, uri)
                Log.d("RestoreDebug", "📄 JSON length: ${jsonString.length}")
                Log.d("RestoreDebug", "📄 First 200 chars: ${jsonString.take(200)}")

                Log.d("RestoreDebug", "🔄 Deserializing...")
                val content = FileManager.readBackupFromJson(jsonString)
                Log.d("RestoreDebug", "✅ Deserialized ${content.legs.size} legs, " +
                        "${content.openingBalance.size} opening balance rows, " +
                        "hasOpeningBalanceKey=${content.hasOpeningBalanceKey}")

                if (content.legs.isEmpty() && content.openingBalance.isEmpty()) {
                    Log.d("RestoreDebug", "⚠️ Nothing to restore")
                    _result.value = ExportImportResult.Error("The file contains no data")
                    return@launch
                }

                Log.d("RestoreDebug", "🗑️ Clearing legs...")
                repository.clearAllLegs()
                Log.d("RestoreDebug", "✅ Legs cleared")

                if (content.legs.isNotEmpty()) {
                    Log.d("RestoreDebug", "📥 Inserting ${content.legs.size} legs...")
                    repository.insertAll(content.legs)
                    Log.d("RestoreDebug", "✅ Legs inserted")
                }

                // Option 1: opening balance is only restored when the file
                // actually carried an opening balance key. Old backups
                // (top-level array) do not, and they must leave the device's
                // opening balance untouched.
                if (content.hasOpeningBalanceKey) {
                    Log.d("RestoreDebug", "🗑️ Clearing opening balances...")
                    repository.clearAllOpeningBalances()
                    Log.d("RestoreDebug", "📥 Inserting ${content.openingBalance.size} opening balance rows...")
                    for (row in content.openingBalance) {
                        repository.insertOpeningBalance(row.copy(id = 0))
                    }
                    Log.d("RestoreDebug", "✅ Opening balances inserted")
                } else {
                    Log.d("RestoreDebug", "ℹ️ Old-format backup: opening balance left untouched")
                }

                _result.value = ExportImportResult.Success
            } catch (e: SerializationException) {
                Log.e("RestoreDebug", "❌ Serialization error", e)
                _result.value = ExportImportResult.Error("Invalid JSON format: ${e.message}")
            } catch (e: IOException) {
                Log.e("RestoreDebug", "❌ IO error", e)
                _result.value = ExportImportResult.Error("IO Error: ${e.message}")
            } catch (e: Exception) {
                Log.e("RestoreDebug", "❌ Unexpected error", e)
                _result.value = ExportImportResult.Error("Restore failed: ${e.message}")
            }
        }
    }

    // ------------------- Export CSV -------------------
    fun exportToCsv(contentResolver: ContentResolver, uri: Uri) {
        viewModelScope.launch {
            try {
                val legs = repository.getAllLegsList()
                if (legs.isEmpty()) {
                    _result.value = ExportImportResult.Error("No legs to export")
                    return@launch
                }
                val csv = FileManager.serializeLegsToCsv(legs)
                FileManager.writeJsonToUri(contentResolver, uri, csv) // reuses write method
                _result.value = ExportImportResult.Success
            } catch (e: Exception) {
                _result.value = ExportImportResult.Error("Export failed: ${e.message}")
            }
        }
    }


    fun exportPdf(
        range: DateRange,
        rowsPerPage: Int,
        ownerName: String,
        signature: android.graphics.Bitmap?,
        config: com.my.striptologbook.data.PdfLayoutConfig
    ) {
        viewModelScope.launch {
            try {
                val legs: List<Leg> = when (range) {
                    is DateRange.All       -> repository.getAllLegsList()
                    is DateRange.Custom    -> repository.getLegsBetweenDates(range.start, range.end)
                    is DateRange.LastPages -> repository.getAllLegsList()
                }
                if (legs.isEmpty()) {
                    _result.value = ExportImportResult.Error("No legs in the selected range")
                    return@launch
                }
                val endDate: Long = when (range) {
                    is DateRange.All       -> Long.MAX_VALUE
                    is DateRange.Custom    -> range.end
                    is DateRange.LastPages -> Long.MAX_VALUE
                }
                val grandTotal = repository.getTotalsUpTo(endDate)
                val lastPages  = (range as? DateRange.LastPages)?.count

                val bytes = withContext(Dispatchers.Default) {
                    PdfGenerator.generate(
                        legs        = legs,
                        grandTotal  = grandTotal,
                        rowsPerPage = rowsPerPage,
                        ownerName   = ownerName,
                        signature   = signature,
                        config      = config,
                        lastPages   = lastPages,
                        title       = "Pilot Logbook"
                    )
                }
                _result.value = ExportImportResult.PdfReady(bytes, FileManager.suggestPdfFileName())
            } catch (e: Exception) {
                _result.value = ExportImportResult.Error("PDF generation failed: ${e.message}")
            }
        }
    }

    fun deleteAllLegs() {
        viewModelScope.launch {
            try {
                repository.clearAllLegs()
                _result.value = ExportImportResult.Success
            } catch (e: Exception) {
                _result.value = ExportImportResult.Error("Failed to delete data: ${e.message}")
            }
        }
    }

    // Reset result after it's been consumed (by UI)
    fun consumeResult() {
        clearResult()
    }
}

sealed class DateRange {
    object All : DateRange()
    data class Custom(val start: Long, val end: Long) : DateRange()
    data class LastPages(val count: Int) : DateRange()
}