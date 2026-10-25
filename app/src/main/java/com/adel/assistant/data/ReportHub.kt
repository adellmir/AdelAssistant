package com.adel.assistant.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/**
 * فضای یکپارچهٔ گزارش‌ها:
 * پیش‌نمایش متن → اشتراک متن/فایل → ذخیره.
 * صفحه‌ها فقط محتوای گزارش را می‌سازند؛ اشتراک و ذخیره از اینجا.
 */
object ReportHub {

    /** ارقام فارسی/عربی → انگلیسی برای خروجی یکسان */
    fun digitsEn(s: String): String = PointConverter.normalizeDigits(s)

    fun formatMoney(toman: Long): String =
        "%,d".format(java.util.Locale.US, toman).replace(',', '٬')

    fun shareText(context: Context, text: String, title: String = "گزارش") {
        val body = text.trim()
        if (body.isEmpty()) return
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, body)
        }
        try {
            context.startActivity(Intent.createChooser(intent, title))
        } catch (_: Exception) {
        }
    }

    fun shareUri(
        context: Context,
        uri: Uri,
        mimeType: String,
        title: String = "اشتراک گزارش"
    ) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            context.startActivity(Intent.createChooser(intent, title))
        } catch (_: Exception) {
        }
    }

    /**
     * بایت را در cache می‌نویسد و با FileProvider اشتراک می‌گذارد.
     * برای PDF/XLSX/DXF که از Documents مستقیم share نمی‌شوند.
     */
    fun shareBytes(
        context: Context,
        fileName: String,
        bytes: ByteArray,
        mimeType: String,
        title: String = "اشتراک گزارش"
    ) {
        try {
            val cache = File(context.cacheDir, fileName)
            cache.writeBytes(bytes)
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                cache
            )
            shareUri(context, uri, mimeType, title)
        } catch (_: Exception) {
        }
    }

    fun shareTextFile(
        context: Context,
        fileName: String,
        text: String,
        mimeType: String,
        title: String = "اشتراک"
    ) {
        shareBytes(context, fileName, text.toByteArray(Charsets.UTF_8), mimeType, title)
    }

    fun saveTextReport(
        context: Context,
        fileName: String,
        text: String
    ): Uri? {
        val name = if (fileName.contains('.')) fileName else "$fileName.txt"
        return ExportHub.exportText(context, name, text, "text/plain")
    }

    fun saveAndShareText(
        context: Context,
        fileName: String,
        text: String,
        title: String = "گزارش"
    ): Uri? {
        val uri = saveTextReport(context, fileName, text)
        shareText(context, text, title)
        return uri
    }
}
