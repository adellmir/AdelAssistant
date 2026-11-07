package com.adel.assistant.data

import android.content.Context

/** یادداشت مستقل پروژه — جدا از توضیحات جلسه در ثبت پروژه */
data class ProjectEventEntry(
    val id: String,
    val projectName: String,
    val employer: String = "",
    val text: String,
    val createdAt: Long = System.currentTimeMillis()
)

object ProjectEventStore {
    private const val CSV = "project_events"

    fun all(context: Context): List<ProjectEventEntry> {
        return CsvStore.readAll(context, CSV).mapNotNull { r ->
            if (r.size < 4) return@mapNotNull null
            try {
                ProjectEventEntry(
                    id = r[0],
                    projectName = r[1],
                    employer = r.getOrElse(2) { "" },
                    text = r[3],
                    createdAt = r.getOrElse(4) { "0" }.toLongOrNull() ?: 0L
                )
            } catch (_: Exception) {
                null
            }
        }
    }

    fun forProject(context: Context, name: String): List<ProjectEventEntry> =
        all(context)
            .filter { it.projectName.trim().equals(name.trim(), ignoreCase = true) }
            .sortedByDescending { it.createdAt }

    fun save(context: Context, entry: ProjectEventEntry) {
        val list = all(context).filterNot { it.id == entry.id } + entry
        writeAll(context, list)
    }

    fun delete(context: Context, id: String) {
        writeAll(context, all(context).filterNot { it.id == id })
    }

    fun nextId(context: Context): String {
        val max = all(context).mapNotNull { it.id.toLongOrNull() }.maxOrNull() ?: 0L
        return (max + 1).toString()
    }

    private fun writeAll(context: Context, list: List<ProjectEventEntry>) {
        CsvStore.overwriteAll(
            context, CSV,
            list.map { listOf(it.id, it.projectName, it.employer, it.text, it.createdAt.toString()) }
        )
    }
}
