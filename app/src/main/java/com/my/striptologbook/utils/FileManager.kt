package com.my.striptologbook.utils

import android.content.ContentResolver
import android.net.Uri
import com.my.striptologbook.data.Leg
import com.my.striptologbook.data.OpeningBalance
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.serialization.json.decodeFromJsonElement

object FileManager {

    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

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
}