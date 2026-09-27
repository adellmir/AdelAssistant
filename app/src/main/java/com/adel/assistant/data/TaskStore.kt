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
    private val SAFE_NAME = Regex("^[A-Za-z0-9_-]+$")

    private fun file(context: Context, name: String): File {
        require(SAFE_NAME.matches(name)) { "Invalid task store name" }
        val dir = File(context.filesDir, "data")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "$name.csv")
    }

    fun load(context: Context, name: String): MutableList<TaskItem> {
        val f = file(context, name)
        if (!f.exists()) return mutableListOf()
        val lines = f.readLines().filter { it.isNotBlank() }
        var needsMigration = false
        val migrationBase = f.lastModified().takeIf { it > 0L } ?: System.currentTimeMillis()
        val tasks = lines.mapIndexedNotNull { index, line ->
            val parts = line.split(",", limit = 5)
            if (parts.size < 2) null else {
                val parsedCreatedAt = parts.getOrNull(2)?.toLongOrNull()
                if (parsedCreatedAt == null) needsMigration = true
                TaskItem(
                    title = parts[0].replace("،", ","),
                    completed = parts[1].trim().equals("true", true) || parts[1].trim() == "1",
                    createdAt = parsedCreatedAt ?: (migrationBase + index),
                    dueDate = parts.getOrNull(3)?.trim()?.ifBlank { null },
                    dueTime = parts.getOrNull(4)?.trim()?.ifBlank { null }
                )
            }
        }.toMutableList()
        if (needsMigration) save(context, name, tasks)
        return tasks
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
