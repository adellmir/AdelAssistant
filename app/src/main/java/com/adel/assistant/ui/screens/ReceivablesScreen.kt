package com.adel.assistant.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
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
fun ReceivablesScreen(color: Color, onBack: () -> Unit) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var employer by remember { mutableStateOf("") }
    var list by remember { mutableStateOf(ProjectStore.all(context).filter { it.remaining > 1e-9 }) }
    var editing by remember { mutableStateOf<ProjectEntry?>(null) }

    fun refresh() {
        val all = ProjectStore.all(context)
        list = all.filter { p ->
            val okRemain = p.remaining > 1e-9
            val okName = name.isBlank() || p.name.contains(name, true)
            val okEmp = employer.isBlank() || p.employer.contains(employer, true)
            okRemain && okName && okEmp
        }
    }

    val totalWork = list.sumOf { it.amount }
    val totalReceived = list.sumOf { it.settled }
    val totalRemain = list.sumOf { it.remaining }

    Column(modifier = Modifier.fillMaxSize().background(Background).padding(horizontal = 20.dp)) {
        ScreenTopBar(title = "مطالبات کلی", color = color, onBack = onBack)

        Surface(shape = RoundedCornerShape(12.dp), color = SurfaceColor, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("کارکرد: ${formatMoney(totalWork)}", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                Text("دریافتی: ${formatMoney(totalReceived)}", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                Text("مانده: ${formatMoney(totalRemain)}", style = MaterialTheme.typography.titleSmall, color = color)
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("نام پروژه") }, modifier = Modifier.weight(1f))
            OutlinedTextField(value = employer, onValueChange = { employer = it }, label = { Text("کارفرما") }, modifier = Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
            Button(onClick = { refresh() }, colors = ButtonDefaults.buttonColors(containerColor = color), modifier = Modifier.weight(1f)) { Text("جستجو") }
            OutlinedButton(onClick = { name = ""; employer = ""; refresh() }, modifier = Modifier.weight(1f)) { Text("رفرش") }
        }
        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(list, key = { it.row }) { p ->
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
                        IconButton(onClick = {
                            if (p.phone.isNotBlank()) {
                                context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${p.phone}")))
                            }
                        }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Filled.Call, contentDescription = "تماس", tint = color)
                        }
                        IconButton(onClick = { editing = p }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Filled.Edit, contentDescription = "ویرایش", tint = color)
                        }
                        Checkbox(
                            checked = false,
                            onCheckedChange = { checked ->
                                if (checked) {
                                    ProjectStore.save(context, p.copy(settled = p.amount, remaining = 0.0))
                                    refresh()
                                }
                            }
                        )
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
                    OutlinedTextField(eAmount, { eAmount = it }, label = { Text("مبلغ") }, singleLine = true)
                    OutlinedTextField(eSettled, { eSettled = it }, label = { Text("دریافتی") }, singleLine = true)
                    OutlinedTextField(ePhone, { ePhone = it }, label = { Text("تلفن") }, singleLine = true)
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
            dismissButton = {
                TextButton(onClick = { editing = null }) { Text("انصراف") }
            }
        )
    }
}
