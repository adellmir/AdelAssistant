package com.adel.assistant.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.adel.assistant.ui.theme.Background
import com.adel.assistant.ui.theme.WorkPrimary

private data class FreeItem(val title: String, val subtitle: String, val route: String)

private val freeItems = listOf(
    FreeItem(
        title = "تبدیل نقاط به DXF",
        subtitle = "فایل نقاط → نقشه DXF (خط + نقطه + لایه)",
        route = "work/free/dxf"
    )
    // بعداً: ثبت پروژه، یادداشت‌ها و ...
)

@Composable
fun FreeScreen(
    onBack: () -> Unit,
    onNavigate: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .padding(horizontal = 20.dp)
    ) {
        ScreenTopBar(title = "آزاد (کاری)", color = WorkPrimary, onBack = onBack)
        Spacer(modifier = Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(freeItems) { item ->
                SubMenuRow(
                    title = item.title,
                    subtitle = item.subtitle,
                    color = WorkPrimary,
                    onClick = { onNavigate(item.route) }
                )
            }
        }
    }
}
