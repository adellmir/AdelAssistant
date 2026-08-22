package com.adel.assistant.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.adel.assistant.ui.theme.Background
import com.adel.assistant.ui.theme.WorkPrimary

private data class WorkItem(val title: String, val subtitle: String, val route: String)

private val workItems = listOf(
    WorkItem("تونل", "گزارش روزانه، محاسبات مهندسی، یادداشت‌ها", "work/tunnel"),
    WorkItem("آزاد", "ثبت پروژه، تبدیل نقاط به DXF، یادداشت‌ها", "work/free"),
    WorkItem("تسک‌ها", "لیست کارهای انجام‌نشده", "work/tasks")
)

@Composable
fun WorkScreen(
    onBack: () -> Unit,
    onNavigate: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .padding(horizontal = 20.dp)
    ) {
        ScreenTopBar(title = "کاری", color = WorkPrimary, onBack = onBack)
        Spacer(modifier = Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(workItems) { item ->
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
