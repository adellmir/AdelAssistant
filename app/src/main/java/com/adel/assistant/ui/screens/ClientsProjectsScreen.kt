package com.adel.assistant.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adel.assistant.data.ProjectEntry
import com.adel.assistant.data.ProjectStore
import com.adel.assistant.data.formatMoney
import com.adel.assistant.ui.ScreenTopBar
import com.adel.assistant.ui.theme.Background
import com.adel.assistant.ui.theme.Surface as SurfaceColor
import com.adel.assistant.ui.theme.TextPrimary
import com.adel.assistant.ui.theme.TextSecondary

/**
 * کارفرمایان و پروژه‌ها — پروفایل از پایگاه ثبت پروژه
 */
@Composable
fun ClientsProjectsScreen(color: Color, onBack: () -> Unit) {
    val context = LocalContext.current
    var searchProject by remember { mutableStateOf("") }
    var searchEmployer by remember { mutableStateOf("") }
    var tab by remember { mutableStateOf(0) } // 0 پروژه 1 کارفرما
    var showList by remember { mutableStateOf(false) }
    var tick by remember { mutableStateOf(0) }

    var editEmployerOld by remember { mutableStateOf<String?>(null) }
    var editEmpName by remember { mutableStateOf("") }
    var editEmpPhone by remember { mutableStateOf("") }

    fun refresh() { tick++ }

    val employers = remember(tick, searchEmployer, showList, tab) {
        if (!showList && searchEmployer.isBlank() && searchProject.isBlank()) emptyList()
        else if (tab == 1) ProjectStore.employerProfiles(context, searchEmployer)
        else emptyList()
    }
    val projects = remember(tick, searchProject, showList, tab) {
        if (!showList && searchEmployer.isBlank() && searchProject.isBlank()) emptyList()
        else if (tab == 0) ProjectStore.projectProfiles(context, searchProject)
        else emptyList()
    }

    fun doSearch() {
        showList = true
        refresh()
    }

    fun call(phone: String) {
        if (phone.isBlank()) return
        try {
            context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone")))
        } catch (_: Exception) {
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Background)
            .padding(horizontal = 16.dp)
    ) {
        ScreenTopBar(title = "کارفرمایان و پروژه‌ها", color = color, onBack = onBack)

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = searchProject,
                onValueChange = { searchProject = it },
                label = { Text("جستجو پروژه") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = searchEmployer,
                onValueChange = { searchEmployer = it },
                label = { Text("جستجو کارفرما") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(6.dp))
        Button(
            onClick = { doSearch() },
            colors = ButtonDefaults.buttonColors(containerColor = color),
            modifier = Modifier.fillMaxWidth()
        ) { Text("جستجو") }

        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            listOf("پروژه", "کارفرمایان").forEachIndexed { i, label ->
                val selected = tab == i
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            tab = i
                            showList = true
                            refresh()
                        },
                    shape = RoundedCornerShape(10.dp),
                    color = if (selected) color.copy(alpha = 0.2f) else Color.Transparent
                ) {
                    Text(
                        label,
                        modifier = Modifier
                            .padding(vertical = 12.dp)
                            .fillMaxWidth(),
                        color = if (selected) color else TextSecondary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        if (!showList) {
            Text(
                "سربرگ را بزن یا جستجو کن تا لیست نمایش داده شود",
                color = TextSecondary,
                style = MaterialTheme.typography.bodySmall
            )
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (tab == 1) {
                items(employers, key = { it.employer }) { e ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = SurfaceColor,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    e.employer,
                                    modifier = Modifier.weight(1f),
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                IconButton(onClick = { call(e.phone) }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Filled.Call, contentDescription = "تماس", tint = color)
                                }
                                IconButton(onClick = {
                                    editEmployerOld = e.employer
                                    editEmpName = e.employer
                                    editEmpPhone = e.phone
                                }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Filled.Edit, contentDescription = "ویرایش", tint = TextSecondary)
                                }
                            }
                            Text(
                                "پروژه: ${e.projectCount}  |  دریافتی: ${formatMoney(e.totalReceived)}  |  مطالبات: ${formatMoney(e.totalClaims)}",
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                            e.sessions.take(8).forEach { s ->
                                Text(
                                    "• ${s.name} | جلسه ${s.day}/${s.month}/${s.year} | ${formatMoney(s.amount)}",
                                    color = TextPrimary,
                                    fontSize = 12.sp
                                )
                            }
                            if (e.sessions.size > 8) {
                                Text("… و ${e.sessions.size - 8} مورد دیگر", color = TextSecondary, fontSize = 11.sp)
                            }
                        }
                    }
                }
            } else {
                items(projects, key = { it.name }) { p ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = SurfaceColor,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(p.name, fontWeight = FontWeight.Bold, color = TextPrimary)
                                    Text(p.employer, color = TextSecondary, fontSize = 12.sp)
                                }
                                IconButton(onClick = { call(p.phone) }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Filled.Call, contentDescription = "تماس", tint = color)
                                }
                            }
                            Text(
                                "جلسات: ${p.sessionCount}  |  دریافتی: ${formatMoney(p.totalReceived)}  |  مطالبات: ${formatMoney(p.totalClaims)}",
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                            p.sessions.forEach { s ->
                                SessionSettleRow(s, color) {
                                    if (ProjectStore.isFullySettled(s)) {
                                        ProjectStore.markUnsettled(context, s.row)
                                    } else {
                                        ProjectStore.markSettled(context, s.row)
                                    }
                                    refresh()
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (editEmployerOld != null) {
        AlertDialog(
            onDismissRequest = { editEmployerOld = null },
            title = { Text("ویرایش کارفرما") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = editEmpName,
                        onValueChange = { editEmpName = it },
                        label = { Text("نام کارفرما") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editEmpPhone,
                        onValueChange = { editEmpPhone = it },
                        label = { Text("شماره تماس") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    ProjectStore.updateEmployerInfo(
                        context,
                        editEmployerOld.orEmpty(),
                        editEmpName,
                        editEmpPhone
                    )
                    editEmployerOld = null
                    refresh()
                }) { Text("ذخیره") }
            },
            dismissButton = {
                TextButton(onClick = { editEmployerOld = null }) { Text("لغو") }
            }
        )
    }
}

@Composable
private fun SessionSettleRow(s: ProjectEntry, color: Color, onToggle: () -> Unit) {
    val settled = ProjectStore.isFullySettled(s)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Checkbox(
            checked = settled,
            onCheckedChange = { onToggle() },
            colors = CheckboxDefaults.colors(checkedColor = color)
        )
        Text(
            "${s.day}/${s.month}/${s.year} — ${formatMoney(s.amount)}" +
                if (settled) " (تسویه)" else "",
            color = TextPrimary,
            fontSize = 12.sp,
            modifier = Modifier.weight(1f)
        )
    }
}
