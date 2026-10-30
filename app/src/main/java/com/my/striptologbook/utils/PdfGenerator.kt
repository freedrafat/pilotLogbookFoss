package com.my.striptologbook.utils

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.my.striptologbook.data.Leg
import com.my.striptologbook.data.LegTotals
import com.my.striptologbook.data.PDF_PAGE_HEIGHT
import com.my.striptologbook.data.PDF_PAGE_WIDTH
import com.my.striptologbook.data.PdfLayoutConfig

object PdfGenerator {

    private const val MIN_ROW_HEIGHT = 6f

    private const val FIRST_DATA_COL = 7
    private const val LAST_DATA_COL  = 22
    private const val REMARKS_COL    = 23

    private val exteriorPaint = Paint().apply {
        color = Color.BLACK; strokeWidth = 1.0f; isAntiAlias = true
    }
    private val sectionPaint = Paint().apply {
        color = Color.DKGRAY; strokeWidth = 0.8f; isAntiAlias = true
    }
    private val columnPaint = Paint().apply {
        color = Color.DKGRAY; strokeWidth = 0.5f; isAntiAlias = true
    }
    private val rowPaint = Paint().apply {
        color = Color.LTGRAY; strokeWidth = 0.3f; isAntiAlias = true
    }

    private val altRowPaint = Paint().apply {
        color = Color.parseColor("#F2F2F2"); style = Paint.Style.FILL; isAntiAlias = true
    }
    private enum class Align { CENTER, LEFT }

    private data class SubCol(
        val label: String,
        val align: Align = Align.CENTER,
        val value: (Leg) -> String
    )

    private data class ColGroup(val name: String, val subCols: List<SubCol>)

    private fun buildGroups(): List<ColGroup> = listOf(
        ColGroup("Date", listOf(SubCol("") { l -> l.offBlock?.let { formatDateOnly(it) } ?: "" })),
        ColGroup("Departure", listOf(
            SubCol("Place") { it.icaoDeparture ?: "" },
            SubCol("Time") { l -> l.offBlock?.let { extractTimeOnly(it) } ?: "" }
        )),
        ColGroup("Arrival", listOf(
            SubCol("Place") { it.icaoDestination ?: "" },
            SubCol("Time") { l -> l.onBlock?.let { extractTimeOnly(it) } ?: "" }
        )),
        ColGroup("Aircraft", listOf(
            SubCol("Make/Model") { it.acType ?: "" },
            SubCol("Reg") { it.acRegistration ?: "" }
        )),
        ColGroup("Single-Pilot", listOf(
            SubCol("SE") { l -> if (l.category == "Single Pilot SE") fmtDur(l.blockTimeTotal) else "" },
            SubCol("ME") { l -> if (l.category == "Single Pilot ME") fmtDur(l.blockTimeTotal) else "" }
        )),
        ColGroup("Multi-Pilot", listOf(
            SubCol("") { l -> if (l.category == "Multi Pilot") fmtDur(l.blockTimeTotal) else "" }
        )),
        ColGroup("Total Flight", listOf(
            SubCol("Total") { fmtDur(it.blockTimeTotal) },
            SubCol("Name PIC") { it.picName ?: "" }
        )),
        ColGroup("Takeoffs", listOf(
            SubCol("Day") { it.numberToDay?.toString() ?: "" },
            SubCol("Night") { it.numberToNight?.toString() ?: "" }
        )),
        ColGroup("Landings", listOf(
            SubCol("Day") { it.numberLdgDay?.toString() ?: "" },
            SubCol("Night") { it.numberLdgNight?.toString() ?: "" }
        )),
        ColGroup("Op. Cond.", listOf(
            SubCol("Night") { fmtDur(it.blockTimeNight) },
            SubCol("IFR") { l -> if ((l.ifrTotal ?: 0) > 0) fmtDur(l.blockTimeTotal) else "" }
        )),
        ColGroup("Pilot Function", listOf(
            SubCol("PIC") { l ->
                if (l.position in setOf("PIC", "SPIC", "PICUS")) fmtDur(l.blockTimeTotal) else ""
            },
            SubCol("Co-pilot") { l -> if (l.position == "SIC") fmtDur(l.blockTimeTotal) else "" },
            SubCol("Dual") { l -> if (l.position == "Dual") fmtDur(l.blockTimeTotal) else "" },
            SubCol("Instructor") { l -> if (l.position == "Instructor") fmtDur(l.blockTimeTotal) else "" }
        )),
        ColGroup("FSTD", listOf(SubCol("Total") { fmtDur(it.simulatorTime) })),
        ColGroup("Remarks", listOf(SubCol("", Align.LEFT) { leg ->
            val parts = mutableListOf<String>()
            leg.comment?.takeIf { it.isNotBlank() }?.let { parts.add(it) }
            leg.jumpseat1?.takeIf { it.isNotBlank() }?.let { parts.add("J/S: $it") }
            parts.joinToString("\n")
        }))
    )

