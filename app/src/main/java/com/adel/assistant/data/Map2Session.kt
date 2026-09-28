package com.adel.assistant.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * نشست مشترک «نقشه ۲» — نقاط، سطوح، الایمنت، تنظیمات.
 * همه ابزارها (توپو / پروفیل / حجم / مساحت / ترسیم) از همین منبع می‌خوانند.
 */
data class Map2Point(
    val id: String,
    val name: String,
    val x: Double,
    val y: Double,
    val z: Double,
    val code: String = "",
    val group: String = ""
) {
    fun toSurvey(): SurveyPoint = SurveyPoint(name.ifBlank { id }, x, y, z, code)
    fun toVol(): VolPoint = VolPoint(name.ifBlank { id }, x, y, z, code)
}

data class Map2SurfaceDef(
    val name: String,
    val pointIds: List<String> = emptyList(),
    /** اگر خالی باشد از همه نقاط یا فیلتر کد استفاده می‌شود */
    val codeFilter: String = ""
)

object Map2Session {
    var projectName by mutableStateOf("نقشه۲")
    var zone by mutableStateOf(UtmGeo.DEFAULT_ZONE)
    var simpleMode by mutableStateOf(false)
    var points by mutableStateOf<List<Map2Point>>(emptyList())
    var surfaces by mutableStateOf<List<Map2SurfaceDef>>(emptyList())
    var alignment by mutableStateOf<List<AlignmentVertex>>(emptyList())
    var startChainage by mutableStateOf(0.0)
    var profileStep by mutableStateOf(10.0)
    var topoInterval by mutableStateOf(1.0)
    var message by mutableStateOf("")

    // نتایج کش‌شده
    var lastArea by mutableStateOf<Double?>(null)
    var lastPerimeter by mutableStateOf<Double?>(null)
    var lastTopo by mutableStateOf<TopographyEngine.Result?>(null)
    var lastProfile by mutableStateOf<ProfileResult2?>(null)
    var lastVolume by mutableStateOf<VolumeResult?>(null)

    fun clearResults() {
        lastArea = null
        lastPerimeter = null
        lastTopo = null
        lastProfile = null
        lastVolume = null
    }

    fun setPoints(list: List<Map2Point>) {
        points = list
        clearResults()
    }

    fun addPoints(list: List<Map2Point>) {
        points = points + list
        clearResults()
    }

    fun updatePoint(p: Map2Point) {
        points = points.map { if (it.id == p.id) p else it }
        clearResults()
    }

    fun removePoint(id: String) {
        points = points.filterNot { it.id == id }
        clearResults()
    }

    fun clearPoints() {
        points = emptyList()
        clearResults()
    }

    fun pointsByCode(code: String): List<Map2Point> {
        val c = code.trim().lowercase()
        if (c.isBlank()) return points
        return points.filter { it.code.trim().lowercase().let { cd -> cd == c || cd.startsWith("${c} ") || cd.startsWith("${c}-") || cd.startsWith("${c}_") || codeBase(cd) == c } }
    }

    fun groups(): List<String> =
        points.map { it.code.trim().ifBlank { "(بدون کد)" } }.distinct().sorted()

    fun toVolPoints(filterCode: String = ""): List<VolPoint> {
        val src = if (filterCode.isBlank()) points else pointsByCode(filterCode)
        return src.map { it.toVol() }
    }

    fun syncToTopography(filterCode: String = "tp") {
        val tp = pointsByCode(filterCode)
        val use = if (tp.size >= 3) tp else points
        TopographySession.updatePoints(use.map { it.toVol() })
    }

    fun syncToProfile(surfaceName: String = "سطح", filterCode: String = "") {
        val vols = toVolPoints(filterCode)
        if (vols.size >= 3) {
            ProfileSession.updateSurfaces(listOf(ProfileSurfaceSlot(surfaceName, vols)))
        }
        if (alignment.size >= 2) {
            ProfileSession.finishAlignment(alignment)
        }
    }

    fun polygonAreaPerimeter(ids: List<String>? = null): Pair<Double, Double> {
        val pts = if (ids.isNullOrEmpty()) points else {
            val set = ids.toSet()
            points.filter { it.id in set }
        }
        if (pts.size < 3) return 0.0 to 0.0
        var signed = 0.0
        var peri = 0.0
        for (i in pts.indices) {
            val a = pts[i]
            val b = pts[(i + 1) % pts.size]
            signed += a.x * b.y - b.x * a.y
            peri += sqrt((b.x - a.x) * (b.x - a.x) + (b.y - a.y) * (b.y - a.y))
        }
        return abs(signed) / 2.0 to peri
    }

    fun dedupe(tol: Double = 0.01): Int {
        val (clean, removed) = VolumeEngine.dedupe(points.map { it.toVol() }, tol)
        if (removed > 0) {
            points = clean.mapIndexed { i, v ->
                Map2Point(
                    id = "d$i-${v.id}",
                    name = v.id,
                    x = v.x, y = v.y, z = v.z,
                    code = v.code
                )
            }
            clearResults()
        }
        return removed
    }

    // —— پایداری ——
    private fun file(context: Context) = File(File(context.filesDir, "data").also { it.mkdirs() }, "map2_session.json")

    fun save(context: Context) {
        val o = JSONObject()
        o.put("projectName", projectName)
        o.put("zone", zone)
        o.put("simpleMode", simpleMode)
        o.put("startChainage", startChainage)
        o.put("profileStep", profileStep)
        o.put("topoInterval", topoInterval)
        val arr = JSONArray()
        points.forEach { p ->
            arr.put(
                JSONObject()
                    .put("id", p.id).put("name", p.name)
                    .put("x", p.x).put("y", p.y).put("z", p.z)
                    .put("code", p.code).put("group", p.group)
            )
        }
        o.put("points", arr)
        val al = JSONArray()
        alignment.forEach { v -> al.put(JSONObject().put("x", v.x).put("y", v.y)) }
        o.put("alignment", al)
        file(context).writeText(o.toString())
        message = "نشست ذخیره شد"
    }

    fun load(context: Context) {
        val f = file(context)
        if (!f.exists()) return
        try {
            val o = JSONObject(f.readText())
            projectName = o.optString("projectName", projectName)
            zone = o.optInt("zone", zone)
            simpleMode = o.optBoolean("simpleMode", false)
            startChainage = o.optDouble("startChainage", 0.0)
            profileStep = o.optDouble("profileStep", 10.0)
            topoInterval = o.optDouble("topoInterval", 1.0)
            val arr = o.optJSONArray("points") ?: JSONArray()
            points = (0 until arr.length()).mapNotNull { i ->
                val p = arr.optJSONObject(i) ?: return@mapNotNull null
                Map2Point(
                    id = p.optString("id"),
                    name = p.optString("name"),
                    x = p.optDouble("x"),
                    y = p.optDouble("y"),
                    z = p.optDouble("z"),
                    code = p.optString("code"),
                    group = p.optString("group")
                )
            }
            val al = o.optJSONArray("alignment") ?: JSONArray()
            alignment = (0 until al.length()).mapNotNull { i ->
                val v = al.optJSONObject(i) ?: return@mapNotNull null
                AlignmentVertex(v.optDouble("x"), v.optDouble("y"))
            }
            clearResults()
            message = "نشست بارگذاری شد (${points.size} نقطه)"
        } catch (e: Exception) {
            message = "خطا در بارگذاری نشست: ${e.message}"
        }
    }
}
