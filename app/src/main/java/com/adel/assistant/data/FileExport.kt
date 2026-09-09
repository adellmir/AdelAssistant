package com.adel.assistant.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore

object FileExport {
    /** خارج کردن فایل متنی (CSV/TXT/DXF) به Documents/AdelAssistant */
    fun exportTextToDocuments(context: Context, fileName: String, text: String, mimeType: String = "text/plain"): Uri? {
        val bytes = text.toByteArray(Charsets.UTF_8)
        return exportBytesToDocuments(context, fileName, bytes, mimeType)
    }

    fun exportBytesToDocuments(context: Context, fileName: String, bytes: ByteArray, mimeType: String): Uri? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS + "/AdelAssistant")
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Files.getContentUri("external"), values)
                uri?.let { resolver.openOutputStream(it)?.use { out -> out.write(bytes) } }
                uri
            } else {
                val dir = java.io.File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "AdelAssistant")
                if (!dir.exists()) dir.mkdirs()
                val f = java.io.File(dir, fileName)
                f.writeBytes(bytes)
                Uri.fromFile(f)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun readAsCsvText(context: Context, csvName: String): String {
        val f = CsvStore.getFile(context, csvName)
        return if (f.exists()) f.readText() else ""
    }
}
