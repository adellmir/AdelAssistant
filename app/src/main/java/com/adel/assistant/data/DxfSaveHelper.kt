package com.adel.assistant.data

import android.content.Context
import android.net.Uri

object DxfSaveHelper {
    fun save(context: Context, fileName: String, dxfBody: String): Uri? {
        val name = fileName.trim().ifBlank { "export" }.let {
            if (it.lowercase().endsWith(".dxf")) it else "$it.dxf"
        }
        return ExportHub.exportDxf(context, name, dxfBody)
    }

    fun queueOpenInMap(dxfBody: String, displayName: String) {
        PendingMapOpen.setDxf(dxfBody, displayName)
    }
}
