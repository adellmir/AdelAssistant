package com.adel.assistant.data

import android.content.Context

data class ProjectEntry(
    val row: String,
    val day: String,
    val month: String,
    val name: String,
    val amount: Double,
    val settled: Double,
    val remaining: Double,
    val employer: String,
    val phone: String,
    val description: String,
    val year: String,
    val hour: String = "9",
    val minute: String = "0"
) {
    val dateSortKey: String
        get() = "%s%02d%02d".format(
            year.ifBlank { "1405" },
            month.toIntOrNullFa() ?: 0,
            day.toIntOrNullFa() ?: 0
        )
}

object ProjectStore {
    private const val CSV = "projects"

    fun all(context: Context): List<ProjectEntry> {
        return CsvStore.readAll(context, CSV).mapNotNull { r ->
            if (r.size < 10) return@mapNotNull null
            try {
                ProjectEntry(
                    row = r[0],
                    day = r[1],
                    month = r[2],
                    name = r[3],
                    amount = r[4].toEnglishDigits().toDoubleOrNull() ?: 0.0,
                    settled = r[5].toEnglishDigits().toDoubleOrNull() ?: 0.0,
                    remaining = r[6].toEnglishDigits().toDoubleOrNull() ?: 0.0,
                    employer = r[7],
                    phone = r[8],
                    description = r[9],
                    year = r.getOrElse(10) { "1405" },
                    hour = r.getOrElse(11) { "9" },
                    minute = r.getOrElse(12) { "0" }
                )
            } catch (e: Exception) {
                null
            }
        }
    }

    private fun writeAll(context: Context, list: List<ProjectEntry>) {
        val rows = list.map {
            listOf(
                it.row, it.day, it.month, it.name,
                it.amount.toString(), it.settled.toString(), it.remaining.toString(),
                it.employer, it.phone, it.description, it.year, it.hour, it.minute
            )
        }
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
        val list = all(context).map {
            if (it.row == row) it.copy(settled = it.amount, remaining = 0.0) else it
        }
        writeAll(context, list)
    }

    /** لغو پرداخت‌شده — برگشت کامل به مانده */
    fun markUnsettled(context: Context, row: String) {
        val list = all(context).map {
            if (it.row == row) it.copy(settled = 0.0, remaining = it.amount) else it
        }
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
        return all(context)
            .filter { it.month.toIntOrNullFa() == month.toIntOrNullFa() && it.year == year }
            .mapNotNull { it.day.toIntOrNullFa() }
            .toSet()
    }

    fun isFullySettled(p: ProjectEntry): Boolean =
        p.remaining <= 1e-9 || (p.amount > 0 && p.settled >= p.amount - 1e-9)

    /** آخرین ثبت هم‌نام پروژه — برای پر کردن پیش‌فرض کارفرما/تلفن/مبلغ */
    fun lastByProjectName(context: Context, projectName: String): ProjectEntry? {
        val key = projectName.trim()
        if (key.isBlank()) return null
        return all(context)
            .filter { it.name.trim().equals(key, ignoreCase = true) }
            .maxByOrNull { it.dateSortKey }
    }

    /** به‌روزرسانی نام و تلفن کارفرما در همه پروژه‌های مشترک */
    fun updateEmployerInfo(
        context: Context,
        oldEmployer: String,
        newEmployer: String,
        newPhone: String
    ) {
        val old = oldEmployer.trim()
        if (old.isBlank()) return
        val list = all(context).map { p ->
            if (p.employer.trim().equals(old, ignoreCase = true)) {
                p.copy(
                    employer = newEmployer.trim().ifBlank { p.employer },
                    phone = newPhone.trim().ifBlank { p.phone }
                )
            } else p
        }
        writeAll(context, list)
    }

    data class EmployerProfile(
        val employer: String,
        val phone: String,
        val projectCount: Int,
        val totalReceived: Double,
        val totalClaims: Double,
        val sessions: List<ProjectEntry>
    )

    data class ProjectProfile(
        val name: String,
        val employer: String,
        val phone: String,
        val sessionCount: Int,
        val totalReceived: Double,
        val totalClaims: Double,
        val sessions: List<ProjectEntry>
    )

    fun employerProfiles(context: Context, query: String = ""): List<EmployerProfile> {
        val q = query.trim()
        val grouped = all(context)
            .filter { it.employer.isNotBlank() }
            .filter { q.isBlank() || it.employer.contains(q, true) }
            .groupBy { it.employer.trim() }
        return grouped.map { (emp, rows) ->
            val sorted = rows.sortedByDescending { it.dateSortKey }
            EmployerProfile(
                employer = emp,
                phone = sorted.firstOrNull { it.phone.isNotBlank() }?.phone
                    ?: sorted.first().phone,
                projectCount = rows.map { it.name.trim() }.filter { it.isNotBlank() }.distinct().size,
                totalReceived = rows.sumOf { it.settled },
                totalClaims = rows.sumOf { it.remaining.coerceAtLeast(0.0) },
                sessions = sorted
            )
        }.sortedByDescending { it.sessions.firstOrNull()?.dateSortKey.orEmpty() }
    }

    fun projectProfiles(context: Context, query: String = ""): List<ProjectProfile> {
        val q = query.trim()
        val grouped = all(context)
            .filter { it.name.isNotBlank() && it.name != "پروژه" }
            .filter { q.isBlank() || it.name.contains(q, true) }
            .groupBy { it.name.trim() }
        return grouped.map { (nm, rows) ->
            val sorted = rows.sortedByDescending { it.dateSortKey }
            ProjectProfile(
                name = nm,
                employer = sorted.first().employer,
                phone = sorted.firstOrNull { it.phone.isNotBlank() }?.phone
                    ?: sorted.first().phone,
                sessionCount = rows.size,
                totalReceived = rows.sumOf { it.settled },
                totalClaims = rows.sumOf { it.remaining.coerceAtLeast(0.0) },
                sessions = sorted
            )
        }.sortedByDescending { it.sessions.firstOrNull()?.dateSortKey.orEmpty() }
    }
}
