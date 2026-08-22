package com.adel.assistant.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.adel.assistant.ui.theme.Background
import com.adel.assistant.ui.theme.FinancePrimary

private data class FinanceItem(val title: String, val subtitle: String, val route: String)

private val financeItems = listOf(
    FinanceItem("تونل", "کارکرد ماهانه، دریافتی‌ها، خلاصه مطالبات", "finance/tunnel"),
    FinanceItem("آزاد", "صدور فاکتور، ثبت دریافتی هر پروژه", "finance/free"),
    FinanceItem("مطالبات کلی", "جدول ترکیبی مطالبات باز تونل و آزاد", "finance/receivables")
)

@Composable
fun FinanceScreen(
    onBack: () -> Unit,
    onNavigate: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .padding(horizontal = 20.dp)
    ) {
        ScreenTopBar(title = "مالی", color = FinancePrimary, onBack = onBack)
        Spacer(modifier = Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(financeItems) { item ->
                SubMenuRow(
                    title = item.title,
                    subtitle = item.subtitle,
                    color = FinancePrimary,
                    onClick = { onNavigate(item.route) }
                )
            }
        }
    }
}
