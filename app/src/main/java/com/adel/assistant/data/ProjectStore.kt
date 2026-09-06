package com.adel.assistant.data

import android.content.Context

data class ProjectEntry(
    val row: String, val day: String, val month: String,
    val name: String, val amount: Double, val settled: Double, val remaining: Double,
    val employer: String, val phone: String, val description: String, val year: String
) {
    val dateSortKey: String get() = "%s%02d%02d".format(year.ifBlank { "1405" }, month.toIntOrNullFa() ?: 0, day.toIntOrNullFa() ?: 0)
}

object ProjectStore {
    private const val CSV = "projects"

    fun all(context: Context): List<ProjectEntry> {
        return CsvStore.readAll(context, CSV).mapNotNull { r ->
            if (r.size < 10) return@mapNotNull null
            try {
                ProjectEntry(
                    row = r[0], day = r[1], month = r[2], name = r[3],
                    amount = r[4].toEnglishDigits().toDoubleOrNull() ?: 0.0,
                    settled = r[5].toEnglishDigits().toDoubleOrNull() ?: 0.0,
                    remaining = r[6].toEnglishDigits().toDoubleOrNull() ?: 0.0,
                    employer = r[7], phone = r[8], description = r[9],
                    year = r.getOrElse(10) { "1405" }
                )
            } catch (e: Exception) { null }
        }
    }

    private fun writeAll(context: Context, list: List<ProjectEntry>) {
        val rows = list.map { listOf(it.row, it.day, it.month, it.name, it.amount.toString(),
            it.settled.toString(), it.remaining.toString(), it.employer, it.phone, it.description, it.year) }
        CsvStore.overwriteAll(context, CSV, rows)
    }

    fun nextRowId(context: Context): String {
        val maxRow = all(context).mapNotNull { it.row.toIntOrNullFa() }.maxOrNull() ?: 0
        return (maxRow + 1).toString()
    }

    fun save(context: Context, entry: ProjectEntry) {
        val existing = all(context)
        val without = existing.filterNot { it.row == entry.row }
        writeAll(context, without + entry)
    }

    fun delete(context: Context, row: String) {
        writeAll(context, all(context).filterNot { it.row == row })
    }

    fun markSettled(context: Context, row: String) {
        val list = all(context).map { if (it.row == row) it.copy(settled = it.amount, remaining = 0.0) else it }
        writeAll(context, list)
    }

    fun search(context: Context, name: String, employer: String): List<ProjectEntry> {
        return all(context).filter {
            (name.isBlank() || it.name.contains(name)) &&
            (employer.isBlank() || it.employer.contains(employer))
        }.sortedByDescending { it.dateSortKey }
    }

    fun forDate(context: Context, day: String, month: String, year: String): List<ProjectEntry> {
        val key = "%s%02d%02d".format(year, month.toIntOrNullFa() ?: 0, day.toIntOrNullFa() ?: 0)
        return all(context).filter { it.dateSortKey == key }
    }

    fun daysWithProjectsIn(context: Context, month: String, year: String): Set<Int> {
        return all(context).filter { it.month.toIntOrNullFa() == month.toIntOrNullFa() && it.year == year }
            .mapNotNull { it.day.toIntOrNullFa() }.toSet()
    }
}
