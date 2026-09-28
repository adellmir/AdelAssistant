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
    val textSize: Double = 1.0
)

/**
 * نشست مشترک نقشه ۲ — دسته‌های نقطه، سطوح توپو، الایمنت.
 */
object Map2Session {
    var projectName by mutableStateOf("نقشه۲")
    var zone by mutableStateOf(UtmGeo.DEFAULT_ZONE)
    var categories by mutableStateOf<List<Map2PointCategory>>(emptyList())
    var alignment by mutableStateOf<List<AlignmentVertex>>(emptyList())
    var message by mutableStateOf("")

    /** سطوح ساخته‌شده در توپوگرافی: نام دسته → VolPoints */
    var topoSurfaces by mutableStateOf<Map<String, List<VolPoint>>>(emptyMap())

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

    fun clearAll() {
        categories = emptyList()
        topoSurfaces = emptyMap()
        alignment = emptyList()
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
        } catch (e: Exception) {
            message = "خطا بارگذاری: ${e.message}"
        }
    }
}
