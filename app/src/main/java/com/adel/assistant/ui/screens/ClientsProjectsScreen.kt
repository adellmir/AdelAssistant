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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.NearMe
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
import com.adel.assistant.data.openNeshanNav
import com.adel.assistant.ui.PendingProjectEdit
import com.adel.assistant.ui.ScreenTopBar
import com.adel.assistant.ui.theme.Background
import com.adel.assistant.ui.theme.Surface as SurfaceColor
import com.adel.assistant.ui.theme.TextPrimary
import com.adel.assistant.ui.theme.TextSecondary

private fun moneyM(v: Double): String {
    return if (kotlin.math.abs(v - v.toLong().toDouble()) < 1e-9)
        String.format(java.util.Locale.US, "%,.0f م", v)
    else
        String.format(java.util.Locale.US, "%,.1f م", v)
}

@Composable
fun ClientsProjectsScreen(
    color: Color,
    onBack: () -> Unit,
    onEditProject: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var searchProject by remember { mutableStateOf("") }
    var searchEmployer by remember { mutableStateOf("") }
    var tab by remember { mutableStateOf(0) }
    var tick by remember { mutableStateOf(0) }

    var editEmployerOld by remember { mutableStateOf<String?>(null) }
    var editEmpName by remember { mutableStateOf("") }
    var editEmpPhone by remember { mutableStateOf("") }

    var manageProject by remember { mutableStateOf<ProjectStore.ProjectProfile?>(null) }
    var showLocFor by remember { mutableStateOf<String?>(null) } // project name for location

    fun refresh() { tick++ }

    val employers = remember(tick, searchEmployer, tab) {
        if (tab == 1) ProjectStore.employerProfiles(context, searchEmployer) else emptyList()
    }
    val projects = remember(tick, searchProject, tab) {
        if (tab == 0) ProjectStore.projectProfiles(context, searchProject) else emptyList()
    }

    fun call(phone: String) {
        if (phone.isBlank()) return
        try {
            context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone")))
        } catch (_: Exception) { }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        ScreenTopBar(title = "کارفرمایان و پروژه‌ها", color = color, onBack = onBack)

        TabRow(selectedTabIndex = tab, containerColor = SurfaceColor) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("پروژه‌ها") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("کارفرمایان") })
        }

        Row(
            Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (tab == 0) {
                OutlinedTextField(
                    value = searchProject,
                    onValueChange = { searchProject = it },
                    label = { Text("جستجوی پروژه") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            } else {
                OutlinedTextField(
                    value = searchEmployer,
                    onValueChange = { searchEmployer = it },
                    label = { Text("جستجوی کارفرما") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            if (tab == 1) {
                items(employers, key = { it.employer }) { e ->
                    Surface(shape = RoundedCornerShape(12.dp), color = SurfaceColor, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(e.employer, Modifier.weight(1f), fontWeight = FontWeight.Bold, color = TextPrimary)
                                IconButton(onClick = { call(e.phone) }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Filled.Call, "تماس", tint = color)
                                }
                                IconButton(onClick = {
                                    editEmployerOld = e.employer
                                    editEmpName = e.employer
                                    editEmpPhone = e.phone
                                }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Filled.Edit, "ویرایش", tint = TextSecondary)
                                }
                            }
                            Text(
                                "پروژه: ${e.projectCount} | درآمد: ${moneyM(e.sessions.sumOf { it.amount })} | دریافتی: ${moneyM(e.totalReceived)} | مانده: ${moneyM(e.totalClaims)}",
                                color = TextSecondary, fontSize = 12.sp
                            )
                            e.sessions.take(8).forEach { s ->
                                Text(
                                    "• ${s.name} | جلسه ${s.day}/${s.month}/${s.year} | ${moneyM(s.amount)}",
                                    color = if (s.hasLocation) color else TextPrimary,
                                    fontSize = 12.sp,
                                    modifier = Modifier.clickable {
                                        if (s.hasLocation) openNeshanNav(context, s)
                                        else {
                                            manageProject = ProjectStore.projectProfiles(context)
                                                .firstOrNull { it.name == s.name }
                                            showLocFor = s.name
                                        }
                                    }
                                )
                            }
                            if (e.sessions.size > 8) {
                                Text("+ ${e.sessions.size - 8} مورد دیگر", color = TextSecondary, fontSize = 11.sp)
                            }
                        }
                    }
                }
            } else {
                items(projects, key = { it.name }) { p ->
                    Surface(shape = RoundedCornerShape(12.dp), color = SurfaceColor, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Row(
                                    Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(p.name, fontWeight = FontWeight.Bold, color = TextPrimary, maxLines = 1)
                                    Text("|", color = TextSecondary, fontSize = 12.sp)
                                    Text(p.employer, color = TextSecondary, fontSize = 12.sp, maxLines = 1)
                                }
                                IconButton(onClick = { call(p.phone) }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Filled.Call, "تماس", tint = color)
                                }
                                IconButton(
                                    onClick = {
                                        val withLoc = p.sessions.firstOrNull { it.hasLocation }
                                        if (withLoc != null) openNeshanNav(context, withLoc)
                                        else {
                                            manageProject = p
                                            showLocFor = p.name
                                        }
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Filled.NearMe, "مسیریاب", tint = color)
                                }
                                IconButton(
                                    onClick = { manageProject = p },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Filled.Edit, "ویرایش", tint = TextSecondary)
                                }
                            }
                            Text(
                                "جلسات: ${p.sessionCount} | درآمد: ${moneyM(p.sessions.sumOf { it.amount })} | دریافتی: ${moneyM(p.totalReceived)} | مانده: ${moneyM(p.totalClaims)}",
                                color = TextSecondary, fontSize = 12.sp
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

    // مدیریت پروژه: موقعیت + لیست جلسات
    manageProject?.let { profile ->
        val sessions = remember(tick, profile.name) {
            ProjectStore.all(context).filter { it.name.trim() == profile.name.trim() }
                .sortedByDescending { it.dateSortKey }
        }
        AlertDialog(
            onDismissRequest = {
                manageProject = null
                showLocFor = null
            },
            title = { Text("مدیریت: ${profile.name}") },
            text = {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val locSample = sessions.firstOrNull { it.hasLocation }
                    Text(
                        if (locSample != null) {
                            if (locSample.neshanLink.isNotBlank()) "آدرس/لینک ثبت شده"
                            else String.format(java.util.Locale.US, "مختصات: %.5f , %.5f", locSample.lat, locSample.lon)
                        } else "موقعیت ثبت نشده",
                        color = TextSecondary, fontSize = 12.sp
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { showLocFor = profile.name }) {
                            Text(if (locSample != null) "تغییر موقعیت" else "ثبت موقعیت")
                        }
                        if (locSample != null) {
                            OutlinedButton(onClick = { openNeshanNav(context, locSample) }) {
                                Text("مسیریاب")
                            }
                        }
                    }
                    Divider()
                    Text("جلسات پروژه", fontWeight = FontWeight.Bold)
                    sessions.forEach { s ->
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "${s.day}/${s.month}/${s.year} ${s.hour}:${s.minute.padStart(2, '0')} — ${moneyM(s.amount)}",
                                    fontSize = 13.sp, color = TextPrimary
                                )
                                if (s.hasLocation) {
                                    Text("موقعیت ✓", fontSize = 11.sp, color = color)
                                }
                            }
                            IconButton(onClick = {
                                PendingProjectEdit.rowId = s.row
                                manageProject = null
                                onEditProject?.invoke()
                            }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Filled.Edit, "ویرایش", tint = TextSecondary)
                            }
                            IconButton(onClick = {
                                ProjectStore.delete(context, s.row)
                                refresh()
                                if (ProjectStore.all(context).none { it.name.trim() == profile.name.trim() }) {
                                    manageProject = null
                                }
                            }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Filled.Delete, "حذف", tint = Color(0xFFC62828))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    manageProject = null
                    showLocFor = null
                }) { Text("بستن") }
            }
        )
    }

    showLocFor?.let { projectName ->
        val sample = ProjectStore.all(context).firstOrNull { it.name.trim() == projectName.trim() }
        ProjectLocationPickerDialog(
            initialLat = sample?.lat ?: 0.0,
            initialLon = sample?.lon ?: 0.0,
            initialLink = sample?.neshanLink.orEmpty(),
            onConfirm = { la, lo, link ->
                ProjectStore.updateLocationByProjectName(context, projectName, la, lo, link)
                showLocFor = null
                refresh()
            },
            onDismiss = { showLocFor = null }
        )
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
            "${s.day}/${s.month}/${s.year} — ${moneyM(s.amount)}" +
                if (settled) " (تسویه)" else "",
            color = TextPrimary,
            fontSize = 12.sp,
            modifier = Modifier.weight(1f)
        )
    }
}
