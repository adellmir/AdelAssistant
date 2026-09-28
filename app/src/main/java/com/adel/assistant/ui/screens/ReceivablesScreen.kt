package com.adel.assistant.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adel.assistant.data.ProjectEntry
import com.adel.assistant.data.ProjectStore
import com.adel.assistant.data.formatMoney
import com.adel.assistant.data.toDoubleOrNullFa
import com.adel.assistant.data.tomanToProjectInput
import com.adel.assistant.ui.ScreenTopBar
import com.adel.assistant.ui.theme.Background
import com.adel.assistant.ui.theme.Surface as SurfaceColor
import com.adel.assistant.ui.theme.TextPrimary
import com.adel.assistant.ui.theme.TextSecondary

private data class EmployerGroup(
    val employer: String,
    val phone: String,
    val projects: List<ProjectEntry>,
    val totalWork: Double,
    val totalReceived: Double,
    val totalRemain: Double
)

/** مبلغ به میلیون تومان برای متن پیامک */
private fun formatMillionToman(toman: Double): String {
    val m = tomanToProjectInput(toman)
    return if (kotlin.math.abs(m - m.toLong().toDouble()) < 1e-6) {
        formatMoney(m, 0)
    } else {
        formatMoney(m, 2)
    }
}

private fun smsBodyForClaims(totalRemainToman: Double): String {
    val amount = formatMillionToman(totalRemainToman)
    return "با سلام جهت پرداخت هزینه نقشه‌برداری به‌مبلغ $amount م تومان. ممنون می‌شوم پس از پرداخت اطلاع‌رسانی بفرمایید.\n" +
        "کارت: 5859831142797561\n" +
        "شبا: 430180000000242375213376"
}

private fun projectDateLabel(p: ProjectEntry): String {
    val d = p.day.ifBlank { "—" }
    val m = p.month.ifBlank { "—" }
    val y = p.year.ifBlank { "—" }
    return "$y/$m/$d"
}

