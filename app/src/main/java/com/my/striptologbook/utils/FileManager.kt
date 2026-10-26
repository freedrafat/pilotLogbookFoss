package com.my.striptologbook.utils

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import com.my.striptologbook.data.Leg
import com.my.striptologbook.data.OpeningBalance
import com.my.striptologbook.utils.FileManager.SUBFOLDER_NAME
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
 * Result of resolving the work directory.
 *
 * The work directory is a user-chosen SAF tree used for every
 * logbook-related file: JSON backups/restores, CSV exports/imports,
 * PDF exports, and (planned) preferences/settings save+restore.
 *
 * The URI string is stored under the existing preference key
 * `backup_folder_uri`. The name is historical; the semantics have
 * widened to "work directory".
 */
sealed class WorkDirectoryState {
    /** No folder has been set yet. */
    object NotSet : WorkDirectoryState()

    /** A folder was set, but is no longer accessible (deleted, permission revoked, etc.). */
    object Invalid : WorkDirectoryState()

    /** A folder is set and usable. */
    data class Ready(val folder: DocumentFile) : WorkDirectoryState()
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

    fun serializeLegsToCsv(legs: List<Leg>): String {
        val header = listOf(
            "id", "position", "picName", "sicName", "jumpseat1", "jumpseat2",
            "cabinCrew", "acRegistration", "acType", "flightNumber", "category",
            "isSimulator", "icaoDeparture", "icaoDestination",
            "offBlock", "takeoffTime", "landingTime", "onBlock", "sunrise", "sunset",
            "blockTimeDay", "blockTimeNight", "blockTimeTotal",
            "flightTimeDay", "flightTimeNight", "flightTimeTotal",
            "simulatorTime",
            "ifrDay", "ifrNight", "ifrTotal",
            "numberToDay", "numberToNight", "numberToTotal",
            "numberLdgDay", "numberLdgNight", "numberLdgTotal",
            "numberInstrumentApproaches",
            "pilotFlying", "comment"
        )
        val lines = mutableListOf<String>()
        lines.add(header.joinToString(",") { it })

        for (leg in legs) {
            val row = listOf(
                leg.id.toString(),
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
                if (leg.isSimulator == true) "true" else if (leg.isSimulator == false) "false" else "",
                quote(leg.icaoDeparture),
                quote(leg.icaoDestination),
                formatTimestampIsoUtc(leg.offBlock),
                formatTimestampIsoUtc(leg.takeoffTime),
                formatTimestampIsoUtc(leg.landingTime),
                formatTimestampIsoUtc(leg.onBlock),
                formatTimestampIsoUtc(leg.sunrise),
                formatTimestampIsoUtc(leg.sunset),
                leg.blockTimeDay?.toString() ?: "",
                leg.blockTimeNight?.toString() ?: "",
                leg.blockTimeTotal?.toString() ?: "",
                leg.flightTimeDay?.toString() ?: "",
                leg.flightTimeNight?.toString() ?: "",
                leg.flightTimeTotal?.toString() ?: "",
                leg.simulatorTime?.toString() ?: "",
                leg.ifrDay?.toString() ?: "",
                leg.ifrNight?.toString() ?: "",
                leg.ifrTotal?.toString() ?: "",
                leg.numberToDay?.toString() ?: "",
                leg.numberToNight?.toString() ?: "",
                leg.numberToTotal?.toString() ?: "",
                leg.numberLdgDay?.toString() ?: "",
                leg.numberLdgNight?.toString() ?: "",
                leg.numberLdgTotal?.toString() ?: "",
                leg.numberInstrumentApproaches?.toString() ?: "",
                if (leg.pilotFlying == true) "true" else if (leg.pilotFlying == false) "false" else "",
                quote(leg.comment)
            )
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

// ---------- Work directory ----------

    /**
     * Resolves the work directory.
     *
     * The stored preference `backup_folder_uri` is the parent tree the
     * user picked. The actual work directory is the subfolder
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
    fun resolveWorkDirectory(context: Context): WorkDirectoryState {
        val uriString = PreferencesManager.getInstance(context).getBackupFolderUri()
            ?: return WorkDirectoryState.NotSet

        val tree = try {
            DocumentFile.fromTreeUri(context, uriString.toUri())
        } catch (_: Exception) {
            return WorkDirectoryState.Invalid
        } ?: return WorkDirectoryState.Invalid

        if (!tree.exists() || !tree.isDirectory) {
            return WorkDirectoryState.Invalid
        }

        // If the user picked the subfolder itself, use it directly.
        if (tree.name == SUBFOLDER_NAME) {
            return WorkDirectoryState.Ready(tree)
        }

        val subfolder = try {
            tree.findFile(SUBFOLDER_NAME) ?: tree.createDirectory(SUBFOLDER_NAME)
        } catch (_: Exception) {
            null
        }

        return if (subfolder != null && subfolder.exists() && subfolder.isDirectory) {
            WorkDirectoryState.Ready(subfolder)
        } else {
            // Subfolder creation failed. Fall back to the picked tree.
            WorkDirectoryState.Ready(tree)
        }
    }

    /**
     * Requests READ|WRITE access on the given tree URI, persists the grant,
     * and stores the URI as the work directory parent.
     *
     * Returns true on success, false if the system refused the grant
     * (SecurityException) — in which case the preference is NOT updated.
     */
    fun persistWorkDirectoryPermission(context: Context, uri: Uri): Boolean {
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
     * Display-only helper: "parent/PilotLogbookFoss" for the stored parent,
     * "PilotLogbookFoss" if the user picked that folder directly,
     * "Unavailable" if the parent tree can no longer be opened,
     * "Not set" if no folder has ever been picked.
     */
    fun workDirectoryDisplayName(context: Context): String {
        val uriString = PreferencesManager.getInstance(context).getBackupFolderUri()
            ?: return "Not set"
        return try {
            val tree = DocumentFile.fromTreeUri(context, uriString.toUri())
            if (tree == null || !tree.exists() || !tree.isDirectory) {
                "Unavailable"
            } else {
                val parentName = tree.name ?: "?"
                if (parentName == SUBFOLDER_NAME) parentName
                else "$parentName/$SUBFOLDER_NAME"
            }
        } catch (_: Exception) {
            "Unavailable"
        }
    }

    /**
     * Suggested initial URI for the work-directory picker.
     *
     * If a folder is already set, opens the picker at that folder so the
     * user can navigate into or around it. Otherwise opens at Downloads,
     * so the [SUBFOLDER_NAME] subfolder is one tap away.
     *
     * Returns null on any failure; the picker will then open at its
     * default location.
     */
    fun workDirectoryPickerInitialUri(context: Context): Uri? {
        val current = PreferencesManager.getInstance(context).getBackupFolderUri()
        if (current != null) {
            return try {
                current.toUri()
            } catch (_: Exception) {
                null
            }
        }
        return try {
            MediaStore.Downloads.EXTERNAL_CONTENT_URI
        } catch (_: Exception) {
            null
        }
    }

    // ---------- Work directory write helpers ----------

    /**
     * Writes text to a new file in the given work directory and verifies
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
     * Writes bytes to a new file in the given work directory and verifies
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