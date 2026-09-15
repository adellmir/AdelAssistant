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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.adel.assistant.data.ProjectEntry
import com.adel.assistant.data.ProjectStore
import com.adel.assistant.data.formatMoney
import com.adel.assistant.data.toDoubleOrNullFa
import com.adel.assistant.ui.ScreenTopBar
import com.adel.assistant.ui.theme.Background
import com.adel.assistant.ui.theme.Surface as SurfaceColor
import com.adel.assistant.ui.theme.TextPrimary
import com.adel.assistant.ui.theme.TextSecondary

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
    var all by remember { mutableStateOf(ProjectStore.all(context)) }
    var editing by remember { mutableStateOf<ProjectEntry?>(null) }
    val numKb = KeyboardOptions(keyboardType = KeyboardType.Number)

    fun refresh() {
        all = ProjectStore.all(context)
    }

    val filtered = all.filter { p ->
        if (p.name.isBlank() || p.name == "پروژه") return@filter false
        val okName = name.isBlank() || p.name.contains(name, true)
        val okEmp = employer.isBlank() || p.employer.contains(employer, true)
        val isPaid = p.remaining <= 1e-9 || (p.amount > 0 && p.settled >= p.amount - 1e-9)
        val okTab = if (tab == 0) !isPaid else isPaid
        okName && okEmp && okTab
    }.sortedByDescending { it.dateSortKey }

    val totalWork = filtered.sumOf { it.amount }
    val totalReceived = filtered.sumOf { it.settled }
    val totalRemain = filtered.sumOf { it.remaining.coerceAtLeast(0.0) }

    Column(modifier = Modifier.fillMaxSize().background(Background).padding(horizontal = 20.dp)) {
        ScreenTopBar(title = "مطالبات کلی", color = color, onBack = onBack)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 6.dp)) {
            listOf("مانده", "پرداخت‌شده‌ها").forEachIndexed { i, label ->
                val selected = tab == i
                Surface(
                    modifier = Modifier.weight(1f).clickable { tab = i },
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
        }

        Surface(shape = RoundedCornerShape(12.dp), color = SurfaceColor, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("کارکرد: ${formatMoney(totalWork)}", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                Text("دریافتی: ${formatMoney(totalReceived)}", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                if (tab == 0) {
                    Text("مانده: ${formatMoney(totalRemain)}", style = MaterialTheme.typography.titleSmall, color = color)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("نام پروژه") }, modifier = Modifier.weight(1f), singleLine = true)
            OutlinedTextField(value = employer, onValueChange = { employer = it }, label = { Text("کارفرما") }, modifier = Modifier.weight(1f), singleLine = true)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
            Button(onClick = { refresh() }, colors = ButtonDefaults.buttonColors(containerColor = color), modifier = Modifier.weight(1f)) { Text("جستجو") }
            OutlinedButton(onClick = { name = ""; employer = ""; refresh() }, modifier = Modifier.weight(1f)) { Text("رفرش") }
        }

        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(filtered, key = { it.row }) { p ->
                Surface(shape = RoundedCornerShape(10.dp), color = SurfaceColor, modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("${p.name} — ${p.employer}", style = MaterialTheme.typography.bodySmall, color = TextPrimary)
                            Text(
                                "مبلغ: ${formatMoney(p.amount)} | دریافتی: ${formatMoney(p.settled)} | مانده: ${formatMoney(p.remaining)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                        if (p.phone.isNotBlank()) {
                            IconButton(onClick = {
                                context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${p.phone}")))
                            }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Filled.Call, contentDescription = "تماس", tint = color)
                            }
                            IconButton(onClick = {
                                val msg = "سلام، مانده مطالبه پروژه «${p.name}» برابر ${formatMoney(p.remaining)} می‌باشد. با سپاس"
                                val intent = Intent(Intent.ACTION_SENDTO).apply {
                                    data = Uri.parse("smsto:${p.phone}")
                                    putExtra("sms_body", msg)
                                }
                                try {
                                    context.startActivity(intent)
                                } catch (_: Exception) {}
                            }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Filled.Sms, contentDescription = "پیامک", tint = color)
                            }
                        }
                        IconButton(onClick = { onInvoice(p) }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Filled.ReceiptLong, contentDescription = "فاکتور", tint = color)
                        }
                        IconButton(onClick = { editing = p }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Filled.Edit, contentDescription = "ویرایش", tint = color)
                        }
                        if (tab == 0) {
                            // تیک = علامت پرداخت‌شده
                            Checkbox(
                                checked = false,
                                onCheckedChange = { checked ->
                                    if (checked) {
                                        ProjectStore.markSettled(context, p.row)
                                        refresh()
                                    }
                                }
                            )
                        } else {
                            // تیک برداشته شود = برگشت به مانده (لغو پرداخت‌شده)
                            Checkbox(
                                checked = true,
                                onCheckedChange = { checked ->
                                    if (!checked) {
                                        ProjectStore.markUnsettled(context, p.row)
                                        refresh()
                                    }
                                }
                            )
                        }
                    }
                }
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
