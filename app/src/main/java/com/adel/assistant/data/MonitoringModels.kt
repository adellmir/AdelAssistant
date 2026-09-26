package com.adel.assistant.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import kotlin.math.hypot
import kotlin.math.sqrt

data class MonPoint(
    var name: String,
    var x: Double,
    var y: Double,
    var z: Double,
    var isBm: Boolean = false
)

data class MonAnalysisRow(
    val name: String,
    val isBm: Boolean,
    val baseX: Double?,
    val baseY: Double?,
    val baseZ: Double?,
    val epX: Double?,
    val epY: Double?,
    val epZ: Double?,
    val inOutMm: Double?,      // محوری: + داخل گود
    val settleMm: Double?,     // محوری: نشست mm
    val d3dMm: Double?,
    val dxMm: Double? = null,  // مختصاتی: اختلاف X mm
    val dyMm: Double? = null,  // مختصاتی: اختلاف Y mm
    val dhMm: Double? = null,  // مختصاتی: اختلاف H mm
    val matchedBy: String = "name" // name | proximity | missing
)

data class MonEpoch(
    val id: String = UUID.randomUUID().toString(),
    var day: String = "",
    var month: String = "",
    var year: String = "",
    var points: MutableList<MonPoint> = mutableListOf(),
    var createdAt: Long = System.currentTimeMillis()
)

data class MonProject(
    val id: String = UUID.randomUUID().toString(),
    var name: String = "",
    var client: String = "",
    var reportNo: String = "",
    var day: String = "",
    var month: String = "",
    var year: String = "",
    var basePoints: MutableList<MonPoint> = mutableListOf(),
    var epochs: MutableList<MonEpoch> = mutableListOf(),
    var templatePath: String? = null, // absolute path in filesDir
    var updatedAt: Long = System.currentTimeMillis()
)

object MonitoringStore {
    private const val FILE = "monitoring_projects.json"

    private fun file(ctx: Context) = File(ctx.filesDir, FILE)

