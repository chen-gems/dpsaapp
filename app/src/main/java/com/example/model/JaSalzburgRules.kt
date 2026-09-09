package com.example.model

import java.time.DayOfWeek
import java.time.LocalDate

data class ShiftTiming(
    val startTime: String,
    val endTime: String,
    val breakMinutes: Int,
    val activeHours: Double,
    val journalHours: Double,
    val isAbsence: Boolean = false,
    val surcharges: String = "",
    val description: String = ""
)

object JaSalzburgRules {

    /**
     * Prüft, ob ein Datum ein gesetzlicher österreichischer Feiertag ist.
     */
    fun isAustrianPublicHoliday(date: LocalDate): Boolean {
        val year = date.year
        val month = date.monthValue
        val day = date.dayOfMonth

        // Feste gesetzliche Feiertage in Österreich
        if (month == 1 && (day == 1 || day == 6)) return true // Neujahr, Heilige Drei Könige
        if (month == 5 && day == 1) return true               // Staatsfeiertag
        if (month == 8 && day == 15) return true              // Mariä Himmelfahrt
        if (month == 10 && day == 26) return true             // Nationalfeiertag
        if (month == 11 && day == 1) return true              // Allerheiligen
        if (month == 12 && (day == 8 || day == 25 || day == 26)) return true // Mariä Empfängnis, Christtag, Stefanitag

        // Bewegliche Osterfeiertage (Anonymous Gregorian Algorithmus)
        val a = year % 19
        val b = year / 100
        val c = year % 100
        val d = b / 4
        val e = b % 4
        val f = (b + 8) / 25
        val g = (b - f + 1) / 3
        val h = (19 * a + b - d - g + 15) % 30
        val i = c / 4
        val k = c % 4
        val l = (32 + 2 * e + 2 * i - h - k) % 7
        val m = (a + 11 * h + 22 * l) / 451
        val easterMonth = (h + l - 7 * m + 114) / 31
        val easterDay = ((h + l - 7 * m + 114) % 31) + 1
        val easterSunday = LocalDate.of(year, easterMonth, easterDay)

        val easterMonday = easterSunday.plusDays(1)
        val ascensionDay = easterSunday.plusDays(39) // Christi Himmelfahrt
        val whitMonday = easterSunday.plusDays(50)   // Pfingstmontag
        val corpusChristi = easterSunday.plusDays(60) // Fronleichnam

        return date == easterMonday || date == ascensionDay || date == whitMonday || date == corpusChristi
    }

    /**
     * An Samstagen, Sonntagen und Feiertagen gibt es keinen Verlängerten Dienst (VD).
     */
    fun isVDAllowed(dateStr: String): Boolean {
        val date = try {
            LocalDate.parse(dateStr)
        } catch (_: Exception) {
            return false
        }
        if (date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY) {
            return false
        }
        return !isAustrianPublicHoliday(date)
    }

