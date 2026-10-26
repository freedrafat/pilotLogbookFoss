package com.my.striptologbook.utils

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.documentfile.provider.DocumentFile
import com.my.striptologbook.ui.StatisticNames
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object SettingsBackupManager {

    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    const val FORMAT_VERSION = 1
    const val FILE_PREFIX = "logbook_prefs_"
    const val FILE_SUFFIX = ".json"
    private const val SIGNATURE_FILENAME = "pdf_signature.png"

    sealed class SaveResult {
        data class Verified(val file: DocumentFile) : SaveResult()
        data class Failed(val reason: String) : SaveResult()
    }

    sealed class RestoreResult {
        data class Restored(val signatureWarning: String?) : RestoreResult()
        data class Failed(val reason: String) : RestoreResult()
    }

    @Serializable
    data class SettingsPayload(
        val formatVersion: Int = FORMAT_VERSION,
        val autoCheckUpdates: Boolean? = null,
        val checkUpdatesEveryStart: Boolean? = null,
        val lastUpdateCheck: Long? = null,
        val backupFolderUri: String? = null,
        val defaultAltitude: Int? = null,
        val defaultIsIfr: Boolean? = null,
        val defaultSimuIfrApproach: Int? = null,
        val defaultPosition: String? = null,
        val defaultPicName: String? = null,
        val defaultSicName: String? = null,
        val defaultJumpseat1: String? = null,
        val defaultJumpseat2: String? = null,
        val defaultCabinCrew: String? = null,
        val defaultAcRegistration: String? = null,
        val defaultFlightNumberPrefix: String? = null,
        val defaultIcaoDeparture: String? = null,
        val defaultIcaoDestination: String? = null,
        val defaultComment: String? = null,
        val defaultOffblockNow: Boolean? = null,
        val defaultIsSimulator: String? = null,
        val defaultPilotFlying: String? = null,
        val customPositionList: List<String>? = null,
        val customCategoryList: List<String>? = null,
        val legItemLayoutRaw: String? = null,
        val pdfLayoutConfigRaw: String? = null,
        val pdfRowsPerPage: Int? = null,
        val pdfOwnerName: String? = null,
        val statVisibility: Map<String, Boolean> = emptyMap(),
        val signatureBase64: String? = null
    )

    fun suggestFileName(): String {
        val dateStr = SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.US).format(Date())
        return "$FILE_PREFIX$dateStr$FILE_SUFFIX"
    }

    // ------------------------------------------------------------------------
    // Save
    // ------------------------------------------------------------------------

    fun save(
        context: Context,
        folder: DocumentFile,
        contentResolver: ContentResolver
    ): SaveResult {
        val prefs = PreferencesManager.getInstance(context)

        val payload = try {
            buildPayload(prefs)
        } catch (e: Exception) {
            return SaveResult.Failed("Failed to read preferences: ${e.message}")
        }

        val text = try {
            json.encodeToString(SettingsPayload.serializer(), payload)
        } catch (e: Exception) {
            return SaveResult.Failed("Serialization failed: ${e.message}")
        }

        return try {
            val file = FileManager.writeTextToWorkDir(
                contentResolver = contentResolver,
                folder = folder,
                mimeType = "application/json",
                fileName = suggestFileName(),
                content = text
            )
            SaveResult.Verified(file)
        } catch (e: Exception) {
            SaveResult.Failed("Write failed: ${e.message}")
        }
    }

    private fun buildPayload(prefs: PreferencesManager): SettingsPayload {
        val statMap = LinkedHashMap<String, Boolean>()
        for (name in StatisticNames.ALL_STATISTICS) {
            statMap[name] = prefs.isStatisticVisible(name)
        }

        return SettingsPayload(
            formatVersion = FORMAT_VERSION,
            autoCheckUpdates = prefs.isAutoCheckUpdatesEnabled(),
            checkUpdatesEveryStart = prefs.isCheckUpdatesEveryStartEnabled(),
            lastUpdateCheck = prefs.getLastUpdateCheck(),
            backupFolderUri = prefs.getBackupFolderUri(),
            defaultAltitude = prefs.getDefaultAltitude(),
            defaultIsIfr = prefs.getDefaultIsIfr(),
            defaultSimuIfrApproach = prefs.getDefaultSimuIfrApproach(),
            defaultPosition = prefs.getDefaultPosition(),
            defaultPicName = prefs.getDefaultPicName(),
            defaultSicName = prefs.getDefaultSicName(),
            defaultJumpseat1 = prefs.getDefaultJumpseat1(),
            defaultJumpseat2 = prefs.getDefaultJumpseat2(),
            defaultCabinCrew = prefs.getDefaultCabinCrew(),
            defaultAcRegistration = prefs.getDefaultAcRegistration(),
            defaultFlightNumberPrefix = prefs.getDefaultFlightNumberPrefix(),
            defaultIcaoDeparture = prefs.getDefaultIcaoDeparture(),
            defaultIcaoDestination = prefs.getDefaultIcaoDestination(),
            defaultComment = prefs.getDefaultComment(),
            defaultOffblockNow = prefs.getDefaultOffBlockNow(),
            defaultIsSimulator = prefs.getDefaultIsSimulator(),
            defaultPilotFlying = prefs.getDefaultPilotFlying(),
            customPositionList = prefs.getCustomPositionList(),
            customCategoryList = prefs.getCustomCategoryList(),
            legItemLayoutRaw = prefs.getLegItemLayoutRaw(),
            pdfLayoutConfigRaw = prefs.getPdfLayoutConfigRaw(),
            pdfRowsPerPage = prefs.getPdfRowsPerPage(),
            pdfOwnerName = prefs.getPdfOwnerName(),
            statVisibility = statMap,
            signatureBase64 = readSignatureBase64(prefs)
        )
    }

    private fun readSignatureBase64(prefs: PreferencesManager): String? {
        val path = prefs.getPdfSignaturePath() ?: return null
        val file = File(path)
        if (!file.exists()) return null
        return try {
            val bmp = BitmapFactory.decodeFile(path) ?: return null
            val baos = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.PNG, 100, baos)
            Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
        } catch (_: Exception) {
            null
        }
    }

    // ------------------------------------------------------------------------
    // Restore
    // ------------------------------------------------------------------------

    fun restore(
        context: Context,
        uri: Uri,
        contentResolver: ContentResolver
    ): RestoreResult {
        val text = try {
            FileManager.readJsonFromUri(contentResolver, uri)
        } catch (e: Exception) {
            return RestoreResult.Failed("Read failed: ${e.message}")
        }

        val payload = try {
            json.decodeFromString(SettingsPayload.serializer(), text)
        } catch (e: Exception) {
            return RestoreResult.Failed("Invalid JSON: ${e.message}")
        }

        if (payload.formatVersion != FORMAT_VERSION) {
            return RestoreResult.Failed(
                "Unsupported formatVersion: ${payload.formatVersion} (expected $FORMAT_VERSION)"
            )
        }

        val prefs = PreferencesManager.getInstance(context)

        try {
            applyPayload(prefs, payload)
        } catch (e: Exception) {
            return RestoreResult.Failed("Apply failed: ${e.message}")
        }

        val signatureWarning = applySignature(context, prefs, payload.signatureBase64)
        return RestoreResult.Restored(signatureWarning)
    }

    private fun applyPayload(prefs: PreferencesManager, p: SettingsPayload) {
        p.autoCheckUpdates?.let { prefs.setAutoCheckUpdatesEnabled(it) }
        p.checkUpdatesEveryStart?.let { prefs.setCheckUpdatesEveryStartEnabled(it) }
        p.lastUpdateCheck?.let { prefs.setLastUpdateCheck(it) }
        // backupFolderUri: skipped per Q8 (target's work directory is already set)

        p.defaultAltitude?.let { prefs.setDefaultAltitude(it) }
        p.defaultIsIfr?.let { prefs.setDefaultIsIfr(it) }
        p.defaultSimuIfrApproach?.let { prefs.setDefaultSimuIfrApproach(it) }
        p.defaultPosition?.let { prefs.setDefaultPosition(it) }
        p.defaultPicName?.let { prefs.setDefaultPicName(it) }
        p.defaultSicName?.let { prefs.setDefaultSicName(it) }
        p.defaultJumpseat1?.let { prefs.setDefaultJumpseat1(it) }
        p.defaultJumpseat2?.let { prefs.setDefaultJumpseat2(it) }
        p.defaultCabinCrew?.let { prefs.setDefaultCabinCrew(it) }
        p.defaultAcRegistration?.let { prefs.setDefaultAcRegistration(it) }
        p.defaultFlightNumberPrefix?.let { prefs.setDefaultFlightNumberPrefix(it) }
        p.defaultIcaoDeparture?.let { prefs.setDefaultIcaoDeparture(it) }
        p.defaultIcaoDestination?.let { prefs.setDefaultIcaoDestination(it) }
        p.defaultComment?.let { prefs.setDefaultComment(it) }
        p.defaultOffblockNow?.let { prefs.setDefaultOffBlockNow(it) }
        p.defaultIsSimulator?.let { prefs.setDefaultIsSimulator(it) }
        p.defaultPilotFlying?.let { prefs.setDefaultPilotFlying(it) }

        p.customPositionList?.let { prefs.setCustomPositionList(it) }
        p.customCategoryList?.let { prefs.setCustomCategoryList(it) }

        p.legItemLayoutRaw?.let { prefs.setLegItemLayoutRaw(it) }
        p.pdfLayoutConfigRaw?.let { prefs.setPdfLayoutConfigRaw(it) }
        p.pdfRowsPerPage?.let { prefs.setPdfRowsPerPage(it) }
        p.pdfOwnerName?.let { prefs.setPdfOwnerName(it) }

        for ((name, visible) in p.statVisibility) {
            prefs.setStatisticVisible(name, visible)
        }
    }

    private fun applySignature(
        context: Context,
        prefs: PreferencesManager,
        base64: String?
    ): String? {
        val targetFile = File(context.filesDir, SIGNATURE_FILENAME)

        if (base64 == null) {
            // No signature in file: delete target's PNG and null the path.
            try {
                targetFile.delete()
            } catch (_: Exception) {
            }
            prefs.setPdfSignaturePath(null)
            return null
        }

        return try {
            val bytes = Base64.decode(base64, Base64.NO_WRAP)
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                ?: return "Signature could not be decoded; existing signature left untouched."
            targetFile.outputStream().use { out ->
                bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            prefs.setPdfSignaturePath(targetFile.absolutePath)
            null
        } catch (e: Exception) {
            "Signature could not be applied: ${e.message}. Existing signature left untouched."
        }
    }
}