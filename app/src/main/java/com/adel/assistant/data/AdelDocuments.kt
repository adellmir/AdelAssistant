package com.adel.assistant.data

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.result.contract.ActivityResultContract

/**
 * مسیر پیش‌فرض انتخاب فایل: Documents/AdelAssistant
 * (همان جایی که خروجی‌های برنامه ذخیره می‌شوند)
 */
object AdelDocuments {

    /** URI سند اولیه برای SAF */
    fun initialUri(): Uri {
        return DocumentsContract.buildDocumentUri(
            "com.android.externalstorage.documents",
            "primary:Documents/AdelAssistant"
        )
    }

    /** Intent باز کردن فایل با شروع از پوشهٔ AdelAssistant */
    fun openDocumentIntent(vararg mimeTypes: String): Intent {
        val types = if (mimeTypes.isEmpty()) arrayOf("*/*") else mimeTypes
        return Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            if (types.size == 1) {
                type = types[0]
            } else {
                type = "*/*"
                putExtra(Intent.EXTRA_MIME_TYPES, types)
            }
            putExtra(DocumentsContract.EXTRA_INITIAL_URI, initialUri())
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
    }

    /**
     * جایگزین ActivityResultContracts.OpenDocument —
     * همیشه از Documents/AdelAssistant شروع می‌کند.
     */
    class OpenDocumentContract : ActivityResultContract<Array<String>, Uri?>() {
        override fun createIntent(context: Context, input: Array<String>): Intent =
            openDocumentIntent(*input)

        override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
            intent?.takeIf { resultCode == Activity.RESULT_OK }?.data
    }
}
