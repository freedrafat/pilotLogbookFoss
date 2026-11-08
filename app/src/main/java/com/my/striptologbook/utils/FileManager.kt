package com.my.striptologbook.utils

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import com.my.striptologbook.data.Leg
import com.my.striptologbook.data.OpeningBalance
import com.my.striptologbook.utils.FileManager.SUBFOLDER_NAME
import com.my.striptologbook.data.CsvExportFormat
import com.my.striptologbook.data.CsvFormatType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.CRC32


/**
 * Result of resolving the work folder.
 *
 * The work folder is a user-chosen SAF tree used for every
 * logbook-related file: JSON backups/restores, CSV exports/imports,
 * PDF exports, and (planned) preferences/settings save+restore.
 *
 * The URI string is stored under the existing preference key
 * `backup_folder_uri`. The name is historical; the semantics have
 * widened to "work folder".
 */
sealed class WorkFolderState {
    /** No folder has been set yet. */
    object NotSet : WorkFolderState()

    /** A folder was set, but is no longer accessible (deleted, permission revoked, etc.). */
    object Invalid : WorkFolderState()

    /** A folder is set and usable. */
    data class Ready(val folder: DocumentFile) : WorkFolderState()
}


object FileManager {

    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    private const val SUBFOLDER_NAME = "PilotLogbookFoss"

    /**
     * Combined backup payload, top-level object.
     *
     * formatVersion 2 is the first version of this object format.
     * formatVersion 1 is the implicit shape of the old backups: a
     * top-level JSON array of legs. Those are still readable; see
     * readBackupFromJson.
     */
    @Serializable
    data class BackupPayload(
        val formatVersion: Int = 2,
        val legs: List<Leg> = emptyList(),
        val openingBalance: List<OpeningBalance> = emptyList()
    )

    /** Result of reading a backup, regardless of which format it used. */
    data class BackupContent(
        val legs: List<Leg>,
        val openingBalance: List<OpeningBalance>,
        val hasOpeningBalanceKey: Boolean
    )

    // ---------- JSON — combined backup ----------

    fun serializeBackupToJson(
        legs: List<Leg>,
        openingBalance: List<OpeningBalance>
    ): String = json.encodeToString(
        BackupPayload(
            formatVersion = 2,
            legs = legs,
            openingBalance = openingBalance
        )
    )

    /**
     * Reads a backup file that may be either:
     *   • the old format — a top-level JSON array of legs; or
     *   • the new format — a top-level JSON object with a version, a
     *     legs array and an openingBalance array.
     *
     * The hasOpeningBalanceKey flag tells the caller whether the file
     * actually carried an opening balance section. Old files do not,
     * and restoring them must leave the device's opening balance
     * untouched.
     */
    fun readBackupFromJson(jsonString: String): BackupContent {
        val element: JsonElement = json.parseToJsonElement(jsonString)

        return when {
            element is kotlinx.serialization.json.JsonArray -> {
                // Old format: a top-level array of legs.
                val legs = json.decodeFromJsonElement<List<Leg>>(element)
                BackupContent(
                    legs = legs,
                    openingBalance = emptyList(),
                    hasOpeningBalanceKey = false
                )
            }
            element is kotlinx.serialization.json.JsonObject -> {
                val payload = json.decodeFromJsonElement<BackupPayload>(element)
                BackupContent(
                    legs = payload.legs,
                    openingBalance = payload.openingBalance,
                    hasOpeningBalanceKey = element.containsKey("openingBalance")
                )
            }
            else -> throw IllegalArgumentException(
                "Backup file is neither a JSON array nor a JSON object"
            )
        }
    }

    // ---------- JSON — low-level I/O ----------

    fun writeJsonToUri(contentResolver: ContentResolver, uri: Uri, jsonString: String) {
        contentResolver.openOutputStream(uri)?.use { outputStream: OutputStream ->
            outputStream.write(jsonString.toByteArray())
        } ?: throw IllegalStateException("Unable to open output stream for URI: $uri")
    }

    fun readJsonFromUri(contentResolver: ContentResolver, uri: Uri): String {
        return contentResolver.openInputStream(uri)?.use { inputStream: InputStream ->
            inputStream.bufferedReader().use { it.readText() }
        } ?: throw IllegalStateException("Unable to open input stream for URI: $uri")
    }

