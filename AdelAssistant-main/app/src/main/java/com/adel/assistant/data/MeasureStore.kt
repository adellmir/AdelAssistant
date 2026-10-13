package com.adel.assistant.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.math.*

enum class MeasureType { FIELD, LINE, POI }

/**
 * مدل یکپارچه اندازه‌گیری (فاز ۲) — زمین / مسیر / نقطه.
 * rings: اولی حلقه بیرونی، بقیه حفره؛ مختصات E,N در UTM.
 */
data class Measure(
    val id: String,
    val type: MeasureType,
    val name: String,
    val lot: String = "",
    val client: String = "",
    val groupId: String? = null,
    val note: String = "",
    val zone: Int = UtmGeo.DEFAULT_ZONE,
    val rings: List<List<Pair<Double, Double>>> = emptyList(),
    val zList: List<Double> = emptyList(),
    val source: String = "gps",
    val createdMs: Long = System.currentTimeMillis(),
    val updatedMs: Long = System.currentTimeMillis()
) {
    val areaM2: Double
        get() = when (type) {
            MeasureType.FIELD -> MeasureGeometry.areaWithHoles(rings)
            else -> 0.0
        }
    val lengthM: Double
        get() = when (type) {
            MeasureType.LINE -> rings.firstOrNull()?.let { MeasureGeometry.pathLength(it) } ?: 0.0
            MeasureType.FIELD -> MeasureGeometry.perimeter(rings.firstOrNull().orEmpty())
            MeasureType.POI -> 0.0
        }
}

data class MeasureGroup(val id: String, val name: String, val colorArgb: Long = 0xFF1E88E5)

object MeasureGeometry {
    data class P(val x: Double, val y: Double)

    /** مساحت علامت‌دار حلقه (نسبت به مبدأ محلی برای دقت عددی) */
    fun ringArea(r: List<Pair<Double, Double>>): Double {
        if (r.size < 3) return 0.0
        val x0 = r[0].first
        val y0 = r[0].second
        var sum = 0.0
        for (i in r.indices) {
            val j = (i + 1) % r.size
            val xi = r[i].first - x0
            val yi = r[i].second - y0
            val xj = r[j].first - x0
            val yj = r[j].second - y0
            sum += xi * yj - xj * yi
        }
        return sum / 2.0
    }

    fun areaWithHoles(rings: List<List<Pair<Double, Double>>>): Double {
        if (rings.isEmpty()) return 0.0
        val outer = abs(ringArea(rings[0]))
        var holes = 0.0
        for (i in 1 until rings.size) holes += abs(ringArea(rings[i]))
        return (outer - holes).coerceAtLeast(0.0)
    }

    fun pathLength(pts: List<Pair<Double, Double>>): Double {
        if (pts.size < 2) return 0.0
        var s = 0.0
        for (i in 0 until pts.size - 1) {
            val dx = pts[i + 1].first - pts[i].first
            val dy = pts[i + 1].second - pts[i].second
            s += hypot(dx, dy)
        }
        return s
    }

    fun perimeter(pts: List<Pair<Double, Double>>): Double {
        if (pts.size < 2) return 0.0
        return pathLength(pts) + hypot(pts.last().first - pts.first().first, pts.last().second - pts.first().second)
    }

    fun fmtArea(m2: Double): String =
        if (m2 >= 10_000) String.format(java.util.Locale.US, "%.3f ha", m2 / 10_000.0)
        else String.format(java.util.Locale.US, "%.2f m²", m2)

    fun fmtDist(m: Double): String =
        if (m >= 1000) String.format(java.util.Locale.US, "%.3f km", m / 1000.0)
        else String.format(java.util.Locale.US, "%.2f m", m)

    /** مرکز ثقل تقریبی حلقه بیرونی */
    fun centroid(pts: List<Pair<Double, Double>>): Pair<Double, Double>? {
        if (pts.isEmpty()) return null
        return pts.map { it.first }.average() to pts.map { it.second }.average()
    }
}