    fun loadAll(ctx: Context): List<MonProject> {
        val f = file(ctx)
        if (!f.exists()) return emptyList()
        return try {
            val arr = JSONArray(f.readText())
            (0 until arr.length()).map { parseProject(arr.getJSONObject(it)) }
                .sortedByDescending { it.updatedAt }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveAll(ctx: Context, list: List<MonProject>) {
        val arr = JSONArray()
        list.forEach { arr.put(toJson(it)) }
        file(ctx).writeText(arr.toString())
    }

    fun upsert(ctx: Context, p: MonProject) {
        val all = loadAll(ctx).toMutableList()
        val i = all.indexOfFirst { it.id == p.id }
        p.updatedAt = System.currentTimeMillis()
        if (i >= 0) all[i] = p else all.add(0, p)
        saveAll(ctx, all)
    }

    fun delete(ctx: Context, id: String) {
        saveAll(ctx, loadAll(ctx).filter { it.id != id })
    }

    fun exportCsv(ctx: Context, list: List<MonProject> = loadAll(ctx)): String {
        val sb = StringBuilder()
        sb.appendLine(
            "project_id,project_name,client,report_no,day,month,year," +
            "epoch_id,epoch_day,epoch_month,epoch_year," +
            "point_name,is_bm," +
            "base_x,base_y,base_z,epoch_x,epoch_y,epoch_z," +
            "dx_mm,dy_mm,dh_mm," +
            "in_out_mm,settle_mm,d3d_mm"
        )
        list.forEach { p ->
            if (p.epochs.isEmpty()) {
                p.basePoints.forEach { b ->
                    sb.appendLine(listOf(
                        p.id, esc(p.name), esc(p.client), esc(p.reportNo), p.day, p.month, p.year,
                        "", "", "", "",
                        esc(b.name), if (b.isBm) "1" else "0",
                        b.x, b.y, b.z, "", "", "",
                        "", "", "", "", "", ""
                    ).joinToString(","))
                }
            } else {
                p.epochs.forEach { e ->
                    val axial = MonitoringAnalyzer.analyze(p.basePoints, e.points).associateBy { it.name.lowercase() }
                    val coord = MonitoringAnalyzer.analyzeCoordinate(p.basePoints, e.points)
                    coord.forEach { r ->
                        val a = axial[r.name.lowercase()]
                        sb.appendLine(listOf(
                            p.id, esc(p.name), esc(p.client), esc(p.reportNo), p.day, p.month, p.year,
                            e.id, e.day, e.month, e.year,
                            esc(r.name), if (r.isBm) "1" else "0",
                            r.baseX ?: "", r.baseY ?: "", r.baseZ ?: "",
                            r.epX ?: "", r.epY ?: "", r.epZ ?: "",
                            r.dxMm ?: "", r.dyMm ?: "", r.dhMm ?: "",
                            a?.inOutMm ?: "", a?.settleMm ?: "", a?.d3dMm ?: ""
                        ).joinToString(","))
                    }
                }
            }
        }
        return sb.toString()
    }

    private fun esc(s: String): String = "\"" + s.replace("\"", "\"\"") + "\""



    fun saveTemplate(ctx: Context, projectId: String, bytes: ByteArray): String {
        val dir = File(ctx.filesDir, "monitoring_templates")
        if (!dir.exists()) dir.mkdirs()
        val f = File(dir, "$projectId.xlsx")
        f.writeBytes(bytes)
        return f.absolutePath
    }

    fun loadTemplateBytes(ctx: Context, p: MonProject): ByteArray? {
        val path = p.templatePath ?: return null
        val f = File(path)
        if (!f.exists() || f.length() < 1000) return null
        return f.readBytes()
    }

    private fun toJson(p: MonProject) = JSONObject().apply {
        put("id", p.id)
        put("name", p.name)
        put("client", p.client)
        put("reportNo", p.reportNo)
        put("day", p.day); put("month", p.month); put("year", p.year)
        put("templatePath", p.templatePath)
        put("updatedAt", p.updatedAt)
        put("basePoints", pointsArr(p.basePoints))
        val ea = JSONArray()
        p.epochs.forEach { e ->
            ea.put(JSONObject().apply {
                put("id", e.id)
                put("day", e.day); put("month", e.month); put("year", e.year)
                put("createdAt", e.createdAt)
                put("points", pointsArr(e.points))
            })
        }
        put("epochs", ea)
    }

    private fun pointsArr(list: List<MonPoint>) = JSONArray().apply {
        list.forEach { pt ->
            put(JSONObject().apply {
                put("name", pt.name); put("x", pt.x); put("y", pt.y); put("z", pt.z); put("isBm", pt.isBm)
            })
        }
    }

    private fun parseProject(o: JSONObject): MonProject {
        val base = parsePoints(o.optJSONArray("basePoints"))
        val epochs = mutableListOf<MonEpoch>()
        val ea = o.optJSONArray("epochs") ?: JSONArray()
        for (i in 0 until ea.length()) {
            val e = ea.getJSONObject(i)
            epochs.add(
                MonEpoch(
                    id = e.optString("id", UUID.randomUUID().toString()),
                    day = e.optString("day"),
                    month = e.optString("month"),
                    year = e.optString("year"),
                    points = parsePoints(e.optJSONArray("points")),
                    createdAt = e.optLong("createdAt", System.currentTimeMillis())
                )
            )
        }
        return MonProject(
            id = o.optString("id", UUID.randomUUID().toString()),
            name = o.optString("name"),
            client = o.optString("client"),
            reportNo = o.optString("reportNo"),
            day = o.optString("day"),
            month = o.optString("month"),
            year = o.optString("year"),
            basePoints = base,
            epochs = epochs,
            templatePath = o.optString("templatePath").ifBlank { null },
            updatedAt = o.optLong("updatedAt", System.currentTimeMillis())
        )
    }

    private fun parsePoints(arr: JSONArray?): MutableList<MonPoint> {
        val list = mutableListOf<MonPoint>()
        if (arr == null) return list
        for (i in 0 until arr.length()) {
            val p = arr.getJSONObject(i)
            list.add(
                MonPoint(
                    name = p.optString("name"),
                    x = p.optDouble("x"),
                    y = p.optDouble("y"),
                    z = p.optDouble("z"),
                    isBm = p.optBoolean("isBm", false)
                )
            )
        }
        return list
    }
}

object MonitoringAnalyzer {
    private const val MATCH_TOL = 0.05 // 5 cm

    fun analyze(base: List<MonPoint>, epoch: List<MonPoint>): List<MonAnalysisRow> {
        if (base.isEmpty()) return emptyList()

        // centroid of base (pit center approx)
        val cx = base.map { it.x }.average()
        val cy = base.map { it.y }.average()

        // match epoch to base
        data class Pairing(val b: MonPoint, val e: MonPoint?, val by: String)
        val used = mutableSetOf<Int>()
        val pairings = mutableListOf<Pairing>()

        base.forEach { b ->
            val byName = epoch.indexOfFirst { it.name.equals(b.name, true) && it.name.isNotBlank() }
            if (byName >= 0 && byName !in used) {
                used.add(byName)
                pairings.add(Pairing(b, epoch[byName], "name"))
                return@forEach
            }
            var bestI = -1
            var bestD = MATCH_TOL
            epoch.forEachIndexed { i, e ->
                if (i in used) return@forEachIndexed
                val d = hypot(e.x - b.x, e.y - b.y)
                if (d <= bestD) {
                    bestD = d
                    bestI = i
                }
            }
            if (bestI >= 0) {
                used.add(bestI)
                pairings.add(Pairing(b, epoch[bestI], "proximity"))
            } else {
                pairings.add(Pairing(b, null, "missing"))
            }
        }

        // common translation from BM pairs only
        val bmPairs = pairings.filter { it.b.isBm && it.e != null }
        val src = if (bmPairs.size >= 1) bmPairs else pairings.filter { it.e != null }
        val tx: Double
        val ty: Double
        val tz: Double
        if (src.isNotEmpty()) {
            tx = src.map { it.e!!.x - it.b.x }.average()
            ty = src.map { it.e!!.y - it.b.y }.average()
            tz = src.map { it.e!!.z - it.b.z }.average()
        } else {
            tx = 0.0; ty = 0.0; tz = 0.0
        }

        fun row(p: Pairing): MonAnalysisRow {
            val b = p.b
            val e = p.e
            if (e == null) {
                return MonAnalysisRow(
                    name = b.name, isBm = b.isBm,
                    baseX = b.x, baseY = b.y, baseZ = b.z,
                    epX = null, epY = null, epZ = null,
                    inOutMm = null, settleMm = null, d3dMm = null,
                    matchedBy = "missing"
                )
            }
            val dX = e.x - b.x
            val dY = e.y - b.y
            val dZ = e.z - b.z
            // corrected
            val cX = dX - tx
            val cY = dY - ty
            val cZ = dZ - tz
            // into pit: toward centroid from base point
            val vx = cx - b.x
            val vy = cy - b.y
            val len = hypot(vx, vy).coerceAtLeast(1e-9)
            val ux = vx / len
            val uy = vy / len
            val inOutM = cX * ux + cY * uy
            val d3d = sqrt(cX * cX + cY * cY + cZ * cZ)
            return MonAnalysisRow(
                name = b.name,
                isBm = b.isBm,
                baseX = b.x, baseY = b.y, baseZ = b.z,
                epX = e.x, epY = e.y, epZ = e.z,
                inOutMm = inOutM * 1000.0,
                settleMm = cZ * 1000.0,
                d3dMm = d3d * 1000.0,
                matchedBy = p.by
            )
        }

        val rows = pairings.map { row(it) }
        // sort BM first then TP by name
        return rows.sortedWith(compareBy({ !it.isBm }, { it.name }))
    }


    /** پایش مختصاتی: فقط اختلاف خام مختصات بدون Translation */
    fun analyzeCoordinate(base: List<MonPoint>, epoch: List<MonPoint>): List<MonAnalysisRow> {
        if (base.isEmpty()) return emptyList()
        val used = mutableSetOf<Int>()
        val rows = mutableListOf<MonAnalysisRow>()
        base.forEach { b ->
            val byName = epoch.indexOfFirst { it.name.equals(b.name, true) && it.name.isNotBlank() }
            val e: MonPoint?
            val by: String
            if (byName >= 0 && byName !in used) {
                used.add(byName)
                e = epoch[byName]
                by = "name"
            } else {
                var bestI = -1
                var bestD = MATCH_TOL
                epoch.forEachIndexed { i, ep ->
                    if (i in used) return@forEachIndexed
                    val d = hypot(ep.x - b.x, ep.y - b.y)
                    if (d <= bestD) { bestD = d; bestI = i }
                }
                if (bestI >= 0) {
                    used.add(bestI)
                    e = epoch[bestI]
                    by = "proximity"
                } else {
                    e = null
                    by = "missing"
                }
            }
            if (e == null) {
                rows.add(
                    MonAnalysisRow(
                        name = b.name, isBm = b.isBm,
                        baseX = b.x, baseY = b.y, baseZ = b.z,
                        epX = null, epY = null, epZ = null,
                        inOutMm = null, settleMm = null, d3dMm = null,
                        dxMm = null, dyMm = null, dhMm = null,
                        matchedBy = "missing"
                    )
                )
            } else {
                val dX = (e.x - b.x) * 1000.0
                val dY = (e.y - b.y) * 1000.0
                val dH = (e.z - b.z) * 1000.0
                rows.add(
                    MonAnalysisRow(
                        name = b.name, isBm = b.isBm,
                        baseX = b.x, baseY = b.y, baseZ = b.z,
                        epX = e.x, epY = e.y, epZ = e.z,
                        inOutMm = null, settleMm = null, d3dMm = null,
                        dxMm = dX, dyMm = dY, dhMm = dH,
                        matchedBy = by
                    )
                )
            }
        }
        return rows.sortedWith(compareBy({ !it.isBm }, { it.name }))
    }

    fun persianOrdinal(n: Int): String {
        val map = listOf(
            "اول", "دوم", "سوم", "چهارم", "پنجم", "ششم", "هفتم", "هشتم", "نهم", "دهم",
            "یازدهم", "دوازدهم", "سیزدهم", "چهاردهم", "پانزدهم", "شانزدهم", "هفدهم", "هجدهم", "نوزدهم", "بیستم"
        )
        return if (n in 1..map.size) map[n - 1] else "$n"
    }

    /** parse simple N,X,Y,Z or X,Y,Z,N lines */
    fun parsePointsFile(text: String): List<MonPoint> {
        val out = mutableListOf<MonPoint>()
        text.lineSequence().forEach { raw ->
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("*")) return@forEach
            val parts = line.split(',', '\t', ';', ' ').map { it.trim() }.filter { it.isNotEmpty() }
            if (parts.size < 3) return@forEach
            fun d(s: String) = s.replace(',', '.').toDoubleOrNull()
            // try name first: N X Y Z
            if (parts.size >= 4 && d(parts[1]) != null && d(parts[2]) != null && d(parts[3]) != null) {
                val name = parts[0]
                if (d(parts[0]) == null || name.any { it.isLetter() }) {
                    out.add(MonPoint(name, d(parts[1])!!, d(parts[2])!!, d(parts[3])!!))
                    return@forEach
                }
            }
            // X Y Z [N]
            if (d(parts[0]) != null && d(parts[1]) != null && d(parts[2]) != null) {
                val name = parts.getOrNull(3)?.takeIf { d(it) == null } ?: "P${out.size + 1}"
                out.add(MonPoint(name, d(parts[0])!!, d(parts[1])!!, d(parts[2])!!))
            }
        }
        return out
    }
}
