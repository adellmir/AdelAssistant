package com.adel.assistant.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Engineering
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.adel.assistant.ui.theme.*

@Composable
fun HomeScreen(
    onOpenWork: () -> Unit,
    onOpenFinance: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .padding(20.dp)
    ) {
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            "دستیار عادل",
            style = MaterialTheme.typography.headlineMedium,
            color = TextPrimary
        )
        Text(
            "مهندس نقشه‌برداری",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )

        Spacer(modifier = Modifier.height(40.dp))

        MainSectionCard(
            title = "کاری",
            subtitle = "تونل، آزاد و تسک‌ها",
            icon = Icons.Filled.Engineering,
            color = WorkPrimary,
            colorLight = WorkPrimaryLight,
            onClick = onOpenWork
        )

        Spacer(modifier = Modifier.height(16.dp))

        MainSectionCard(
            title = "مالی",
            subtitle = "تونل، آزاد و مطالبات کلی",
            icon = Icons.Filled.AttachMoney,
            color = FinancePrimary,
            colorLight = FinancePrimaryLight,
            onClick = onOpenFinance
        )
    }
}

@Preview(showBackground = true, locale = "fa")
@Composable
private fun HomeScreenPreview() {
    AdelAssistantTheme {
        HomeScreen(onOpenWork = {}, onOpenFinance = {})
    }
}
