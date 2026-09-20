package com.adel.assistant.data

import android.content.Context
import android.net.Uri

object DxfSaveHelper {
    fun save(context: Context, fileName: String, dxfBody: String): Uri? {
        val name = fileName.trim().ifBlank { "export" }.let {
            if (it.lowercase().endsWith(".dxf")) it else "$it.dxf"
        }
        return FileExport.exportTextToDocuments(context, name, dxfBody, "application/dxf")
    }

    fun queueOpenInMap(dxfBody: String, displayName: String) {
        PendingMapOpen.setDxf(dxfBody, displayName)
    }
}
