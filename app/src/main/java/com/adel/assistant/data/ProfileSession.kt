package com.adel.assistant.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object ProfileSession {
    var surfaces by mutableStateOf<List<ProfileSurfaceSlot>>(emptyList())
        private set
    var alignmentRequest by mutableStateOf(false)
        private set
    var alignmentResult by mutableStateOf<List<AlignmentVertex>>(emptyList())
        private set
    var placementDxf by mutableStateOf<String?>(null)
        private set
    var placementName by mutableStateOf("پروفیل.dxf")
        private set
    var alignmentMapModel by mutableStateOf<DxfModel?>(null)
        private set

    fun updateSurfaces(value: List<ProfileSurfaceSlot>) { surfaces = value }
    fun clearSurfaces() { surfaces = emptyList() }

    fun beginAlignment(surfaceSlots: List<ProfileSurfaceSlot>) {
        alignmentRequest = true
        alignmentResult = emptyList()
        alignmentMapModel = buildAlignmentMapModel(surfaceSlots)
    }

    private fun buildAlignmentMapModel(surfaceSlots: List<ProfileSurfaceSlot>): DxfModel? {
        val points = surfaceSlots.flatMap { it.points }
        if (points.isEmpty()) return null
        var minX = Double.POSITIVE_INFINITY
        var minY = Double.POSITIVE_INFINITY
        var maxX = Double.NEGATIVE_INFINITY
        var maxY = Double.NEGATIVE_INFINITY
        points.forEach {
            minX = minOf(minX, it.x); minY = minOf(minY, it.y)
            maxX = maxOf(maxX, it.x); maxY = maxOf(maxY, it.y)
        }
        val span = maxOf(maxX - minX, maxY - minY).coerceAtLeast(1.0)
        val r = (span * 0.0015).coerceIn(0.05, 2.0)
        val textH = (span * 0.006).coerceIn(0.5, 8.0)
        val lines = mutableListOf<DxfLine>()
        val texts = mutableListOf<DxfText>()
        val layers = linkedMapOf<String, DxfLayerInfo>(
            "سطوح پروفیل" to DxfLayerInfo("سطوح پروفیل", 2)
        )
        points.forEachIndexed { i, pt ->
            lines += DxfLine(pt.x - r, pt.y, pt.x + r, pt.y, "سطوح پروفیل", 2)
            lines += DxfLine(pt.x, pt.y - r, pt.x, pt.y + r, "سطوح پروفیل", 2)
            val label = if (pt.id.isNotBlank()) pt.id else "P${i + 1}"
            texts += DxfText(pt.x + r * 1.5, pt.y + r * 1.5, textH, label, "سطوح پروفیل", 2)
        }
        return DxfModel(lines, emptyList(), texts, layers, minX, minY, maxX, maxY)
    }

    fun beginAlignment() { alignmentRequest = true }
    fun finishAlignment(vertices: List<AlignmentVertex>) {
        alignmentResult = vertices
        alignmentRequest = false
        alignmentMapModel = null
    }

    fun consumeAlignmentMapModel(): DxfModel? {
        val model = alignmentMapModel
        alignmentMapModel = null
        return model
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