    fun suggestBackupFileName(): String {
        val dateStr = SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.US).format(Date())
        return "logbook_backup_${dateStr}.json"
    }

    // ---------- CSV ----------


    fun serializeLegsToCsv(config: CsvExportFormat, legs: List<Leg>): String {
        return when (config.formatType) {
            CsvFormatType.PDF_COLUMNS -> serializeAsPdfColumns(legs, config)
            CsvFormatType.RAW -> serializeAsRaw(legs)
            CsvFormatType.FORMATTED -> serializeAsFormatted(legs, config)
        }
    }

    private fun buildRemarks(leg: Leg): String {
        val parts = mutableListOf<String>()
        leg.remarks?.takeIf { it.isNotBlank() }?.let { parts.add(it) }
        leg.jumpseat1?.takeIf { it.isNotBlank() }?.let { parts.add("J/S: $it") }
        return parts.joinToString(" | ")
    }

    private fun serializeAsPdfColumns(legs: List<Leg>, config: CsvExportFormat): String {
        val header = listOf(
            "Date", "Dep Place", "Dep Time", "Arr Place", "Arr Time",
            "a/c Model", "a/c Reg",
            "SP SE", "SP ME", "MP",
            "Total Flight", "PIC Name",
            "TO Day", "TO Night", "LDG Day", "LDG Night",
            "Op Night", "Op IFR",
            "PIC Time", "SIC Time", "Dual Time", "Instructor Time",
            "FSTD", "Remarks"
        )
        val filtered = if (config.includeSimulator) legs else legs.filter { it.isSimulator != true }

        fun dur(v: Int?): String =
            if (v == null || v == 0) "" else formatDurationWith(v, config.durationFormat)
        fun cnt(v: Int?): String =
            if (v == null || v == 0) "" else v.toString()

        val lines = mutableListOf<String>()
        lines.add(header.joinToString(","))

        for (leg in filtered) {
            val dateStr = leg.offBlock?.let { formatDateWith(it, config.dateFormat) } ?: ""
            val depTime = leg.offBlock?.let { extractTimeOnly(it) } ?: ""
            val arrTime = leg.onBlock?.let { extractTimeOnly(it) } ?: ""
            val spSe = if (leg.category == "Single Pilot SE") dur(leg.blockTimeTotal) else ""
            val spMe = if (leg.category == "Single Pilot ME") dur(leg.blockTimeTotal) else ""
            val mp   = if (leg.category == "Multi Pilot") dur(leg.blockTimeTotal) else ""
            val opIfr = if ((leg.ifrTotal ?: 0) > 0) dur(leg.blockTimeTotal) else ""
            val picTime = if (leg.position in setOf("PIC", "SPIC", "PICUS")) dur(leg.blockTimeTotal) else ""
            val sicTime = if (leg.position == "SIC") dur(leg.blockTimeTotal) else ""
            val dualTime = if (leg.position == "Dual") dur(leg.blockTimeTotal) else ""
            val instrTime = if (leg.position == "Instructor") dur(leg.blockTimeTotal) else ""

            val row = listOf(
                quote(dateStr),
                quote(leg.icaoDeparture ?: ""),
                quote(depTime),
                quote(leg.icaoDestination ?: ""),
                quote(arrTime),
                quote(leg.acType ?: ""),
                quote(leg.acRegistration ?: ""),
                quote(spSe),
                quote(spMe),
                quote(mp),
                quote(dur(leg.blockTimeTotal)),
                quote(leg.picName ?: ""),
                quote(cnt(leg.numberToDay)),
                quote(cnt(leg.numberToNight)),
                quote(cnt(leg.numberLdgDay)),
                quote(cnt(leg.numberLdgNight)),
                quote(dur(leg.blockTimeNight)),
                quote(opIfr),
                quote(picTime),
                quote(sicTime),
                quote(dualTime),
                quote(instrTime),
                quote(dur(leg.simulatorTime)),
                quote(buildRemarks(leg))
            )
            lines.add(row.joinToString(","))
        }
        return lines.joinToString("\n")
    }

    private fun serializeAsRaw(legs: List<Leg>): String {
        val header = listOf(
            "offBlock", "takeoff", "icaoDeparture", "icaoDestination", "landing", "onBlock",
            "position", "picName", "sicName", "jumpseat1", "jumpseat2", "cabinCrew",
            "acRegistration", "acType", "flightNumber", "category",
            "blockTimeDay", "blockTimeNight", "blockTimeTotal",
            "flightTimeDay", "flightTimeNight", "flightTimeTotal",
            "ifrDay", "ifrNight", "ifrTotal",
            "isSimulator", "simulatorTime",
            "isPilotFlying",
            "numberToDay", "numberToNight", "numberToTotal",
            "numberLdgDay", "numberLdgNight", "numberLdgTotal",
            "numberInstrumentApproaches",
            "remarks"
        )
        val lines = mutableListOf<String>()
        lines.add(header.joinToString(","))

        fun ts(v: Long?): String = v?.toString() ?: ""
        fun dur(v: Int?): String = v?.toString() ?: ""
        fun b(v: Boolean?): String =
            if (v == true) "true" else if (v == false) "false" else ""

        for (leg in legs) {
            val row = listOf(
                ts(leg.offBlock),
                ts(leg.takeoff),
                quote(leg.icaoDeparture),
                quote(leg.icaoDestination),
                ts(leg.landing),
                ts(leg.onBlock),
                quote(leg.position),
                quote(leg.picName),
                quote(leg.sicName),
                quote(leg.jumpseat1),
                quote(leg.jumpseat2),
                quote(leg.cabinCrew),
                quote(leg.acRegistration),
                quote(leg.acType),
                quote(leg.flightNumber),
                quote(leg.category),
                dur(leg.blockTimeDay),
                dur(leg.blockTimeNight),
                dur(leg.blockTimeTotal),
                dur(leg.flightTimeDay),
                dur(leg.flightTimeNight),
                dur(leg.flightTimeTotal),
                dur(leg.ifrDay),
                dur(leg.ifrNight),
                dur(leg.ifrTotal),
                b(leg.isSimulator),
                dur(leg.simulatorTime),
                b(leg.isPilotFlying),
                dur(leg.numberToDay),
                dur(leg.numberToNight),
                dur(leg.numberToTotal),
                dur(leg.numberLdgDay),
                dur(leg.numberLdgNight),
                dur(leg.numberLdgTotal),
                dur(leg.numberInstrumentApproaches),
                quote(leg.remarks)
            )
            lines.add(row.joinToString(","))
        }
        return lines.joinToString("\n")
    }

    private fun serializeAsFormatted(legs: List<Leg>, config: CsvExportFormat): String {
        val header = mutableListOf<String>()
        header.add("date")
        header.add("offBlock_time")
        if (config.includeTakeoffLanding) header.add("takeoff_time")
        header.add("icaoDeparture")
        header.add("icaoDestination")
        if (config.includeTakeoffLanding) header.add("landing_time")
        header.add("onBlock_time")
        header.addAll(listOf("position", "picName", "sicName", "jumpseat1", "jumpseat2", "cabinCrew"))
        header.addAll(listOf("acRegistration", "acType", "flightNumber", "category"))
        if (config.includeDay) header.add("blockTimeDay")
        header.add("blockTimeNight")
        header.add("blockTimeTotal")
        if (config.includeTakeoffLanding && config.includeDay) header.add("flightTimeDay")
        if (config.includeTakeoffLanding) {
            header.add("flightTimeNight")
            header.add("flightTimeTotal")
        }
        if (config.includeDay) header.add("ifrDay")
        header.add("ifrNight")
        header.add("ifrTotal")
        if (config.includeSimulator) {
            header.add("isSimulator")
            header.add("simulatorTime")
        }
        if (config.includeIsPilotFlying) header.add("isPilotFlying")
        if (config.includeNumberToDay) header.add("numberToDay")
        if (config.includeNumberToNight) header.add("numberToNight")
        if (config.includeNumberToTotal) header.add("numberToTotal")
        if (config.includeNumberLdgDay) header.add("numberLdgDay")
        if (config.includeNumberLdgNight) header.add("numberLdgNight")
        if (config.includeNumberLdgTotal) header.add("numberLdgTotal")
        if (config.includeNumberInstrumentApproaches) header.add("numberInstrumentApproaches")
        if (config.includeRemarks) header.add("remarks")

        val filtered = if (config.includeSimulator) legs else legs.filter { it.isSimulator != true }
        val lines = mutableListOf<String>()
        lines.add(header.joinToString(","))

        fun dur(v: Int?): String =
            if (v == null || v == 0) "" else formatDurationWith(v, config.durationFormat)
        fun cnt(v: Int?): String = v?.toString() ?: ""
        fun b(v: Boolean?): String =
            if (v == true) "true" else if (v == false) "false" else ""
        fun dateStr(ts: Long?): String = ts?.let { formatDateWith(it, config.dateFormat) } ?: ""
        fun timeStr(ts: Long?): String = ts?.let { extractTimeOnly(it) } ?: ""

        for (leg in filtered) {
            val row = mutableListOf<String>()
            row.add(quote(dateStr(leg.offBlock)))
            row.add(quote(timeStr(leg.offBlock)))
            if (config.includeTakeoffLanding) row.add(quote(timeStr(leg.takeoff)))
            row.add(quote(leg.icaoDeparture ?: ""))
            row.add(quote(leg.icaoDestination ?: ""))
            if (config.includeTakeoffLanding) row.add(quote(timeStr(leg.landing)))
            row.add(quote(timeStr(leg.onBlock)))
            row.add(quote(leg.position ?: ""))
            row.add(quote(leg.picName ?: ""))
            row.add(quote(leg.sicName ?: ""))
            row.add(quote(leg.jumpseat1 ?: ""))
            row.add(quote(leg.jumpseat2 ?: ""))
            row.add(quote(leg.cabinCrew ?: ""))
            row.add(quote(leg.acRegistration ?: ""))
            row.add(quote(leg.acType ?: ""))
            row.add(quote(leg.flightNumber ?: ""))
            row.add(quote(leg.category ?: ""))
            if (config.includeDay) row.add(quote(dur(leg.blockTimeDay)))
            row.add(quote(dur(leg.blockTimeNight)))
            row.add(quote(dur(leg.blockTimeTotal)))
            if (config.includeTakeoffLanding && config.includeDay) row.add(quote(dur(leg.flightTimeDay)))
            if (config.includeTakeoffLanding) {
                row.add(quote(dur(leg.flightTimeNight)))
                row.add(quote(dur(leg.flightTimeTotal)))
            }
            if (config.includeDay) row.add(quote(dur(leg.ifrDay)))
            row.add(quote(dur(leg.ifrNight)))
            row.add(quote(dur(leg.ifrTotal)))
            if (config.includeSimulator) {
                row.add(quote(b(leg.isSimulator)))
                row.add(quote(dur(leg.simulatorTime)))
            }
            if (config.includeIsPilotFlying) row.add(quote(b(leg.isPilotFlying)))
            if (config.includeNumberToDay) row.add(quote(cnt(leg.numberToDay)))
            if (config.includeNumberToNight) row.add(quote(cnt(leg.numberToNight)))
            if (config.includeNumberToTotal) row.add(quote(cnt(leg.numberToTotal)))
            if (config.includeNumberLdgDay) row.add(quote(cnt(leg.numberLdgDay)))
            if (config.includeNumberLdgNight) row.add(quote(cnt(leg.numberLdgNight)))
            if (config.includeNumberLdgTotal) row.add(quote(cnt(leg.numberLdgTotal)))
            if (config.includeNumberInstrumentApproaches) row.add(quote(cnt(leg.numberInstrumentApproaches)))
            if (config.includeRemarks) row.add(quote(buildRemarks(leg)))
            lines.add(row.joinToString(","))
        }
        return lines.joinToString("\n")
    }

    private fun quote(value: String?): String {
        if (value.isNullOrEmpty()) return ""
        val escaped = value.replace("\"", "\"\"")
        return "\"$escaped\""
    }

    fun suggestExportFileName(): String {
        val dateStr = SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.US).format(Date())
        return "logbook_export_${dateStr}.csv"
    }

    fun suggestPdfFileName(): String {
        val dateStr = SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.US).format(Date())
        return "logbook_${dateStr}.pdf"
    }

    fun suggestFilteredPdfFileName(): String {
        val dateStr = SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.US).format(Date())
        return "logbook_filtered_${dateStr}.pdf"
    }

    fun suggestFilteredCsvFileName(): String {
        val dateStr = SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.US).format(Date())
        return "logbook_filtered_${dateStr}.csv"
    }

