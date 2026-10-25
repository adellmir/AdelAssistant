package com.adel.assistant.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adel.assistant.data.AppearanceMode
import com.adel.assistant.data.AppearanceStore
import com.adel.assistant.ui.ScreenTopBar
import com.adel.assistant.ui.appearanceCardColor
import com.adel.assistant.ui.appearanceScreenBg
import com.adel.assistant.ui.theme.TextPrimary
import com.adel.assistant.ui.theme.TextSecondary

/**
 * تنظیم نمای برنامه (پس‌زمینه + کاشی شیشه‌ای)
 * مسیر: ابزار → نما
 */
@Composable
fun AppearanceScreen(color: androidx.compose.ui.graphics.Color, onBack: () -> Unit) {
    val context = LocalContext.current
    AppearanceStore.ensureLoaded(context)
    var status by remember { mutableStateOf("") }
    val mode = AppearanceStore.modeState

    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            if (AppearanceStore.saveCustomFromUri(context, uri)) {
                AppearanceStore.setMode(context, AppearanceMode.Custom)
                status = "تصویر ذخیره شد — حالت «تصویر من» فعال است"
            } else {
                status = "خطا در ذخیره تصویر"
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(appearanceScreenBg())
            .padding(horizontal = 20.dp)
    ) {
        ScreenTopBar(title = "نمای برنامه", color = color, onBack = onBack)
        Spacer(Modifier.height(12.dp))
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "پس‌زمینه و ظاهر کاشی‌ها را انتخاب کنید. روی برنامه‌های نقشه اعمال نمی‌شود.",
                color = TextSecondary,
                fontSize = 13.sp
            )
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = appearanceCardColor(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(
                        AppearanceMode.Simple to "۱. ساده — ظاهر فعلی برنامه",
                        AppearanceMode.Iran to "۲. ایران — تصویر ماهواره‌ای محو",
                        AppearanceMode.Topo to "۳. توپوگرافی — نقشه توپو",
                        AppearanceMode.Custom to "۴. تصویر من — از گالری گوشی"
                    ).forEach { (m, label) ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { AppearanceStore.setMode(context, m) }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = mode == m,
                                onClick = { AppearanceStore.setMode(context, m) },
                                colors = RadioButtonDefaults.colors(selectedColor = color)
                            )
                            Text(
                                label,
                                color = TextPrimary,
                                fontWeight = if (mode == m) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 14.sp,
                                modifier = Modifier.padding(start = 4.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { pickImage.launch("image/*") },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (AppearanceStore.hasCustom(context)) "تعویض تصویر پس‌زمینه"
                            else "انتخاب تصویر از گالری"
                        )
                    }
                }
            }
            Text(
                "پس از انتخاب، به صفحه اصلی برگردید تا پس‌زمینه را ببینید.",
                color = TextSecondary,
                fontSize = 12.sp
            )
            if (status.isNotBlank()) {
                Text(status, color = TextPrimary, fontSize = 13.sp)
            }
        }
    }
}
