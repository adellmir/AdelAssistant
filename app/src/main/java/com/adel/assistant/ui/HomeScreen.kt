package com.adel.assistant.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adel.assistant.data.AppMenu
import com.adel.assistant.data.MenuItem
import com.adel.assistant.ui.theme.Background
import com.adel.assistant.ui.theme.BorderColor
import com.adel.assistant.ui.theme.Surface as SurfaceColor
import com.adel.assistant.ui.theme.SurfaceHigh
import com.adel.assistant.ui.theme.TextMuted
import com.adel.assistant.ui.theme.TextPrimary
import com.adel.assistant.ui.theme.TextSecondary
import java.util.Calendar

private fun timeBasedGreeting(): String {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when (hour) {
        in 5..10 -> "صبح بخیر"
        in 11..13 -> "ظهر بخیر"
        in 14..18 -> "عصر بخیر"
        else -> "شب بخیر"
    }
}

@Composable
fun HomeScreen(onNavigate: (String) -> Unit) {
    val sections = AppMenu.sections
    var sectionIndex by remember { mutableStateOf(1) } // پیش‌فرض: نقشه‌برداری (وسط)
    var tabIndex by remember { mutableStateOf(0) }

    val section = sections[sectionIndex]
    val tabs = section.tabs
    val currentTab = tabs.getOrNull(tabIndex) ?: tabs.first()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)) {
            Text(
                "${timeBasedGreeting()} مهندس پورمیر",
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary
            )
        }

        if (tabs.size > 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                tabs.forEachIndexed { index, tab ->
                    val selected = index == tabIndex
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { tabIndex = index },
                        shape = RoundedCornerShape(12.dp),
                        color = if (selected) section.color.copy(alpha = 0.18f) else Color.Transparent,
                        border = if (!selected) BorderStroke(0.5.dp, BorderColor) else null
                    ) {
                        Text(
                            tab.title,
                            modifier = Modifier
                                .padding(vertical = 10.dp)
                                .fillMaxWidth(),
                            textAlign = TextAlign.Center,
                            color = if (selected) section.color else TextSecondary,
                            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        } else {
            Spacer(modifier = Modifier.height(8.dp))
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(currentTab.items) { item: MenuItem ->
                Surface(
                    modifier = Modifier.clickable { onNavigate(item.route) },
                    shape = RoundedCornerShape(12.dp),
                    color = SurfaceColor
                ) {
                    Column(
                        modifier = Modifier
                            .padding(vertical = 14.dp, horizontal = 4.dp)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(item.icon, contentDescription = item.title, tint = section.color)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            item.title,
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextPrimary
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(SurfaceHigh)
                .padding(vertical = 10.dp)
        ) {
            sections.forEachIndexed { index, s ->
                val selected = index == sectionIndex
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            sectionIndex = index
                            tabIndex = 0
                        },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        s.icon,
                        contentDescription = s.title,
                        tint = if (selected) s.color else TextMuted
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        s.title,
                        fontSize = 11.sp,
                        color = if (selected) s.color else TextMuted
                    )
                }
            }
        }
    }
}
