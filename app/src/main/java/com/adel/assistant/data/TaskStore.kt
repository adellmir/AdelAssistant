package com.adel.assistant.data

import android.content.Context
import java.io.File

data class TaskItem(
    val title: String,
    val completed: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val dueDate: String? = null,
    val dueTime: String? = null
)

object TaskStore {
    private fun file(context: Context, name: String): File {
        val dir = File(context.filesDir, "data")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "$name.csv")
    }

    fun load(context: Context, name: String): MutableList<TaskItem> {
        val f = file(context, name)
        if (!f.exists()) return mutableListOf()
        return f.readLines().filter { it.isNotBlank() }.mapNotNull { line ->
            val parts = line.split(",", limit = 5)
            if (parts.size < 2) null else TaskItem(
                title = parts[0].replace("،", ","),
                completed = parts[1].trim().equals("true", true) || parts[1].trim() == "1",
                createdAt = parts.getOrNull(2)?.toLongOrNull() ?: System.currentTimeMillis(),
                dueDate = parts.getOrNull(3)?.trim()?.ifBlank { null },
                dueTime = parts.getOrNull(4)?.trim()?.ifBlank { null }
            )
        }.toMutableList()
    }

    fun save(context: Context, name: String, tasks: List<TaskItem>) {
        val text = tasks.joinToString("\n") {
            listOf(
                it.title.replace(",", "،").replace("\n", " "),
                it.completed,
                it.createdAt,
                it.dueDate.orEmpty().replace(",", " "),
                it.dueTime.orEmpty().replace(",", " ")
            ).joinToString(",")
        }
        file(context, name).writeText(if (text.isBlank()) "" else "$text\n")
    }

    fun raw(context: Context, name: String): String {
        val f = file(context, name)
        return if (f.exists()) f.readText() else ""
    }

    fun importRaw(context: Context, name: String, raw: String): MutableList<TaskItem> {
        file(context, name).writeText(raw)
        return load(context, name)
    }
}
