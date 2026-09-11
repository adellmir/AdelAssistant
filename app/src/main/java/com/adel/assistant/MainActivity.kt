package com.adel.assistant

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.adel.assistant.data.DeepLinkHolder
import com.adel.assistant.navigation.AppNavigation
import com.adel.assistant.ui.theme.AdelAssistantTheme
import com.adel.assistant.widget.AdelWidgetProvider

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        captureRoute(intent)
        setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                AdelAssistantTheme {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        AppNavigation()
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        captureRoute(intent)
    }

    override fun onResume() {
        super.onResume()
        // به‌روزرسانی ویجت وقتی به اپ برمی‌گردیم
        try {
            AdelWidgetProvider.refreshAll(this)
        } catch (_: Exception) {
        }
    }

    private fun captureRoute(intent: Intent?) {
        val route = intent?.getStringExtra("open_route")
        if (!route.isNullOrBlank()) {
            DeepLinkHolder.pendingRoute = route
            intent?.removeExtra("open_route")
        }
    }
}
