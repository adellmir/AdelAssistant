package com.adel.assistant.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * مسیر درخواستی از ویجت / اعلان.
 */
object DeepLinkHolder {
    var pendingRoute by mutableStateOf<String?>(null)
        private set

    fun setRoute(route: String?) {
        pendingRoute = route
    }
}
