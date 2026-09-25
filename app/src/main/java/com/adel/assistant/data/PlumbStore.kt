package com.adel.assistant.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class PlumbReading(
    var refBottom: Double? = null,
    var refTop: Double? = null,
    var lineBottom: Double? = null,
    var lineTop: Double? = null,
    var refValueMm: Double? = null, // manual override or computed
    var lineValueMm: Double? = null
) {
    fun computedRefMm(): Double? {
        if (refValueMm != null) return refValueMm
        val b = refBottom ?: return null
        val t = refTop ?: return null
        return (t - b) * 1000.0
    }

    fun computedLineMm(): Double? {
        if (lineValueMm != null) return lineValueMm
        val b = lineBottom ?: return null
        val t = lineTop ?: return null
        return (t - b) * 1000.0
    }
}

data class PlumbColumn(
    val name: String,
    val letterIdx: Int,
    val numberIdx: Int,
    var report: PlumbReading = PlumbReading(),
    var control: PlumbReading = PlumbReading(),
    var isWallPlumb: Boolean = false
)

data class PlumbNeighbor(
    var isNeighbor: Boolean = false,
    var street: String = ""
)

data class PlumbProject(
    val id: String = UUID.randomUUID().toString(),
    var name: String = "",
    var client: String = "",
    var reportDay: String = "",
    var reportMonth: String = "",
    var reportYear: String = "",
    var controlDay: String = "",
    var controlMonth: String = "",
    var controlYear: String = "",
    var heightM: Double = 22.0,
    var letterCount: Int = 0,
    var numberCount: Int = 0,
    var factor: Double = 1.0,
    var columns: MutableList<PlumbColumn> = mutableListOf(),
    var topN: PlumbNeighbor = PlumbNeighbor(),
    var bottomN: PlumbNeighbor = PlumbNeighbor(),
    var rightN: PlumbNeighbor = PlumbNeighbor(),
    var leftN: PlumbNeighbor = PlumbNeighbor(),
    var axesVisible: Boolean = true,
    var updatedAt: Long = System.currentTimeMillis()
) {
    fun toleranceMm(): Double = heightM / 600.0 * 1000.0 // height/600 in meters → mm: height/600*1000 = height*1000/600

    fun sortKey(): String {
        val y = reportYear.ifBlank { controlYear }
        val m = reportMonth.ifBlank { controlMonth }.padStart(2, '0')
        val d = reportDay.ifBlank { controlDay }.padStart(2, '0')
        return "$y$m$d-${updatedAt}"
    }

    /** محور طولی (کمتر) عمودی؛ محور عرضی (بیشتر) افقی */
    fun longIsLetters(): Boolean = letterCount <= numberCount
    fun longCount(): Int = min(letterCount, numberCount)
    fun crossCount(): Int = max(letterCount, numberCount)
    fun longSpacing(): Double = 1.0
    fun crossSpacing(): Double = factor.coerceAtLeast(0.01)
}

object PlumbStore {
    private const val FILE = "plumb_projects.json"

    private fun file(ctx: Context) = File(ctx.filesDir, FILE)

