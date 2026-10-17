package com.my.striptologbook.ui

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.my.striptologbook.R
import com.my.striptologbook.data.Leg
import com.my.striptologbook.data.LegItemFields
import com.my.striptologbook.data.LegItemLayout
import com.my.striptologbook.data.LegProfile
import com.my.striptologbook.data.LegRowItem
import com.my.striptologbook.data.LegRowRenderer
import com.my.striptologbook.data.hasUnknownFields
import com.my.striptologbook.utils.InsetsHelper
import com.my.striptologbook.utils.PreferencesManager
import java.util.Calendar
import java.util.TimeZone

/**
 * Leg Items editor.
 *
 * - [persistedSnapshot] mirrors what is currently stored on disk.
 * - [layoutState] is the working copy that will be persisted on Save.
 * - [workingRows] is the live edit buffer for the profile the user is viewing.
 *
 * Unsaved changes are detected by comparing the "staged" state (layoutState
 * plus workingRows) against [persistedSnapshot], so profile creation, rename,
 * deletion and active-profile selection are all covered.
 */
class LegItemsPreferencesActivity : BaseActivity() {

    private companion object {
        const val MAX_PROFILE_NAME_LENGTH = 30

        const val STATE_WORKING_LAYOUT      = "leg_items_working_layout"
        const val STATE_PERSISTED_SNAPSHOT  = "leg_items_persisted_snapshot"
        const val STATE_CURRENT_PROFILE     = "leg_items_current_profile"
    }

    // Persisted state (as of the last Save / onCreate load)
    private lateinit var persistedSnapshot: LegItemLayout

    // Working copy of the whole layout
    private lateinit var layoutState: LegItemLayout

    // Working copy of the currently viewed profile
    private var currentProfileName: String = LegItemLayout.DEFAULT_PROFILE_NAME
    private var workingBuiltIn: Boolean = true
    private val workingRows: MutableList<MutableList<LegRowItem>> =
        MutableList(4) { mutableListOf() }

    private val rowAdapters = mutableListOf<LegRowItemAdapter>()
    private val rowEditorIds = listOf(
        R.id.row_editor_1,
        R.id.row_editor_2,
        R.id.row_editor_3,
        R.id.row_editor_4
    )

    private lateinit var sampleLeg: Leg

