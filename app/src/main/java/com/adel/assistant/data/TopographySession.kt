package com.adel.assistant.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object TopographySession {
    var points by mutableStateOf<List<VolPoint>>(emptyList())
        private set

    fun updatePoints(value: List<VolPoint>) { points = value }
    fun clear() { points = emptyList() }
}
