package com.adel.assistant.data

import android.content.Context

/**
 * نقاط بنچ‌مارک نقشه تونل — CSV در data تا در پشتیبان ZIP باشد.
 * id = نام نقطه (یکتا)
 */
data class TunnelBenchmarkPoint(
    val id: String,
    val name: String,
    val x: Double,
    val y: Double,
    val z: Double,
    val code: String = ""
)

object TunnelBenchmarkStore {
    private const val CSV = "tunnel_benchmarks"

    fun all(context: Context): List<TunnelBenchmarkPoint> =
        CsvStore.readAll(context, CSV).mapNotNull { row ->
            if (row.size < 5) return@mapNotNull null
            try {
                TunnelBenchmarkPoint(
                    id = row[0].ifBlank { row[1] },
                    name = row[1].ifBlank { row[0] },
                    x = row[2].toEnglishDigits().toDouble(),
                    y = row[3].toEnglishDigits().toDouble(),
                    z = row[4].toEnglishDigits().toDouble(),
                    code = row.getOrElse(5) { "" }
                )
            } catch (_: Exception) {
                null
            }
        }

    fun saveAll(context: Context, points: List<TunnelBenchmarkPoint>) {
        val rows = points.map {
            listOf(it.id, it.name, it.x.toString(), it.y.toString(), it.z.toString(), it.code)
        }
        CsvStore.overwriteAll(context, CSV, rows)
    }

    /** ادغام: تکراری‌ها (همان id/name با مختصات نزدیک) جایگزین نمی‌شوند اگر از قبل باشد — جدیدها اضافه */
    fun merge(context: Context, incoming: List<TunnelBenchmarkPoint>): List<TunnelBenchmarkPoint> {
        val existing = all(context).toMutableList()
        val byKey = existing.associateBy { keyOf(it) }.toMutableMap()
        incoming.forEach { p ->
            val k = keyOf(p)
            if (!byKey.containsKey(k)) {
                byKey[k] = p
                existing.add(p)
            }
        }
        saveAll(context, byKey.values.toList())
        return byKey.values.toList()
    }

    fun removeIds(context: Context, ids: Set<String>) {
        saveAll(context, all(context).filterNot { it.id in ids || it.name in ids })
    }

    fun upsert(context: Context, p: TunnelBenchmarkPoint) {
        val rest = all(context).filterNot { it.id == p.id || it.name == p.name }
        saveAll(context, rest + p)
    }

    private fun keyOf(p: TunnelBenchmarkPoint): String {
        val n = p.name.trim().ifBlank { p.id }.lowercase()
        // مختصات گرد‌شده برای تشخیص تکراری تقریبی
        val xr = "%.3f".format(java.util.Locale.US, p.x)
        val yr = "%.3f".format(java.util.Locale.US, p.y)
        return "$n|$xr|$yr"
    }
}
