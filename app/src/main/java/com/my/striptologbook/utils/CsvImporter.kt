package com.my.striptologbook.utils

import java.io.InputStream
import java.io.InputStreamReader
import java.io.BufferedReader

data class CsvData(
    val headers: List<String>,
    val rows: List<List<String>>,
)

object CsvImporter {

    /**
     * Reads CSV from an InputStream and returns structured data with headers and samples.
     * Automatically detects the delimiter (comma or semicolon) from the header line.
     */
    fun readCsv(inputStream: InputStream): CsvData {
        val reader = BufferedReader(InputStreamReader(inputStream))
        val lines = reader.readLines()

        if (lines.isEmpty()) {
            throw IllegalArgumentException("CSV file is empty")
        }

        // Detect delimiter from the first non-empty line.
        // Strip a leading UTF-8 BOM if present (Excel and some editors add one).
        val rawHeaderLine = lines.firstOrNull { it.isNotBlank() }
            ?: throw IllegalArgumentException("No header found")
        val headerLine = rawHeaderLine.removePrefix("\uFEFF")
        val delimiter = detectDelimiter(headerLine)

        // Parse header
        val headers = parseCsvLine(headerLine, delimiter)
        if (headers.isEmpty()) {
            throw IllegalArgumentException("No headers found in CSV")
        }

        // Parse data rows
        val rows = mutableListOf<List<String>>()
        for (i in 1 until lines.size) {
            val line = lines[i]
            if (line.isBlank()) continue
            val row = parseCsvLine(line, delimiter)
            if (row.isNotEmpty()) {
                rows.add(row)
            }
        }

        return CsvData(
            headers = headers,
            rows = rows
        )
    }

    /**
     * Detects the delimiter by counting commas and semicolons in the header line (ignoring quoted parts).
     * Returns the character that appears more often (commas or semicolons).
     * Defaults to comma if none found or equal count.
     */
    private fun detectDelimiter(line: String): Char {
        var commaCount = 0
        var semicolonCount = 0
        var inQuotes = false
        for (c in line) {
            if (c == '"') {
                inQuotes = !inQuotes
                continue
            }
            if (!inQuotes) {
                when (c) {
                    ',' -> commaCount++
                    ';' -> semicolonCount++
                }
            }
        }
        return if (semicolonCount > commaCount) ';' else ','
    }

    /**
     * Parses a single CSV line, handling quoted fields with commas inside.
     * @param delimiter The delimiter character (e.g., ',' or ';')
     */
    private fun parseCsvLine(line: String, delimiter: Char = ','): List<String> {
        val result = mutableListOf<String>()
        var current = StringBuilder()
        var inQuotes = false
        var i = 0

        while (i < line.length) {
            when (val c = line[i]) {
                '"' -> {
                    if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                        // Escaped quote: add one quote, skip the next
                        current.append('"')
                        i += 2
                    } else {
                        inQuotes = !inQuotes
                        i++
                    }
                }
                delimiter if !inQuotes -> {
                    // End of field
                    result.add(current.toString().trim())
                    current = StringBuilder()
                    i++
                }
                else -> {
                    current.append(c)
                    i++
                }
            }
        }

        // Add last field
        result.add(current.toString().trim())
        return result
    }
}