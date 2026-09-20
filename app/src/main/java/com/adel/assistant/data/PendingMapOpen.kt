package com.adel.assistant.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object PendingMapOpen {
    var pendingDxfText by mutableStateOf<String?>(null)
        private set
    var pendingName by mutableStateOf<String?>(null)
        private set

    fun setDxf(text: String, name: String = "export.dxf") {
        pendingDxfText = text
        pendingName = name
    }

    fun set(text: String, name: String) = setDxf(text, name)

    fun consume(): Pair<String, String>? {
        val t = pendingDxfText ?: return null
        val n = pendingName ?: "export.dxf"
        pendingDxfText = null
        pendingName = null
        return t to n
    }
}