@Composable
fun ReceivablesScreen(
    color: Color,
    onBack: () -> Unit,
    onInvoice: (ProjectEntry) -> Unit = {}
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var employer by remember { mutableStateOf("") }
    var tab by remember { mutableStateOf(0) } // 0 مانده 1 پرداخت‌شده
    var reportMode by remember { mutableStateOf(false) }
    var all by remember { mutableStateOf(ProjectStore.all(context)) }
    var editing by remember { mutableStateOf<ProjectEntry?>(null) }
    val numKb = KeyboardOptions(keyboardType = KeyboardType.Number)

    fun refresh() {
        all = ProjectStore.all(context)
    }

    // فیلتر پایه روی نام پروژه / کارفرما
    val baseFiltered = all.filter { p ->
        if (p.name.isBlank() || p.name == "پروژه") return@filter false
        val okName = name.isBlank() || p.name.contains(name, true)
        val okEmp = employer.isBlank() || p.employer.contains(employer, true)
        okName && okEmp
    }

    // در حالت عادی: فقط مانده یا فقط پرداخت‌شده؛ در گزارش: همه (با فیلتر نام)
    val listForView = if (reportMode) {
        baseFiltered
    } else {
        baseFiltered.filter { p ->
            val isPaid = ProjectStore.isFullySettled(p)
            if (tab == 0) !isPaid else isPaid
        }
    }

    val totalWork = listForView.sumOf { it.amount }
    val totalReceived = listForView.sumOf { it.settled }
    val totalRemain = listForView.sumOf { it.remaining.coerceAtLeast(0.0) }

    // گروه‌بندی بر اساس کارفرما — پروژه‌ها جدید→قدیم؛ کارفرماها بر اساس جدیدترین پروژه
    val groups: List<EmployerGroup> = listForView
        .groupBy { it.employer.trim().ifBlank { "بدون کارفرما" } }
        .map { (emp, rows) ->
            val sorted = rows.sortedByDescending { it.dateSortKey }
            EmployerGroup(
                employer = emp,
                phone = sorted.firstOrNull { it.phone.isNotBlank() }?.phone.orEmpty(),
                projects = sorted,
                totalWork = rows.sumOf { it.amount },
                totalReceived = rows.sumOf { it.settled },
                totalRemain = rows.sumOf { it.remaining.coerceAtLeast(0.0) }
            )
        }
        .sortedByDescending { it.projects.firstOrNull()?.dateSortKey.orEmpty() }

    Column(modifier = Modifier.fillMaxSize().background(Background).padding(horizontal = 16.dp)) {
        ScreenTopBar(title = "مطالبات کلی", color = color, onBack = onBack)

        // سربرگ‌ها
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 6.dp)) {
            listOf("مانده", "پرداخت‌شده‌ها").forEachIndexed { i, label ->
                val selected = !reportMode && tab == i
                Surface(
                    modifier = Modifier.weight(1f).clickable {
                        tab = i
                        reportMode = false
                    },
                    shape = RoundedCornerShape(10.dp),
                    color = if (selected) color.copy(alpha = 0.18f) else Color.Transparent
                ) {
                    Text(
                        label,
                        modifier = Modifier.padding(vertical = 10.dp).fillMaxWidth(),
                        color = if (selected) color else TextSecondary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            if (reportMode) {
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    color = color.copy(alpha = 0.22f)
                ) {
                    Text(
                        "گزارش",
                        modifier = Modifier.padding(vertical = 10.dp).fillMaxWidth(),
                        color = color,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // کادر محاسبات در یک خط
        Surface(shape = RoundedCornerShape(12.dp), color = SurfaceColor, modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("کارکرد: ${formatMoney(totalWork)}", style = MaterialTheme.typography.bodySmall, color = TextPrimary)
                Text("دریافتی: ${formatMoney(totalReceived)}", style = MaterialTheme.typography.bodySmall, color = TextPrimary)
                Text(
                    "مانده: ${formatMoney(totalRemain)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = color,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("نام پروژه") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            OutlinedTextField(
                value = employer,
                onValueChange = { employer = it },
                label = { Text("کارفرما") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
        }

        // جستجو / رفرش / گزارش
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(vertical = 8.dp).fillMaxWidth()
        ) {
            Button(
                onClick = { refresh() },
                colors = ButtonDefaults.buttonColors(containerColor = color),
                modifier = Modifier.weight(1f)
            ) { Text("جستجو") }
            OutlinedButton(
                onClick = { name = ""; employer = ""; refresh() },
                modifier = Modifier.weight(1f)
            ) { Text("رفرش") }
            OutlinedButton(
                onClick = {
                    reportMode = true
                    refresh()
                },
                modifier = Modifier.weight(1f)
            ) { Text("گزارش") }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            items(groups, key = { it.employer }) { g ->
                EmployerClaimsCard(
                    group = g,
                    color = color,
                    reportMode = reportMode,
                    remainingTab = tab == 0 && !reportMode,
                    onCall = {
                        if (g.phone.isNotBlank()) {
                            context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${g.phone}")))
                        }
                    },
                    onSms = {
                        if (g.phone.isBlank()) return@EmployerClaimsCard
                        val remain = if (reportMode) g.totalRemain else g.totalRemain
                        if (remain <= 1e-9 && reportMode) return@EmployerClaimsCard
                        val intent = Intent(Intent.ACTION_SENDTO).apply {
                            data = Uri.parse("smsto:${g.phone}")
                            putExtra("sms_body", smsBodyForClaims(remain.coerceAtLeast(0.0)))
                        }
                        try {
                            context.startActivity(intent)
                        } catch (_: Exception) {
                        }
                    },
                    onToggleSettle = { p, settle ->
                        if (settle) ProjectStore.markSettled(context, p.row)
                        else ProjectStore.markUnsettled(context, p.row)
                        refresh()
                    },
                    onEdit = { editing = it },
                    onInvoice = onInvoice
                )
            }
        }
    }

    editing?.let { p ->
        var eName by remember(p.row) { mutableStateOf(p.name) }
        var eEmployer by remember(p.row) { mutableStateOf(p.employer) }
        var eAmount by remember(p.row) { mutableStateOf(p.amount.toString()) }
        var eSettled by remember(p.row) { mutableStateOf(p.settled.toString()) }
        var ePhone by remember(p.row) { mutableStateOf(p.phone) }
        var eDesc by remember(p.row) { mutableStateOf(p.description) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("ویرایش مطالبه") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(eName, { eName = it }, label = { Text("نام پروژه") }, singleLine = true)
                    OutlinedTextField(eEmployer, { eEmployer = it }, label = { Text("کارفرما") }, singleLine = true)
                    OutlinedTextField(eAmount, { eAmount = it }, label = { Text("مبلغ") }, singleLine = true, keyboardOptions = numKb)
                    OutlinedTextField(eSettled, { eSettled = it }, label = { Text("دریافتی") }, singleLine = true, keyboardOptions = numKb)
                    OutlinedTextField(ePhone, { ePhone = it }, label = { Text("تلفن") }, singleLine = true, keyboardOptions = numKb)
                    OutlinedTextField(eDesc, { eDesc = it }, label = { Text("توضیح") })
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val amt = eAmount.toDoubleOrNullFa() ?: p.amount
                    val set = eSettled.toDoubleOrNullFa() ?: p.settled
                    val remain = (amt - set).coerceAtLeast(0.0)
                    ProjectStore.save(
                        context,
                        p.copy(
                            name = eName,
                            employer = eEmployer,
                            amount = amt,
                            settled = set,
                            remaining = remain,
                            phone = ePhone,
                            description = eDesc
                        )
                    )
                    editing = null
                    refresh()
                }) { Text("ذخیره") }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("انصراف") } }
        )
    }
}

@Composable
private fun EmployerClaimsCard(
    group: EmployerGroup,
    color: Color,
    reportMode: Boolean,
    remainingTab: Boolean,
    onCall: () -> Unit,
    onSms: () -> Unit,
    onToggleSettle: (ProjectEntry, Boolean) -> Unit,
    onEdit: (ProjectEntry) -> Unit,
    onInvoice: (ProjectEntry) -> Unit
) {
    val showSms = if (reportMode) group.totalRemain > 1e-9 else true
    Surface(shape = RoundedCornerShape(12.dp), color = SurfaceColor, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // سربرگ کارفرما
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        group.employer,
                        style = MaterialTheme.typography.titleSmall,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    if (reportMode) {
                        Text(
                            "کارکرد: ${formatMoney(group.totalWork)}  |  مطالبات: ${formatMoney(group.totalRemain)}  |  مانده: ${formatMoney(group.totalRemain)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    } else if (remainingTab) {
                        Text(
                            "جمع مطالبات: ${formatMoney(group.totalRemain)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = color,
                            fontWeight = FontWeight.SemiBold
                        )
                    } else {
                        Text(
                            "جمع دریافتی: ${formatMoney(group.totalReceived)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = color,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                if (group.phone.isNotBlank()) {
                    IconButton(onClick = onCall, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Filled.Call, contentDescription = "تماس", tint = color)
                    }
                    if (showSms) {
                        IconButton(onClick = onSms, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Filled.Sms, contentDescription = "پیامک", tint = color)
                        }
                    }
                }
            }

            HorizontalDivider(color = TextSecondary.copy(alpha = 0.25f))

            // لیست پروژه‌ها — جدیدترین اول
            group.projects.forEach { p ->
                val settled = ProjectStore.isFullySettled(p)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(p.name, style = MaterialTheme.typography.bodySmall, color = TextPrimary)
                        Text(
                            "${projectDateLabel(p)}  |  ${formatMoney(if (remainingTab) p.remaining.coerceAtLeast(0.0) else p.amount)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                    IconButton(onClick = { onInvoice(p) }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Filled.ReceiptLong, contentDescription = "فاکتور", tint = color)
                    }
                    IconButton(onClick = { onEdit(p) }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Filled.Edit, contentDescription = "ویرایش", tint = color)
                    }
                    // مانده: خالی (تیک = تسویه) | گزارش/پرداخت‌شده: پر اگر تسویه شده
                    Checkbox(
                        checked = if (remainingTab) false else settled,
                        onCheckedChange = { checked ->
                            if (remainingTab) {
                                if (checked) onToggleSettle(p, true)
                            } else {
                                onToggleSettle(p, checked)
                            }
                        },
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    }
}
