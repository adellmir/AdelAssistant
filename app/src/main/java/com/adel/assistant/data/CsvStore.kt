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

    fun getFile(context: Context, name: String): File = file(context, name)
}
