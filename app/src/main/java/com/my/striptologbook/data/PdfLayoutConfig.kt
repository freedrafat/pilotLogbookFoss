package com.my.striptologbook.data

import kotlinx.serialization.Serializable

/** Physical page size, A4 landscape, in PDF points (1 pt = 1/72 inch). */
const val PDF_PAGE_WIDTH  = 842f
const val PDF_PAGE_HEIGHT = 595f

@Serializable
data class PdfLayoutConfig(
    // Column widths (pre-scale, in pt)
    val widthDate: Float          = 44f,
    val widthPlace: Float         = 28f,
    val widthTime: Float          = 28f,
    val widthAircraftModel: Float = 40f,
    val widthAircraftReg: Float   = 34f,
    val widthDuration: Float      = 34f,
    val widthNamePic: Float       = 44f,
    val widthCount: Float         = 20f,
    val widthRemarks: Float       = 90f,

    // Page layout
    val marginLeft: Float    = 12f,
    val marginRight: Float   = 12f,
    val marginTop: Float     = 22f,
    val marginBottom: Float  = 18f,
    val titleHeight: Float   = 20f,
    val headerRowHeight: Float = 13f,
    val pageNumArea: Float   = 12f,

    // Text sizes
    val titleTextSize: Float   = 10f,
    val headerTextSize: Float  = 5.5f,
    val bodyTextSize: Float    = 6.5f,
    val remarksTextSize: Float = 5.5f,
    val certTextSize: Float    = 6f,
    val ownerTextSize: Float = 6.5f,

    val fontFamily: String = "SANS_SERIF",   // "SANS_SERIF" | "SERIF" | "MONOSPACE"
    val alternateRowBackground: Boolean = false,

) {
    /** Flat 24‑column array. Indices must match the layout in PdfGenerator. */
    fun columnWidths(): FloatArray = floatArrayOf(
        widthDate,          // 0  Date
        widthPlace,         // 1  Dep Place
        widthTime,          // 2  Dep Time
        widthPlace,         // 3  Arr Place
        widthTime,          // 4  Arr Time
        widthAircraftModel, // 5  A/C Model
        widthAircraftReg,   // 6  A/C Reg
        widthDuration,      // 7  SP SE
        widthDuration,      // 8  SP ME
        widthDuration,      // 9  Multi
        widthDuration,      // 10 Total Flight
        widthNamePic,       // 11 Name PIC
        widthCount,         // 12 TO Day
        widthCount,         // 13 TO Night
        widthCount,         // 14 LDG Day
        widthCount,         // 15 LDG Night
        widthDuration,      // 16 Op Night
        widthDuration,      // 17 Op IFR
        widthDuration,      // 18 PF PIC
        widthDuration,      // 19 PF Co-pilot
        widthDuration,      // 20 PF Dual
        widthDuration,      // 21 PF Instructor
        widthDuration,      // 22 FSTD
        widthRemarks        // 23 Remarks
    )

    val declaredSum: Float   get() = columnWidths().sum()
    val contentWidth: Float  get() = PDF_PAGE_WIDTH - marginLeft - marginRight
    val scaleFactor: Float   get() = contentWidth / declaredSum

    companion object {
        val DEFAULT = PdfLayoutConfig()
    }
}