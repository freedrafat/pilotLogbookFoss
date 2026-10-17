package com.my.striptologbook.data

object PdfTemplates {
    val ICAO: List<String> = listOf(
        "offBlock",
        "flightNumber",
        "icaoDeparture",
        "icaoDestination",
        "acType",
        "acRegistration",
        "picName",
        "sicName",
        "blockTimeTotal",
        "flightTimeTotal",
        "numberLdgTotal",
        "comment"
    )

    val EASA: List<String> = listOf(
        "offBlock",
        "flightNumber",
        "icaoDeparture",
        "icaoDestination",
        "acType",
        "acRegistration",
        "picName",
        "sicName",
        "blockTimeDay",
        "blockTimeNight",
        "blockTimeTotal",
        "flightTimeDay",
        "flightTimeNight",
        "flightTimeTotal",
        "ifrDay",
        "ifrNight",
        "ifrTotal",
        "numberToDay",
        "numberToNight",
        "numberToTotal",
        "numberLdgDay",
        "numberLdgNight",
        "numberLdgTotal",
        "simulatorTime",
        "comment"
    )
}