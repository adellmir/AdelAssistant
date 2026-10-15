package com.adel.assistant.data

import android.content.Context
import android.net.Uri

/**
 * فضای یکپارچهٔ خروجی فایل.
 * همهٔ Exportها باید از اینجا بروند تا مسیر Documents و نام فایل یکسان بماند.
 */
object ExportHub {

    fun exportText(
        context: Context,
        fileName: String,
        text: String,
        mimeType: String = "text/plain"
    ): Uri? = FileExport.exportTextToDocuments(context, fileName, text, mimeType)

    fun exportBytes(
        context: Context,
        fileName: String,
        bytes: ByteArray,
        mimeType: String
    ): Uri? = FileExport.exportBytesToDocuments(context, fileName, bytes, mimeType)

    fun exportPoints(
        context: Context,
        points: List<SurveyPoint>,
        fileName: String,
        extension: String
    ): Uri? {
        val body = PointImport.write(points, extension)
        val name = if (fileName.contains('.')) fileName else "$fileName.$extension"
        val mime = when (extension.lowercase()) {
            "csv" -> "text/csv"
            "kml" -> "application/vnd.google-earth.kml+xml"
            "dxf" -> "application/dxf"
            else -> "text/plain"
        }
        return exportText(context, name, body, mime)
    }

    fun exportGsiPoints(
        context: Context,
        points: List<GsiPoint>,
        fileName: String,
        extension: String
    ): Uri? {
        val body = PointImport.writeGsiList(points, extension)
        val name = if (fileName.contains('.')) fileName else "$fileName.$extension"
        return exportText(context, name, body)
    }
}
