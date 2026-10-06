package com.adel.assistant.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** نقطه داخل دسته نقشه ۲ */
data class Map2Point(
    val id: String,
    val name: String,
    val x: Double,
    val y: Double,
    val z: Double,
    val code: String = ""
) {
    fun toSurvey() = SurveyPoint(name.ifBlank { id }, x, y, z, code)
    fun toVol() = VolPoint(name.ifBlank { id }, x, y, z, code)
}

/**
 * دسته نقاط (لایه هم‌نام روی نقشه).
 * پیش‌فرض نام: PG 1، PG 2، …
 */
data class Map2PointCategory(
    val id: String,
    val name: String,
    val points: List<Map2Point> = emptyList(),
    val visible: Boolean = true,
    val showSymbol: Boolean = true,
    val showName: Boolean = true,
    val showCode: Boolean = false,
    val showElev: Boolean = false,
    val textSize: Double = 1.0,
    val textColorAci: Int = 7
)

/**
 * نشست مشترک نقشه ۲ — دسته‌های نقطه، سطوح توپو، الایمنت.
 */
object Map2Session {
    var projectName by mutableStateOf("نقشه")
    var zone by mutableStateOf(UtmGeo.DEFAULT_ZONE)
    var categories by mutableStateOf<List<Map2PointCategory>>(emptyList())
    var alignment by mutableStateOf<List<AlignmentVertex>>(emptyList())
    var message by mutableStateOf("")

    /** سطوح ساخته‌شده در توپوگرافی: نام دسته → VolPoints */
    var topoSurfaces by mutableStateOf<Map<String, List<VolPoint>>>(emptyMap())

    /** رنگ خطوط هر کد خانواده (ACI) — برای ترسیم خطی مشترک در پروژه */
    var lineColors by mutableStateOf<Map<String, Int>>(emptyMap())

    /**
     * مدل‌های DXF اضافه‌شده به همین پروژه (توپو، پروفیل، خطوط، …)
     * همه روی یک نقشه/خروجی واحد می‌مانند.
     */
    var projectDxfParts by mutableStateOf<List<Pair<String, String>>>(emptyList())
    /** پروژهٔ ترسیمی باز — تا ذخیره یا صرف‌نظر */
    var dirty by mutableStateOf(false)
    var openProject by mutableStateOf(true)


    fun nextPgName(): String {
        var n = 1
        val used = categories.map { it.name.trim().uppercase() }.toSet()
        while (used.contains("PG $n") || used.contains("PG$n")) n++
        return "PG $n"
    }

    fun addCategory(name: String = nextPgName(), points: List<Map2Point> = emptyList()): Map2PointCategory {
        val cat = Map2PointCategory(
            id = "c${System.currentTimeMillis()}_${categories.size}",
            name = name.ifBlank { nextPgName() },
            points = points
        )
        categories = categories + cat
        return cat
    }

    fun updateCategory(id: String, transform: (Map2PointCategory) -> Map2PointCategory) {
        categories = categories.map { if (it.id == id) transform(it) else it }
    }

    fun removeCategory(id: String) {
        categories = categories.filterNot { it.id == id }
        topoSurfaces = topoSurfaces.filterKeys { key -> categories.any { it.name == key } }
    }

    fun allPoints(): List<Map2Point> = categories.flatMap { it.points }

    /** مرکز حدود نقاط پروژه — برای قرارگیری پروفیل نزدیک نقاط */
    fun projectBoundsCenter(): Pair<Double, Double>? {
        val pts = allPoints()
        if (pts.isEmpty()) return null
        val minX = pts.minOf { it.x }
        val maxX = pts.maxOf { it.x }
        val minY = pts.minOf { it.y }
        val maxY = pts.maxOf { it.y }
        return (minX + maxX) / 2.0 to (minY + maxY) / 2.0
    }

    /** جایگزینی بخش هم‌نام یا افزودن — ترسیم‌ها روی هم می‌مانند */
    fun upsertProjectPart(name: String, dxfText: String) {
        val without = projectDxfParts.filterNot { it.first.equals(name, true) }
        projectDxfParts = without + (name to dxfText)
        dirty = true
        openProject = true
    }

    fun visiblePoints(): List<Map2Point> =
        categories.filter { it.visible }.flatMap { it.points }

    fun categoryById(id: String) = categories.find { it.id == id }

    fun pointsOf(ids: Set<String>): List<Map2Point> =
        categories.filter { it.id in ids }.flatMap { it.points }

    fun toVolPoints(categoryIds: Set<String> = emptySet()): List<VolPoint> {
        val src = if (categoryIds.isEmpty()) allPoints() else pointsOf(categoryIds)
        return src.map { it.toVol() }
    }

    fun setTopoSurface(name: String, pts: List<VolPoint>) {
        topoSurfaces = topoSurfaces + (name to pts)
    }


    /** همه نقاط نشست به‌صورت SurveyPoint — پیش‌فرض همه ابزارها */
    fun toSurveyPoints(categoryIds: Set<String> = emptySet()): List<SurveyPoint> {
        val src = if (categoryIds.isEmpty()) allPoints() else pointsOf(categoryIds)
        return src.map { it.toSurvey() }
    }

