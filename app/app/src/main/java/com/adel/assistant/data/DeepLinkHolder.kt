package com.adel.assistant.data

/**
 * مسیر درخواستی از ویجت / اعلان.
 * tick باعث می‌شود LaunchedEffect در ناوبری دوباره اجرا شود.
 */
object DeepLinkHolder {
    @Volatile
    var pendingRoute: String? = null

    @Volatile
    var tick: Int = 0
}