    // ------------------------------------------------------------
    // Font resolution
    // ------------------------------------------------------------

    /**
     * Maps the stored fontFamily key to a Typeface.
     * Unknown values fall back to Sans-serif.
     */
    private fun resolveTypeface(family: String): Typeface = when (family) {
        "SERIF"     -> Typeface.SERIF
        "MONOSPACE" -> Typeface.MONOSPACE
        else        -> Typeface.SANS_SERIF
    }

    // ============================================================

    fun generate(
        legs: List<Leg>,
        grandTotal: LegTotals,
        rowsPerPage: Int,
        ownerName: String,
        signature: Bitmap?,
        config: PdfLayoutConfig,
        lastPages: Int? = null,
        title: String = "Pilot Logbook"
    ): ByteArray {

        val document = PdfDocument()
        val groups = buildGroups()
        val flatCols = groups.flatMap { it.subCols }

        val contentWidth = PDF_PAGE_WIDTH - config.marginLeft - config.marginRight
        val declared = config.columnWidths()
        val declaredSum = declared.sum()
        val scale = if (declaredSum > 0f) contentWidth / declaredSum else 1f
        val widths = FloatArray(declared.size) { declared[it] * scale }

        val titleSize   = config.titleTextSize   * scale
        val headerSize  = config.headerTextSize  * scale
        val bodySize    = config.bodyTextSize    * scale
        val remarksSize = config.remarksTextSize * scale
        val certSize    = config.certTextSize    * scale
        val ownerSize   = config.ownerTextSize   * scale

        val tf = resolveTypeface(config.fontFamily)

        val headerBlockHeight = config.headerRowHeight * 2f

        val availableForRows =
            PDF_PAGE_HEIGHT - config.marginTop - config.titleHeight - headerBlockHeight -
                    config.marginBottom - config.pageNumArea

        val maxRows = ((availableForRows / MIN_ROW_HEIGHT) - 3).toInt().coerceAtLeast(1)
        val effectiveRows = rowsPerPage.coerceIn(1, maxRows)
        val rowHeight = availableForRows / (effectiveRows + 3).toFloat()

        val sortedLegs = legs.sortedBy { it.offBlock ?: 0L }
        val contentTop = config.marginTop + config.titleHeight + headerBlockHeight

        data class PageData(val legs: List<Leg>, val total: LegTotals)
        val pages = mutableListOf<PageData>()
        var i = 0
        while (i < sortedLegs.size) {
            val end = minOf(i + effectiveRows, sortedLegs.size)
            val slice = sortedLegs.subList(i, end)
            var sum = LegTotals()
            for (l in slice) sum += LegTotals.fromLeg(l)
            pages.add(PageData(slice, sum))
            i = end
        }

        if (pages.isEmpty()) {
            val info = PdfDocument.PageInfo.Builder(
                PDF_PAGE_WIDTH.toInt(), PDF_PAGE_HEIGHT.toInt(), 1
            ).create()
            val page = document.startPage(info)
            val p = Paint().apply {
                textSize = 12f; color = Color.BLACK; typeface = tf; isAntiAlias = true
            }
            page.canvas.drawText("No legs to export", config.marginLeft, config.marginTop + 20f, p)
            document.finishPage(page)
        } else {
            // Grand total of every leg in the DB up to endDate
            // minus the sum of the legs to be paginated.
            // This is the starting point of the running accumulator.
            val exportSum = pages.fold(LegTotals()) { acc, p -> acc + p.total }
            val beforeExport = grandTotal - exportSum

            // Determine which pages to actually render.
            //   lastPages == null  → render all pages
            //   lastPages >= pages.size → render all pages
            //   otherwise → render the last `lastPages` pages, keeping the
            //   absolute page index and the absolute page count so that the
            //   footer and the running totals match a full export.
            val firstRenderedIndex: Int =
                if (lastPages != null && lastPages in 1 until pages.size) {
                    pages.size - lastPages
                } else {
                    0
                }
            val renderedPages: List<PageData> = pages.subList(firstRenderedIndex, pages.size)
            val totalPageCount: Int = pages.size

            val tableLeft  = config.marginLeft
            val tableRight = config.marginLeft + widths.sum()
            val tableTop   = config.marginTop + config.titleHeight
            val totalsTopY = contentTop + effectiveRows * rowHeight

            // Advance the running accumulator through the unrendered pages so
            // that "Total from previous page" on the first rendered page is
            // exactly the value it would show in a full export.
            var running = beforeExport
            for (k in 0 until firstRenderedIndex) {
                running += pages[k].total
            }

            renderedPages.forEachIndexed { i, pageData ->
                val absoluteIdx = firstRenderedIndex + i
                val previous = running
                running += pageData.total
                val runningTotals = running

                val info = PdfDocument.PageInfo.Builder(
                    PDF_PAGE_WIDTH.toInt(), PDF_PAGE_HEIGHT.toInt(), absoluteIdx + 1
                ).create()
                val page = document.startPage(info)
                val c = page.canvas

                drawHeader(c, title, ownerName, pageData.legs, config, titleSize, tf)
                drawColumnHeaders(c, groups, widths, config, headerSize, tableLeft, tf)
                drawRows(c, pageData.legs, flatCols, widths, contentTop, rowHeight,
                    bodySize, remarksSize, tableLeft, tf, config.alternateRowBackground)

                drawHeaderGroupBorders(c, groups, widths, tableTop,
                    tableTop + config.headerRowHeight, tableLeft)
                drawBodyBorders(c, groups, widths, tableTop + config.headerRowHeight,
                    totalsTopY, tableLeft)

                c.drawLine(tableLeft, tableTop, tableRight, tableTop, exteriorPaint)

                drawTotalsRows(
                    canvas       = c,
                    groups       = groups,
                    widths       = widths,
                    leftEdge     = tableLeft,
                    totalsTopY   = totalsTopY,
                    blockHeight  = 3 * rowHeight,
                    previous     = previous,
                    pageSubtotal = pageData.total,
                    running      = runningTotals,
                    signature    = signature,
                    ownerName    = ownerName,
                    bodySize     = bodySize,
                    certSize     = certSize,
                    ownerSize    = ownerSize,
                    tf           = tf
                )

                // Absolute page number and absolute page count
                drawPageNumber(c, absoluteIdx + 1, totalPageCount, tf)
                document.finishPage(page)
            }
        }

        val out = java.io.ByteArrayOutputStream()
        document.writeTo(out)
        document.close()
        return out.toByteArray()
    }