    /**
     * Regeln gemäß JA Salzburg Dienstplan-Spezifikationen:
     * - D (Tagdienst):
     *     * Mo-Do: 07:00-15:00 (8h, keine Pause).
     *     * Fr: 07:00-13:00 (6h, keine Pause).
     *     * Sa, So & Feiertag: 07:00-12:00 (5h, keine Pause).
     * - VD (Verlängerter Dienst):
     *     * Mo-Do: 07:00-17:00 (10h, keine Pause).
     *     * Fr: 07:00-15:00 (8h, keine Pause).
     *     * Sa, So & Feiertag: Gibt es keinen VD!
     * - ND (Nachtdienst 24h):
     *     * ND-Tag: 07:00-22:00 (15h aktiv), 22:00-24:00 (2h Journaldienst).
     *     * NF-Tag (Folgetag): 00:00-06:00 (6h Journaldienst), 06:00-07:00 (1h aktiv).
     *     * In Summe: 16h aktiv + 8h Journaldienst = 24h.
     * - DB (Dienst 11h): 07:00-18:00 (11h, keine Pause).
     * - TD (Teildienst): 07:00-12:00 (5h, keine Pause).
     * - 6-Tage-Wochenmodell: 40h / 6 = 6,67h (06:40) pro Abwesenheitstag (Urlaub "U", FZA, Krankenstand).
     */
    fun getTimingForShift(code: String, dateStr: String): ShiftTiming {
        val cleanCode = code.trim().uppercase()
        val localDate = try {
            LocalDate.parse(dateStr)
        } catch (_: Exception) {
            LocalDate.now()
        }
        val dow = localDate.dayOfWeek
        val isHoliday = isAustrianPublicHoliday(localDate)
        val isWeekendOrHoliday = dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY || isHoliday

        return when (cleanCode) {
            "D" -> when {
                isWeekendOrHoliday -> ShiftTiming(
                    startTime = "07:00",
                    endTime = "12:00",
                    breakMinutes = 0,
                    activeHours = 5.0,
                    journalHours = 0.0,
                    description = if (isHoliday) "Tagdienst Feiertag (5h, keine Pause)" else "Tagdienst Wochenende (5h, keine Pause)"
                )
                dow == DayOfWeek.FRIDAY -> ShiftTiming(
                    startTime = "07:00",
                    endTime = "13:00",
                    breakMinutes = 0,
                    activeHours = 6.0,
                    journalHours = 0.0,
                    description = "Tagdienst Freitag (6h, keine Pause)"
                )
                else -> ShiftTiming(
                    startTime = "07:00",
                    endTime = "15:00",
                    breakMinutes = 0,
                    activeHours = 8.0,
                    journalHours = 0.0,
                    description = "Tagdienst Mo-Do (8h, keine Pause)"
                )
            }
            "VD" -> when {
                isWeekendOrHoliday -> ShiftTiming(
                    startTime = "",
                    endTime = "",
                    breakMinutes = 0,
                    activeHours = 0.0,
                    journalHours = 0.0,
                    description = "Kein VD (Verlängerter Dienst) an Sa, So und Feiertagen"
                )
                dow == DayOfWeek.FRIDAY -> ShiftTiming(
                    startTime = "07:00",
                    endTime = "15:00",
                    breakMinutes = 0,
                    activeHours = 8.0,
                    journalHours = 0.0,
                    description = "Verlängerter Dienst Freitag (07:00-15:00, 8h)"
                )
                else -> ShiftTiming(
                    startTime = "07:00",
                    endTime = "17:00",
                    breakMinutes = 0,
                    activeHours = 10.0,
                    journalHours = 0.0,
                    description = "Verlängerter Dienst Mo-Do (07:00-17:00, 10h)"
                )
            }
            "ND" -> ShiftTiming(
                startTime = "07:00",
                endTime = "22:00",
                breakMinutes = 0,
                activeHours = 15.0,
                journalHours = 2.0, // 22:00-24:00 Journaldienst am ND-Tag
                surcharges = "Journaldienst (2h), Nachtdienst",
                description = "Nachtdienst 24h: 15h aktiv (07-22), 2h Journal (22-24)"
            )
            "NF" -> ShiftTiming(
                startTime = "06:00",
                endTime = "07:00",
                breakMinutes = 0,
                activeHours = 1.0,
                journalHours = 6.0, // 00:00-06:00 Journaldienst am NF-Tag
                surcharges = "Journaldienst (6h)",
                description = "Nachtfolgezeit: 6h Journal (00-06), 1h aktiv (06-07)"
            )
            "DB" -> ShiftTiming(
                startTime = "07:00",
                endTime = "18:00",
                breakMinutes = 0,
                activeHours = 11.0,
                journalHours = 0.0,
                description = "Dienst 11 Stunden (07:00-18:00)"
            )
            "TD" -> ShiftTiming(
                startTime = "07:00",
                endTime = "12:00",
                breakMinutes = 0,
                activeHours = 5.0,
                journalHours = 0.0,
                description = "Teildienst 5 Stunden (07:00-12:00)"
            )
            "DR" -> ShiftTiming(
                startTime = "07:00",
                endTime = "15:00",
                breakMinutes = 0,
                activeHours = 8.0,
                journalHours = 0.0,
                description = "Reservedienst (8h)"
            )
            "U" -> ShiftTiming(
                startTime = "07:00",
                endTime = "13:40",
                breakMinutes = 0,
                activeHours = 6.67,
                journalHours = 0.0,
                isAbsence = true,
                description = "Urlaub (6-Tage-Woche: 06:40 / 6,67h)"
            )
            "K", "KR" -> ShiftTiming(
                startTime = "07:00",
                endTime = "13:40",
                breakMinutes = 0,
                activeHours = 6.67,
                journalHours = 0.0,
                isAbsence = true,
                description = "Krankenstand (6-Tage-Woche: 06:40 / 6,67h)"
            )
            "FZA" -> ShiftTiming(
                startTime = "07:00",
                endTime = "13:40",
                breakMinutes = 0,
                activeHours = 6.67,
                journalHours = 0.0,
                isAbsence = true,
                description = "Freizeitausgleich (6,67h)"
            )
            else -> ShiftTiming(
                startTime = "07:00",
                endTime = "15:00",
                breakMinutes = 0,
                activeHours = 8.0,
                journalHours = 0.0,
                description = "Dienst ($cleanCode)"
            )
        }
    }
}
