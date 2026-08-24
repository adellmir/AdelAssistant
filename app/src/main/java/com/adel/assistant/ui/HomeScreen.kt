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
        Spacer(modifier = Modifier.height(20.dp))

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
                        color = if (selected) section.color.copy(alpha = 0.15f) else Color.Transparent,
                        border = if (!selected) BorderStroke(0.5.dp, Color(0xFFDDDDDD)) else null
                    ) {
                        Text(
                            tab.title,
                            modifier = Modifier
                                .padding(vertical = 10.dp)
                                .fillMaxWidth(),
                            textAlign = TextAlign.Center,
                            color = if (selected) section.color else Color(0xFF6B6B6B),
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
                    color = Color.White
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
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

        // نوار پایین ثابت: راست=مالی، وسط=نقشه‌برداری، چپ=ابزار (به‌ترتیب لیست sections با راست‌چین بودن اپ)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
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
                        tint = if (selected) s.color else Color(0xFFAAAAAA)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        s.title,
                        fontSize = 11.sp,
                        color = if (selected) s.color else Color(0xFFAAAAAA)
                    )
                }
            }
        }
    }
}