    // ============================================================

    private fun drawHeader(
        canvas: Canvas, title: String, ownerName: String,
        pageLegs: List<Leg>, config: PdfLayoutConfig, titleSize: Float,
        tf: Typeface
    ) {
        val baseline = config.marginTop + titleSize + 2f

        val left = Paint().apply {
            color = Color.BLACK; textSize = titleSize - 1f
            typeface = tf; isAntiAlias = true
        }
        if (ownerName.isNotBlank())
            canvas.drawText("Owner: $ownerName", config.marginLeft, baseline, left)

        val mid = Paint().apply {
            color = Color.BLACK; textSize = titleSize
            typeface = Typeface.create(tf, Typeface.BOLD)
            textAlign = Paint.Align.CENTER; isAntiAlias = true
        }
        canvas.drawText(title, PDF_PAGE_WIDTH / 2f, baseline, mid)

        val right = Paint().apply {
            color = Color.BLACK; textSize = titleSize - 1f
            typeface = tf
            textAlign = Paint.Align.RIGHT; isAntiAlias = true
        }
        val rng = dateRangeText(pageLegs)
        if (rng.isNotEmpty())
            canvas.drawText(rng, PDF_PAGE_WIDTH - config.marginRight, baseline, right)
    }

    private fun dateRangeText(pageLegs: List<Leg>): String {
        if (pageLegs.isEmpty()) return ""
        val first = pageLegs.first().offBlock ?: return ""
        val last  = pageLegs.last().offBlock  ?: return ""
        val d1 = formatDateOnly(first); val d2 = formatDateOnly(last)
        return if (d1 == d2) d1 else "from $d1 to $d2"
    }

    private fun drawPageNumber(canvas: Canvas, pageNum: Int, totalPages: Int, tf: Typeface) {
        val p = Paint().apply {
            color = Color.DKGRAY; textSize = 7f
            typeface = tf
            textAlign = Paint.Align.CENTER; isAntiAlias = true
        }
        canvas.drawText(
            "Page $pageNum / $totalPages",
            PDF_PAGE_WIDTH / 2f,
            PDF_PAGE_HEIGHT - 5f,
            p
        )
    }

