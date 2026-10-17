package com.my.striptologbook.ui

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.my.striptologbook.R
import com.my.striptologbook.data.AirportDataManager
import com.my.striptologbook.data.AirportDataManager.Airport
import com.my.striptologbook.utils.InsetsHelper
import java.util.*
import kotlin.math.abs

class AirportEditorActivity : BaseActivity() {

    private lateinit var dataManager: AirportDataManager
    private lateinit var autoCompleteSearch: AutoCompleteTextView
    private lateinit var editName: EditText
    private lateinit var editCity: EditText
    private lateinit var editState: EditText
    private lateinit var editCountry: EditText
    private lateinit var editIata: EditText
    private lateinit var editIcao: EditText
    private lateinit var editLatitude: EditText
    private lateinit var editLongitude: EditText
    private lateinit var editElevation: EditText
    private lateinit var editTimezone: EditText
    private lateinit var tvCoordinates: TextView
    private lateinit var tvTimeOffsetDisplay: TextView
    private lateinit var btnEdit: Button
    private lateinit var btnSave: Button
    private lateinit var btnCancel: Button
    private lateinit var btnDelete: Button
    private lateinit var btnAddNew: Button

    private var currentAirport: Airport? = null
    private var isAddingNew = false
    private var isEditMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_airport_editor)

        // -------- Toolbar ------------
        val spacer = findViewById<View>(R.id.status_bar_spacer)
        InsetsHelper.applyStatusBarSpacer(spacer)

        val scrollView = findViewById<ScrollView>(R.id.scroll_view)
        InsetsHelper.attachScrollToFocusedView(this, scrollView)
        InsetsHelper.applyImeBottomPaddingToRoot(this)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = "Airports Editor"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        // -------- Toolbar ------------

        dataManager = AirportDataManager.getInstance(this)

        autoCompleteSearch = findViewById(R.id.autoCompleteSearch)
        editName = findViewById(R.id.editAirportName)
        editCity = findViewById(R.id.editCity)
        editState = findViewById(R.id.editState)
        editCountry = findViewById(R.id.editCountry)
        editIata = findViewById(R.id.editIata)
        editIcao = findViewById(R.id.editIcao)
        editLatitude = findViewById(R.id.editLatitude)
        editLongitude = findViewById(R.id.editLongitude)
        editElevation = findViewById(R.id.editElevation)
        editTimezone = findViewById(R.id.editTimezone)
        tvCoordinates = findViewById(R.id.tvCoordinates)
        tvTimeOffsetDisplay = findViewById(R.id.tvTimeOffsetDisplay)
        btnEdit = findViewById(R.id.btnEdit)
        btnSave = findViewById(R.id.btnSaveAirport)
        btnCancel = findViewById(R.id.btnCancel)
        btnDelete = findViewById(R.id.btnDeleteAirport)
        btnAddNew = findViewById(R.id.btnAddNew)

        setupAutoComplete()

        // Restore state if any
        if (savedInstanceState != null) {
            val selectedIcao = savedInstanceState.getString("selectedIcao")
            isAddingNew = savedInstanceState.getBoolean("isAddingNew", false)
            isEditMode = savedInstanceState.getBoolean("isEditMode", false)
            if (!selectedIcao.isNullOrEmpty()) {
                currentAirport = dataManager.findAirportByCode(selectedIcao)
                currentAirport?.let { populateFields(it) }
                btnDelete.isEnabled = currentAirport != null
            }
            autoCompleteSearch.setText(savedInstanceState.getString("searchText", ""))
            setEditMode(isEditMode)
        } else {
            setEditMode(false)
            btnDelete.isEnabled = false
        }

        // Click listeners
        btnEdit.setOnClickListener {
            if (currentAirport == null && !isAddingNew) {
                Toast.makeText(this, "Please select an airport or add a new one", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            setEditMode(true)
        }

        btnCancel.setOnClickListener {
            if (currentAirport != null) {
                populateFields(currentAirport!!)
            } else {
                clearFields()
            }
            setEditMode(false)
        }

        btnSave.setOnClickListener { saveAirport() }

        btnAddNew.setOnClickListener {
            clearFields()
            currentAirport = null
            isAddingNew = true
            btnDelete.isEnabled = false
            setEditMode(true)
            editName.requestFocus()
        }

        btnDelete.setOnClickListener {
            val airport = currentAirport
            if (airport == null) {
                Toast.makeText(this, "No airport selected to delete", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            AlertDialog.Builder(this)
                .setTitle("Delete Airport")
                .setMessage("Are you sure you want to delete ${airport.iata} (${airport.icao})?")
                .setPositiveButton("Delete") { _, _ ->
                    val deleted = dataManager.deleteAirport(airport.icao)
                    if (deleted) {
                        Toast.makeText(this, "Airport deleted", Toast.LENGTH_SHORT).show()
                        clearFields()
                        currentAirport = null
                        isAddingNew = false
                        autoCompleteSearch.setText("")
                        setEditMode(false)
                        setupAutoComplete()
                        btnDelete.isEnabled = false
                    } else {
                        Toast.makeText(this, "Delete failed", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        currentAirport?.let { outState.putString("selectedIcao", it.icao) }
        outState.putBoolean("isAddingNew", isAddingNew)
        outState.putBoolean("isEditMode", isEditMode)
        outState.putString("searchText", autoCompleteSearch.text.toString())
    }

    // ---------- Mode management ----------
    private fun setEditMode(editMode: Boolean) {
        isEditMode = editMode

        // Enable/disable fields without dimming (use focusable)
        val editTexts = arrayOf(
            editName, editCity, editState, editCountry,
            editIata, editIcao, editLatitude, editLongitude,
            editElevation, editTimezone
        )
        for (et in editTexts) {
            et.isFocusable = editMode
            et.isFocusableInTouchMode = editMode
            et.isClickable = editMode
            et.isCursorVisible = editMode
        }

        // Show/hide background (underline)
        if (editMode) {
            val attrs = intArrayOf(android.R.attr.editTextBackground)
            val ta = obtainStyledAttributes(attrs)
            val drawable = ta.getDrawable(0)
            ta.recycle()
            for (et in editTexts) {
                et.background = drawable
            }
        } else {
            for (et in editTexts) {
                et.background = null
            }
        }

        // Buttons
        btnAddNew.visibility = if (editMode) View.GONE else View.VISIBLE
        btnEdit.visibility = if (editMode) View.GONE else View.VISIBLE
        btnSave.visibility = if (editMode) View.VISIBLE else View.GONE
        btnCancel.visibility = if (editMode) View.VISIBLE else View.GONE
        btnDelete.isEnabled = currentAirport != null
    }

    // ---------- Auto-complete ----------
    private fun setupAutoComplete() {
        val adapter = object : ArrayAdapter<Airport>(this, android.R.layout.simple_dropdown_item_1line) {
            override fun getFilter(): Filter {
                return object : Filter() {
                    override fun performFiltering(constraint: CharSequence?): FilterResults {
                        val results = FilterResults()
                        if (constraint.isNullOrEmpty()) {
                            results.values = emptyList<Airport>()
                            results.count = 0
                            return results
                        }
                        val filtered = dataManager.searchAirports(constraint.toString())
                        results.values = filtered
                        results.count = filtered.size
                        return results
                    }

                    override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                        clear()
                        if (results?.values != null) {
                            @Suppress("UNCHECKED_CAST")
                            addAll(results.values as List<Airport>)
                        }
                        notifyDataSetChanged()
                    }
                }
            }

            override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                val view = super.getView(position, convertView, parent)
                val airport = getItem(position)
                if (airport != null) {
                    val textView = view as TextView
                    textView.text = "${airport.icao} - ${airport.iata} - ${airport.city} - ${airport.name}"
                }
                return view
            }
        }

        autoCompleteSearch.setAdapter(adapter)
        autoCompleteSearch.threshold = 1
        autoCompleteSearch.setOnItemClickListener { parent, _, position, _ ->
            val selected = parent.getItemAtPosition(position) as Airport
            autoCompleteSearch.setText("") // clear after selection
            populateFields(selected)
            currentAirport = selected
            isAddingNew = false
            btnDelete.isEnabled = true
            setEditMode(isEditMode)
            autoCompleteSearch.clearFocus()
            hideKeyboard(autoCompleteSearch)
        }
    }

    // ---------- Form helpers ----------
    private fun populateFields(airport: Airport) {
        editName.setText(airport.name)
        editCity.setText(airport.city)
        editState.setText(airport.state)
        editCountry.setText(airport.country)
        editIata.setText(airport.iata)
        editIcao.setText(airport.icao)
        editLatitude.setText(if (airport.latitude.isNaN()) "" else airport.latitude.toString())
        editLongitude.setText(if (airport.longitude.isNaN()) "" else airport.longitude.toString())
        editElevation.setText(if (airport.elevation.isNaN()) "" else airport.elevation.toString())
        editTimezone.setText(airport.timezone)
        updateDisplayInfo(airport)
    }

    private fun clearFields() {
        arrayOf(editName, editCity, editState, editCountry, editIata, editIcao,
            editLatitude, editLongitude, editElevation, editTimezone).forEach { it.setText("") }
        tvCoordinates.text = "--"
        tvTimeOffsetDisplay.text = "--"
    }

    private fun updateDisplayInfo(airport: Airport?) {
        if (airport == null) {
            tvCoordinates.text = "--"
            tvTimeOffsetDisplay.text = "--"
            return
        }
        val coordStr = if (!airport.latitude.isNaN() && !airport.longitude.isNaN()) {
            "${formatLatitude(airport.latitude)} - ${formatLongitude(airport.longitude)}"
        } else {
            "N/A"
        }
        tvCoordinates.text = coordStr

        val tzStr = if (!airport.timezone.isNullOrEmpty()) {
            formatTimezoneOffset(airport.timezone)
        } else {
            "N/A"
        }
        tvTimeOffsetDisplay.text = tzStr
    }

    private fun formatLatitude(value: Double) = formatCoordinate(value, 'N', 'S', 2)
    private fun formatLongitude(value: Double) = formatCoordinate(value, 'E', 'W', 3)

    private fun formatCoordinate(value: Double, positive: Char, negative: Char, degWidth: Int): String {
        if (value.isNaN()) return "--"
        val sign = if (value >= 0) positive else negative
        val abs = abs(value)
        val degrees = abs.toInt()
        val minutes = (abs - degrees) * 60
        return String.format(Locale.US, "%c%0${degWidth}d°%02.1f", sign, degrees, minutes)
    }

    private fun formatTimezoneOffset(timezoneId: String): String {
        val tz = TimeZone.getTimeZone(timezoneId)
        val offset = tz.getOffset(System.currentTimeMillis())
        val hours = offset / 3600000
        val minutes = (offset % 3600000) / 60000
        val sign = if (hours >= 0) "+" else "-"
        val absHours = abs(hours)
        return if (minutes == 0) {
            String.format(Locale.US, "UTC%s%d", sign, absHours)
        } else {
            String.format(Locale.US, "UTC%s%d:%02d", sign, absHours, abs(minutes))
        }
    }

    // ---------- Save logic ----------
    private fun saveAirport() {
        val name = editName.text.toString().trim()
        val iata = editIata.text.toString().trim().uppercase()
        val icao = editIcao.text.toString().trim().uppercase()

        if (name.isEmpty() || iata.isEmpty() || icao.isEmpty()) {
            Toast.makeText(this, "Name, IATA and ICAO are required", Toast.LENGTH_SHORT).show()
            return
        }
        if (iata.length != 3) {
            Toast.makeText(this, "IATA must be exactly 3 characters", Toast.LENGTH_SHORT).show()
            return
        }
        if (icao.length != 4) {
            Toast.makeText(this, "ICAO must be exactly 4 characters", Toast.LENGTH_SHORT).show()
            return
        }

        val lat = editLatitude.text.toString().trim().toDoubleOrNull() ?: Double.NaN
        val lon = editLongitude.text.toString().trim().toDoubleOrNull() ?: Double.NaN
        val elev = editElevation.text.toString().trim().toDoubleOrNull() ?: Double.NaN
        val city = editCity.text.toString().trim()
        val state = editState.text.toString().trim()
        val country = editCountry.text.toString().trim()
        val timezone = editTimezone.text.toString().trim()

        val airport = Airport(name, city, state, country, iata, icao, lat, lon, elev, timezone)

        val success = if (isAddingNew) {
            dataManager.addAirport(airport)
        } else {
            val current = currentAirport
            if (current == null) {
                Toast.makeText(this, "No airport selected to update", Toast.LENGTH_SHORT).show()
                return
            }
            if (current.icao != icao) {
                Toast.makeText(this, "Cannot change ICAO. Delete and re-add if needed.", Toast.LENGTH_SHORT).show()
                return
            }
            dataManager.updateAirport(airport)
        }

        if (success) {
            Toast.makeText(this, if (isAddingNew) "Airport added" else "Airport updated", Toast.LENGTH_SHORT).show()
            if (isAddingNew) {
                clearFields()
                currentAirport = null
                isAddingNew = false
                setEditMode(false)
                setupAutoComplete()
                btnDelete.isEnabled = false
                updateDisplayInfo(null)
            } else {
                populateFields(airport)
                currentAirport = airport
                setEditMode(false)
                setupAutoComplete()
                btnDelete.isEnabled = true
            }
        } else {
            Toast.makeText(this, if (isAddingNew) "Failed to add (ICAO/IATA may exist)" else "Update failed", Toast.LENGTH_SHORT).show()
        }
    }

    // ---------- Helper ----------
    private fun hideKeyboard(view: View) {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }
}