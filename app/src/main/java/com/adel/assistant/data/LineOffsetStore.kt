package com.adel.assistant.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class LineOffsetProject(
    val id: String,
    val name: String,
    val x1: Double, val y1: Double,
    val x2: Double, val y2: Double,
    val firstOffset: Double,
    val firstLine: Double,
    val perpLength: Double,
    val parallelCount: Int,
    val perpCount: Int,
    val parallelIsLetter: Boolean,
    val parallelSpacings: List<Double>,
    val perpSpacings: List<Double>,
    val columnMode: String = "all",
    // plate axes offsets relative to column axes (same labels)
    val plateParallelOffsets: List<Double> = emptyList(),
    val platePerpOffsets: List<Double> = emptyList(),
    val createdAt: Long = System.currentTimeMillis()
)

object LineOffsetStore {
    private const val PREF = "line_offset_projects"

    fun all(context: Context): List<LineOffsetProject> {
        val raw = context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString("list", "[]") ?: "[]"
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i -> parse(arr.getJSONObject(i)) }
                .sortedByDescending { it.createdAt }
        } catch (_: Exception) { emptyList() }
    }

    fun save(context: Context, p: LineOffsetProject) {
        val list = all(context).filterNot { it.id == p.id } + p
        write(context, list)
    }

    fun delete(context: Context, id: String) {
        write(context, all(context).filterNot { it.id == id })
    }

    fun nextId(context: Context): String =
        ((all(context).mapNotNull { it.id.toLongOrNull() }.maxOrNull() ?: 0L) + 1).toString()

    private fun write(context: Context, list: List<LineOffsetProject>) {
        val arr = JSONArray()
        list.forEach { p ->
            arr.put(JSONObject().apply {
                put("id", p.id); put("name", p.name)
                put("x1", p.x1); put("y1", p.y1); put("x2", p.x2); put("y2", p.y2)
                put("firstOffset", p.firstOffset); put("firstLine", p.firstLine); put("perpLength", p.perpLength)
                put("parallelCount", p.parallelCount); put("perpCount", p.perpCount)
                put("parallelIsLetter", p.parallelIsLetter)
                put("parallelSpacings", JSONArray(p.parallelSpacings))
                put("perpSpacings", JSONArray(p.perpSpacings))
                put("columnMode", p.columnMode)
                put("plateParallelOffsets", JSONArray(p.plateParallelOffsets))
                put("platePerpOffsets", JSONArray(p.platePerpOffsets))
                put("createdAt", p.createdAt)
            })
        }
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString("list", arr.toString()).apply()
    }

    private fun parse(o: JSONObject): LineOffsetProject? = try {
        fun doubles(key: String): List<Double> {
            val a = o.optJSONArray(key) ?: return emptyList()
            return (0 until a.length()).map { a.getDouble(it) }
        }
        LineOffsetProject(
            id = o.getString("id"),
            name = o.optString("name"),
            x1 = o.getDouble("x1"), y1 = o.getDouble("y1"),
            x2 = o.getDouble("x2"), y2 = o.getDouble("y2"),
            firstOffset = o.getDouble("firstOffset"),
            firstLine = o.getDouble("firstLine"),
            perpLength = o.getDouble("perpLength"),
            parallelCount = o.getInt("parallelCount"),
            perpCount = o.getInt("perpCount"),
            parallelIsLetter = o.optBoolean("parallelIsLetter", true),
            parallelSpacings = doubles("parallelSpacings"),
            perpSpacings = doubles("perpSpacings"),
            columnMode = o.optString("columnMode", "all"),
            plateParallelOffsets = doubles("plateParallelOffsets"),
            platePerpOffsets = doubles("platePerpOffsets"),
            createdAt = o.optLong("createdAt", 0L)
        )
    } catch (_: Exception) { null }

    /** خروجی متنی برای پشتیبان زیپ */
    fun exportText(context: Context): String {
        val list = all(context)
        if (list.isEmpty()) return ""
        return buildString {
            appendLine("id,name,x1,y1,x2,y2,firstOffset,firstLine,perpLength,parallelCount,perpCount")
            list.forEach { p ->
                appendLine("${p.id},${p.name},${p.x1},${p.y1},${p.x2},${p.y2},${p.firstOffset},${p.firstLine},${p.perpLength},${p.parallelCount},${p.perpCount}")
            }
        }
    }
}
