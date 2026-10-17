package com.my.striptologbook.data

sealed class SearchField(
    val name: String,
    val displayName: String,
    val type: String // "string", "boolean", "timestamp", "duration", "number"
) {
    abstract fun getValue(leg: Leg): Any?
}

class StringField(name: String, displayName: String, private val getter: (Leg) -> String?) :
    SearchField(name, displayName, "string") {
    override fun getValue(leg: Leg): Any? = getter(leg)
}

class BooleanField(name: String, displayName: String, private val getter: (Leg) -> Boolean?) :
    SearchField(name, displayName, "boolean") {
    override fun getValue(leg: Leg): Any? = getter(leg)
}

class TimestampField(name: String, displayName: String, private val getter: (Leg) -> Long?) :
    SearchField(name, displayName, "timestamp") {
    override fun getValue(leg: Leg): Any? = getter(leg)
}

class DurationField(name: String, displayName: String, private val getter: (Leg) -> Int?) :
    SearchField(name, displayName, "duration") {
    override fun getValue(leg: Leg): Any? = getter(leg)
}

class NumberField(name: String, displayName: String, private val getter: (Leg) -> Int?) :
    SearchField(name, displayName, "number") {
    override fun getValue(leg: Leg): Any? = getter(leg)
}

object SearchFields {
    val allFields = listOf(
        StringField("position", "Position") { it.position },
        StringField("picName", "PIC Name") { it.picName },
        StringField("sicName", "SIC Name") { it.sicName },
        StringField("jumpseat1", "Jumpseat 1") { it.jumpseat1 },
        StringField("jumpseat2", "Jumpseat 2") { it.jumpseat2 },
        StringField("cabinCrew", "Cabin Crew") { it.cabinCrew },
        StringField("acRegistration", "Registration") { it.acRegistration },
        StringField("acType", "Aircraft Type") { it.acType },
        StringField("flightNumber", "Flight Number") { it.flightNumber },
        StringField("category", "Category") { it.category },
        StringField("icaoDeparture", "Departure ICAO") { it.icaoDeparture },
        StringField("icaoDestination", "Destination ICAO") { it.icaoDestination },
        StringField("comment", "Comment") { it.comment },
        // Boolean
        BooleanField("isSimulator", "Simulator") { it.isSimulator },
        BooleanField("pilotFlying", "Pilot Flying") { it.pilotFlying },
        // Timestamp
        TimestampField("offBlock", "Off Block") { it.offBlock },
        TimestampField("takeoffTime", "Takeoff Time") { it.takeoffTime },
        TimestampField("landingTime", "Landing Time") { it.landingTime },
        TimestampField("onBlock", "On Block") { it.onBlock },
        TimestampField("sunrise", "Sunrise") { it.sunrise },
        TimestampField("sunset", "Sunset") { it.sunset },
        // Duration
        DurationField("blockTimeDay", "Block Time Day") { it.blockTimeDay },
        DurationField("blockTimeNight", "Block Time Night") { it.blockTimeNight },
        DurationField("blockTimeTotal", "Block Time Total") { it.blockTimeTotal },
        DurationField("flightTimeDay", "Flight Time Day") { it.flightTimeDay },
        DurationField("flightTimeNight", "Flight Time Night") { it.flightTimeNight },
        DurationField("flightTimeTotal", "Flight Time Total") { it.flightTimeTotal },
        DurationField("simulatorTime", "Simulator Time") { it.simulatorTime },
        DurationField("ifrDay", "IFR Day") { it.ifrDay },
        DurationField("ifrNight", "IFR Night") { it.ifrNight },
        DurationField("ifrTotal", "IFR Total") { it.ifrTotal },
        // Number
        NumberField("numberToDay", "Takeoffs Day") { it.numberToDay },
        NumberField("numberToNight", "Takeoffs Night") { it.numberToNight },
        NumberField("numberToTotal", "Takeoffs Total") { it.numberToTotal },
        NumberField("numberLdgDay", "Landings Day") { it.numberLdgDay },
        NumberField("numberLdgNight", "Landings Night") { it.numberLdgNight },
        NumberField("numberLdgTotal", "Landings Total") { it.numberLdgTotal },
        NumberField("numberInstrumentApproaches", "Instrument Approaches") { it.numberInstrumentApproaches }

    )

    fun getFieldsForOperator(operator: SearchOperator): List<SearchField> {
        return when (operator) {
            SearchOperator.EQ_CONTAINS, SearchOperator.NOT_EQ -> allFields // CHANGED
            SearchOperator.LE, SearchOperator.GE -> allFields.filter {
                it.type in listOf("timestamp", "duration", "number")
            }
        }
    }
}