    private fun drawColumnHeaders(
        canvas: Canvas, groups: List<ColGroup>, widths: FloatArray,
        config: PdfLayoutConfig, headerSize: Float, leftEdge: Float, tf: Typeface
    ) {
        val text = Paint().apply {
            color = Color.BLACK; textSize = headerSize
            typeface = Typeface.create(tf, Typeface.BOLD)
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        val baseY = config.marginTop + config.titleHeight
        val y1 = baseY + headerSize + 2f
        val y2 = baseY + config.headerRowHeight + headerSize + 2f

        var x = leftEdge
        var wIdx = 0
        for (g in groups) {
            var groupWidth = 0f
            for (k in g.subCols.indices) groupWidth += widths[wIdx + k]
            if (g.name.isNotEmpty()) canvas.drawText(g.name, x + groupWidth / 2f, y1, text)
            x += groupWidth
            wIdx += g.subCols.size
        }

        x = leftEdge
        val flat = groups.flatMap { it.subCols }
        for (i in widths.indices) {
            val label = flat[i].label
            if (label.isNotEmpty()) canvas.drawText(label, x + widths[i] / 2f, y2, text)
            x += widths[i]
        }

        canvas.drawLine(
            leftEdge, baseY + config.headerRowHeight,
            leftEdge + widths.sum(), baseY + config.headerRowHeight, rowPaint
        )
        canvas.drawLine(
            leftEdge, y2 + 3f,
            leftEdge + widths.sum(), y2 + 3f, sectionPaint
        )
    }

    private fun drawRows(
        canvas: Canvas, legs: List<Leg>, cols: List<SubCol>, widths: FloatArray,
        startY: Float, rowHeight: Float, bodySize: Float, remarksSize: Float,
        leftEdge: Float, tf: Typeface, alternate: Boolean
    ) {
        val basePaint = Paint().apply {
            color = Color.BLACK; typeface = tf; isAntiAlias = true
        }
        val remarksPaint = Paint().apply {
            color = Color.BLACK; typeface = tf; isAntiAlias = true
        }

        val rowRight = leftEdge + widths.sum()

        var rowTop = startY
        legs.forEachIndexed { index, leg ->
            // Alternating background: even rows get the light grey fill.
            if (alternate && index % 2 == 0) {
                canvas.drawRect(leftEdge, rowTop, rowRight, rowTop + rowHeight, altRowPaint)
            }

            var cx = leftEdge
            for (i in cols.indices) {
                val raw = cols[i].value(leg)
                if (raw.isNotEmpty()) {
                    if (i == REMARKS_COL) {
                        val paint = remarksPaint.apply { textSize = remarksSize }
                        val lines = raw.split("\n")
                        val lineH = remarksSize * 1.15f
                        val blockH = lines.size * lineH
                        var baseline = rowTop + (rowHeight - blockH) / 2f + remarksSize
                        for (line in lines) {
                            val drawn = clip(line, widths[i] - 3f, paint)
                            val tw = paint.measureText(drawn)
                            val tx = when (cols[i].align) {
                                Align.CENTER -> cx + (widths[i] - tw) / 2f
                                Align.LEFT   -> cx + 2f
                            }
                            canvas.drawText(drawn, tx, baseline, paint)
                            baseline += lineH
                        }
                    } else {
                        val paint = basePaint.apply { textSize = bodySize }
                        val drawn = clip(raw, widths[i] - 3f, paint)
                        val tw = paint.measureText(drawn)
                        val tx = when (cols[i].align) {
                            Align.CENTER -> cx + (widths[i] - tw) / 2f
                            Align.LEFT   -> cx + 2f
                        }
                        val baseline = rowTop + (rowHeight + bodySize) / 2f - 1f
                        canvas.drawText(drawn, tx, baseline, paint)
                    }
                }
                cx += widths[i]
            }

            canvas.drawLine(leftEdge, rowTop + rowHeight, rowRight, rowTop + rowHeight, rowPaint)
            rowTop += rowHeight
        }
    }

    private fun drawHeaderGroupBorders(
        canvas: Canvas, groups: List<ColGroup>, widths: FloatArray,
        top: Float, bottom: Float, leftEdge: Float
    ) {
        var x = leftEdge
        var wIdx = 0
        // Left exterior edge
        canvas.drawLine(x, top, x, bottom, exteriorPaint)
        for (g in groups) {
            var groupWidth = 0f
            for (k in g.subCols.indices) groupWidth += widths[wIdx + k]
            x += groupWidth
            // Every group boundary (including the last) is exterior-thick
            canvas.drawLine(x, top, x, bottom, exteriorPaint)
            wIdx += g.subCols.size
        }
    }

    private fun drawBodyBorders(
        canvas: Canvas, groups: List<ColGroup>, widths: FloatArray,
        top: Float, bottom: Float, leftEdge: Float
    ) {
        // Compute which column boundaries coincide with a ColGroup boundary.
        val groupBoundaries = mutableSetOf<Int>()
        groupBoundaries.add(0)
        var idx = 0
        for (g in groups) {
            idx += g.subCols.size
            groupBoundaries.add(idx)
        }

        var x = leftEdge
        // Left exterior edge
        canvas.drawLine(x, top, x, bottom, exteriorPaint)
        for (i in widths.indices) {
            x += widths[i]
            val boundaryIndex = i + 1
            val paint = if (boundaryIndex in groupBoundaries) exteriorPaint else columnPaint
            canvas.drawLine(x, top, x, bottom, paint)
        }
    }

    private fun valueForColumn(colIdx: Int, t: LegTotals): String = when (colIdx) {
        7  -> fmtDur(t.singlePilotSeTime)
        8  -> fmtDur(t.singlePilotMeTime)
        9  -> fmtDur(t.multiPilotTime)
        10 -> fmtDur(t.blockTimeTotal)
        11 -> ""
        12 -> fmtCnt(t.numberToDay)
        13 -> fmtCnt(t.numberToNight)
        14 -> fmtCnt(t.numberLdgDay)
        15 -> fmtCnt(t.numberLdgNight)
        16 -> fmtDur(t.blockTimeNight)
        17 -> fmtDur(t.ifrTotal)
        18 -> fmtDur(t.pilotFunctionPicTime)
        19 -> fmtDur(t.pilotFunctionSicTime)
        20 -> fmtDur(t.pilotFunctionDualTime)
        21 -> fmtDur(t.pilotFunctionInstructorTime)
        22 -> fmtDur(t.simulatorTime)
        else -> ""
    }

    private fun drawTotalsRows(
        canvas: Canvas, groups: List<ColGroup>, widths: FloatArray, leftEdge: Float,
        totalsTopY: Float, blockHeight: Float,
        previous: LegTotals, pageSubtotal: LegTotals, running: LegTotals,
        signature: Bitmap?, ownerName: String,
        bodySize: Float, certSize: Float, ownerSize: Float,
        tf: Typeface
    ) {
        val xPos = FloatArray(widths.size + 1)
        xPos[0] = leftEdge
        for (i in widths.indices) xPos[i + 1] = xPos[i] + widths[i]
        val tableRight = xPos[widths.size]

        val labelRight = xPos[FIRST_DATA_COL]
        val remarksLeft  = xPos[REMARKS_COL]
        val remarksRight = xPos[REMARKS_COL + 1]
        val remarksWidth = remarksRight - remarksLeft
        val blockBottom = totalsTopY + blockHeight
        val rowH = blockHeight / 3f

        // Group boundaries (same rule as body)
        val groupBoundaries = mutableSetOf<Int>()
        groupBoundaries.add(0)
        var idx = 0
        for (g in groups) {
            idx += g.subCols.size
            groupBoundaries.add(idx)
        }

        val text = Paint().apply {
            color = Color.BLACK; textSize = bodySize
            typeface = tf; isAntiAlias = true
        }
        val boldText = Paint().apply {
            color = Color.BLACK; textSize = bodySize
            typeface = Typeface.create(tf, Typeface.BOLD); isAntiAlias = true
        }

        val labels = listOf("Total this page", "Total from previous page", "Total time")
        val values = listOf(pageSubtotal, previous, running)
        val labelPadding = 4f

        for (rowIdx in 0..2) {
            val rowTop   = totalsTopY + rowIdx * rowH
            val baseline = rowTop + (rowH + bodySize) / 2f - 1f

            val lbl = labels[rowIdx]
            val lw  = boldText.measureText(lbl)
            canvas.drawText(lbl, labelRight - lw - labelPadding, baseline, boldText)

            val t = values[rowIdx]
            for (colIdx in FIRST_DATA_COL..LAST_DATA_COL) {
                val v = valueForColumn(colIdx, t)
                if (v.isNotEmpty()) {
                    val vw = text.measureText(v)
                    val cx = xPos[colIdx] + (widths[colIdx] - vw) / 2f
                    canvas.drawText(v, cx, baseline, text)
                }
            }
        }

        canvas.drawLine(leftEdge, totalsTopY, tableRight, totalsTopY, sectionPaint)
        canvas.drawLine(leftEdge, blockBottom, tableRight, blockBottom, sectionPaint)

        for (k in 1..2) {
            val y = totalsTopY + k * rowH
            canvas.drawLine(xPos[FIRST_DATA_COL], y, tableRight, y, rowPaint)
        }

        // Vertical borders: left edge, then every boundary from FIRST_DATA_COL to end,
        // using exteriorPaint at group boundaries and columnPaint between subgroups.
        canvas.drawLine(xPos[0], totalsTopY, xPos[0], blockBottom, exteriorPaint)
        for (i in FIRST_DATA_COL..widths.size) {
            val paint = if (i in groupBoundaries) exteriorPaint else columnPaint
            canvas.drawLine(xPos[i], totalsTopY, xPos[i], blockBottom, paint)
        }

        val white = Paint().apply { color = Color.WHITE; strokeWidth = 1.0f }
        for (k in 1..2) {
            val y = totalsTopY + k * rowH
            canvas.drawLine(remarksLeft + 0.5f, y, remarksRight - 0.5f, y, white)
        }

        // ---- Certification text (wrapped, configured size) ----
        val certText = "I certify that the entries in this log are true."
        val cert = Paint().apply {
            color = Color.BLACK; typeface = tf; isAntiAlias = true
            textSize = certSize; textAlign = Paint.Align.LEFT
        }
        val maxTextWidth = remarksWidth - 6f
        val certLines = wrapText(certText, maxTextWidth, cert)
        val lineHeight = certSize * 1.2f

        var certBaseline = totalsTopY + certSize + 2f
        for (line in certLines) {
            val lw = cert.measureText(line)
            val x = remarksLeft + (remarksWidth - lw) / 2f
            canvas.drawText(line, x, certBaseline, cert)
            certBaseline += lineHeight
        }

        val ownerPaint = Paint().apply {
            color = Color.BLACK; textSize = ownerSize
            typeface = tf
            textAlign = Paint.Align.CENTER; isAntiAlias = true
        }
        val signatureBottomLimit = blockBottom - ownerSize - 4f

        if (signature != null && signature.width > 0 && signature.height > 0) {
            val availTop = certBaseline + 2f
            val availHeight = signatureBottomLimit - availTop
            val availWidth = remarksWidth - 8f
            if (availHeight > 6f && availWidth > 6f) {
                val sf = minOf(availWidth / signature.width, availHeight / signature.height)
                val w = signature.width * sf
                val h = signature.height * sf
                val left = remarksLeft + (remarksWidth - w) / 2f
                canvas.drawBitmap(signature, null, RectF(left, availTop, left + w, availTop + h), null)
            }
        }

        if (ownerName.isNotBlank()) {
            canvas.drawText(
                ownerName,
                remarksLeft + remarksWidth / 2f,
                blockBottom - 3f,
                ownerPaint
            )
        }
    }

    private fun fmtDur(minutes: Int?): String =
        if (minutes == null || minutes == 0) "" else formatDuration(minutes)

    private fun fmtCnt(count: Int?): String =
        if (count == null || count == 0) "" else count.toString()

    private fun clip(text: String, maxWidth: Float, paint: Paint): String {
        if (paint.measureText(text) <= maxWidth) return text
        var cut = text
        while (cut.isNotEmpty() && paint.measureText("$cut…") > maxWidth) cut = cut.dropLast(1)
        return "$cut…"
    }

    /**
     * Breaks [text] into lines that fit inside [maxWidth] using [paint].
     * Long words that do not fit on their own are kept whole (they will overflow),
     * but the sentence used for the certification contains no such word.
     */
    private fun wrapText(text: String, maxWidth: Float, paint: Paint): List<String> {
        val words = text.split(" ")
        val lines = mutableListOf<String>()
        val current = StringBuilder()

        for (word in words) {
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (paint.measureText(candidate) <= maxWidth) {
                current.clear()
                current.append(candidate)
            } else {
                if (current.isNotEmpty()) {
                    lines.add(current.toString())
                    current.clear()
                }
                current.append(word)
            }
        }
        if (current.isNotEmpty()) lines.add(current.toString())
        return if (lines.isEmpty()) listOf(text) else lines
    }

}