    /**
     * پس از ورود نقاط: برای پروفیل/توپو/خطوط یک سطح پیش‌فرض بساز
     * تا بدون ورود مجدد فایل کار کنند.
     */
    fun seedToolsFromPoints() {
        val vols = toVolPoints()
        if (vols.isEmpty()) return
        if (topoSurfaces.isEmpty()) {
            setTopoSurface("سطح اصلی", vols)
        }
        // پروفیل: اگر سطحی ندارد، همین نقاط را بگذار
        try {
            if (ProfileSession.surfaces.isEmpty()) {
                ProfileSession.updateSurfaces(
                    listOf(ProfileSurfaceSlot("سطح اصلی", vols))
                )
            }
        } catch (_: Exception) {
        }
        message = "نقاط برای پروفیل / توپو / ترسیم آماده است (${vols.size})"
    }

    fun addProjectPart(name: String, dxfText: String) {
        projectDxfParts = projectDxfParts + (name to dxfText)
        dirty = true
        openProject = true
    }

    fun markDirty() {
        dirty = true
        openProject = true
    }

    fun discardProject(context: Context? = null) {
        categories = emptyList()
        topoSurfaces = emptyMap()
        alignment = emptyList()
        lineColors = emptyMap()
        projectDxfParts = emptyList()
        dirty = false
        openProject = false
        message = "پروژه صرف‌نظر شد"
        context?.let { save(it) }
    }

    fun setLineColor(code: String, aci: Int) {
        val key = codeBase(code).ifBlank { code }.lowercase()
        if (key.isBlank()) return
        lineColors = lineColors + (key to aci.coerceIn(1, 255))
    }

    fun clearAll() {
        categories = emptyList()
        topoSurfaces = emptyMap()
        alignment = emptyList()
        lineColors = emptyMap()
        projectDxfParts = emptyList()
        message = ""
    }

    // —— پایداری ——
    private fun file(context: Context) =
        File(File(context.filesDir, "data").also { it.mkdirs() }, "map2_session.json")

    fun save(context: Context) {
        val o = JSONObject()
        o.put("projectName", projectName)
        o.put("zone", zone)
        val arr = JSONArray()
        categories.forEach { c ->
            val co = JSONObject()
            co.put("id", c.id)
            co.put("name", c.name)
            co.put("visible", c.visible)
            co.put("showSymbol", c.showSymbol)
            co.put("showName", c.showName)
            co.put("showCode", c.showCode)
            co.put("showElev", c.showElev)
            co.put("textSize", c.textSize)
            val pa = JSONArray()
            c.points.forEach { p ->
                pa.put(
                    JSONObject()
                        .put("id", p.id).put("name", p.name)
                        .put("x", p.x).put("y", p.y).put("z", p.z).put("code", p.code)
                )
            }
            co.put("points", pa)
            arr.put(co)
        }
        o.put("categories", arr)
        val parts = JSONArray()
        projectDxfParts.forEach { (n, d) ->
            parts.put(JSONObject().put("name", n).put("dxf", d))
        }
        o.put("projectDxfParts", parts)
        val lc = JSONObject()
        lineColors.forEach { (k, v) -> lc.put(k, v) }
        o.put("lineColors", lc)
        o.put("dirty", dirty)
        o.put("openProject", openProject)
        file(context).writeText(o.toString())
        dirty = false
        message = "پروژه ذخیره شد"
    }

    fun load(context: Context) {
        val f = file(context)
        if (!f.exists()) return
        try {
            val o = JSONObject(f.readText())
            projectName = o.optString("projectName", projectName)
            zone = o.optInt("zone", zone)
            val arr = o.optJSONArray("categories") ?: JSONArray()
            categories = (0 until arr.length()).mapNotNull { i ->
                val c = arr.optJSONObject(i) ?: return@mapNotNull null
                val pa = c.optJSONArray("points") ?: JSONArray()
                val pts = (0 until pa.length()).mapNotNull { j ->
                    val p = pa.optJSONObject(j) ?: return@mapNotNull null
                    Map2Point(
                        p.optString("id"), p.optString("name"),
                        p.optDouble("x"), p.optDouble("y"), p.optDouble("z"),
                        p.optString("code")
                    )
                }
                Map2PointCategory(
                    id = c.optString("id"),
                    name = c.optString("name"),
                    points = pts,
                    visible = c.optBoolean("visible", true),
                    showSymbol = c.optBoolean("showSymbol", true),
                    showName = c.optBoolean("showName", true),
                    showCode = c.optBoolean("showCode", false),
                    showElev = c.optBoolean("showElev", false),
                    textSize = c.optDouble("textSize", 1.0)
                )
            }
            message = "نشست بارگذاری شد (${categories.size} دسته)"
        
            val partsArr = o.optJSONArray("projectDxfParts")
            if (partsArr != null) {
                projectDxfParts = (0 until partsArr.length()).mapNotNull { i ->
                    val po = partsArr.optJSONObject(i) ?: return@mapNotNull null
                    po.optString("name") to po.optString("dxf")
                }
            }
            val lcObj = o.optJSONObject("lineColors")
            if (lcObj != null) {
                val map = mutableMapOf<String, Int>()
                lcObj.keys().forEach { k -> map[k] = lcObj.optInt(k, 7) }
                lineColors = map
            }
            dirty = o.optBoolean("dirty", false)
            openProject = o.optBoolean("openProject", categories.isNotEmpty() || projectDxfParts.isNotEmpty())
} catch (e: Exception) {
            message = "خطا بارگذاری: ${e.message}"
        }
    }
}
