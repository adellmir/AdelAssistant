package com.adel.assistant.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object ProfileSession {
    var alignmentRequest by mutableStateOf(false)
        private set
    var alignmentResult by mutableStateOf<List<AlignmentVertex>>(emptyList())
        private set
    var placementDxf by mutableStateOf<String?>(null)
        private set
    var placementName by mutableStateOf("پروفیل.dxf")
        private set

    fun beginAlignment() { alignmentRequest = true }
    fun finishAlignment(vertices: List<AlignmentVertex>) {
        alignmentResult = vertices
        alignmentRequest = false
    }
    fun clearAlignmentResult() { alignmentResult = emptyList() }

    fun beginPlacement(dxf: String, name: String = "پروفیل.dxf") {
        placementDxf = dxf
        placementName = name
    }
    fun consumePlacement(): Pair<String, String>? {
        val d = placementDxf ?: return null
        val n = placementName
        placementDxf = null
        placementName = "پروفیل.dxf"
        return d to n
    }
}
