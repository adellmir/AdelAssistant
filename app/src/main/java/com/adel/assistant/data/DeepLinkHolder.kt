package com.adel.assistant.data

object DeepLinkHolder {
    @Volatile
    var pendingRoute: String? = null
}