    // ------------------------------------------------------------------ setup

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_leg_items_preferences)

        // -------- Status bar spacer ------------------------------
        InsetsHelper.applyStatusBarSpacer(findViewById(R.id.status_bar_spacer))

        // -------- Toolbar -----------------------------------------
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = "Leg items"              //change title
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // Intercept system Back so we can warn about unsaved changes
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                tryFinish()
            }
        })

        sampleLeg = buildSampleLeg()

        // Restore staged state after a configuration change, or load from
        // PreferencesManager on a cold start.
        val restoredWorking = savedInstanceState?.getString(STATE_WORKING_LAYOUT)
        if (restoredWorking != null) {
            layoutState = LegItemLayout.fromJson(restoredWorking)
            persistedSnapshot = LegItemLayout.fromJson(
                savedInstanceState.getString(STATE_PERSISTED_SNAPSHOT)
            )
            val restoredProfile = savedInstanceState.getString(STATE_CURRENT_PROFILE)
            currentProfileName =
                if (restoredProfile != null && layoutState.profiles.containsKey(restoredProfile)) {
                    restoredProfile
                } else {
                    layoutState.activeProfile
                }
        } else {
            persistedSnapshot = PreferencesManager.getInstance(this).getLegItemLayout()
            layoutState = persistedSnapshot
            currentProfileName = layoutState.activeProfile
        }


        // Row editors
        setupRowAdapters()

        // Profile selector
        findViewById<Button>(R.id.btn_profile_selector).setOnClickListener {
            showProfileDialog()
        }

        // Profile management
        setupProfileButtons()

        // Restore Default: switch active profile back to Default (working copy)
        findViewById<Button>(R.id.btn_restore_default).setOnClickListener {
            requestProfileChange(LegItemLayout.DEFAULT_PROFILE_NAME)
        }

        // Save
        findViewById<Button>(R.id.btn_save).setOnClickListener { saveAndConfirm() }

        // Initial working copy + render
        loadWorkingFromProfile(currentProfileName)
        refreshUi()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        // Fold the live working rows into layoutState (in memory only) so the
        // entire staged state can be serialized as one LegItemLayout.
        // This does NOT touch disk or persistedSnapshot.
        saveCurrentWorkingCopy()

        outState.putString(STATE_WORKING_LAYOUT, LegItemLayout.toJson(layoutState))
        outState.putString(STATE_PERSISTED_SNAPSHOT, LegItemLayout.toJson(persistedSnapshot))
        outState.putString(STATE_CURRENT_PROFILE, currentProfileName)
    }

    private fun setupRowAdapters() {
        rowAdapters.clear()
        for (index in rowEditorIds.indices) {
            val rowRoot = findViewById<View>(rowEditorIds[index])

            rowRoot.findViewById<TextView>(R.id.tv_row_label).text = "Row ${index + 1}"

            val rv = rowRoot.findViewById<RecyclerView>(R.id.rv_row_items)
            rv.layoutManager = LinearLayoutManager(this)
            rv.isNestedScrollingEnabled = false

            val adapter = LegRowItemAdapter(
                isEditableProvider = { !workingBuiltIn },
                onMoveUp = { pos -> moveItem(index, pos, pos - 1) },
                onMoveDown = { pos -> moveItem(index, pos, pos + 1) },
                onDelete = { pos -> deleteItem(index, pos) },
                onEdit = { pos -> editItem(index, pos) }
            )
            rv.adapter = adapter
            rowAdapters.add(adapter)

            rowRoot.findViewById<Button>(R.id.btn_add_field).setOnClickListener {
                if (!workingBuiltIn) showFieldDialog(index, -1)
            }
            rowRoot.findViewById<Button>(R.id.btn_add_text).setOnClickListener {
                if (!workingBuiltIn) showLiteralDialog(index, -1)
            }
        }
    }

    private fun setupProfileButtons() {
        findViewById<Button>(R.id.btn_new).setOnClickListener {
            runWithUnsavedCheck { startNewProfile() }
        }
        findViewById<Button>(R.id.btn_duplicate).setOnClickListener {
            runWithUnsavedCheck { startDuplicateProfile() }
        }
        findViewById<Button>(R.id.btn_rename).setOnClickListener {
            startRenameProfile()
        }
        findViewById<Button>(R.id.btn_delete).setOnClickListener {
            confirmDeleteProfile()
        }
    }

    // ------------------------------------------------------------------ state

    private fun loadWorkingFromProfile(profileName: String) {
        val profile = layoutState.profiles[profileName]
            ?: LegItemLayout.getDefaultProfile()
        currentProfileName = profileName
        workingBuiltIn = profile.builtIn
        for (i in 0 until 4) {
            workingRows[i].clear()
            workingRows[i].addAll(profile.rows.getOrNull(i).orEmpty())
        }
    }

    /**
     * Compares the current staged state (layoutState + workingRows applied to
     * the viewed profile + active-profile selection) against persistedSnapshot.
     * Catches: row edits, active-profile changes, profile create/rename/delete.
     */
    private fun hasUnsavedChanges(): Boolean {
        val stagedProfiles = layoutState.profiles.toMutableMap()
        val viewed = stagedProfiles[currentProfileName]
        if (viewed != null && !viewed.builtIn) {
            stagedProfiles[currentProfileName] =
                viewed.copy(rows = (0 until 4).map { workingRows[it].toList() })
        }
        val staged = layoutState.copy(
            activeProfile = currentProfileName,
            profiles = stagedProfiles
        )
        return staged != persistedSnapshot
    }

    // ------------------------------------------------------------------ ui

    private fun refreshUi() {
        findViewById<Button>(R.id.btn_profile_selector).text = currentProfileName

        val viewed = layoutState.profiles[currentProfileName]
            ?: LegItemLayout.getDefaultProfile()
        val hasUnknown = viewed.hasUnknownFields(LegItemFields.knownIds)
        findViewById<View>(R.id.warning_banner).visibility =
            if (hasUnknown) View.VISIBLE else View.GONE

        for (i in 0 until 4) {
            val rowRoot = findViewById<View>(rowEditorIds[i])
            rowRoot.findViewById<Button>(R.id.btn_add_field).isEnabled = !workingBuiltIn
            rowRoot.findViewById<Button>(R.id.btn_add_text).isEnabled = !workingBuiltIn
            rowAdapters[i].submit(workingRows[i].toList())
        }

        updateProfileButtons()
        refreshPreview()
    }

    private fun updateProfileButtons() {
        // New and Duplicate are always available.
        findViewById<Button>(R.id.btn_new).isEnabled = true
        findViewById<Button>(R.id.btn_duplicate).isEnabled = true
        // Rename: not on the built-in Default.
        findViewById<Button>(R.id.btn_rename).isEnabled = !workingBuiltIn
        // Delete: enabled for any non-built-in profile. If the user then taps
        // it while viewing the active profile, confirmDeleteProfile() shows a
        // toast instead of proceeding.
        findViewById<Button>(R.id.btn_delete).isEnabled = !workingBuiltIn
    }

    private fun refreshPreview() {
        val previewCard = findViewById<View>(R.id.preview_card)
        previewCard.findViewById<TextView>(R.id.tv_line1).text =
            LegRowRenderer.renderRow(workingRows[0], sampleLeg, this)
        previewCard.findViewById<TextView>(R.id.tv_line2).text =
            LegRowRenderer.renderRow(workingRows[1], sampleLeg, this)
        previewCard.findViewById<TextView>(R.id.tv_block).text =
            LegRowRenderer.renderRow(workingRows[2], sampleLeg, this)
        previewCard.findViewById<TextView>(R.id.tv_comment).text =
            LegRowRenderer.renderRow(workingRows[3], sampleLeg, this)
    }

    private fun refreshRow(rowIndex: Int) {
        rowAdapters[rowIndex].submit(workingRows[rowIndex].toList())
        refreshPreview()
    }

    // ------------------------------------------------------------------ edits

    private fun moveItem(rowIndex: Int, from: Int, to: Int) {
        if (workingBuiltIn) return
        val row = workingRows[rowIndex]
        if (from !in row.indices || to !in row.indices) return
        val item = row.removeAt(from)
        row.add(to, item)
        refreshRow(rowIndex)
    }

    private fun deleteItem(rowIndex: Int, itemIndex: Int) {
        if (workingBuiltIn) return
        val row = workingRows[rowIndex]
        if (itemIndex !in row.indices) return
        row.removeAt(itemIndex)
        refreshRow(rowIndex)
    }

    private fun editItem(rowIndex: Int, itemIndex: Int) {
        if (workingBuiltIn) return
        val item = workingRows[rowIndex].getOrNull(itemIndex) ?: return
        when (item.type) {
            LegRowItem.TYPE_FIELD -> showFieldDialog(rowIndex, itemIndex)
            LegRowItem.TYPE_LITERAL -> showLiteralDialog(rowIndex, itemIndex)
        }
    }

    // ------------------------------------------------------------------ field dialog

    private fun showFieldDialog(rowIndex: Int, itemIndex: Int) {
        if (workingBuiltIn) return

        val existing = if (itemIndex >= 0)
            workingRows[rowIndex].getOrNull(itemIndex) else null

        val view = layoutInflater.inflate(R.layout.dialog_leg_item_field, null)
        val spinnerField = view.findViewById<Spinner>(R.id.spinner_field)
        val spinnerRenderer = view.findViewById<Spinner>(R.id.spinner_renderer)
        val rendererContainer = view.findViewById<View>(R.id.renderer_container)

        val fieldList = LegItemFields.all

        spinnerField.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            fieldList.map { it.displayName }
        ).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        fun updateRendererOptions(fieldIndex: Int, preferredRendererId: String?) {
            val field = fieldList[fieldIndex]
            if (field.renderers.isEmpty()) {
                rendererContainer.visibility = View.GONE
                return
            }
            rendererContainer.visibility = View.VISIBLE
            spinnerRenderer.adapter = ArrayAdapter(
                this,
                android.R.layout.simple_spinner_item,
                field.renderers.map { it.displayName }
            ).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
            val idx = field.renderers.indexOfFirst { it.id == preferredRendererId }
            spinnerRenderer.setSelection(if (idx >= 0) idx else 0)
        }

        var initialising = true

        spinnerField.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, position: Int, id: Long) {
                if (initialising) return
                updateRendererOptions(position, preferredRendererId = null)
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }

        val preselectedFieldIndex =
            fieldList.indexOfFirst { it.id == existing?.fieldId }.coerceAtLeast(0)
        spinnerField.setSelection(preselectedFieldIndex)
        updateRendererOptions(preselectedFieldIndex, existing?.rendererId)
        initialising = false

        AlertDialog.Builder(this)
            .setTitle(if (existing == null) "Add Field" else "Edit Field")
            .setView(view)
            .setPositiveButton("OK") { _, _ ->
                val field = fieldList[spinnerField.selectedItemPosition]
                val rendererId: String? = if (field.renderers.isEmpty()) {
                    null
                } else {
                    field.renderers
                        .getOrNull(spinnerRenderer.selectedItemPosition)
                        ?.id
                }
                val newItem = LegRowItem.field(field.id, rendererId)
                val row = workingRows[rowIndex]
                if (itemIndex >= 0) row[itemIndex] = newItem else row.add(newItem)
                refreshRow(rowIndex)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ------------------------------------------------------------------ literal dialog

    private fun showLiteralDialog(rowIndex: Int, itemIndex: Int) {
        if (workingBuiltIn) return

        val existing = if (itemIndex >= 0)
            workingRows[rowIndex].getOrNull(itemIndex) else null

        val view = layoutInflater.inflate(R.layout.dialog_leg_item_literal, null)
        val et = view.findViewById<EditText>(R.id.et_literal)
        et.setText(existing?.text ?: "")
        et.setSelection(et.text?.length ?: 0)

        AlertDialog.Builder(this)
            .setTitle(if (existing == null) "Add Text" else "Edit Text")
            .setView(view)
            .setPositiveButton("OK") { _, _ ->
                val raw = et.text?.toString() ?: ""
                val text = if (raw.length > LegItemLayout.MAX_LITERAL_LENGTH)
                    raw.substring(0, LegItemLayout.MAX_LITERAL_LENGTH) else raw
                val newItem = LegRowItem.literal(text)
                val row = workingRows[rowIndex]
                if (itemIndex >= 0) row[itemIndex] = newItem else row.add(newItem)
                refreshRow(rowIndex)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ------------------------------------------------------------------ profile dialogs

    private fun showProfileDialog() {
        val names = layoutState.profiles.keys.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Active profile")
            .setItems(names) { _, which ->
                requestProfileChange(names[which])
            }
            .show()
    }

    private fun requestProfileChange(newName: String) {
        if (newName == currentProfileName) return
        if (hasUnsavedChanges()) {
            AlertDialog.Builder(this)
                .setTitle("Discard changes?")
                .setMessage("Unsaved changes to \"$currentProfileName\" will be lost.")
                .setPositiveButton("Discard") { _, _ -> switchProfile(newName) }
                .setNegativeButton("Cancel", null)
                .show()
        } else {
            switchProfile(newName)
        }
    }

    private fun switchProfile(newName: String) {
        loadWorkingFromProfile(newName)
        refreshUi()
    }

    // ---------------------------------------------------- profile CRUD: New / Duplicate

    private fun startNewProfile() {
        showProfileNameDialog(
            title = "New profile",
            initial = "New profile",
            confirmLabel = "Create"
        ) { name ->
            createProfileFromWorkingCopy(name)
        }
    }

    private fun startDuplicateProfile() {
        showProfileNameDialog(
            title = "Duplicate profile",
            initial = "$currentProfileName copy",
            confirmLabel = "Duplicate"
        ) { name ->
            createProfileFromWorkingCopy(name)
        }
    }

    private fun createProfileFromWorkingCopy(name: String) {
        val newProfile = LegProfile(
            name = name,
            builtIn = false,
            rows = (0 until 4).map { workingRows[it].toList() }
        )
        layoutState = layoutState.copy(
            profiles = layoutState.profiles + (name to newProfile)
        )
        switchProfile(name)
    }

    // ---------------------------------------------------- profile CRUD: Rename

    private fun startRenameProfile() {
        if (workingBuiltIn) return
        showProfileNameDialog(
            title = "Rename profile",
            initial = currentProfileName,
            confirmLabel = "Rename",
            allowExisting = currentProfileName
        ) { newName ->
            if (newName == currentProfileName) return@showProfileNameDialog
            val oldProfile = layoutState.profiles[currentProfileName]
                ?: return@showProfileNameDialog
            val renamed = oldProfile.copy(name = newName)
            val wasActive = layoutState.activeProfile == currentProfileName
            val newProfiles = layoutState.profiles.toMutableMap()
            newProfiles.remove(currentProfileName)
            newProfiles[newName] = renamed
            layoutState = layoutState.copy(
                profiles = newProfiles,
                activeProfile = if (wasActive) newName else layoutState.activeProfile
            )
            currentProfileName = newName
            refreshUi()
        }
    }

    // ---------------------------------------------------- profile CRUD: Delete

    private fun confirmDeleteProfile() {
        if (workingBuiltIn) return
        if (currentProfileName == layoutState.activeProfile) {
            Toast.makeText(
                this,
                "Can NOT Delete Active Profile",
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Delete profile?")
            .setMessage("\"$currentProfileName\" will be deleted. This cannot be undone.")
            .setPositiveButton("Delete") { _, _ -> performDeleteProfile() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun performDeleteProfile() {
        val newProfiles = layoutState.profiles.toMutableMap()
        newProfiles.remove(currentProfileName)
        layoutState = layoutState.copy(profiles = newProfiles)
        switchProfile(LegItemLayout.DEFAULT_PROFILE_NAME)
    }

    // ------------------------------------------------------------------ name dialog

    private fun showProfileNameDialog(
        title: String,
        initial: String,
        confirmLabel: String,
        allowExisting: String? = null,
        onConfirm: (String) -> Unit
    ) {
        val view = layoutInflater.inflate(R.layout.dialog_leg_profile_name, null)
        val et = view.findViewById<EditText>(R.id.et_profile_name)
        et.setText(initial)
        et.setSelection(et.text?.length ?: 0)

        val dialog = AlertDialog.Builder(this)
            .setTitle(title)
            .setView(view)
            .setPositiveButton(confirmLabel, null)   // overridden below
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = et.text?.toString()?.trim().orEmpty()
                when {
                    name.isEmpty() -> {
                        et.error = "Name cannot be empty"
                    }
                    name.length > MAX_PROFILE_NAME_LENGTH -> {
                        et.error = "Name too long (max $MAX_PROFILE_NAME_LENGTH)"
                    }
                    name != allowExisting && layoutState.profiles.containsKey(name) -> {
                        et.error = "A profile with this name already exists"
                    }
                    else -> {
                        dialog.dismiss()
                        onConfirm(name)
                    }
                }
            }
        }
        dialog.show()
    }

    // ------------------------------------------------------------------ unsaved guard

    /**
     * Runs [action], but first asks the user what to do if the current
     * working state differs from what is persisted.
     *  - Save    -> commits the working copy in memory, then runs action.
     *  - Discard -> resets the working copy of the current profile, then runs action.
     *  - Cancel  -> nothing.
     */
    private fun runWithUnsavedCheck(action: () -> Unit) {
        if (!hasUnsavedChanges()) {
            action()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Unsaved changes")
            .setMessage("Save changes to \"$currentProfileName\"?")
            .setPositiveButton("Save") { _, _ ->
                saveCurrentWorkingCopy()
                action()
            }
            .setNeutralButton("Discard") { _, _ ->
                loadWorkingFromProfile(currentProfileName)
                refreshUi()
                action()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /**
     * Writes the working rows into layoutState for the current profile.
     * Does not persist to disk — only updates the in-memory working copy.
     */
    private fun saveCurrentWorkingCopy() {
        val current = layoutState.profiles[currentProfileName] ?: return
        if (current.builtIn) return
        val newProfiles = layoutState.profiles.toMutableMap()
        newProfiles[currentProfileName] = current.copy(
            rows = (0 until 4).map { workingRows[it].toList() }
        )
        layoutState = layoutState.copy(profiles = newProfiles)
    }

    // ------------------------------------------------------------------ save / finish

    private fun saveAndConfirm() {
        // Commit working rows into layoutState
        saveCurrentWorkingCopy()

        val updated = layoutState.copy(activeProfile = currentProfileName)
        PreferencesManager.getInstance(this).saveLegItemLayout(updated)

        layoutState = updated
        persistedSnapshot = updated

        // The viewed profile just became the active one, so the Delete button
        // must be disabled again. Refresh its state (and only it - the rows,
        // preview and banner are unaffected).
        updateProfileButtons()

        Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
    }

    private fun tryFinish() {
        if (hasUnsavedChanges()) {
            AlertDialog.Builder(this)
                .setTitle("Discard changes?")
                .setMessage("Unsaved changes will be lost.")
                .setPositiveButton("Discard") { _, _ -> finish() }
                .setNegativeButton("Cancel", null)
                .show()
        } else {
            finish()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            tryFinish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    // ------------------------------------------------------------------ sample leg

    private fun buildSampleLeg(): Leg = Leg(
        offBlock        = utc(2026, 5, 30, 14, 30),
        takeoffTime     = utc(2026, 5, 30, 14, 45),
        landingTime     = utc(2026, 5, 30, 16,  0),
        onBlock         = utc(2026, 5, 30, 16, 15),
        position        = "PIC",
        picName         = "SELF",
        sicName         = "DOE J.",
        jumpseat1       = "Elvis P.",
        jumpseat2       = "Ruben S.",
        cabinCrew       = "Halley W.",
        acRegistration  = "D-ABCD",
        acType          = "B737-Max",
        category        = "Multi Pilot",
        flightNumber    = "FN123",
        icaoDeparture   = "EDDF",
        icaoDestination = "EGLL",
        blockTimeTotal  = 105,
        blockTimeNight  = 15,
        pilotFlying     = true,
        comment         = "Sample comment"
    )

    private fun utc(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            set(year, month - 1, day, hour, minute, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }
}