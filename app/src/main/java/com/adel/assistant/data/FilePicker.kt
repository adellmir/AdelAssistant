package com.adel.assistant.data

import android.net.Uri
import androidx.activity.result.contract.ActivityResultContract

/**
 * فضای یکپارچهٔ انتخاب فایل.
 * همهٔ صفحه‌ها باید از [contract] و MIMEهای اینجا استفاده کنند
 * تا همیشه از Documents/AdelAssistant شروع شود و نوع فایل یکسان باشد.
 */
object FilePicker {

    /** قرارداد SAF — شروع از Documents/AdelAssistant */
    fun contract(): ActivityResultContract<Array<String>, Uri?> =
        AdelDocuments.OpenDocumentContract()

    /** نقاط نقشه‌برداری: txt dat gsi kml csv … */
    val POINTS: Array<String> = arrayOf(
        "*/*",
        "text/plain",
        "text/csv",
        "application/octet-stream",
        "application/vnd.google-earth.kml+xml",
        "application/vnd.google-earth.kmz"
    )

    /** هر فایل */
    val ANY: Array<String> = arrayOf("*/*")

    /** متن / CSV */
    val TEXT_CSV: Array<String> = arrayOf("text/*", "text/csv", "text/plain", "*/*")

    /** DXF / CAD */
    val DXF: Array<String> = arrayOf("*/*", "application/dxf", "text/*", "application/octet-stream")

    /** پشتیبان / زیپ */
    val BACKUP: Array<String> = arrayOf("*/*", "application/zip", "application/octet-stream")
}
