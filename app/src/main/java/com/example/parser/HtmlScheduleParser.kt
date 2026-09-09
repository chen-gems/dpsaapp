package com.example.parser

import com.example.data.BillingShiftEntity
import com.example.data.DpsaMonthSummaryEntity
import com.example.data.PlannedShiftEntity
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.Locale
import java.util.regex.Pattern

object HtmlScheduleParser {

    data class DpsaParseResult(
        val plannedShifts: List<PlannedShiftEntity> = emptyList(),
        val billingShifts: List<BillingShiftEntity> = emptyList(),
        val monthSummary: DpsaMonthSummaryEntity? = null,
        val detectedPeriod: String = "",
        val isAbrechnung: Boolean = false
    )

    // --- Main Entry Points ---

    /**
     * Parses an HTML document or snippet into a list of PlannedShiftEntity.
     * Automatically handles DPSA Dienst-Streifen (table#stunden) as well as standard tables.
     */
    fun parsePlannedSchedule(htmlContent: String, sourceName: String = "Import.html"): List<PlannedShiftEntity> {
        val doc = Jsoup.parse(htmlContent)

        if (isDpsaDocument(doc)) {
            val dpsa = parseDpsaDocument(doc, sourceName)
            if (dpsa.plannedShifts.isNotEmpty()) {
                return dpsa.plannedShifts
            }
        }

        // Standard / Fallback table parser
        val shifts = mutableListOf<PlannedShiftEntity>()
        val tables = doc.select("table")
        for (table in tables) {
            val rows = table.select("tr")
            if (rows.size < 2) continue

            val headerMapping = detectHeaderColumns(rows.first()!!)
            val startRowIdx = if (headerMapping.hasKeyColumns()) 1 else 0

            for (i in startRowIdx until rows.size) {
                val row = rows[i]
                val cells = row.select("td, th").map { it.text().trim() }
                if (cells.size < 2) continue

                val shift = extractPlannedShiftFromCells(cells, headerMapping, sourceName)
                if (shift != null && isValidDate(shift.date)) {
                    shifts.add(shift)
                }
            }
        }

        if (shifts.isEmpty()) {
            val lines = doc.body().text().split("\n", ";", "|").map { it.trim() }.filter { it.isNotEmpty() }
            for (line in lines) {
                val shift = parsePlannedShiftFromTextLine(line, sourceName)
                if (shift != null) {
                    shifts.add(shift)
                }
            }
        }

        return shifts.distinctBy { it.date + "_" + it.startTime + "_" + it.shiftCode }
    }

    /**
     * Parses an HTML document or snippet into a list of BillingShiftEntity.
     * Automatically handles DPSA Stunden-Abrechnung (table#stunden) as well as standard tables.
     */
    fun parseBillingStatement(
        htmlContent: String,
        sourceName: String = "Abrechnung.html",
        defaultPeriod: String = ""
    ): List<BillingShiftEntity> {
        val doc = Jsoup.parse(htmlContent)

        if (isDpsaDocument(doc)) {
            val dpsa = parseDpsaDocument(doc, sourceName, defaultPeriod)
            if (dpsa.billingShifts.isNotEmpty()) {
                return dpsa.billingShifts
            }
        }

        val billingItems = mutableListOf<BillingShiftEntity>()
        val detectedPeriod = extractPeriodFromText(doc.text()).ifEmpty { defaultPeriod }

        val tables = doc.select("table")
        for (table in tables) {
            val rows = table.select("tr")
            if (rows.size < 2) continue

            val headerMapping = detectBillingHeaderColumns(rows.first()!!)
            val startRowIdx = if (headerMapping.hasKeyColumns()) 1 else 0

            for (i in startRowIdx until rows.size) {
                val row = rows[i]
                val cells = row.select("td, th").map { it.text().trim() }
                if (cells.size < 2) continue

                val item = extractBillingShiftFromCells(cells, headerMapping, sourceName, detectedPeriod)
                if (item != null && isValidDate(item.date)) {
                    billingItems.add(item)
                }
            }
        }

        if (billingItems.isEmpty()) {
            val lines = doc.body().text().split("\n", ";").map { it.trim() }.filter { it.isNotEmpty() }
            for (line in lines) {
                val item = parseBillingShiftFromTextLine(line, sourceName, detectedPeriod)
                if (item != null) {
                    billingItems.add(item)
                }
            }
        }

        return billingItems.distinctBy { it.date + "_" + it.billedStartTime + "_" + it.shiftCode }
    }

    /**
     * Complete DPSA Parser parsing both shifts and monthly summary balances.
     */
    fun parseDpsaFull(htmlContent: String, sourceName: String = ""): DpsaParseResult {
        val doc = Jsoup.parse(htmlContent)
        return parseDpsaDocument(doc, sourceName)
    }

    fun isDpsaDocument(doc: Document): Boolean {
        if (doc.select("table#stunden").isNotEmpty()) return true
        val title = doc.title().lowercase()
        val text = doc.text().lowercase()
        return title.contains("dpsa") || text.contains("dpsa") || text.contains("dienst-streifen") || text.contains("stunden-abrechnung")
    }

    // --- DPSA Horizontal Matrix Parser ---