/** فیلتر نقاط مسیر GPS */
class TrackFilter(
    private val minStep: Double = 1.5,
    private val maxAcc: Float = 12f,
    private val maxSpeed: Double = 15.0
) {
    data class Loc(val e: Double, val n: Double, val acc: Float, val t: Long)

    private var last: Loc? = null

    fun reset() { last = null }

    fun accept(p: Loc): Boolean {
        if (p.acc > 0 && p.acc > maxAcc) return false
        val l = last
        if (l == null) {
            last = p
            return true
        }
        val d = hypot(p.e - l.e, p.n - l.n)
        val dt = (p.t - l.t) / 1000.0
        if (d < minStep) return false
        if (dt > 0 && d / dt > maxSpeed) return false
        // ایستادن: سرعت خیلی کم
        if (dt > 0 && d / dt < 0.3 && d < 3.0) return false
        last = p
        return true
    }

    fun shouldAutoClose(pts: List<Loc>, cur: Loc, closeDist: Double = 3.0): Boolean =
        pts.size >= 3 && hypot(cur.e - pts.first().e, cur.n - pts.first().n) < closeDist
}

object MeasureStore {
    private const val FILE = "measures.json"
    private const val GROUPS = "measure_groups.json"

    private fun file(ctx: Context, name: String): File {
        val dir = File(ctx.filesDir, "data")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, name)
    }

    fun load(ctx: Context): List<Measure> {
        val f = file(ctx, FILE)
        if (!f.exists()) return emptyList()
        return try {
            val arr = JSONArray(f.readText())
            (0 until arr.length()).mapNotNull { i -> parseMeasure(arr.optJSONObject(i)) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun save(ctx: Context, list: List<Measure>) {
        val arr = JSONArray()
        list.forEach { m ->
            arr.put(JSONObject().apply {
                put("id", m.id)
                put("type", m.type.name)
                put("name", m.name)
                put("lot", m.lot)
                put("client", m.client)
                put("groupId", m.groupId ?: "")
                put("note", m.note)
                put("zone", m.zone)
                put("source", m.source)
                put("createdMs", m.createdMs)
                put("updatedMs", m.updatedMs)
                val ringsJa = JSONArray()
                m.rings.forEach { ring ->
                    val rja = JSONArray()
                    ring.forEach { (e, n) ->
                        rja.put(JSONArray().put(e).put(n))
                    }
                    ringsJa.put(rja)
                }
                put("rings", ringsJa)
                val zja = JSONArray()
                m.zList.forEach { zja.put(it) }
                put("zList", zja)
            })
        }
        file(ctx, FILE).writeText(arr.toString())
    }

    fun loadGroups(ctx: Context): List<MeasureGroup> {
        val f = file(ctx, GROUPS)
        if (!f.exists()) return listOf(MeasureGroup("none", "بدون گروه", 0xFF757575))
        return try {
            val arr = JSONArray(f.readText())
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                MeasureGroup(o.optString("id"), o.optString("name"), o.optLong("colorArgb", 0xFF1E88E5))
            }
        } catch (_: Exception) {
            listOf(MeasureGroup("none", "بدون گروه", 0xFF757575))
        }
    }

    fun saveGroups(ctx: Context, groups: List<MeasureGroup>) {
        val arr = JSONArray()
        groups.forEach { g ->
            arr.put(JSONObject().put("id", g.id).put("name", g.name).put("colorArgb", g.colorArgb))
        }
        file(ctx, GROUPS).writeText(arr.toString())
    }

    private fun parseMeasure(o: JSONObject?): Measure? {
        if (o == null) return null
        return try {
            val type = MeasureType.valueOf(o.optString("type", "LINE"))
            val ringsJa = o.optJSONArray("rings") ?: JSONArray()
            val rings = (0 until ringsJa.length()).map { ri ->
                val rja = ringsJa.getJSONArray(ri)
                (0 until rja.length()).map { pi ->
                    val p = rja.getJSONArray(pi)
                    p.getDouble(0) to p.getDouble(1)
                }
            }
            val zja = o.optJSONArray("zList") ?: JSONArray()
            val zList = (0 until zja.length()).map { zja.getDouble(it) }
            Measure(
                id = o.optString("id", System.nanoTime().toString()),
                type = type,
                name = o.optString("name", ""),
                lot = o.optString("lot", ""),
                client = o.optString("client", ""),
                groupId = o.optString("groupId", "").ifBlank { null },
                note = o.optString("note", ""),
                zone = o.optInt("zone", UtmGeo.DEFAULT_ZONE),
                rings = rings,
                zList = zList,
                source = o.optString("source", "gps"),
                createdMs = o.optLong("createdMs", 0L),
                updatedMs = o.optLong("updatedMs", 0L)
            )
        } catch (_: Exception) {
            null
        }
    }
}
