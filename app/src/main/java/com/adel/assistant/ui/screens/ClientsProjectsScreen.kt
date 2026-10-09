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
import com.adel.assistant.data.formatMoney
import com.adel.assistant.data.openNeshanNav
import com.adel.assistant.ui.ScreenTopBar
import com.adel.assistant.ui.theme.Background
import com.adel.assistant.ui.theme.Surface as SurfaceColor
import com.adel.assistant.ui.theme.TextPrimary
import com.adel.assistant.ui.theme.TextSecondary

/** مبلغ در پایگاه از قبل به «میلیون تومان» ذخیره می‌شود — دوباره تقسیم نشود */
private fun moneyM(v: Double): String {
    return if (kotlin.math.abs(v - v.toLong().toDouble()) < 1e-9)
        String.format(java.util.Locale.US, "%,.0f م", v)
    else
        String.format(java.util.Locale.US, "%,.1f م", v)
}

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
    var locEditProject by remember { mutableStateOf<ProjectEntry?>(null) }

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
    ) {
        ScreenTopBar(title = "کارفرمایان و پروژه‌ها", color = color, onBack = onBack)

        Column(Modifier.padding(horizontal = 16.dp)) {
            OutlinedTextField(
                value = searchProject,
                onValueChange = { searchProject = it },
                label = { Text("جستجو پروژه") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = searchEmployer,
                onValueChange = { searchEmployer = it },
                label = { Text("جستجو کارفرما") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { doSearch() },
                colors = ButtonDefaults.buttonColors(containerColor = color),
                modifier = Modifier.fillMaxWidth()
            ) { Text("جستجو / نمایش لیست") }

            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("پروژه", "کارفرمایان").forEachIndexed { i, label ->
                    FilterChip(
                        selected = tab == i,
                        onClick = { tab = i; if (showList) refresh() },
                        label = { Text(label) }
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        LazyColumn(
            Modifier
                .weight(1f)
                .padding(horizontal = 16.dp),
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
                                IconButton(
                                    onClick = {
                                        editEmployerOld = e.employer
                                        editEmpName = e.employer
                                        editEmpPhone = e.phone
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Filled.Edit, contentDescription = "ویرایش", tint = TextSecondary)
                                }
                            }
                            Text(
                                "پروژه: ${e.projectCount} | درآمد: ${moneyM(e.sessions.sumOf { it.amount })} | دریافتی: ${moneyM(e.totalReceived)} | مانده: ${moneyM(e.totalClaims)}",
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                            e.sessions.take(8).forEach { s ->
                                Text(
                                    "• ${s.name} | جلسه ${s.day}/${s.month}/${s.year} | ${moneyM(s.amount)}",
                                    color = if (s.hasLocation) color else TextPrimary,
                                    fontSize = 12.sp,
                                    modifier = Modifier.clickable {
                                        if (s.hasLocation) openNeshanNav(context, s.lat, s.lon)
                                        else locEditProject = s
                                    }
                                )
                            }
                            e.sessions.take(5).forEach { s ->
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
                                IconButton(
                                    onClick = {
                                        val withLoc = p.sessions.firstOrNull { it.hasLocation }
                                        if (withLoc != null) openNeshanNav(context, withLoc.lat, withLoc.lon)
                                        else locEditProject = p.sessions.firstOrNull()
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Filled.NearMe, contentDescription = "مسیریاب", tint = color)
                                }
                            }
                            Text(
                                "جلسه: ${p.sessionCount} | دریافتی: ${moneyM(p.totalReceived)} | مانده: ${moneyM(p.totalClaims)}",
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                            p.sessions.take(5).forEach { s ->
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

    locEditProject?.let { pe ->
        ProjectLocationPickerDialog(
            initialLat = pe.lat,
            initialLon = pe.lon,
            onConfirm = { la, lo ->
                ProjectStore.save(context, pe.copy(lat = la, lon = lo))
                locEditProject = null
                refresh()
            },
            onDismiss = { locEditProject = null }
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