    private fun parseDpsaDocument(
        doc: Document,
        sourceName: String,
        fallbackPeriod: String = ""
    ): DpsaParseResult {
        val fullText = doc.text()
        val isAbrechnung = fullText.contains("Stunden-Abrechnung", ignoreCase = true) ||
                doc.title().contains("Abrechnung", ignoreCase = true)

        // 1. Period extraction (e.g. "September - 2026", "August - 2026")
        var period = extractDpsaPeriod(fullText)
        if (period.isEmpty()) period = extractPeriodFromText(fullText).ifEmpty { fallbackPeriod }
        if (period.isEmpty()) period = "2026-08"

        // 2. Metadata (Dienststelle, Name)
        val dienststelle = extractDpsaDienststelle(fullText).ifEmpty { "JA Salzburg" }
        val employeeName = extractDpsaEmployeeName(doc).ifEmpty { "Chen Shang Tun" }

        val plannedList = mutableListOf<PlannedShiftEntity>()
        val billingList = mutableListOf<BillingShiftEntity>()

        // 3. Find table#stunden
        val stundenTable = doc.select("table#stunden").first()
        if (stundenTable != null) {
            val rows = stundenTable.select("tr")

            var dayNumCells: List<String> = emptyList()
            var dayNameCells: List<String> = emptyList()
            var knzCells: List<String> = emptyList()
            var sollCells: List<String> = emptyList()
            var gdCells: List<String> = emptyList()
            var ueCells: List<String> = emptyList()
            var abwCells: List<String> = emptyList()
            var jnCells: List<String> = emptyList()

            for (row in rows) {
                val rowHtml = row.html()
                val cells = row.select("th, td").map { it.text().trim() }
                when {
                    rowHtml.contains("DayNumber") || (cells.isNotEmpty() && cells.drop(1).any { it == "1" }) ->
                        dayNumCells = cells
                    rowHtml.contains("DayName") || (cells.isNotEmpty() && cells.drop(1).any { it in listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So") }) ->
                        dayNameCells = cells
                    rowHtml.contains("KnzText") || cells.any { it.contains("Dienstbezeichnung", ignoreCase = true) } ->
                        knzCells = cells
                    rowHtml.contains("SollStd") || cells.any { it.contains("Sollstunden", ignoreCase = true) } ->
                        sollCells = cells
                    rowHtml.contains("GdStd") || cells.any { it.contains("Gesamtstunden", ignoreCase = true) } ->
                        gdCells = cells
                    rowHtml.contains("UeStd") || cells.any { it.contains("Überstunden", ignoreCase = true) || it.contains("Ueberstunden", ignoreCase = true) } ->
                        ueCells = cells
                    rowHtml.contains("AbwStd") || cells.any { it.contains("Abwesenheit", ignoreCase = true) } ->
                        abwCells = cells
                    rowHtml.contains("JnStd") || cells.any { it.contains("Journalstunden", ignoreCase = true) } ->
                        jnCells = cells
                }
            }

            val numDays = dayNumCells.size
            for (idx in 1 until numDays) {
                val dayStr = dayNumCells.getOrNull(idx) ?: continue
                val day = dayStr.toIntOrNull() ?: continue
                if (day !in 1..31) continue

                val shiftCodeRaw = knzCells.getOrNull(idx) ?: ""
                val sollStr = sollCells.getOrNull(idx) ?: ""
                val gdStr = gdCells.getOrNull(idx) ?: ""
                val ueStr = ueCells.getOrNull(idx) ?: ""
                val abwStr = abwCells.getOrNull(idx) ?: ""
                val jnStr = jnCells.getOrNull(idx) ?: ""

                val date = String.format(Locale.US, "%s-%02d", period, day)
                val dayOfWeek = try {
                    LocalDate.parse(date).dayOfWeek
                } catch (_: Exception) {
                    DayOfWeek.MONDAY
                }

                val sollHours = parseTimeSpanToDecimal(sollStr)
                val gdHours = parseTimeSpanToDecimal(gdStr)
                val ueHours = parseTimeSpanToDecimal(ueStr)
                val abwHours = parseTimeSpanToDecimal(abwStr)
                val jnHours = parseTimeSpanToDecimal(jnStr)

                // Clean shiftCode
                val code = shiftCodeRaw.replace("&nbsp;", "").trim().ifEmpty {
                    if (sollHours > 0 || gdHours > 0) "D" else "--"
                }

                // Compute standard start & end times based on JA Salzburg rules
                val (startTime, endTime) = computeStandardTimes(code, dayOfWeek, if (isAbrechnung && gdHours > 0) gdHours else sollHours)

                if (isAbrechnung) {
                    // Billing shift
                    // We record all entries where either hours > 0 or a planned code was given
                    val billedHours = if (gdHours > 0) gdHours else sollHours
                    val effShiftCode = if (code == "--" || code == "--W") {
                        if (billedHours > 0) "D" else code
                    } else code

                    val billedSurchargesList = mutableListOf<String>()
                    if (jnHours > 0) billedSurchargesList.add("Journaldienst (${formatDecimalAsHours(jnHours)}h)")
                    if (dayOfWeek == DayOfWeek.SUNDAY || dayOfWeek == DayOfWeek.SATURDAY) billedSurchargesList.add("Wochenende")
                    if (ueHours > 0) billedSurchargesList.add("Überstunden (${formatDecimalAsHours(ueHours)}h)")

                    billingList.add(
                        BillingShiftEntity(
                            date = date,
                            shiftCode = effShiftCode,
                            billedStartTime = startTime,
                            billedEndTime = endTime,
                            plannedHours = sollHours,
                            billedHours = billedHours,
                            billedOvertimeHours = ueHours,
                            journalHours = jnHours,
                            absenceHours = abwHours,
                            totalHours = gdHours,
                            billedSurcharges = billedSurchargesList.joinToString(", "),
                            billedAmountEur = null,
                            notes = if (abwHours > 0) "Abwesenheit: ${formatDecimalAsHours(abwHours)}h" else "",
                            billingPeriod = period,
                            sourceFileName = sourceName
                        )
                    )
                } else {
                    // Planned shift (Dienst-Streifen)
                    val effShiftCode = code.ifEmpty { "D" }
                    val plannedHours = if (sollHours > 0) sollHours else gdHours

                    plannedList.add(
                        PlannedShiftEntity(
                            date = date,
                            startTime = startTime,
                            endTime = endTime,
                            plannedHours = plannedHours,
                            breakMinutes = 0, // JA Salzburg: keine Pause
                            shiftCode = effShiftCode,
                            dutyOrLocation = dienststelle,
                            notes = if (abwHours > 0) "Abwesenheit: ${formatDecimalAsHours(abwHours)}h" else "",
                            journalHours = jnHours,
                            absenceHours = abwHours,
                            totalHours = gdHours,
                            sourceFileName = sourceName
                        )
                    )
                }
            }
        }

        // 4. Parse DPSA Monthly Summary Blocks
        val summary = parseDpsaMonthSummary(doc, period, dienststelle, employeeName, sourceName)

        return DpsaParseResult(
            plannedShifts = plannedList,
            billingShifts = billingList,
            monthSummary = summary,
            detectedPeriod = period,
            isAbrechnung = isAbrechnung
        )
    }

    /**
     * JA Salzburg specific duty time calculations based on official duty rules:
     * - D (Tagdienst):
     *     Mo-Do: 07:00 bis 15:00 (8:00 Std)
     *     Fr:    07:00 bis 13:00 (6:00 Std)
     *     Sa/So/Feiertag: 07:00 bis 12:00 (5:00 Std)
     *     Keine Pause (0 Min)
     * - ND (Nachtdienst):
     *     24-Stunden-Dienst: 07:00 bis 22:00 (15:00 Std Tagdienst; oder 09:00 bis 22:00 bei 13h)
     *     22:00 bis 06:00 Journalstunden (2 Std am ND-Tag, 6 Std am NF-Tag)
     * - NF (Nachtfolgetag):
     *     06:00 bis 07:00 (1:00 Std)
     * - DB:
     *     07:00 bis 18:00 (11:00 Std)
     * - VD:
     *     07:00 bis 17:00 (10:00 Std; bzw. 07:00 bis 15:00 bei 8h)
     * - TD / TD5:
     *     07:00 bis 12:00 (5:00 Std)
     * - U / SU74:
     *     06:40 Std (Urlaub / Sonderurlaub bei 6-Tage-Woche)
     * - DR (Dienstreise / Dienstprüfung):
     *     07:00 bis 15:00 (8:00 Std)
     */
    fun computeStandardTimes(code: String, dayOfWeek: DayOfWeek, hours: Double): Pair<String, String> {
        val cleanCode = code.uppercase().trim()

        return when {
            cleanCode == "D" -> {
                when {
                    dayOfWeek == DayOfWeek.FRIDAY || hours == 6.0 -> Pair("07:00", "13:00")
                    dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY || hours == 5.0 -> Pair("07:00", "12:00")
                    hours > 8.0 -> {
                        // Extended shift with overtime, e.g. 9h15 -> 07:00 to 16:15
                        val endMin = (7 * 60 + (hours * 60).toInt())
                        val endH = (endMin / 60) % 24
                        val endM = endMin % 60
                        Pair("07:00", String.format(Locale.US, "%02d:%02d", endH, endM))
                    }
                    else -> Pair("07:00", "15:00")
                }
            }
            cleanCode == "ND" -> {
                if (hours <= 13.0 && hours > 0.0) {
                    Pair("09:00", "22:00")
                } else {
                    Pair("07:00", "22:00")
                }
            }
            cleanCode == "NF" -> {
                Pair("06:00", "07:00")
            }
            cleanCode == "DB" -> {
                Pair("07:00", "18:00")
            }
            cleanCode == "VD" -> {
                if (dayOfWeek == DayOfWeek.FRIDAY || hours == 9.0) {
                    Pair("07:00", "16:00")
                } else {
                    Pair("07:00", "17:00")
                }
            }
            cleanCode == "TD" || cleanCode == "TD5" -> {
                Pair("07:00", "12:00")
            }
            cleanCode == "DR" -> {
                Pair("07:00", "15:00")
            }
            cleanCode == "U" || cleanCode == "SU74" -> {
                // Abwesenheit Urlaub 06:40
                Pair("07:00", "13:40")
            }
            cleanCode == "ZGV" -> {
                Pair("07:00", "15:00")
            }
            cleanCode == "--" || cleanCode == "--W" || cleanCode == "E" -> {
                if (hours > 0) Pair("07:00", "15:00") else Pair("", "")
            }
            hours > 0 -> {
                val endMin = (7 * 60 + (hours * 60).toInt())
                val endH = (endMin / 60) % 24
                val endM = endMin % 60
                Pair("07:00", String.format(Locale.US, "%02d:%02d", endH, endM))
            }
            else -> Pair("", "")
        }
    }

    // --- Parse DPSA Monthly Summary Blocks ---

    fun parseDpsaMonthSummary(
        doc: Document,
        period: String,
        dienststelle: String,
        mitarbeiterName: String,
        sourceName: String
    ): DpsaMonthSummaryEntity {
        var sollStd = 0.0
        var gesStd = 0.0
        var ueberStd = 0.0
        var bezUeberStd = 0.0
        var ueberStdMinusFza = 0.0

        var vormonatPflichtStd = 0.0
        var vormonatUrlaub = 0.0
        var vormonatFza = 0.0
        var vormonatZgh = 0.0
        var vormonatZgsBer = 0.0
        var vormonatZgsLfd = 0.0
        var vormonatZgsLfdBer = 0.0

        var folgemonatPflichtStd = 0.0
        var folgemonatUrlaub = 0.0
        var folgemonatFza = 0.0
        var folgemonatZgh = 0.0
        var folgemonatZgsBer = 0.0
        var folgemonatZgsLfd = 0.0
        var folgemonatZgsLfdBer = 0.0

        var zgsNeu = 0.0
        var zgsOffenBer = 0.0
        var zgsAbgeltung = 0.0
        var bereitschaftsStd = 0.0
        var bereitschaftWerktag = 0.0
        var bereitschaftSoFeiertag = 0.0

        var ueberstundenW1 = 0.0
        var ueberstundenW2 = 0.0
        var ueberstundenWN1 = 0.0
        var ueberstundenWN2 = 0.0
        var ueberstundenSF18 = 0.0
        var ueberstundenSFAb9 = 0.0

        var sfZulage = 0.0
        var journalDienstStd = 0.0
        var gefahrenStd = 0.0
        var nachtdienstBlockStd = 0.0
        var nachtdienstEinzelStd = 0.0

        var fza1zu1 = 0.0
        var fza1zu1mZ = 0.0
        var fza1zu15 = 0.0
        var fzaUeberStdFuerFza = 0.0
        var fzaMitnahme1zu1 = 0.0
        var fzaMitnahmeFM = 0.0

        var abwUrlaub = 0.0
        var abwPflegeUrl = 0.0
        var abwKrank = 0.0
        var abwSonderUrl = 0.0
        var abwKur = 0.0

        // Parse key-value tables inside containers
        val tables = doc.select("div.container, div.containertop").flatMap { it.select("table") }
        for (table in tables) {
            val rows = table.select("tr")
            for (row in rows) {
                val th = row.select("th.rtitle, th").text().trim()
                val td = row.select("td").map { it.text().trim() }
                if (th.isEmpty() || td.isEmpty()) continue

                val firstTdVal = parseGermanDecimal(td[0])

                when {
                    th.contains("SollStd", ignoreCase = true) -> sollStd = firstTdVal
                    th.contains("GesStd", ignoreCase = true) -> gesStd = firstTdVal
                    th.contains("ÜberStd:", ignoreCase = true) || th.contains("UeberStd:", ignoreCase = true) -> ueberStd = firstTdVal
                    th.contains("SF-Zul", ignoreCase = true) -> sfZulage = firstTdVal
                    th.contains("JD-Std", ignoreCase = true) -> journalDienstStd = firstTdVal
                    th.contains("Gef-Std", ignoreCase = true) -> gefahrenStd = firstTdVal
                    th.contains("ND (Block)", ignoreCase = true) -> nachtdienstBlockStd = firstTdVal
                    th.contains("ND (Einzel)", ignoreCase = true) -> nachtdienstEinzelStd = firstTdVal
                    th.contains("ZGS (neu)", ignoreCase = true) -> zgsNeu = firstTdVal
                    th.contains("offene ZGS", ignoreCase = true) -> zgsOffenBer = firstTdVal
                    th.contains("Abgeltung", ignoreCase = true) -> zgsAbgeltung = firstTdVal
                    th.contains("BereitschaftsStd", ignoreCase = true) -> bereitschaftsStd = firstTdVal
                    th.contains("FZA-FM", ignoreCase = true) -> fzaMitnahmeFM = firstTdVal
                    th.contains("ÜStd für Fza", ignoreCase = true) -> fzaUeberStdFuerFza = firstTdVal
                }
            }
        }

        // Specifically check Vormonat / Folgemonat tables by header
        val vormonatDiv = doc.select("div.container:has(th:contains(Übertrag-Vormonat)), div.container:has(th:contains(Uebertrag-Vormonat))").first()
        if (vormonatDiv != null) {
            vormonatDiv.select("tr").forEach { r ->
                val th = r.select("th").text().trim()
                val tdVal = parseGermanDecimal(r.select("td").text().trim())
                when {
                    th.contains("PflichtStd") -> vormonatPflichtStd = tdVal
                    th.contains("Urlaub") -> vormonatUrlaub = tdVal
                    th.contains("FZA") -> vormonatFza = tdVal
                    th.contains("ZGH") -> vormonatZgh = tdVal
                    th.contains("ZGS (ber)") -> vormonatZgsBer = tdVal
                    th.contains("ZGS (lfd):") -> vormonatZgsLfd = tdVal
                    th.contains("ZGS (lfd - ber)") -> vormonatZgsLfdBer = tdVal
                }
            }
        }

        val folgemonatDiv = doc.select("div.container:has(th:contains(Übertrag-Folgemonat)), div.container:has(th:contains(Uebertrag-Folgemonat))").first()
        if (folgemonatDiv != null) {
            folgemonatDiv.select("tr").forEach { r ->
                val th = r.select("th").text().trim()
                val tdVal = parseGermanDecimal(r.select("td").text().trim())
                when {
                    th.contains("PflichtStd") -> folgemonatPflichtStd = tdVal
                    th.contains("Urlaub") -> folgemonatUrlaub = tdVal
                    th.contains("FZA") -> folgemonatFza = tdVal
                    th.contains("ZGH") -> folgemonatZgh = tdVal
                    th.contains("ZGS (ber)") -> folgemonatZgsBer = tdVal
                    th.contains("ZGS (lfd):") -> folgemonatZgsLfd = tdVal
                    th.contains("ZGS (lfd -ber)") -> folgemonatZgsLfdBer = tdVal
                }
            }
        }

        // Abwesenheiten container
        val abwDiv = doc.select("div.container:has(th:contains(Abwesenheiten))").first()
        if (abwDiv != null) {
            abwDiv.select("tr").forEach { r ->
                val th = r.select("th").text().trim()
                val tdVal = parseGermanDecimal(r.select("td").text().trim())
                when {
                    th.contains("Urlaub") -> abwUrlaub = tdVal
                    th.contains("PflegeUrl") -> abwPflegeUrl = tdVal
                    th.contains("Krank") -> abwKrank = tdVal
                    th.contains("Kur") -> abwKur = tdVal
                    th.contains("SonderUrl") -> abwSonderUrl = tdVal
                }
            }
        }

        // Überstunden container (W1, W2, SF 1-8, etc.)
        val ueberstundenDiv = doc.select("div.container:has(th:contains(Überstuden)), div.container:has(th:contains(Mehrleistungsstunden))").first()
        if (ueberstundenDiv != null) {
            val tdList = ueberstundenDiv.select("td").map { it.text().trim() }
            // Look for numbers after W1/W2 labels
            val w1Idx = tdList.indexOfFirst { it.equals("W1", ignoreCase = true) }
            if (w1Idx != -1 && w1Idx + 4 < tdList.size) {
                ueberstundenW1 = parseGermanDecimal(tdList[w1Idx + 4])
                ueberstundenW2 = parseGermanDecimal(tdList.getOrElse(w1Idx + 5) { "0" })
                ueberstundenWN1 = parseGermanDecimal(tdList.getOrElse(w1Idx + 6) { "0" })
                ueberstundenWN2 = parseGermanDecimal(tdList.getOrElse(w1Idx + 7) { "0" })
            }
            val sfIdx = tdList.indexOfFirst { it.contains("SF-", ignoreCase = true) }
            if (sfIdx != -1 && sfIdx + 2 < tdList.size) {
                ueberstundenSF18 = parseGermanDecimal(tdList[sfIdx + 2])
                ueberstundenSFAb9 = parseGermanDecimal(tdList.getOrElse(sfIdx + 3) { "0" })
            }
        }

        // Freizeitausgleich & Mitnahme
        val mitnahmeDiv = doc.select("div.container:has(th:contains(Mitnahme))").first()
        if (mitnahmeDiv != null) {
            mitnahmeDiv.select("tr").forEach { r ->
                val th = r.select("th").text().trim()
                val tdVal = parseGermanDecimal(r.select("td").text().trim())
                when {
                    th.contains("Fza (1:1)") -> fzaMitnahme1zu1 = tdVal
                    th.contains("FZA-FM") -> fzaMitnahmeFM = tdVal
                }
            }
        }

        val fzaDiv = doc.select("div.container:has(th:contains(Freizeitausgleich))").first()
        if (fzaDiv != null) {
            fzaDiv.select("tr").forEach { r ->
                val th = r.select("th").text().trim()
                val tdList = r.select("td").map { parseGermanDecimal(it.text().trim()) }
                when {
                    th.contains("Fza (1:1):") -> if (tdList.isNotEmpty()) fza1zu1 = tdList[0]
                    th.contains("Fza (1:1 m. Z.)") -> if (tdList.isNotEmpty()) fza1zu1mZ = tdList[0]
                    th.contains("Fza (1:1,5)") -> if (tdList.isNotEmpty()) fza1zu15 = tdList[0]
                    th.contains("ÜStd für Fza") -> if (tdList.isNotEmpty()) fzaUeberStdFuerFza = tdList[0]
                }
            }
        }

        // Small Header Allgemein for bez. ÜStd
        val bezUeTh = doc.select("table:has(th:contains(bez.)):has(th:contains(ÜStd))").first()
        if (bezUeTh != null) {
            val vals = bezUeTh.select("td").map { parseGermanDecimal(it.text().trim()) }
            if (vals.size >= 2) {
                ueberStdMinusFza = vals[0]
                bezUeberStd = vals[1]
            }
        }

        return DpsaMonthSummaryEntity(
            period = period,
            dienststelle = dienststelle,
            mitarbeiterName = mitarbeiterName,
            sollStd = sollStd,
            gesStd = gesStd,
            ueberStd = ueberStd,
            bezUeberStd = bezUeberStd,
            ueberStdMinusFza = ueberStdMinusFza,
            vormonatPflichtStd = vormonatPflichtStd,
            vormonatUrlaub = vormonatUrlaub,
            vormonatFza = vormonatFza,
            vormonatZgh = vormonatZgh,
            vormonatZgsBer = vormonatZgsBer,
            vormonatZgsLfd = vormonatZgsLfd,
            vormonatZgsLfdBer = vormonatZgsLfdBer,
            folgemonatPflichtStd = folgemonatPflichtStd,
            folgemonatUrlaub = folgemonatUrlaub,
            folgemonatFza = folgemonatFza,
            folgemonatZgh = folgemonatZgh,
            folgemonatZgsBer = folgemonatZgsBer,
            folgemonatZgsLfd = folgemonatZgsLfd,
            folgemonatZgsLfdBer = folgemonatZgsLfdBer,
            zgsNeu = zgsNeu,
            zgsOffenBer = zgsOffenBer,
            zgsAbgeltung = zgsAbgeltung,
            bereitschaftsStd = bereitschaftsStd,
            bereitschaftWerktag = bereitschaftWerktag,
            bereitschaftSoFeiertag = bereitschaftSoFeiertag,
            ueberstundenW1 = ueberstundenW1,
            ueberstundenW2 = ueberstundenW2,
            ueberstundenWN1 = ueberstundenWN1,
            ueberstundenWN2 = ueberstundenWN2,
            ueberstundenSF18 = ueberstundenSF18,
            ueberstundenSFAb9 = ueberstundenSFAb9,
            sfZulage = sfZulage,
            journalDienstStd = journalDienstStd,
            gefahrenStd = gefahrenStd,
            nachtdienstBlockStd = nachtdienstBlockStd,
            nachtdienstEinzelStd = nachtdienstEinzelStd,
            fza1zu1 = fza1zu1,
            fza1zu1mZ = fza1zu1mZ,
            fza1zu15 = fza1zu15,
            fzaUeberStdFuerFza = fzaUeberStdFuerFza,
            fzaMitnahme1zu1 = fzaMitnahme1zu1,
            fzaMitnahmeFM = fzaMitnahmeFM,
            abwUrlaub = abwUrlaub,
            abwPflegeUrl = abwPflegeUrl,
            abwKrank = abwKrank,
            abwSonderUrl = abwSonderUrl,
            abwKur = abwKur,
            sourceFileName = sourceName
        )
    }

    // --- Header & Period Parsers ---

    fun extractDpsaPeriod(text: String): String {
        // e.g. "Dienst-Streifen für den Monat September - 2026" or "Stunden-Abrechnung für den Monat August - 2026"
        val m = Pattern.compile("Monat\\s+([A-Za-zäöüÄÖÜ]+)\\s*-\\s*(\\d{4})", Pattern.CASE_INSENSITIVE).matcher(text)
        if (m.find()) {
            val monthName = m.group(1)!!.lowercase()
            val year = m.group(2)!!
            val monthNum = germanMonthToNumber(monthName)
            if (monthNum.isNotEmpty()) return "$year-$monthNum"
        }
        return ""
    }

    fun extractDpsaDienststelle(text: String): String {
        val m = Pattern.compile("Dienstelle:\\s*([^\\n\\r<]+)", Pattern.CASE_INSENSITIVE).matcher(text)
        return if (m.find()) m.group(1)!!.trim() else ""
    }

    fun extractDpsaEmployeeName(doc: Document): String {
        val rows = doc.select("table#stunden tr")
        var lastName = ""
        var firstName = ""
        if (rows.isNotEmpty()) {
            val th1 = rows[0].select("th.rtitle").first()
            if (th1 != null) lastName = th1.text().trim()
        }
        if (rows.size > 1) {
            val th2 = rows[1].select("th.rtitle").first()
            if (th2 != null) firstName = th2.text().trim()
        }
        return listOf(lastName, firstName).filter { it.isNotEmpty() }.joinToString(" ")
    }

    private fun germanMonthToNumber(name: String): String {
        return when {
            name.startsWith("jan") -> "01"
            name.startsWith("feb") -> "02"
            name.startsWith("mär") || name.startsWith("maerz") || name.startsWith("mar") -> "03"
            name.startsWith("apr") -> "04"
            name.startsWith("mai") -> "05"
            name.startsWith("jun") -> "06"
            name.startsWith("jul") -> "07"
            name.startsWith("aug") -> "08"
            name.startsWith("sep") -> "09"
            name.startsWith("okt") -> "10"
            name.startsWith("nov") -> "11"
            name.startsWith("dez") -> "12"
            else -> ""
        }
    }

    fun parseTimeSpanToDecimal(text: String): Double {
        val cleaned = text.trim()
        if (cleaned.isEmpty() || cleaned == "--:--" || cleaned == "--" || cleaned == "&nbsp;") return 0.0
        val parts = cleaned.split(":")
        if (parts.size == 2) {
            val h = parts[0].trim().toDoubleOrNull() ?: 0.0
            val m = parts[1].trim().toDoubleOrNull() ?: 0.0
            return h + (m / 60.0)
        }
        return parseGermanDecimal(cleaned)
    }

    fun parseGermanDecimal(text: String): Double {
        val cleaned = text.replace("Std", "")
            .replace("std", "")
            .replace("€", "")
            .replace("EUR", "")
            .replace("&nbsp;", "")
            .replace(" ", "")
            .trim()
        if (cleaned.isEmpty() || cleaned == "--:--" || cleaned == "--") return 0.0
        val normalized = cleaned.replace(".", "").replace(",", ".")
        return normalized.toDoubleOrNull() ?: 0.0
    }

    fun formatDecimalAsHours(hours: Double): String {
        val totalMinutes = Math.round(hours * 60).toInt()
        val h = totalMinutes / 60
        val m = totalMinutes % 60
        return String.format(Locale.GERMANY, "%02d:%02d", h, m)
    }

    // --- Standard Fallback Table Parser ---

    private data class TableHeaderMapping(
        var dateIdx: Int = -1,
        var startTimeIdx: Int = -1,
        var endTimeIdx: Int = -1,
        var timeRangeIdx: Int = -1,
        var hoursIdx: Int = -1,
        var breakIdx: Int = -1,
        var shiftCodeIdx: Int = -1,
        var dutyIdx: Int = -1,
        var overtimeIdx: Int = -1,
        var surchargesIdx: Int = -1,
        var amountIdx: Int = -1,
        var notesIdx: Int = -1
    ) {
        fun hasKeyColumns(): Boolean = dateIdx != -1 && (startTimeIdx != -1 || timeRangeIdx != -1 || hoursIdx != -1)
    }

    private fun detectHeaderColumns(headerRow: Element): TableHeaderMapping {
        val mapping = TableHeaderMapping()
        val cells = headerRow.select("th, td").map { it.text().lowercase(Locale.GERMANY).trim() }

        cells.forEachIndexed { index, text ->
            when {
                text.contains("datum") || text == "tag" || text.contains("date") ->
                    if (mapping.dateIdx == -1) mapping.dateIdx = index
                (text.contains("beginn") || text == "von" || text.contains("start")) && !text.contains("ende") ->
                    if (mapping.startTimeIdx == -1) mapping.startTimeIdx = index
                text.contains("ende") || text == "bis" || text.contains("stop") ->
                    if (mapping.endTimeIdx == -1) mapping.endTimeIdx = index
                (text.contains("zeit") || text.contains("uhrzeit") || text.contains("arbeitszeit")) && !text.contains("std") ->
                    if (mapping.timeRangeIdx == -1) mapping.timeRangeIdx = index
                text.contains("pause") ->
                    if (mapping.breakIdx == -1) mapping.breakIdx = index
                text.contains("stunden") || text == "std" || text.contains("dauer") || text.contains("soll") || text.contains("ist") ->
                    if (mapping.hoursIdx == -1) mapping.hoursIdx = index
                text.contains("dienst") || text.contains("schicht") || text.contains("kürzel") || text.contains("art") ->
                    if (mapping.shiftCodeIdx == -1) mapping.shiftCodeIdx = index
                text.contains("ort") || text.contains("station") || text.contains("bereich") || text.contains("tour") ->
                    if (mapping.dutyIdx == -1) mapping.dutyIdx = index
                text.contains("überstd") || text.contains("mehrarbeit") || text.contains("üs") ->
                    if (mapping.overtimeIdx == -1) mapping.overtimeIdx = index
                text.contains("bemerkung") || text.contains("hinweis") || text.contains("notiz") ->
                    if (mapping.notesIdx == -1) mapping.notesIdx = index
            }
        }
        return mapping
    }

    private fun detectBillingHeaderColumns(headerRow: Element): TableHeaderMapping {
        val mapping = TableHeaderMapping()
        val cells = headerRow.select("th, td").map { it.text().lowercase(Locale.GERMANY).trim() }

        cells.forEachIndexed { index, text ->
            when {
                text.contains("datum") || text == "tag" ->
                    if (mapping.dateIdx == -1) mapping.dateIdx = index
                (text.contains("beginn") || text == "von") && !text.contains("ende") ->
                    if (mapping.startTimeIdx == -1) mapping.startTimeIdx = index
                text.contains("ende") || text == "bis" ->
                    if (mapping.endTimeIdx == -1) mapping.endTimeIdx = index
                text.contains("abrechnung") || text.contains("stunden") || text == "std" || text.contains("arbeitszeit") ->
                    if (mapping.hoursIdx == -1) mapping.hoursIdx = index
                text.contains("überstd") || text.contains("mehrarbeit") || text.contains("mehrstunden") ->
                    if (mapping.overtimeIdx == -1) mapping.overtimeIdx = index
                text.contains("zuschlag") || text.contains("spesen") || text.contains("zulage") ->
                    if (mapping.surchargesIdx == -1) mapping.surchargesIdx = index
                text.contains("betrag") || text.contains("euro") || text.contains("€") || text.contains("auszahlung") ->
                    if (mapping.amountIdx == -1) mapping.amountIdx = index
                text.contains("dienst") || text.contains("bezeichnung") || text.contains("schicht") ->
                    if (mapping.shiftCodeIdx == -1) mapping.shiftCodeIdx = index
                text.contains("bemerkung") ->
                    if (mapping.notesIdx == -1) mapping.notesIdx = index
            }
        }
        return mapping
    }

    private fun extractPlannedShiftFromCells(
        cells: List<String>,
        mapping: TableHeaderMapping,
        sourceName: String
    ): PlannedShiftEntity? {
        var rawDate = ""
        var startTime = ""
        var endTime = ""
        var hours = 0.0
        var breakMin = 0
        var shiftCode = ""
        var duty = ""
        var notes = ""

        if (mapping.dateIdx in cells.indices) rawDate = cells[mapping.dateIdx]
        if (mapping.startTimeIdx in cells.indices) startTime = parseTime(cells[mapping.startTimeIdx])
        if (mapping.endTimeIdx in cells.indices) endTime = parseTime(cells[mapping.endTimeIdx])

        if (mapping.timeRangeIdx in cells.indices && (startTime.isEmpty() || endTime.isEmpty())) {
            val (st, et) = extractTimeRange(cells[mapping.timeRangeIdx])
            if (startTime.isEmpty()) startTime = st
            if (endTime.isEmpty()) endTime = et
        }

        if (mapping.hoursIdx in cells.indices) {
            hours = parseDecimalHours(cells[mapping.hoursIdx])
        }

        if (mapping.breakIdx in cells.indices) {
            breakMin = parseBreakMinutes(cells[mapping.breakIdx])
        }

        if (mapping.shiftCodeIdx in cells.indices) {
            shiftCode = cells[mapping.shiftCodeIdx]
        }

        if (mapping.dutyIdx in cells.indices) {
            duty = cells[mapping.dutyIdx]
        }

        if (mapping.notesIdx in cells.indices) {
            notes = cells[mapping.notesIdx]
        }

        if (rawDate.isEmpty()) {
            for (cell in cells) {
                val d = extractDate(cell)
                if (d.isNotEmpty()) {
                    rawDate = d
                    break
                }
            }
        }

        val normalizedDate = normalizeDate(rawDate)
        if (normalizedDate.isEmpty()) return null

        if (hours <= 0.0 && startTime.isNotEmpty() && endTime.isNotEmpty()) {
            hours = calculateHoursFromTimes(startTime, endTime, breakMin)
        }

        return PlannedShiftEntity(
            date = normalizedDate,
            startTime = startTime,
            endTime = endTime,
            plannedHours = hours,
            breakMinutes = breakMin,
            shiftCode = shiftCode.ifEmpty { "D" },
            dutyOrLocation = duty,
            notes = notes,
            sourceFileName = sourceName
        )
    }

    private fun extractBillingShiftFromCells(
        cells: List<String>,
        mapping: TableHeaderMapping,
        sourceName: String,
        period: String
    ): BillingShiftEntity? {
        var rawDate = ""
        var startTime = ""
        var endTime = ""
        var hours = 0.0
        var overtime = 0.0
        var shiftCode = ""
        var surcharges = ""
        var amount: Double? = null
        var notes = ""

        if (mapping.dateIdx in cells.indices) rawDate = cells[mapping.dateIdx]
        if (mapping.startTimeIdx in cells.indices) startTime = parseTime(cells[mapping.startTimeIdx])
        if (mapping.endTimeIdx in cells.indices) endTime = parseTime(cells[mapping.endTimeIdx])

        if (mapping.hoursIdx in cells.indices) {
            hours = parseDecimalHours(cells[mapping.hoursIdx])
        }

        if (mapping.overtimeIdx in cells.indices) {
            overtime = parseDecimalHours(cells[mapping.overtimeIdx])
        }

        if (mapping.surchargesIdx in cells.indices) {
            surcharges = cells[mapping.surchargesIdx]
        }

        if (mapping.amountIdx in cells.indices) {
            amount = parseAmount(cells[mapping.amountIdx])
        }

        if (mapping.shiftCodeIdx in cells.indices) {
            shiftCode = cells[mapping.shiftCodeIdx]
        }

        if (mapping.notesIdx in cells.indices) {
            notes = cells[mapping.notesIdx]
        }

        if (rawDate.isEmpty()) {
            for (cell in cells) {
                val d = extractDate(cell)
                if (d.isNotEmpty()) {
                    rawDate = d
                    break
                }
            }
        }

        val normalizedDate = normalizeDate(rawDate)
        if (normalizedDate.isEmpty()) return null

        val effPeriod = period.ifEmpty {
            if (normalizedDate.length >= 7) normalizedDate.substring(0, 7) else ""
        }

        return BillingShiftEntity(
            date = normalizedDate,
            shiftCode = shiftCode.ifEmpty { "Schicht" },
            billedStartTime = startTime,
            billedEndTime = endTime,
            billedHours = hours,
            billedOvertimeHours = overtime,
            billedSurcharges = surcharges,
            billedAmountEur = amount,
            notes = notes,
            billingPeriod = effPeriod,
            sourceFileName = sourceName
        )
    }

    // --- Line By Line Parser Heuristics ---

    private fun parsePlannedShiftFromTextLine(line: String, sourceName: String): PlannedShiftEntity? {
        val date = extractDate(line)
        if (date.isEmpty()) return null

        val (st, et) = extractTimeRange(line)
        val hours = parseDecimalHours(line)
        val normDate = normalizeDate(date)

        return PlannedShiftEntity(
            date = normDate,
            startTime = st,
            endTime = et,
            plannedHours = if (hours > 0.0) hours else calculateHoursFromTimes(st, et, 0),
            breakMinutes = 0,
            shiftCode = "Plan",
            notes = line.take(80),
            sourceFileName = sourceName
        )
    }

    private fun parseBillingShiftFromTextLine(
        line: String,
        sourceName: String,
        period: String
    ): BillingShiftEntity? {
        val date = extractDate(line)
        if (date.isEmpty()) return null

        val (st, et) = extractTimeRange(line)
        val hours = parseDecimalHours(line)
        val normDate = normalizeDate(date)

        return BillingShiftEntity(
            date = normDate,
            shiftCode = "Abrechnung",
            billedStartTime = st,
            billedEndTime = et,
            billedHours = hours,
            billedOvertimeHours = 0.0,
            billingPeriod = period,
            sourceFileName = sourceName,
            notes = line.take(80)
        )
    }

    // --- Utilities & Normalizers ---

    private val datePattern1 = Pattern.compile("\\b(\\d{1,2})\\.(\\d{1,2})\\.(\\d{4})\\b")
    private val datePattern2 = Pattern.compile("\\b(\\d{4})-(\\d{2})-(\\d{2})\\b")
    private val datePatternShort = Pattern.compile("\\b(\\d{1,2})\\.(\\d{1,2})\\.?\\b")

    fun extractDate(text: String): String {
        val m2 = datePattern2.matcher(text)
        if (m2.find()) return m2.group()

        val m1 = datePattern1.matcher(text)
        if (m1.find()) return m1.group()

        val mShort = datePatternShort.matcher(text)
        if (mShort.find()) return mShort.group()

        return ""
    }

    fun normalizeDate(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return ""

        val mIso = datePattern2.matcher(trimmed)
        if (mIso.find()) return mIso.group()

        val mDe = datePattern1.matcher(trimmed)
        if (mDe.find()) {
            val d = mDe.group(1)!!.padStart(2, '0')
            val m = mDe.group(2)!!.padStart(2, '0')
            val y = mDe.group(3)!!
            return "$y-$m-$d"
        }

        val mShort = datePatternShort.matcher(trimmed)
        if (mShort.find()) {
            val d = mShort.group(1)!!.padStart(2, '0')
            val m = mShort.group(2)!!.padStart(2, '0')
            return "2026-$m-$d"
        }

        return ""
    }

    fun isValidDate(dateStr: String): Boolean {
        return datePattern2.matcher(dateStr).matches()
    }

    private val timeRangePattern = Pattern.compile("(\\d{1,2}:\\d{2})\\s*(?:-|bis|–)\\s*(\\d{1,2}:\\d{2})")
    private val timePattern = Pattern.compile("(\\d{1,2}:\\d{2})")

    fun extractTimeRange(text: String): Pair<String, String> {
        val m = timeRangePattern.matcher(text)
        if (m.find()) {
            val st = formatHHmm(m.group(1)!!)
            val et = formatHHmm(m.group(2)!!)
            return Pair(st, et)
        }
        val times = mutableListOf<String>()
        val tm = timePattern.matcher(text)
        while (tm.find()) {
            times.add(formatHHmm(tm.group(1)!!))
        }
        return when {
            times.size >= 2 -> Pair(times[0], times[1])
            times.size == 1 -> Pair(times[0], "")
            else -> Pair("", "")
        }
    }

    fun parseTime(text: String): String {
        val tm = timePattern.matcher(text)
        return if (tm.find()) formatHHmm(tm.group(1)!!) else ""
    }

    private fun formatHHmm(raw: String): String {
        val parts = raw.split(":")
        if (parts.size == 2) {
            val h = parts[0].padStart(2, '0')
            val m = parts[1].padStart(2, '0')
            return "$h:$m"
        }
        return raw
    }

    private val hoursPattern = Pattern.compile("(\\d{1,2})[,.](\\d{1,2})")

    fun parseDecimalHours(text: String): Double {
        val cleaned = text.replace("Std", "").replace("std", "").replace("h", "").trim()
        val m = hoursPattern.matcher(cleaned)
        if (m.find()) {
            val whole = m.group(1)!!.toIntOrNull() ?: 0
            val fraction = m.group(2)!!.toIntOrNull() ?: 0
            val fracDiv = if (m.group(2)!!.length == 1) 10.0 else 100.0
            return whole + (fraction / fracDiv)
        }
        val intVal = cleaned.toIntOrNull()
        if (intVal != null && intVal in 1..24) {
            return intVal.toDouble()
        }
        return 0.0
    }

    fun parseBreakMinutes(text: String): Int {
        val digits = text.filter { it.isDigit() }
        val num = digits.toIntOrNull() ?: 0
        return if (num in 0..240) num else 0
    }

    fun parseAmount(text: String): Double? {
        val cleaned = text.replace("€", "").replace("EUR", "").replace(" ", "").trim()
        val m = hoursPattern.matcher(cleaned)
        if (m.find()) {
            val str = cleaned.replace(".", "").replace(",", ".")
            return str.toDoubleOrNull()
        }
        return null
    }

    fun calculateHoursFromTimes(startTime: String, endTime: String, breakMinutes: Int): Double {
        if (startTime.isEmpty() || endTime.isEmpty()) return 0.0
        try {
            val sdf = SimpleDateFormat("HH:mm", Locale.GERMANY)
            val dStart = sdf.parse(startTime) ?: return 0.0
            val dEnd = sdf.parse(endTime) ?: return 0.0
            var diffMs = dEnd.time - dStart.time
            if (diffMs < 0) {
                diffMs += 24 * 60 * 60 * 1000
            }
            val netMinutes = (diffMs / (60 * 1000)) - breakMinutes
            return if (netMinutes > 0) (netMinutes / 60.0) else 0.0
        } catch (_: Exception) {
            return 0.0
        }
    }

    fun extractPeriodFromText(text: String): String {
        val mNum = Pattern.compile("(0[1-9]|1[0-2])[./-](20\\d{2})").matcher(text)
        if (mNum.find()) {
            val month = mNum.group(1)!!
            val year = mNum.group(2)!!
            return "$year-$month"
        }

        val months = listOf(
            "Januar" to "01", "Februar" to "02", "März" to "03", "April" to "04",
            "Mai" to "05", "Juni" to "06", "Juli" to "07", "August" to "08",
            "September" to "09", "Oktober" to "10", "November" to "11", "Dezember" to "12"
        )
        for ((name, num) in months) {
            val mWord = Pattern.compile("$name\\s+(20\\d{2})", Pattern.CASE_INSENSITIVE).matcher(text)
            if (mWord.find()) {
                val year = mWord.group(1)!!
                return "$year-$num"
            }
        }
        return ""
    }
}