// ---------- Work folder ----------

    /**
     * Resolves the work folder.
     *
     * The stored preference `backup_folder_uri` is the parent tree the
     * user picked. The actual work folder is the subfolder
     * [SUBFOLDER_NAME] inside that tree. If the user picked a folder
     * already named [SUBFOLDER_NAME], that folder itself is used.
     *
     * NotSet   — no folder has ever been set.
     * Invalid  — the tree is no longer accessible.
     * Ready    — a usable folder (the subfolder, or the picked folder
     *            as a fallback if subfolder creation fails).
     *
     * This method may create the subfolder on first use. It never throws.
     */
    fun resolveWorkFolder(context: Context): WorkFolderState {
        val uriString = PreferencesManager.getInstance(context).getBackupFolderUri()
            ?: return WorkFolderState.NotSet

        val tree = try {
            DocumentFile.fromTreeUri(context, uriString.toUri())
        } catch (_: Exception) {
            return WorkFolderState.Invalid
        } ?: return WorkFolderState.Invalid

        if (!tree.exists() || !tree.isDirectory) {
            return WorkFolderState.Invalid
        }

        // If the user picked the subfolder itself, use it directly.
        if (tree.name == SUBFOLDER_NAME) {
            return WorkFolderState.Ready(tree)
        }

        val subfolder = try {
            tree.findFile(SUBFOLDER_NAME) ?: tree.createDirectory(SUBFOLDER_NAME)
        } catch (_: Exception) {
            null
        }

        return if (subfolder != null && subfolder.exists() && subfolder.isDirectory) {
            WorkFolderState.Ready(subfolder)
        } else {
            // Subfolder creation failed. Fall back to the picked tree.
            WorkFolderState.Ready(tree)
        }
    }

    /**
     * Requests READ|WRITE access on the given tree URI, persists the grant,
     * and stores the URI as the work folder parent.
     *
     * Returns true on success, false if the system refused the grant
     * (SecurityException) — in which case the preference is NOT updated.
     */
    fun persistWorkFolderPermission(context: Context, uri: Uri): Boolean {
        return try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            PreferencesManager.getInstance(context).setBackupFolderUri(uri.toString())
            true
        } catch (_: SecurityException) {
            false
        }
    }

    /**
     * Display-only helper. Shows the full path of the work folder
     * (the parent the user picked, plus PilotLogbookFoss unless the
     * picked folder is already named PilotLogbookFoss).
     *
     * External-storage providers (primary + SD card) use the
     * "volume:path" document-ID format, so the real path is recoverable:
     *   • primary volume  → "Download/MyFolder/PilotLogbookFoss"
     *   • non-primary    → "Ext:Download/MyFolder/PilotLogbookFoss"
     * Other providers (Drive, cloud) return an opaque document ID with
     * no readable path, so the display is just "Ext".
     *
     * "Not set"   — no work folder has ever been picked.
     * "Unavailable" — a tree was set but cannot be opened.
     * "Ext"       — a non-External-Storage provider was picked.
     */
    fun workFolderDisplayName(context: Context): String {
        val uriString = PreferencesManager.getInstance(context).getBackupFolderUri()
            ?: return "Not set"
        return try {
            val uri = uriString.toUri()
            val tree = DocumentFile.fromTreeUri(context, uri)
            if (tree == null || !tree.exists() || !tree.isDirectory) {
                return "Unavailable"
            }

            val isExternalStorage =
                uri.authority == "com.android.externalstorage.documents"
            val docId: String? = try {
                DocumentsContract.getTreeDocumentId(uri)
            } catch (_: Exception) {
                null
            }

            if (!isExternalStorage || docId.isNullOrEmpty()) {
                return "Ext"
            }

            val colonIdx = docId.indexOf(':')
            if (colonIdx < 0) {
                return "Ext"
            }

            val volume = docId.substring(0, colonIdx)
            val path = docId.substring(colonIdx + 1)

            val withSub = when {
                path.isEmpty() -> SUBFOLDER_NAME
                path.substringAfterLast('/') == SUBFOLDER_NAME -> path
                else -> "$path/$SUBFOLDER_NAME"
            }
            return if (volume == "primary") withSub else "Ext:$withSub"
        } catch (_: Exception) {
            "Unavailable"
        }
    }

    /**
     * Suggested initial URI for the work-folder picker.
     *
     * If a folder is already set, opens the picker at that folder so the
     * user can navigate into or around it. Otherwise opens at Documents/,
     * because on Android 11+ the picker refuses to confirm Downloads/
     * (and the root of external storage) as a tree.
     *
     * Returns null on any failure; the picker will then open at its
     * default location.
     */
    fun workFolderPickerInitialUri(context: Context): Uri? {
        val current = PreferencesManager.getInstance(context).getBackupFolderUri()
        if (current != null) {
            return try {
                current.toUri()
            } catch (_: Exception) {
                null
            }
        }
        return try {
            DocumentsContract.buildTreeDocumentUri(
                "com.android.externalstorage.documents",
                "primary:Documents"
            )
        } catch (_: Exception) {
            try {
                MediaStore.Downloads.EXTERNAL_CONTENT_URI
            } catch (_: Exception) {
                null
            }
        }
    }

    // ---------- Work folder write helpers ----------

    /**
     * Writes text to a new file in the given work folder and verifies
     * the written bytes by CRC32 read-back. Throws on any failure
     * (create, write, read-back, checksum mismatch). The partially-written
     * file is deleted before the exception propagates.
     */
    fun writeTextToWorkDir(
        contentResolver: ContentResolver,
        folder: DocumentFile,
        mimeType: String,
        fileName: String,
        content: String
    ): DocumentFile = writeBytesToWorkDir(
        contentResolver = contentResolver,
        folder = folder,
        mimeType = mimeType,
        fileName = fileName,
        bytes = content.toByteArray(Charsets.UTF_8)
    )

    /**
     * Writes bytes to a new file in the given work folder and verifies
     * the written bytes by CRC32 read-back. See writeTextToWorkDir.
     */
    fun writeBytesToWorkDir(
        contentResolver: ContentResolver,
        folder: DocumentFile,
        mimeType: String,
        fileName: String,
        bytes: ByteArray
    ): DocumentFile {
        val file = folder.createFile(mimeType, fileName)
            ?: throw IllegalStateException("Could not create file: $fileName")
        try {
            contentResolver.openOutputStream(file.uri)?.use { out ->
                out.write(bytes)
            } ?: throw IllegalStateException("Could not open output stream for $fileName")

            val readBack = contentResolver.openInputStream(file.uri)?.use { it.readBytes() }
                ?: throw IllegalStateException("Could not open input stream for verification of $fileName")

            val originalCrc = crc32(bytes)
            val readBackCrc = crc32(readBack)
            if (originalCrc != readBackCrc) {
                throw IllegalStateException(
                    "Verification failed: CRC32 mismatch (wrote ${bytes.size} bytes, " +
                            "read ${readBack.size} bytes)"
                )
            }
            return file
        } catch (e: Exception) {
            try {
                file.delete()
            } catch (_: Exception) {
            }
            throw e
        }
    }

    /**
     * Lists files with the given extension (e.g. "csv") in the folder,
     * sorted by lastModified descending (most recent first).
     */
    fun listWorkDirFilesByExtension(folder: DocumentFile, extension: String): List<DocumentFile> {
        val ext = "." + extension.trimStart('.').lowercase()
        return folder.listFiles()
            .filter { it.isFile && it.name?.lowercase()?.endsWith(ext) == true }
            .sortedByDescending { it.lastModified() }
    }

    private fun crc32(bytes: ByteArray): Long {
        val crc = CRC32()
        crc.update(bytes)
        return crc.value
    }

}