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
import androidx.compose.material.icons.filled.Message
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.adel.assistant.data.ProjectEntry
import com.adel.assistant.data.ProjectStore
import com.adel.assistant.data.formatEn
import com.adel.assistant.data.formatMoney
import com.adel.assistant.ui.ScreenTopBar
import com.adel.assistant.ui.theme.Background
import com.adel.assistant.ui.theme.Surface as SurfaceColor

// ⚠️ شماره کارت و شماره شبای واقعی خودت رو اینجا جایگزین کن
private const val CARD_NUMBER = "XXXX-XXXX-XXXX-XXXX"
private const val SHEBA_NUMBER = "IRXXXXXXXXXXXXXXXXXXXXXXXX"

private fun smsText(p: ProjectEntry): String {
    return "با سلام جهت یادآوری پرداخت هزینه نقشه‌برداری برای پروژه \"${p.name}\" به‌مبلغ \"${formatEn("%.0f", p.remaining)}\" تومان. " +
        "ممنون می‌شوم پس از پرداخت اطلاع‌رسانی بفرمایید. شماره کارت: $CARD_NUMBER، شماره شبا: $SHEBA_NUMBER"
}

@Composable
fun ReceivablesScreen(color: Color, onBack: () -> Unit) {
    val context = LocalContext.current
    var tab by remember { mutableStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .padding(horizontal = 20.dp)
    ) {
        ScreenTopBar(title = "مطالبات کلی", color = color, onBack = onBack)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
            listOf("پرداخت‌نشده", "پرداخت‌شده").forEachIndexed { i, label ->
                val selected = i == tab
                Surface(
                    modifier = Modifier.weight(1f).clickable { tab = i },
                    shape = RoundedCornerShape(10.dp),
                    color = if (selected) color.copy(alpha = 0.18f) else Color.Transparent
                ) {
                    Text(label, modifier = Modifier.padding(vertical = 8.dp).fillMaxWidth(), textAlign = TextAlign.Center,
                        color = if (selected) color else Color(0xFFAAB697), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (tab == 0) UnpaidTab(context, color) else PaidTab(context, color)
    }
}

@Composable
private fun UnpaidTab(context: android.content.Context, color: Color) {
    var name by remember { mutableStateOf("") }
    var employer by remember { mutableStateOf("") }
    var list by remember { mutableStateOf(ProjectStore.all(context).filter { it.remaining > 0 }.sortedByDescending { it.dateSortKey }) }

    fun refresh() { list = ProjectStore.all(context).filter { it.remaining > 0 }.sortedByDescending { it.dateSortKey } }
    fun search() { list = ProjectStore.search(context, name, employer).filter { it.remaining > 0 } }

    val totalRemaining = list.sumOf { it.remaining }
    val totalWork = list.sumOf { it.amount }
    val totalReceived = list.sumOf { it.settled }

    Column(modifier = Modifier.fillMaxSize()) {
        Surface(shape = RoundedCornerShape(10.dp), color = SurfaceColor, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("کارکرد: ${formatMoney(totalWork)} تومان", style = MaterialTheme.typography.bodyMedium)
                Text("دریافتی: ${formatMoney(totalReceived)} تومان", style = MaterialTheme.typography.bodyMedium)
                Text("مانده: ${formatMoney(totalRemaining)} تومان", style = MaterialTheme.typography.titleSmall, color = color)
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("نام پروژه") }, modifier = Modifier.weight(1f))
            OutlinedTextField(value = employer, onValueChange = { employer = it }, label = { Text("کارفرما") }, modifier = Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
            Button(onClick = { search() }, colors = ButtonDefaults.buttonColors(containerColor = color), modifier = Modifier.weight(1f)) { Text("جستجو") }
            OutlinedButton(onClick = { refresh() }, modifier = Modifier.weight(1f)) { Text("رفرش") }
        }
        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(list, key = { it.row }) { p ->
                Surface(shape = RoundedCornerShape(10.dp), color = SurfaceColor, modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("${p.name} — ${p.employer}", style = MaterialTheme.typography.bodySmall)
                            Text("مانده: ${formatMoney(p.remaining)}", style = MaterialTheme.typography.bodySmall, color = Color(0xFF7C8A6B))
                        }
                        IconButton(onClick = {
                            context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${p.phone}")))
                        }, modifier = Modifier.size(28.dp)) { Icon(Icons.Filled.Call, contentDescription = "تماس", tint = color) }
                        IconButton(onClick = {
                            val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${p.phone}"))
                            intent.putExtra("sms_body", smsText(p))
                            context.startActivity(intent)
                        }, modifier = Modifier.size(28.dp)) { Icon(Icons.Filled.Message, contentDescription = "پیامک", tint = color) }
                        Checkbox(checked = false, onCheckedChange = {
                            if (it) {
                                ProjectStore.save(context, p.copy(settled = p.amount, remaining = 0.0))
                                refresh()
                            }
                        })
                    }
                }
            }
        }
    }
}

@Composable
private fun PaidTab(context: android.content.Context, color: Color) {
    var name by remember { mutableStateOf("") }
    var employer by remember { mutableStateOf("") }
    var list by remember { mutableStateOf(ProjectStore.all(context).filter { it.remaining <= 0 && it.amount > 0 }.sortedByDescending { it.dateSortKey }) }

    fun refresh() { list = ProjectStore.all(context).filter { it.remaining <= 0 && it.amount > 0 }.sortedByDescending { it.dateSortKey } }
    fun search() { list = ProjectStore.search(context, name, employer).filter { it.remaining <= 0 && it.amount > 0 } }

    val totalWork = list.sumOf { it.amount }
    val totalReceived = list.sumOf { it.settled }

    Column(modifier = Modifier.fillMaxSize()) {
        Surface(shape = RoundedCornerShape(10.dp), color = SurfaceColor, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("کارکرد: ${formatMoney(totalWork)} تومان", style = MaterialTheme.typography.bodyMedium)
                Text("دریافتی: ${formatMoney(totalReceived)} تومان", style = MaterialTheme.typography.bodyMedium)
                Text("مانده: 0", style = MaterialTheme.typography.titleSmall, color = color)
            }
        }
        Spacer(modifier = Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("نام پروژه") }, modifier = Modifier.weight(1f))
            OutlinedTextField(value = employer, onValueChange = { employer = it }, label = { Text("کارفرما") }, modifier = Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
            Button(onClick = { search() }, colors = ButtonDefaults.buttonColors(containerColor = color), modifier = Modifier.weight(1f)) { Text("جستجو") }
            OutlinedButton(onClick = { refresh() }, modifier = Modifier.weight(1f)) { Text("رفرش") }
        }
        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(list, key = { it.row }) { p ->
                Surface(shape = RoundedCornerShape(10.dp), color = SurfaceColor, modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("${p.name} — ${p.employer}", style = MaterialTheme.typography.bodySmall)
                            Text("مبلغ: ${formatMoney(p.amount)}", style = MaterialTheme.typography.bodySmall, color = Color(0xFF7C8A6B))
                        }
                        IconButton(onClick = {
                            context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${p.phone}")))
                        }, modifier = Modifier.size(28.dp)) { Icon(Icons.Filled.Call, contentDescription = "تماس", tint = color) }
                        Checkbox(checked = true, onCheckedChange = {
                            if (!it) {
                                ProjectStore.save(context, p.copy(settled = 0.0, remaining = p.amount))
                                refresh()
                            }
                        })
                    }
                }
            }
        }
    }
}
