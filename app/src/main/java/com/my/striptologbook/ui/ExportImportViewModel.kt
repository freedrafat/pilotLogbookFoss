package com.my.striptologbook.ui

import android.content.ContentResolver
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.my.striptologbook.data.Leg
import com.my.striptologbook.repository.LegRepository
import com.my.striptologbook.utils.FileManager
import com.my.striptologbook.utils.PdfGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import java.io.IOException

sealed class ExportImportResult {
    object Success : ExportImportResult()
    data class Error(val message: String) : ExportImportResult()
}

class ExportImportViewModel(private val repository: LegRepository) : ViewModel() {

    private val _result = MutableStateFlow<ExportImportResult?>(null)
    val result: StateFlow<ExportImportResult?> = _result.asStateFlow()

    private fun clearResult() {
        _result.value = null
    }

    // ------------------- Backup (JSON) -------------------
    fun backupToJson(contentResolver: ContentResolver, uri: android.net.Uri) {
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
    fun restoreFromJson(contentResolver: ContentResolver, uri: android.net.Uri) {
        viewModelScope.launch {
            try {
                val jsonString = FileManager.readJsonFromUri(contentResolver, uri)
                val content = FileManager.readBackupFromJson(jsonString)

                if (content.legs.isEmpty() && content.openingBalance.isEmpty()) {
                    _result.value = ExportImportResult.Error("The file contains no data")
                    return@launch
                }

                repository.clearAllLegs()
                if (content.legs.isNotEmpty()) {
                    repository.insertAll(content.legs)
                }
                if (content.hasOpeningBalanceKey) {
                    repository.clearAllOpeningBalances()
                    for (row in content.openingBalance) {
                        repository.insertOpeningBalance(row.copy(id = 0))
                    }
                }
                _result.value = ExportImportResult.Success
            } catch (e: SerializationException) {
                _result.value = ExportImportResult.Error("Invalid JSON format: ${e.message}")
            } catch (e: IOException) {
                _result.value = ExportImportResult.Error("IO Error: ${e.message}")
            } catch (e: Exception) {
                _result.value = ExportImportResult.Error("Restore failed: ${e.message}")
            }
        }
    }

    // ------------------- Export CSV -------------------
    fun exportToCsv(contentResolver: ContentResolver, folder: DocumentFile) {
        viewModelScope.launch {
            try {
                val legs = repository.getAllLegsList()
                if (legs.isEmpty()) {
                    _result.value = ExportImportResult.Error("No legs to export")
                    return@launch
                }
                val csv = FileManager.serializeLegsToCsv(legs)
                FileManager.writeTextToWorkDir(
                    contentResolver = contentResolver,
                    folder = folder,
                    mimeType = "text/csv",
                    fileName = FileManager.suggestExportFileName(),
                    content = csv
                )
                _result.value = ExportImportResult.Success
            } catch (e: Exception) {
                _result.value = ExportImportResult.Error("Export failed: ${e.message}")
            }
        }
    }

    // ------------------- Export PDF -------------------
    fun exportPdfToFolder(
        contentResolver: ContentResolver,
        folder: DocumentFile,
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
                FileManager.writeBytesToWorkDir(
                    contentResolver = contentResolver,
                    folder = folder,
                    mimeType = "application/pdf",
                    fileName = FileManager.suggestPdfFileName(),
                    bytes = bytes
                )
                _result.value = ExportImportResult.Success
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

    fun consumeResult() {
        clearResult()
    }
}

sealed class DateRange {
    object All : DateRange()
    data class Custom(val start: Long, val end: Long) : DateRange()
    data class LastPages(val count: Int) : DateRange()
}