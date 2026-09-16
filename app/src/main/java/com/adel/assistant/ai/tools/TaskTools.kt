package com.adel.assistant.ai.tools

import android.content.Context
import com.adel.assistant.data.TaskItem
import com.adel.assistant.data.TaskStore

/**
 * ابزارهای واقعی مدیریت Task برای Agent.
 * مدل فقط درخواست ابزار را مشخص می‌کند؛ تغییر داده توسط این کد کنترل‌شده انجام می‌شود.
 */
object TaskTools {
    const val CREATE = "create_task"
    const val LIST = "list_tasks"
    const val COMPLETE = "complete_task"
    const val DELETE = "delete_task"

    data class Result(
        val success: Boolean,
        val message: String,
        val task: TaskItem? = null
    )

    fun storeFor(category: String): String? = when (category.trim().lowercase()) {
        "tunnel", "تونل" -> "tunnel_tasks"
        "project", "projects", "پروژه" -> "project_tasks"
        else -> null
    }

    fun create(
        context: Context,
        category: String,
        title: String,
        dueDate: String? = null,
        dueTime: String? = null
    ): Result {
        val store = storeFor(category)
            ?: return Result(false, "بخش تسک مشخص نیست؛ تونل یا پروژه را تعیین کن.")
        val cleanTitle = title.trim()
        if (cleanTitle.isBlank()) return Result(false, "عنوان تسک خالی است.")

        val all = TaskStore.load(context, store)
        val task = TaskItem(
            title = cleanTitle,
            dueDate = dueDate?.trim()?.ifBlank { null },
            dueTime = dueTime?.trim()?.ifBlank { null }
        )
        all.add(0, task)
        TaskStore.save(context, store, all)

        val section = if (store == "tunnel_tasks") "تونل" else "پروژه"
        val whenText = buildString {
            task.dueDate?.let { append(" برای $it") }
            task.dueTime?.let { append(" ساعت $it") }
        }
        return Result(true, "تسک «${task.title}» در بخش $section$whenText ثبت شد.", task)
    }

    fun list(context: Context, category: String, onlyOpen: Boolean = true): String {
        val store = storeFor(category) ?: return "بخش تسک مشخص نیست."
        val tasks = TaskStore.load(context, store)
            .let { if (onlyOpen) it.filterNot(TaskItem::completed) else it }
        val section = if (store == "tunnel_tasks") "تونل" else "پروژه"
        if (tasks.isEmpty()) return "تسک بازی برای $section وجود ندارد."
        return buildString {
            appendLine("📋 تسک‌های $section")
            tasks.take(20).forEachIndexed { index, task ->
                append("${index + 1}. ☐ ${task.title}")
                task.dueDate?.let { append(" — $it") }
                task.dueTime?.let { append(" ساعت $it") }
                appendLine()
            }
        }.trimEnd()
    }

    fun complete(context: Context, category: String, title: String): Result {
        val store = storeFor(category) ?: return Result(false, "بخش تسک مشخص نیست.")
        val all = TaskStore.load(context, store)
        val index = all.indexOfFirst { !it.completed && it.title.contains(title.trim(), ignoreCase = true) }
        if (index < 0) return Result(false, "تسک باز با عنوان «${title.trim()}» پیدا نشد.")
        val updated = all[index].copy(completed = true)
        all[index] = updated
        TaskStore.save(context, store, all)
        return Result(true, "تسک «${updated.title}» انجام‌شده شد.", updated)
    }

    fun delete(context: Context, category: String, title: String): Result {
        val store = storeFor(category) ?: return Result(false, "بخش تسک مشخص نیست.")
        val all = TaskStore.load(context, store)
        val index = all.indexOfFirst { it.title.contains(title.trim(), ignoreCase = true) }
        if (index < 0) return Result(false, "تسکی با عنوان «${title.trim()}» پیدا نشد.")
        val removed = all.removeAt(index)
        TaskStore.save(context, store, all)
        return Result(true, "تسک «${removed.title}» حذف شد.", removed)
    }
}