    fun loadAll(ctx: Context): List<PlumbProject> {
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

    fun saveAll(ctx: Context, list: List<PlumbProject>) {
        val arr = JSONArray()
        list.forEach { arr.put(toJson(it)) }
        file(ctx).writeText(arr.toString())
    }

    fun upsert(ctx: Context, p: PlumbProject) {
        val all = loadAll(ctx).toMutableList()
        val i = all.indexOfFirst { it.id == p.id }
        p.updatedAt = System.currentTimeMillis()
        if (i >= 0) all[i] = p else all.add(0, p)
        saveAll(ctx, all)
    }

    fun delete(ctx: Context, id: String) {
        saveAll(ctx, loadAll(ctx).filter { it.id != id })
    }

    fun exportCsv(ctx: Context, list: List<PlumbProject> = loadAll(ctx)): String {
        val maxCols = list.maxOfOrNull { it.columns.size } ?: 0
        val header = StringBuilder("name,client,report_d,report_m,report_y,control_d,control_m,control_y,height")
        for (i in 0 until maxCols) {
            val n = i + 1
            header.append(",col${n}_name,col${n}_rep_ref_b,col${n}_rep_ref_t,col${n}_rep_line_b,col${n}_rep_line_t")
            header.append(",col${n}_ctl_ref_b,col${n}_ctl_ref_t,col${n}_ctl_line_b,col${n}_ctl_line_t")
        }
        header.append('\n')
        val sb = StringBuilder(header)
        list.forEach { p ->
            fun esc(s: String) = "\"${s.replace("\"", "\"\"")}\""
            sb.append(esc(p.name)).append(',')
            sb.append(esc(p.client)).append(',')
            sb.append(p.reportDay).append(',').append(p.reportMonth).append(',').append(p.reportYear).append(',')
            sb.append(p.controlDay).append(',').append(p.controlMonth).append(',').append(p.controlYear).append(',')
            sb.append(p.heightM)
            for (i in 0 until maxCols) {
                val c = p.columns.getOrNull(i)
                if (c == null) {
                    sb.append(",,,,,,,,")
                } else {
                    fun n(v: Double?) = v?.let { String.format(java.util.Locale.US, "%.4f", it) } ?: ""
                    sb.append(',').append(esc(c.name))
                    sb.append(',').append(n(c.report.refBottom)).append(',').append(n(c.report.refTop))
                    sb.append(',').append(n(c.report.lineBottom)).append(',').append(n(c.report.lineTop))
                    sb.append(',').append(n(c.control.refBottom)).append(',').append(n(c.control.refTop))
                    sb.append(',').append(n(c.control.lineBottom)).append(',').append(n(c.control.lineTop))
                }
            }
            sb.append('\n')
        }
        return sb.toString()
    }

    private fun toJson(p: PlumbProject): JSONObject = JSONObject().apply {
        put("id", p.id)
        put("name", p.name)
        put("client", p.client)
        put("reportDay", p.reportDay); put("reportMonth", p.reportMonth); put("reportYear", p.reportYear)
        put("controlDay", p.controlDay); put("controlMonth", p.controlMonth); put("controlYear", p.controlYear)
        put("heightM", p.heightM)
        put("letterCount", p.letterCount); put("numberCount", p.numberCount); put("factor", p.factor)
        put("axesVisible", p.axesVisible)
        put("updatedAt", p.updatedAt)
        put("topN", neighborJson(p.topN))
        put("bottomN", neighborJson(p.bottomN))
        put("rightN", neighborJson(p.rightN))
        put("leftN", neighborJson(p.leftN))
        val cols = JSONArray()
        p.columns.forEach { c ->
            cols.put(JSONObject().apply {
                put("name", c.name)
                put("letterIdx", c.letterIdx)
                put("numberIdx", c.numberIdx)
                put("report", readingJson(c.report))
                put("control", readingJson(c.control))
                put("isWallPlumb", c.isWallPlumb)
            })
        }
        put("columns", cols)
    }

    private fun neighborJson(n: PlumbNeighbor) = JSONObject().apply {
        put("isNeighbor", n.isNeighbor); put("street", n.street)
    }

    private fun readingJson(r: PlumbReading) = JSONObject().apply {
        putOpt("refBottom", r.refBottom); putOpt("refTop", r.refTop)
        putOpt("lineBottom", r.lineBottom); putOpt("lineTop", r.lineTop)
        putOpt("refValueMm", r.refValueMm); putOpt("lineValueMm", r.lineValueMm)
    }

    private fun parseProject(o: JSONObject): PlumbProject {
        val cols = mutableListOf<PlumbColumn>()
        val arr = o.optJSONArray("columns") ?: JSONArray()
        for (i in 0 until arr.length()) {
            val c = arr.getJSONObject(i)
            cols.add(
                PlumbColumn(
                    name = c.optString("name"),
                    letterIdx = c.optInt("letterIdx"),
                    numberIdx = c.optInt("numberIdx"),
                    report = parseReading(c.optJSONObject("report")),
                    control = parseReading(c.optJSONObject("control")),
                    isWallPlumb = c.optBoolean("isWallPlumb", false)
                )
            )
        }
        return PlumbProject(
            id = o.optString("id", UUID.randomUUID().toString()),
            name = o.optString("name"),
            client = o.optString("client"),
            reportDay = o.optString("reportDay"),
            reportMonth = o.optString("reportMonth"),
            reportYear = o.optString("reportYear"),
            controlDay = o.optString("controlDay"),
            controlMonth = o.optString("controlMonth"),
            controlYear = o.optString("controlYear"),
            heightM = o.optDouble("heightM", 22.0),
            letterCount = o.optInt("letterCount"),
            numberCount = o.optInt("numberCount"),
            factor = o.optDouble("factor", 1.0),
            columns = cols,
            topN = parseNeighbor(o.optJSONObject("topN")),
            bottomN = parseNeighbor(o.optJSONObject("bottomN")),
            rightN = parseNeighbor(o.optJSONObject("rightN")),
            leftN = parseNeighbor(o.optJSONObject("leftN")),
            axesVisible = o.optBoolean("axesVisible", true),
            updatedAt = o.optLong("updatedAt", System.currentTimeMillis())
        )
    }

    private fun parseNeighbor(o: JSONObject?) = PlumbNeighbor(
        isNeighbor = o?.optBoolean("isNeighbor") ?: false,
        street = o?.optString("street") ?: ""
    )

    private fun parseReading(o: JSONObject?) = PlumbReading(
        refBottom = o?.optDoubleOrNull("refBottom"),
        refTop = o?.optDoubleOrNull("refTop"),
        lineBottom = o?.optDoubleOrNull("lineBottom"),
        lineTop = o?.optDoubleOrNull("lineTop"),
        refValueMm = o?.optDoubleOrNull("refValueMm"),
        lineValueMm = o?.optDoubleOrNull("lineValueMm")
    )

    private fun JSONObject.optDoubleOrNull(key: String): Double? =
        if (has(key) && !isNull(key)) optDouble(key) else null

    fun letterLabel(i: Int): String {
        var n = i
        val sb = StringBuilder()
        do {
            sb.insert(0, ('A' + n % 26).toChar())
            n = n / 26 - 1
        } while (n >= 0)
        return sb.toString()
    }

    fun isOutOfTol(valueMm: Double, tolMm: Double): Boolean = abs(valueMm) > tolMm + 1e-9
}
