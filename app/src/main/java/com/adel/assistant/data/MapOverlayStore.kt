package com.adel.assistant.data

import android.content.Context

/**
 * نقاط روی‌هم‌گذاری نقشهٔ حفاری تونل (دستی / GPS / ویرایش‌پذیر).
 * CSV: id,x,y,z,d,km,source
 * z = اختلاف تراز با کف خیابان
 */
data class MapOverlayPoint(
    val id: String,
    val x: Double,
    val y: Double,
    val z: Double,
    val d: String,
    val km: Double,
    val source: String = "manual" // manual | gps | report
)

object MapOverlayStore {
    private const val CSV = "tunnel_map_points"

    fun all(context: Context): List<MapOverlayPoint> =
        CsvStore.readAll(context, CSV).mapNotNull { row ->
            if (row.size < 6) return@mapNotNull null
            try {
                MapOverlayPoint(
                    id = row[0],
                    x = row[1].toEnglishDigits().toDouble(),
                    y = row[2].toEnglishDigits().toDouble(),
                    z = row[3].toEnglishDigits().toDouble(),
                    d = row.getOrElse(4) { "" },
                    km = row.getOrElse(5) { "0" }.toEnglishDigits().toDoubleOrNull() ?: 0.0,
                    source = row.getOrElse(6) { "manual" }
                )
            } catch (_: Exception) {
                null
            }
        }

    fun saveAll(context: Context, points: List<MapOverlayPoint>) {
        val rows = points.map {
            listOf(it.id, it.x.toString(), it.y.toString(), it.z.toString(), it.d, it.km.toString(), it.source)
        }
        CsvStore.overwriteAll(context, CSV, rows)
    }

    fun upsert(context: Context, p: MapOverlayPoint) {
        val rest = all(context).filter { it.id != p.id }
        saveAll(context, rest + p)
    }

    fun delete(context: Context, id: String) {
        saveAll(context, all(context).filter { it.id != id })
    }

    fun nextId(context: Context): String {
        val n = all(context).size + 1
        return "M%03d".format(n)
    }

    fun toCsvBody(points: List<MapOverlayPoint>): String = buildString {
        appendLine("X,Y,Z,D,KM")
        points.forEach { p ->
            appendLine("${p.x},${p.y},${p.z},${p.d.replace(",", "،")},${p.km}")
        }
    }

    /** DXF با لایه‌های point-id / point-z / point-d ارتفاع ۰.۰۵ */
    fun toDxf(points: List<MapOverlayPoint>): String = buildString {
        val layers = listOf("POINTS", "point-id", "point-z", "point-d")
        fun f(v: Double) = String.format(java.util.Locale.US, "%.3f", v)
        append("0\r\nSECTION\r\n2\r\nHEADER\r\n9\r\n\$ACADVER\r\n1\r\nAC1009\r\n0\r\nENDSEC\r\n")
        append("0\r\nSECTION\r\n2\r\nTABLES\r\n")
        append("0\r\nTABLE\r\n2\r\nLTYPE\r\n70\r\n1\r\n")
        append("0\r\nLTYPE\r\n2\r\nCONTINUOUS\r\n70\r\n0\r\n3\r\nSolid line\r\n72\r\n65\r\n73\r\n0\r\n40\r\n0.0\r\n0\r\nENDTAB\r\n")
        append("0\r\nTABLE\r\n2\r\nLAYER\r\n70\r\n${layers.size}\r\n")
        layers.forEach { layer ->
            append("0\r\nLAYER\r\n2\r\n$layer\r\n70\r\n0\r\n62\r\n7\r\n6\r\nCONTINUOUS\r\n")
        }
        append("0\r\nENDTAB\r\n0\r\nENDSEC\r\n0\r\nSECTION\r\n2\r\nENTITIES\r\n")
        val h = 0.05 // ۵ سانتی‌متر
        // متن در سمت چپ نقطه (X کمتر)
        val gapX = 0.12
        points.forEach { p ->
            val s = 0.10
            // ضربدر
            append("0\r\nLINE\r\n8\r\nPOINTS\r\n")
            append("10\r\n${f(p.x - s)}\r\n20\r\n${f(p.y - s)}\r\n30\r\n0\r\n")
            append("11\r\n${f(p.x + s)}\r\n21\r\n${f(p.y + s)}\r\n31\r\n0\r\n")
            append("0\r\nLINE\r\n8\r\nPOINTS\r\n")
            append("10\r\n${f(p.x - s)}\r\n20\r\n${f(p.y + s)}\r\n30\r\n0\r\n")
            append("11\r\n${f(p.x + s)}\r\n21\r\n${f(p.y - s)}\r\n31\r\n0\r\n")
            // سه ردیف سمت چپ: شماره / ارتفاع / کیلومتراژ
            val tx = p.x - gapX
            val labelTop = p.id // مثل 050627
            val labelMid = f(p.z)
            val labelBot = f(p.km)
            append("0\r\nTEXT\r\n8\r\npoint-id\r\n62\r\n7\r\n")
            append("10\r\n${f(tx)}\r\n20\r\n${f(p.y + h * 1.2)}\r\n30\r\n0\r\n")
            append("40\r\n${f(h)}\r\n1\r\n$labelTop\r\n50\r\n0\r\n")
            append("0\r\nTEXT\r\n8\r\npoint-z\r\n62\r\n7\r\n")
            append("10\r\n${f(tx)}\r\n20\r\n${f(p.y)}\r\n30\r\n0\r\n")
            append("40\r\n${f(h)}\r\n1\r\n$labelMid\r\n50\r\n0\r\n")
            append("0\r\nTEXT\r\n8\r\npoint-d\r\n62\r\n7\r\n")
            append("10\r\n${f(tx)}\r\n20\r\n${f(p.y - h * 1.2)}\r\n30\r\n0\r\n")
            append("40\r\n${f(h)}\r\n1\r\n$labelBot\r\n50\r\n0\r\n")
        }
        append("0\r\nENDSEC\r\n0\r\nEOF\r\n")
    }
}
