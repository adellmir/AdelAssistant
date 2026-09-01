package com.adel.assistant.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore

object FileExport {
    /** خارج کردن یک فایل متنی (CSV/TXT) به پوشه‌ی عمومی Documents/AdelAssistant گوشی */
    fun exportTextToDocuments(context: Context, fileName: String, text: String): Uri? {
        val bytes = text.toByteArray(Charsets.UTF_8)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS + "/AdelAssistant")
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Files.getContentUri("external"), values)
            uri?.let { resolver.openOutputStream(it)?.use { out -> out.write(bytes) } }
            return uri
        } else {
            val dir = java.io.File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "AdelAssistant")
            if (!dir.exists()) dir.mkdirs()
            val f = java.io.File(dir, fileName)
            f.writeBytes(bytes)
            return Uri.fromFile(f)
        }
    }

    fun readAsCsvText(context: Context, csvName: String): String {
        val f = CsvStore.getFile(context, csvName)
        return if (f.exists()) f.readText() else ""
    }
}
