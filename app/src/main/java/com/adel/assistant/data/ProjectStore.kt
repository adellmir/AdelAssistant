package com.adel.assistant.data

import android.content.Context

data class ProjectEntry(
    val row: String, val day: String, val month: String, val year: String,
    val name: String, val amount: Double, val settled: Double, val remaining: Double,
    val employer: String, val phone: String, val description: String
) {
    val dateSortKey: String get() = "%s%02d%02d".format(year, month.toIntOrNull() ?: 0, day.toIntOrNull() ?: 0)
}

object ProjectStore {
    private const val CSV = "projects"

    fun all(context: Context): List<ProjectEntry> {
        return CsvStore.readAll(context, CSV).mapNotNull { r ->
            if (r.size < 10) return@mapNotNull null
            try {
                ProjectEntry(r[0], r[1], r[2], r.getOrElse(10){""}.ifBlank { "1405" },
                    r[3], r[4].toDoubleOrNull() ?: 0.0, r[5].toDoubleOrNull() ?: 0.0,
                    r[6].toDoubleOrNull() ?: 0.0, r[7], r[8], r[9])
            } catch (e: Exception) { null }
        }
    }

    fun save(context: Context, p: ProjectEntry) {
        CsvStore.appendRow(context, CSV, listOf(
            p.row, p.day, p.month, p.name, p.amount.toString(), p.settled.toString(),
            p.remaining.toString(), p.employer, p.phone, p.description, p.year
        ))
    }

    fun search(context: Context, name: String, employer: String): List<ProjectEntry> {
        return all(context).filter {
            (name.isBlank() || it.name.contains(name)) &&
            (employer.isBlank() || it.employer.contains(employer))
        }.sortedByDescending { it.dateSortKey }
    }
}
