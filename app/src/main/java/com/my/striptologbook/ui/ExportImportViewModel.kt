package com.my.striptologbook.ui

import android.content.ContentResolver
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import com.my.striptologbook.data.Leg
import com.my.striptologbook.repository.LegRepository
import com.my.striptologbook.utils.FileManager
import com.my.striptologbook.utils.PdfGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import java.io.IOException

sealed class ExportImportResult {
    object Success : ExportImportResult()
    data class Error(val message: String) : ExportImportResult()
}

class ExportImportViewModel(private val repository: LegRepository) : ViewModel() {

    suspend fun restoreFromJson(
        contentResolver: ContentResolver,
        uri: android.net.Uri
    ): ExportImportResult {
        return try {
            val jsonString = FileManager.readJsonFromUri(contentResolver, uri)
            val content = FileManager.readBackupFromJson(jsonString)

            if (content.legs.isEmpty() && content.openingBalance.isEmpty()) {
                return ExportImportResult.Error("The file contains no data")
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
            ExportImportResult.Success
        } catch (e: SerializationException) {
            ExportImportResult.Error("Invalid JSON format: ${e.message}")
        } catch (e: IOException) {
            ExportImportResult.Error("IO Error: ${e.message}")
        } catch (e: Exception) {
            ExportImportResult.Error("Restore failed: ${e.message}")
        }
    }

    suspend fun exportCsvLegList(
        contentResolver: ContentResolver,
        folder: DocumentFile,
        legs: List<Leg>,
        config: com.my.striptologbook.data.CsvExportFormat,
        useFilteredName: Boolean
    ): ExportImportResult {
        return try {
            if (legs.isEmpty()) {
                return ExportImportResult.Error("No legs to export")
            }
            val csv = FileManager.serializeLegsToCsv(config, legs)
            val fileName = if (useFilteredName) {
                FileManager.suggestFilteredCsvFileName()
            } else {
                FileManager.suggestExportFileName()
            }
            FileManager.writeTextToWorkDir(
                contentResolver = contentResolver,
                folder = folder,
                mimeType = "text/csv",
                fileName = fileName,
                content = csv
            )
            ExportImportResult.Success
        } catch (e: Exception) {
            ExportImportResult.Error("Export failed: ${e.message}")
        }
    }

    suspend fun exportPdfToFolder(
        contentResolver: ContentResolver,
        folder: DocumentFile,
        range: DateRange,
        rowsPerPage: Int,
        ownerName: String,
        signature: android.graphics.Bitmap?,
        config: com.my.striptologbook.data.PdfLayoutConfig
    ): ExportImportResult {
        return try {
            val legs: List<Leg> = when (range) {
                is DateRange.All       -> repository.getAllLegsList()
                is DateRange.Custom    -> repository.getLegsBetweenDates(range.start, range.end)
                is DateRange.LastPages -> repository.getAllLegsList()
            }
            if (legs.isEmpty()) {
                return ExportImportResult.Error("No legs in the selected range")
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
            ExportImportResult.Success
        } catch (e: Exception) {
            ExportImportResult.Error("PDF generation failed: ${e.message}")
        }
    }

    suspend fun exportPdfFiltered(
        contentResolver: ContentResolver,
        folder: DocumentFile,
        legs: List<Leg>,
        ownerName: String,
        headerRightLabel: String,
        rowsPerPage: Int,
        config: com.my.striptologbook.data.PdfLayoutConfig
    ): ExportImportResult {
        return try {
            if (legs.isEmpty()) {
                return ExportImportResult.Error("No legs to export")
            }
            val bytes = withContext(Dispatchers.Default) {
                PdfGenerator.generateFiltered(
                    legs = legs,
                    ownerName = ownerName,
                    headerRightLabel = headerRightLabel,
                    rowsPerPage = rowsPerPage,
                    config = config,
                    title = "Pilot Logbook"
                )
            }
            FileManager.writeBytesToWorkDir(
                contentResolver = contentResolver,
                folder = folder,
                mimeType = "application/pdf",
                fileName = FileManager.suggestFilteredPdfFileName(),
                bytes = bytes
            )
            ExportImportResult.Success
        } catch (e: Exception) {
            ExportImportResult.Error("PDF generation failed: ${e.message}")
        }
    }

    suspend fun deleteAllLogbook(): ExportImportResult {
        return try {
            repository.clearAllLegs()
            repository.clearAllOpeningBalances()
            ExportImportResult.Success
        } catch (e: Exception) {
            ExportImportResult.Error("Failed to delete data: ${e.message}")
        }
    }
}

sealed class DateRange {
    object All : DateRange()
    data class Custom(val start: Long, val end: Long) : DateRange()
    data class LastPages(val count: Int) : DateRange()
}