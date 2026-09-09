package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "dpsa_month_summaries")
data class DpsaMonthSummaryEntity(
    @PrimaryKey val period: String, // Format: YYYY-MM, z.B. "2026-08"
    val dienststelle: String = "JA Salzburg",
    val mitarbeiterName: String = "Chen Shang Tun",
    val timestamp: String = "",
    // Allgemein
    val sollStd: Double = 0.0,
    val gesStd: Double = 0.0,
    val ueberStd: Double = 0.0,
    val bezUeberStd: Double = 0.0,
    val ueberStdMinusFza: Double = 0.0,
    // Übertrag-Vormonat
    val vormonatPflichtStd: Double = 0.0,
    val vormonatUrlaub: Double = 0.0,
    val vormonatFza: Double = 0.0,
    val vormonatZgh: Double = 0.0,
    val vormonatZgsBer: Double = 0.0,
    val vormonatZgsLfd: Double = 0.0,
    val vormonatZgsLfdBer: Double = 0.0,
    // Übertrag-Folgemonat
    val folgemonatPflichtStd: Double = 0.0,
    val folgemonatUrlaub: Double = 0.0,
    val folgemonatFza: Double = 0.0,
    val folgemonatZgh: Double = 0.0,
    val folgemonatZgsBer: Double = 0.0,
    val folgemonatZgsLfd: Double = 0.0,
    val folgemonatZgsLfdBer: Double = 0.0,
    // Zeitgutschrift § 82b GehG (Nachtdienst)
    val zgsNeu: Double = 0.0,
    val zgsOffenBer: Double = 0.0,
    val zgsAbgeltung: Double = 0.0,
    val bereitschaftsStd: Double = 0.0,
    val bereitschaftWerktag: Double = 0.0,
    val bereitschaftSoFeiertag: Double = 0.0,
    // Überstunden / Mehrleistung Werktage
    val ueberstundenW1: Double = 0.0,
    val ueberstundenW2: Double = 0.0,
    val ueberstundenWN1: Double = 0.0,
    val ueberstundenWN2: Double = 0.0,
    // Überstunden Sonn-/Feiertage
    val ueberstundenSF18: Double = 0.0,
    val ueberstundenSFAb9: Double = 0.0,
    // Zulagen
    val sfZulage: Double = 0.0,
    val journalDienstStd: Double = 0.0, // JD-Std (22:00-06:00)
    val gefahrenStd: Double = 0.0, // Gef-Std (60% aus bez. ÜStd + JD-Std)
    val nachtdienstBlockStd: Double = 0.0,
    val nachtdienstEinzelStd: Double = 0.0,
    // Freizeitausgleich
    val fza1zu1: Double = 0.0,
    val fza1zu1mZ: Double = 0.0,
    val fza1zu15: Double = 0.0,
    val fzaUeberStdFuerFza: Double = 0.0,
    // Mitnahme
    val fzaMitnahme1zu1: Double = 0.0,
    val fzaMitnahmeFM: Double = 0.0,
    // Abwesenheiten
    val abwUrlaub: Double = 0.0,
    val abwPflegeUrl: Double = 0.0,
    val abwKrank: Double = 0.0,
    val abwSonderUrl: Double = 0.0,
    val abwKur: Double = 0.0,
    val sourceFileName: String = ""
)
