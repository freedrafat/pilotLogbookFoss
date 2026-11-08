# Pilot Flight Logbook

A privacy-focused, FOSS & fully local Android application designed for professional and private
pilots.
*   Record flight legs.
*   Calculate precise day/night times along your route using the Flight Sun Calculator.
*   Export PDF logbooks according to ICAO / EASA AMC1 FCL.050 standards.

**All logbook data is stored locally on your device. No personal data is sent to any server.**

## ✨ Features

*   **Logbook Legs:** Record block time, flight time, IFR, simulator time, and crew details.
*   **Auto-Calculations:** Automatically calculates night/day times, block totals, and takeoff/landing counts based on sunrise/sunset and Pilot Flying status.
*   **Flight Sun Calculator:** Computes moving sunrise and sunset events along your route, factoring in changing aircraft position and cruise altitude.
*   **Opening Balance:** Input your prior flight experience to see accurate career totals in the app and PDF exports.
*   **Airport Database:** Local IATA/ICAO database with autocomplete in the Leg Editor and Sun Calculator. Can be updated from the web, edited, and exported.
*   **PDF Export:** Generate printable EASA-compliant logbook pages with customizable layouts and signatures. Export the full logbook from Tools, or a filtered PDF of the current filter or search result from the Leg List.
*   **Backup (JSON and CSV):** Two backup formats, one purpose.
    *   **JSON backup** contains the full logbook (legs and Opening Balance) and is restorable in one step.
    *   **CSV backup** is a human-readable, spreadsheet-editable export of the legs, importable into an empty logbook.
*   **CSV Export from Leg List:** Export the currently filtered or searched legs in a configurable CSV format, for reporting or sharing.
*   **Verified Pre-Backup:** Restore, Delete All, CSV import, and update download all run a verified backup of the current database before touching it, and warn the user with a "Continue anyway?" dialog if the backup cannot be verified.
*   **Work Directory:** Every export, backup, and import writes into a single user-chosen folder, remembered across sessions.
*   **Settings Backup / Restore:** Save and restore your preferences (default fields, custom lists, PDF layout, statistics visibility, signature) as a JSON file, for device migration.
*   **Customizable UI:** Choose what appears on your leg cards in the list view using the "Leg Items" feature.

## 🚀 Installation

You can download the latest version of the application directly from the [Releases page](https://github.com/freedrafat/pilotLogbookFoss/releases/latest). 

*Note: Since the APK is signed with a private developer key, you must uninstall any previous versions from other stores before installing an update from this repository.*

## 📖 User Manual

For detailed instructions on how to use every feature of the app, please refer to the **[User Manual](app/src/main/assets/user_manual.txt)** included in this repository.

## 🛡️ Privacy Policy

This application does not collect, transmit, or share any personal data. All logbook data, settings, and backups are stored entirely on your local device. The app performs only two outbound network operations, neither of which sends personal data:

*   **Update check** — to determine whether a newer version exists.
*   **Airport database download** — to fetch the latest airport JSON when you tap Update Airport DB.

## 🤝 Support

If you encounter any issues or have questions, please contact support at **freedrafat@proton.me** or open an issue on the [GitHub Issues page](https://github.com/freedrafat/pilotLogbookFoss/issues).
