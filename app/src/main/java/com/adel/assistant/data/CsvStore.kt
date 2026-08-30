package com.adel.assistant.data

import android.content.Context
import java.io.File

object CsvStore {

    private fun file(context: Context, name: String): File {
        val dir = File(context.filesDir, "data")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "$name.csv")
    }

    fun appendRow(context: Context, name: String, columns: List<String>) {
        val f = file(context, name)
        val safeColumns = columns.map { it.replace(",", "،").replace("\n", " ") }
        f.appendText(safeColumns.joinToString(",") + "\n")
    }

    fun readAll(context: Context, name: String): List<List<String>> {
        val f = file(context, name)
        if (!f.exists()) return emptyList()
        return f.readLines().filter { it.isNotBlank() }.map { it.split(",") }
    }

    /** بازنویسی کامل فایل با یک لیست جدید از ردیف‌ها (برای ویرایش/حذف رکوردهای گذشته) */
    fun overwriteAll(context: Context, name: String, rows: List<List<String>>) {
        val f = file(context, name)
        val text = rows.joinToString("\n") { row ->
            row.map { it.replace(",", "،").replace("\n", " ") }.joinToString(",")
        }
        f.writeText(if (text.isBlank()) "" else text + "\n")
    }

    /** جایگزینی محتوای فایل با یک فایل خارجی که کاربر آپلود/انتخاب کرده (ایمپورت) */
    fun importRawText(context: Context, name: String, rawText: String) {
        val f = file(context, name)
        f.writeText(rawText)
    }

    fun getFile(context: Context, name: String): File = file(context, name)
}
