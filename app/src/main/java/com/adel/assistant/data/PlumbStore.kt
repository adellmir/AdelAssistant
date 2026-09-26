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
    /** محور رفرنس: تقاطع A و B (letterIdx/numberIdx)؛ -1 = تعریف‌نشده */
    var axisALetter: Int = -1,
    var axisANumber: Int = -1,
    var axisBLetter: Int = -1,
    var axisBNumber: Int = -1,
    var updatedAt: Long = System.currentTimeMillis()
) {
    fun hasAxis(): Boolean =
        axisALetter >= 0 && axisANumber >= 0 && axisBLetter >= 0 && axisBNumber >= 0

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
        val header = StringBuilder(
            "name,client,report_d,report_m,report_y,control_d,control_m,control_y,height," +
            "letter_count,number_count,factor," +
            "top_neighbor,top_street,bottom_neighbor,bottom_street,right_neighbor,right_street,left_neighbor,left_street," +
            "axis_a_letter,axis_a_number,axis_b_letter,axis_b_number"
        )
        for (i in 0 until maxCols) {
            val n = i + 1
            header.append(",col${n}_name,col${n}_letter,col${n}_number,col${n}_wall")
            header.append(",col${n}_rep_ref_b,col${n}_rep_ref_t,col${n}_rep_line_b,col${n}_rep_line_t")
            header.append(",col${n}_rep_ref_mm,col${n}_rep_line_mm")
            header.append(",col${n}_ctl_ref_b,col${n}_ctl_ref_t,col${n}_ctl_line_b,col${n}_ctl_line_t")
            header.append(",col${n}_ctl_ref_mm,col${n}_ctl_line_mm")
        }
        header.append('\n')
        val sb = StringBuilder(header)
        list.forEach { p ->
            fun esc(s: String) = "\"${s.replace("\"", "\"\"")}\""
            fun n(v: Double?) = v?.let { String.format(java.util.Locale.US, "%.6f", it) } ?: ""
            fun b(v: Boolean) = if (v) "1" else "0"
            sb.append(esc(p.name)).append(',').append(esc(p.client)).append(',')
            sb.append(p.reportDay).append(',').append(p.reportMonth).append(',').append(p.reportYear).append(',')
            sb.append(p.controlDay).append(',').append(p.controlMonth).append(',').append(p.controlYear).append(',')
            sb.append(n(p.heightM)).append(',')
            sb.append(p.letterCount).append(',').append(p.numberCount).append(',').append(n(p.factor)).append(',')
            sb.append(b(p.topN.isNeighbor)).append(',').append(esc(p.topN.street)).append(',')
            sb.append(b(p.bottomN.isNeighbor)).append(',').append(esc(p.bottomN.street)).append(',')
            sb.append(b(p.rightN.isNeighbor)).append(',').append(esc(p.rightN.street)).append(',')
            sb.append(b(p.leftN.isNeighbor)).append(',').append(esc(p.leftN.street)).append(',')
            sb.append(p.axisALetter).append(',').append(p.axisANumber).append(',')
            sb.append(p.axisBLetter).append(',').append(p.axisBNumber)
            for (i in 0 until maxCols) {
                val c = p.columns.getOrNull(i)
                if (c == null) {
                    sb.append(",,,,,,,,,,,,,,,,")
                } else {
                    sb.append(',').append(esc(c.name))
                    sb.append(',').append(c.letterIdx).append(',').append(c.numberIdx)
                    sb.append(',').append(b(c.isWallPlumb))
                    sb.append(',').append(n(c.report.refBottom)).append(',').append(n(c.report.refTop))
                    sb.append(',').append(n(c.report.lineBottom)).append(',').append(n(c.report.lineTop))
                    sb.append(',').append(n(c.report.refValueMm)).append(',').append(n(c.report.lineValueMm))
                    sb.append(',').append(n(c.control.refBottom)).append(',').append(n(c.control.refTop))
                    sb.append(',').append(n(c.control.lineBottom)).append(',').append(n(c.control.lineTop))
                    sb.append(',').append(n(c.control.refValueMm)).append(',').append(n(c.control.lineValueMm))
                }
            }
            sb.append('\n')
        }
        return sb.toString()
    }

    fun importCsvfun importCsv(ctx: Context, text: String): Int {
        val lines = text.replace("\r\n", "\n").replace('\r', '\n').lines().filter { it.isNotBlank() }
        if (lines.isEmpty()) return 0
        val header = splitCsvLine(lines.first())
        val nameIdx = header.indexOfFirst { it.equals("name", true) }.takeIf { it >= 0 } ?: 0
        val clientIdx = header.indexOfFirst { it.equals("client", true) }.takeIf { it >= 0 } ?: 1
        fun idx(h: String) = header.indexOfFirst { it.equals(h, true) }
        val rd = idx("report_d"); val rm = idx("report_m"); val ry = idx("report_y")
        val cd = idx("control_d"); val cm = idx("control_m"); val cy = idx("control_y")
        val hi = idx("height")
        val colNameIdx = header.mapIndexedNotNull { i, h -> if (h.matches(Regex("col\\d+_name", RegexOption.IGNORE_CASE))) i else null }
        val imported = mutableListOf<PlumbProject>()
        for (line in lines.drop(1)) {
            val c = splitCsvLine(line)
            if (c.isEmpty()) continue
            fun g(i: Int) = c.getOrNull(i)?.trim().orEmpty()
            fun gd(i: Int) = g(i).replace(',', '.').toDoubleOrNull()
            val columns = mutableListOf<PlumbColumn>()
            for (n in 1..200) {
                val nameH = "col${n}_name"
                val niHdr = header.indexOfFirst { it.equals(nameH, true) }
                if (niHdr < 0) break
                val cname = g(niHdr)
                if (cname.isBlank()) continue
                fun colH(suffix: String) = header.indexOfFirst { it.equals("col${n}_$suffix", true) }
                fun gcol(suffix: String) = colH(suffix).let { if (it >= 0) gd(it) else null }
                fun gcoli(suffix: String) = colH(suffix).let { if (it >= 0) g(it).toIntOrNull() else null }
                fun gcolb(suffix: String) = colH(suffix).let { if (it >= 0) g(it) in listOf("1", "true", "TRUE") else false }
                val letterPart = cname.takeWhile { it.isLetter() }
                val numPart = cname.dropWhile { it.isLetter() }.filter { it.isDigit() }
                val li = gcoli("letter") ?: letterPart.uppercase().firstOrNull()?.let { it - 'A' } ?: 0
                val numi = gcoli("number") ?: numPart.toIntOrNull()?.minus(1) ?: 0
                // old layout: name, rep_ref_b, rep_ref_t, rep_line_b, rep_line_t, ctl...
                val hasNew = colH("letter") >= 0 || colH("wall") >= 0
                val rep: PlumbReading
                val ctl: PlumbReading
                val wall: Boolean
                if (hasNew) {
                    wall = gcolb("wall")
                    rep = PlumbReading(
                        refBottom = gcol("rep_ref_b"), refTop = gcol("rep_ref_t"),
                        lineBottom = gcol("rep_line_b"), lineTop = gcol("rep_line_t"),
                        refValueMm = gcol("rep_ref_mm"), lineValueMm = gcol("rep_line_mm")
                    )
                    ctl = PlumbReading(
                        refBottom = gcol("ctl_ref_b"), refTop = gcol("ctl_ref_t"),
                        lineBottom = gcol("ctl_line_b"), lineTop = gcol("ctl_line_t"),
                        refValueMm = gcol("ctl_ref_mm"), lineValueMm = gcol("ctl_line_mm")
                    )
                } else {
                    wall = false
                    rep = PlumbReading(
                        refBottom = gd(niHdr + 1), refTop = gd(niHdr + 2),
                        lineBottom = gd(niHdr + 3), lineTop = gd(niHdr + 4)
                    )
                    ctl = PlumbReading(
                        refBottom = gd(niHdr + 5), refTop = gd(niHdr + 6),
                        lineBottom = gd(niHdr + 7), lineTop = gd(niHdr + 8)
                    )
                }
                columns.add(PlumbColumn(cname, li, numi, rep, ctl, wall))
            }
            val maxL = (columns.maxOfOrNull { it.letterIdx } ?: 0) + 1
            val maxN = (columns.maxOfOrNull { it.numberIdx } ?: 0) + 1
            fun gi(h: String) = idx(h).let { if (it >= 0) g(it).toIntOrNull() else null }
            fun gb(h: String) = idx(h).let { if (it >= 0) g(it) in listOf("1", "true", "TRUE") else false }
            fun gs(h: String) = idx(h).let { if (it >= 0) g(it) else "" }
            imported.add(
                PlumbProject(
                    name = g(nameIdx),
                    client = g(clientIdx),
                    reportDay = if (rd >= 0) g(rd) else "",
                    reportMonth = if (rm >= 0) g(rm) else "",
                    reportYear = if (ry >= 0) g(ry) else "",
                    controlDay = if (cd >= 0) g(cd) else "",
                    controlMonth = if (cm >= 0) g(cm) else "",
                    controlYear = if (cy >= 0) g(cy) else "",
                    heightM = if (hi >= 0) gd(hi) ?: 22.0 else 22.0,
                    letterCount = (gi("letter_count") ?: maxL).coerceAtLeast(1),
                    numberCount = (gi("number_count") ?: maxN).coerceAtLeast(1),
                    factor = idx("factor").let { if (it >= 0) gd(it) ?: 1.0 else 1.0 },
                    columns = columns,
                    topN = PlumbNeighbor(gb("top_neighbor"), gs("top_street")),
                    bottomN = PlumbNeighbor(gb("bottom_neighbor"), gs("bottom_street")),
                    rightN = PlumbNeighbor(gb("right_neighbor"), gs("right_street")),
                    leftN = PlumbNeighbor(gb("left_neighbor"), gs("left_street")),
                    axisALetter = gi("axis_a_letter") ?: -1,
                    axisANumber = gi("axis_a_number") ?: -1,
                    axisBLetter = gi("axis_b_letter") ?: -1,
                    axisBNumber = gi("axis_b_number") ?: -1
                )
            )
        }
        if (imported.isEmpty()) return 0
        val all = loadAll(ctx).toMutableList()
        imported.forEach { p ->
            val i = all.indexOfFirst { it.name.equals(p.name, true) && it.client.equals(p.client, true) }
            if (i >= 0) all[i] = p.copy(id = all[i].id) else all.add(p)
        }
        saveAll(ctx, all)
        return imported.size
    }

    private fun splitCsvLine(line: String): List<String> {
        val out = mutableListOf<String>()
        val sb = StringBuilder()
        var inQ = false
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            when {
                ch == '"' -> {
                    if (inQ && i + 1 < line.length && line[i + 1] == '"') {
                        sb.append('"'); i++
                    } else inQ = !inQ
                }
                ch == ',' && !inQ -> {
                    out.add(sb.toString()); sb.clear()
                }
                else -> sb.append(ch)
            }
            i++
        }
        out.add(sb.toString())
        return out
    }

    /** JSON کامل پایگاه برای زیپ پشتیبان */
    fun exportJson(ctx: Context): String {
        val arr = org.json.JSONArray()
        loadAll(ctx).forEach { arr.put(toJson(it)) }
        return arr.toString(2)
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
        put("axisALetter", p.axisALetter)
        put("axisANumber", p.axisANumber)
        put("axisBLetter", p.axisBLetter)
        put("axisBNumber", p.axisBNumber)
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
            axisALetter = o.optInt("axisALetter", -1),
            axisANumber = o.optInt("axisANumber", -1),
            axisBLetter = o.optInt("axisBLetter", -1),
            axisBNumber = o.optInt("axisBNumber", -1),
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
