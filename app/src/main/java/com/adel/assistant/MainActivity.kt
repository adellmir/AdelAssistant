package com.adel.assistant

import android.content.Intent
import android.net.Uri
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
import com.adel.assistant.data.PendingMapOpen
import com.adel.assistant.navigation.AppNavigation
import com.adel.assistant.ui.theme.AdelAssistantTheme
import com.adel.assistant.widget.AdelWidgetProvider

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        captureRoute(intent)
        captureOpenFile(intent)
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
        captureOpenFile(intent)
    }

    override fun onResume() {
        super.onResume()
        try {
            AdelWidgetProvider.refreshAll(this)
        } catch (_: Exception) {
        }
    }

    private fun captureRoute(intent: Intent?) {
        if (intent == null) return
        val route = intent.getStringExtra("open_route")
            ?: intent.data?.getQueryParameter("route")
        if (!route.isNullOrBlank()) {
            DeepLinkHolder.setRoute(route)
            intent.removeExtra("open_route")
        }
    }

    /** باز کردن DXF/KML/… از واتساپ، فایل‌منیجر، Share */
    private fun captureOpenFile(intent: Intent?) {
        if (intent == null) return
        val action = intent.action ?: return
        val uri: Uri? = when (action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
                    ?: intent.clipData?.getItemAt(0)?.uri
            }
            else -> null
        } ?: return

        val name = uri.lastPathSegment?.substringAfterLast('/') ?: "opened.dxf"
        val lower = name.lowercase()
        val ok = lower.endsWith(".dxf") || lower.endsWith(".dwg") ||
            lower.endsWith(".kml") || lower.endsWith(".csv") ||
            lower.endsWith(".txt") || lower.endsWith(".dat") ||
            (intent.type?.contains("dxf") == true) ||
            (intent.type?.contains("dwg") == true) ||
            (intent.type == "application/octet-stream") ||
            (intent.type == "text/plain")
        if (!ok && action == Intent.ACTION_VIEW && intent.type.isNullOrBlank()) {
            // بدون mime — با پسوند قضاوت کردیم
        }
        if (!ok && !(lower.contains(".") && (lower.endsWith("dxf") || lower.endsWith("dwg")))) {
            // اگر پسوند مشخص نیست ولی VIEW است، سعی می‌کنیم بخوانیم
            if (action != Intent.ACTION_VIEW && action != Intent.ACTION_SEND) return
        }

        try {
            contentResolver.openInputStream(uri)?.use { input ->
                val bytes = input.readBytes()
                // DWG باینری را خام نمی‌خوانیم به‌عنوان DXF متنی
                val isBinaryDwg = lower.endsWith(".dwg") ||
                    (bytes.size >= 6 && String(bytes, 0, 6) == "AC101")
                if (isBinaryDwg) {
                    // فقط هشدار از طریق نام — کاربر باید DXF بدهد
                    PendingMapOpen.setDxf(
                        "0\nSECTION\n2\nHEADER\n0\nENDSEC\n0\nSECTION\n2\nENTITIES\n0\nTEXT\n8\n0\n10\n0\n20\n0\n40\n2\n1\nDWG binary — convert to DXF first\n0\nENDSEC\n0\nEOF\n",
                        "UNSUPPORTED_DWG_$name"
                    )
                    DeepLinkHolder.setRoute("tool/dxf_preview")
                    return
                }
                val text = bytes.toString(Charsets.UTF_8)
                PendingMapOpen.setDxf(text, name)
                DeepLinkHolder.setRoute("tool/dxf_preview")
            }
        } catch (_: Exception) {
        }
    }
}
