package com.my.striptologbook.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.my.striptologbook.data.PdfTemplates

class PreferencesManager private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)


    companion object {
        @Volatile
        private var instance: PreferencesManager? = null

        fun getInstance(context: Context): PreferencesManager {
            return instance ?: synchronized(this) {
                instance ?: PreferencesManager(context).also { instance = it }
            }
        }

        private const val KEY_LAST_AIRPORTS_DOWNLOAD = "last_airports_download"

        // Historical name: originally only for the Backup folder. Now serves
        // as the "work directory" for all logbook-related file operations
        // (backups, CSV, PDF, and future preferences/settings save+restore).
        // Rename was avoided to preserve existing user data.
        private const val KEY_WORK_FOLDER_URI = "backup_folder_uri"

        private const val KEY_FLIGHT_SUN_DEPARTURE_IATA = "flight_sun_dep_iata"
        private const val KEY_FLIGHT_SUN_ARRIVAL_IATA = "flight_sun_arr_iata"
        private const val KEY_FLIGHT_SUN_DEPARTURE_TIME = "flight_sun_dep_time"
        private const val KEY_FLIGHT_SUN_ARRIVAL_TIME = "flight_sun_arr_time"
        private const val KEY_FLIGHT_SUN_ELEVATION = "flight_sun_elevation"
        private const val KEY_FLIGHT_SUN_WAYPOINTS = "flight_sun_waypoints"
        private const val KEY_DEFAULT_ALTITUDE = "default_altitude"
        private const val KEY_DEFAULT_IS_IFR = "default_is_ifr"
        private const val KEY_DEFAULT_SIMU_IFR_APPROACH = "default_simu_ifr_approach"
        private const val KEY_DEFAULT_POSITION = "default_position"
        private const val KEY_DEFAULT_PIC_NAME             = "default_pic_name"
        private const val KEY_DEFAULT_SIC_NAME             = "default_sic_name"
        private const val KEY_DEFAULT_JUMPSEAT1            = "default_jumpseat1"
        private const val KEY_DEFAULT_JUMPSEAT2            = "default_jumpseat2"
        private const val KEY_DEFAULT_CABIN_CREW           = "default_cabin_crew"
        private const val KEY_DEFAULT_AC_REGISTRATION      = "default_ac_registration"
        private const val KEY_DEFAULT_FLIGHT_NUMBER_PREFIX = "default_flight_number_prefix"
        private const val KEY_DEFAULT_ICAO_DEPARTURE       = "default_icao_departure"
        private const val KEY_DEFAULT_ICAO_DESTINATION     = "default_icao_destination"
        private const val KEY_DEFAULT_COMMENT              = "default_comment"
        private const val KEY_DEFAULT_OFFBLOCK_NOW         = "default_offblock_now"
        private const val KEY_DEFAULT_IS_SIMULATOR         = "default_is_simulator"
        private const val KEY_DEFAULT_PILOT_FLYING         = "default_pilot_flying"

        private const val TRI_NONE  = "none"
        private const val TRI_TRUE  = "true"
        private const val TRI_FALSE = "false"

        private const val KEY_CUSTOM_POSITION_LIST = "custom_position_list"
        private const val KEY_CUSTOM_CATEGORY_LIST = "custom_category_list"
        private const val KEY_LEG_ITEM_LAYOUT = "leg_item_layout"
        private const val KEY_AUTO_CHECK_UPDATES        = "auto_check_updates"
        private const val KEY_CHECK_UPDATES_EVERY_START = "check_updates_every_start"
        private const val KEY_LAST_UPDATE_CHECK         = "last_update_check"

        val DEFAULT_POSITION_LIST = listOf(
            "PIC",
            "SIC",
            "AugCrew",
            "Dual",
            "Instructor"
        )

        val DEFAULT_CATEGORY_LIST = listOf(
            "Multi Pilot",
            "Single Pilot SE",
            "Single Pilot ME",
            "Helicopter",
            "Glider"
        )

        private const val KEY_PDF_TEMPLATE = "pdf_template"
        private const val KEY_PDF_COLUMNS  = "pdf_columns"

        private const val KEY_PDF_ROWS_PER_PAGE  = "pdf_rows_per_page"
        private const val KEY_PDF_OWNER_NAME     = "pdf_owner_name"
        private const val KEY_PDF_SIGNATURE_PATH = "pdf_signature_path"

        private const val KEY_PDF_LAYOUT_CONFIG = "pdf_layout_config"

    }

    // Last airports download date (string like "dd.MM.yyyy")
    fun getLastAirportsDownload(): String? = prefs.getString(KEY_LAST_AIRPORTS_DOWNLOAD, null)
    fun setLastAirportsDownload(date: String?) {
        prefs.edit { putString(KEY_LAST_AIRPORTS_DOWNLOAD, date) }
    }
    // ---------------------------------------------------------------- Update check
    /**
     * One-time cleanup: removes the orphan "auto_install_apk" key left
     * by versions before the DownloadManager refactor. Harmless to call
     * repeatedly — removing an absent key is a no-op.
     */
    fun clearLegacyAutoInstallKey() {
        if (prefs.contains("auto_install_apk")) {
            prefs.edit { remove("auto_install_apk") }
        }
    }

    /**
     * One-time cleanup: removes the visibility keys for the removed
     * VFR statistics ("VFR", "VFR Night", "VFR Day").
     * Harmless to call repeatedly — removing an absent key is a no-op.
     */
    fun clearLegacyVfrVisibilityKeys() {
        val keys = listOf(
            "stat_visibility_VFR",
            "stat_visibility_VFR Night",
            "stat_visibility_VFR Day"
        )
        val toRemove = keys.filter { prefs.contains(it) }
        if (toRemove.isNotEmpty()) {
            prefs.edit { toRemove.forEach { remove(it) } }
        }
    }

    /** Master switch for automatic update checks. Default: true. */
    fun isAutoCheckUpdatesEnabled(): Boolean =
        prefs.getBoolean(KEY_AUTO_CHECK_UPDATES, true)
    fun setAutoCheckUpdatesEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_AUTO_CHECK_UPDATES, enabled) }
    }

    /**
     * When true, automatic checks run on every app start.
     * When false, they run at most once every 7 days. Default: false.
     */
    fun isCheckUpdatesEveryStartEnabled(): Boolean =
        prefs.getBoolean(KEY_CHECK_UPDATES_EVERY_START, false)
    fun setCheckUpdatesEveryStartEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_CHECK_UPDATES_EVERY_START, enabled) }
    }

    /** Timestamp (UTC ms) of the last recorded update check. 0 if never. */
    fun getLastUpdateCheck(): Long =
        prefs.getLong(KEY_LAST_UPDATE_CHECK, 0L)
    fun setLastUpdateCheck(timestampMs: Long) {
        prefs.edit { putLong(KEY_LAST_UPDATE_CHECK, timestampMs) }
    }


    fun getBackupFolderUri(): String? = prefs.getString(KEY_WORK_FOLDER_URI, null)

    fun setBackupFolderUri(uri: String?) {
        if (uri == null) {
            prefs.edit { remove(KEY_WORK_FOLDER_URI) }
        } else {
            prefs.edit { putString(KEY_WORK_FOLDER_URI, uri) }
        }
    }

    fun isStatisticVisible(statName: String): Boolean {
        val key = "stat_visibility_$statName"
        return prefs.getBoolean(key, true)
    }

    fun setStatisticVisible(statName: String, visible: Boolean) {
        val key = "stat_visibility_$statName"
        prefs.edit { putBoolean(key, visible) }
    }

    fun getDefaultAltitude(): Int {
        return try {
            prefs.getInt(KEY_DEFAULT_ALTITUDE, 37000)
        } catch (e: ClassCastException) {
            // Old value is a String – convert and fix
            val stringValue = prefs.getString(KEY_DEFAULT_ALTITUDE, "37000") ?: "37000"
            val intValue = stringValue.toIntOrNull() ?: 37000
            prefs.edit { putInt(KEY_DEFAULT_ALTITUDE, intValue) }
            intValue
        }
    }

    fun setDefaultAltitude(altitude: Int) {
        prefs.edit { putInt(KEY_DEFAULT_ALTITUDE, altitude) }
    }


    fun getDefaultIsIfr(): Boolean = prefs.getBoolean(KEY_DEFAULT_IS_IFR, true)
    fun setDefaultIsIfr(value: Boolean) {
        prefs.edit { putBoolean(KEY_DEFAULT_IS_IFR, value) }
    }

    fun getDefaultSimuIfrApproach(): Int = prefs.getInt(KEY_DEFAULT_SIMU_IFR_APPROACH, 5)
    fun setDefaultSimuIfrApproach(value: Int) {
        prefs.edit { putInt(KEY_DEFAULT_SIMU_IFR_APPROACH, value) }
    }

    fun getDefaultPosition(): String = prefs.getString(KEY_DEFAULT_POSITION, "") ?: ""

    fun setDefaultPosition(value: String) {
        prefs.edit { putString(KEY_DEFAULT_POSITION, value) }
    }

    fun getDefaultPicName(): String = prefs.getString(KEY_DEFAULT_PIC_NAME, "") ?: ""
    fun setDefaultPicName(value: String) { prefs.edit { putString(KEY_DEFAULT_PIC_NAME, value) } }

    fun getDefaultSicName(): String = prefs.getString(KEY_DEFAULT_SIC_NAME, "") ?: ""
    fun setDefaultSicName(value: String) { prefs.edit { putString(KEY_DEFAULT_SIC_NAME, value) } }

    fun getDefaultJumpseat1(): String = prefs.getString(KEY_DEFAULT_JUMPSEAT1, "") ?: ""
    fun setDefaultJumpseat1(value: String) { prefs.edit { putString(KEY_DEFAULT_JUMPSEAT1, value) } }

    fun getDefaultJumpseat2(): String = prefs.getString(KEY_DEFAULT_JUMPSEAT2, "") ?: ""
    fun setDefaultJumpseat2(value: String) { prefs.edit { putString(KEY_DEFAULT_JUMPSEAT2, value) } }

    fun getDefaultCabinCrew(): String = prefs.getString(KEY_DEFAULT_CABIN_CREW, "") ?: ""
    fun setDefaultCabinCrew(value: String) { prefs.edit { putString(KEY_DEFAULT_CABIN_CREW, value) } }

    fun getDefaultAcRegistration(): String = prefs.getString(KEY_DEFAULT_AC_REGISTRATION, "") ?: ""
    fun setDefaultAcRegistration(value: String) { prefs.edit { putString(KEY_DEFAULT_AC_REGISTRATION, value) } }

    fun getDefaultFlightNumberPrefix(): String = prefs.getString(KEY_DEFAULT_FLIGHT_NUMBER_PREFIX, "") ?: ""
    fun setDefaultFlightNumberPrefix(value: String) { prefs.edit { putString(KEY_DEFAULT_FLIGHT_NUMBER_PREFIX, value) } }

    fun getDefaultIcaoDeparture(): String = prefs.getString(KEY_DEFAULT_ICAO_DEPARTURE, "") ?: ""
    fun setDefaultIcaoDeparture(value: String) { prefs.edit { putString(KEY_DEFAULT_ICAO_DEPARTURE, value) } }

    fun getDefaultIcaoDestination(): String = prefs.getString(KEY_DEFAULT_ICAO_DESTINATION, "") ?: ""
    fun setDefaultIcaoDestination(value: String) { prefs.edit { putString(KEY_DEFAULT_ICAO_DESTINATION, value) } }

    fun getDefaultComment(): String = prefs.getString(KEY_DEFAULT_COMMENT, "") ?: ""
    fun setDefaultComment(value: String) { prefs.edit { putString(KEY_DEFAULT_COMMENT, value) } }

    fun getDefaultOffBlockNow(): Boolean = prefs.getBoolean(KEY_DEFAULT_OFFBLOCK_NOW, false)
    fun setDefaultOffBlockNow(value: Boolean) { prefs.edit { putBoolean(KEY_DEFAULT_OFFBLOCK_NOW, value) } }

    fun getDefaultIsSimulator(): String {
        val v = prefs.getString(KEY_DEFAULT_IS_SIMULATOR, TRI_NONE) ?: TRI_NONE
        return if (v == TRI_TRUE || v == TRI_FALSE || v == TRI_NONE) v else TRI_NONE
    }
    fun setDefaultIsSimulator(value: String) {
        val safe = if (value == TRI_TRUE || value == TRI_FALSE) value else TRI_NONE
        prefs.edit { putString(KEY_DEFAULT_IS_SIMULATOR, safe) }
    }

    fun getDefaultPilotFlying(): String {
        val v = prefs.getString(KEY_DEFAULT_PILOT_FLYING, TRI_NONE) ?: TRI_NONE
        return if (v == TRI_TRUE || v == TRI_FALSE || v == TRI_NONE) v else TRI_NONE
    }
    fun setDefaultPilotFlying(value: String) {
        val safe = if (value == TRI_TRUE || value == TRI_FALSE) value else TRI_NONE
        prefs.edit { putString(KEY_DEFAULT_PILOT_FLYING, safe) }
    }

    fun getCustomPositionList(): List<String> {
        val raw = prefs.getString(KEY_CUSTOM_POSITION_LIST, null) ?: return DEFAULT_POSITION_LIST
        val parsed = raw.split("|").filter { it.isNotEmpty() }
        return if (parsed.isEmpty()) DEFAULT_POSITION_LIST else parsed
    }

    fun setCustomPositionList(list: List<String>) {
        prefs.edit { putString(KEY_CUSTOM_POSITION_LIST, list.joinToString("|")) }
    }

    fun getCustomCategoryList(): List<String> {
        val raw = prefs.getString(KEY_CUSTOM_CATEGORY_LIST, null) ?: return DEFAULT_CATEGORY_LIST
        val parsed = raw.split("|").filter { it.isNotEmpty() }
        return if (parsed.isEmpty()) DEFAULT_CATEGORY_LIST else parsed
    }

    fun setCustomCategoryList(list: List<String>) {
        prefs.edit { putString(KEY_CUSTOM_CATEGORY_LIST, list.joinToString("|")) }
    }

    fun saveFlightSunState(
        depIATA: String,
        arrIATA: String,
        depTime: Long?,
        arrTime: Long?,
        elevation: Int?,
        waypoints: List<WaypointRowData>   // custom data class or list of triple
    ) {
        prefs.edit {
            putString(KEY_FLIGHT_SUN_DEPARTURE_IATA, depIATA)
            putString(KEY_FLIGHT_SUN_ARRIVAL_IATA, arrIATA)
            depTime?.let { putLong(KEY_FLIGHT_SUN_DEPARTURE_TIME, it) } ?: remove(KEY_FLIGHT_SUN_DEPARTURE_TIME)
            arrTime?.let { putLong(KEY_FLIGHT_SUN_ARRIVAL_TIME, it) } ?: remove(KEY_FLIGHT_SUN_ARRIVAL_TIME)
            putInt(KEY_FLIGHT_SUN_ELEVATION, elevation ?: 0)
            val waypointsStr = waypoints.joinToString("|") { wp ->
                "${wp.lat ?: ""},${wp.lon ?: ""},${wp.time ?: ""}"
            }
            putString(KEY_FLIGHT_SUN_WAYPOINTS, waypointsStr)
        }
    }

    fun loadFlightSunState(): FlightSunState? {
        val depIATA = prefs.getString(KEY_FLIGHT_SUN_DEPARTURE_IATA, null) ?: return null
        val arrIATA = prefs.getString(KEY_FLIGHT_SUN_ARRIVAL_IATA, null) ?: return null
        val depTime = if (prefs.contains(KEY_FLIGHT_SUN_DEPARTURE_TIME)) prefs.getLong(KEY_FLIGHT_SUN_DEPARTURE_TIME, 0L) else null
        val arrTime = if (prefs.contains(KEY_FLIGHT_SUN_ARRIVAL_TIME)) prefs.getLong(KEY_FLIGHT_SUN_ARRIVAL_TIME, 0L) else null
        val elevation = try {
            prefs.getInt(KEY_FLIGHT_SUN_ELEVATION, 0)
        } catch (e: ClassCastException) {
            // Old value is a String – convert and fix
            val stringValue = prefs.getString(KEY_FLIGHT_SUN_ELEVATION, "0") ?: "0"
            val intValue = stringValue.toIntOrNull() ?: 0
            prefs.edit { putInt(KEY_FLIGHT_SUN_ELEVATION, intValue) }
            intValue
        }
        val waypointsStr = prefs.getString(KEY_FLIGHT_SUN_WAYPOINTS, "") ?: ""
        val waypoints = waypointsStr.split("|").filter { it.isNotEmpty() }.map { part ->
            val pieces = part.split(",", limit = 3)
            val lat = pieces.getOrNull(0)?.takeIf { it.isNotEmpty() }?.toDoubleOrNull()
            val lon = pieces.getOrNull(1)?.takeIf { it.isNotEmpty() }?.toDoubleOrNull()
            val time = pieces.getOrNull(2)
                ?.takeIf { it.isNotEmpty() }
                ?.takeIf { Regex("^\\d{2}:\\d{2}$").matches(it) }
            WaypointRowData(lat, lon, time)
        }
        return FlightSunState(depIATA, arrIATA, depTime, arrTime, elevation, waypoints)
    }

    // Data classes for return
    data class FlightSunState(
        val depIATA: String,
        val arrIATA: String,
        val toTime: Long?,
        val ldgTime: Long?,
        val elevation: Int,
        val waypoints: List<WaypointRowData>
    )

    data class WaypointRowData(
        val lat: Double?,
        val lon: Double?,
        val time: String?
    )


    fun getPdfTemplate(): String = prefs.getString(KEY_PDF_TEMPLATE, "ICAO") ?: "ICAO"
    fun setPdfTemplate(template: String) { prefs.edit { putString(KEY_PDF_TEMPLATE, template) } }

    fun getPdfColumns(): List<String> {
        val raw = prefs.getString(KEY_PDF_COLUMNS, null) ?: return PdfTemplates.ICAO
        return raw.split("|").filter { it.isNotEmpty() }
    }
    fun setPdfColumns(columns: List<String>) {
        prefs.edit { putString(KEY_PDF_COLUMNS, columns.joinToString("|")) }
    }

    fun getPdfRowsPerPage(): Int = prefs.getInt(KEY_PDF_ROWS_PER_PAGE, 18)
    fun setPdfRowsPerPage(value: Int) { prefs.edit { putInt(KEY_PDF_ROWS_PER_PAGE, value) } }

    fun getPdfOwnerName(): String = prefs.getString(KEY_PDF_OWNER_NAME, "") ?: ""
    fun setPdfOwnerName(name: String) { prefs.edit { putString(KEY_PDF_OWNER_NAME, name) } }

    fun getPdfSignaturePath(): String? = prefs.getString(KEY_PDF_SIGNATURE_PATH, null)
    fun setPdfSignaturePath(path: String?) {
        if (path == null) prefs.edit { remove(KEY_PDF_SIGNATURE_PATH) }
        else prefs.edit { putString(KEY_PDF_SIGNATURE_PATH, path) }
    }

    fun getPdfLayoutConfig(): com.my.striptologbook.data.PdfLayoutConfig {
        val json = prefs.getString(KEY_PDF_LAYOUT_CONFIG, null)
            ?: return com.my.striptologbook.data.PdfLayoutConfig.DEFAULT
        return try {
            kotlinx.serialization.json.Json.decodeFromString(json)
        } catch (_: Exception) {
            com.my.striptologbook.data.PdfLayoutConfig.DEFAULT
        }
    }

    fun setPdfLayoutConfig(config: com.my.striptologbook.data.PdfLayoutConfig) {
        val json = kotlinx.serialization.json.Json.encodeToString(config)
        prefs.edit { putString(KEY_PDF_LAYOUT_CONFIG, json) }
    }

    // ---------------------------------------------------------------- Leg Items layout

    /**
     * Returns the persisted Leg Items layout. Never throws.
     *  - If nothing has ever been saved, returns the built-in Default layout.
     *  - If the stored JSON is corrupt or unreadable, returns the built-in
     *    Default layout (the corrupt value is left in place so a future write
     *    can overwrite it cleanly).
     *  - Otherwise returns a normalized layout (see LegItemLayout.fromJson).
     */
    fun getLegItemLayout(): com.my.striptologbook.data.LegItemLayout {
        val raw = prefs.getString(KEY_LEG_ITEM_LAYOUT, null)
        return com.my.striptologbook.data.LegItemLayout.fromJson(raw)
    }

    /**
     * Persists the given Leg Items layout as a single JSON string.
     * Overwrites whatever was there before.
     */
    fun saveLegItemLayout(layout: com.my.striptologbook.data.LegItemLayout) {
        val json = com.my.striptologbook.data.LegItemLayout.toJson(layout)
        prefs.edit { putString(KEY_LEG_ITEM_LAYOUT, json) }
    }

    /**
     * Raw stored JSON for the Leg Items layout, without normalisation.
     * Used by settings backup/restore (Q6: raw string). Returns null if
     * the key has never been written.
     */
    fun getLegItemLayoutRaw(): String? = prefs.getString(KEY_LEG_ITEM_LAYOUT, null)

    fun setLegItemLayoutRaw(raw: String?) {
        if (raw == null) prefs.edit { remove(KEY_LEG_ITEM_LAYOUT) }
        else prefs.edit { putString(KEY_LEG_ITEM_LAYOUT, raw) }
    }

    /**
     * Raw stored JSON for the PDF layout config, without normalisation.
     * Used by settings backup/restore (Q6: raw string). Returns null if
     * the key has never been written.
     */
    fun getPdfLayoutConfigRaw(): String? = prefs.getString(KEY_PDF_LAYOUT_CONFIG, null)

    fun setPdfLayoutConfigRaw(raw: String?) {
        if (raw == null) prefs.edit { remove(KEY_PDF_LAYOUT_CONFIG) }
        else prefs.edit { putString(KEY_PDF_LAYOUT_CONFIG, raw) }
    }

    /**
     * Convenience: returns the built-in Default profile, bypassing storage.
     * Useful for the editor's "Restore Default" action and for tests.
     */
    fun getLegItemDefaultProfile(): com.my.striptologbook.data.LegProfile =
        com.my.striptologbook.data.LegItemLayout.getDefaultProfile()

}