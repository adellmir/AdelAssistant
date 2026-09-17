package com.adel.assistant.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.adel.assistant.data.CalendarStore
import com.adel.assistant.data.InvoiceData
import com.adel.assistant.data.InvoiceExport
import com.adel.assistant.data.InvoiceLine
import com.adel.assistant.data.ProjectEntry
import com.adel.assistant.data.ProjectStore
import com.adel.assistant.data.formatMoney
import com.adel.assistant.data.toDoubleOrNullFa
import com.adel.assistant.ui.ScreenTopBar
import com.adel.assistant.ui.theme.Background
import com.adel.assistant.ui.theme.Surface as SurfaceColor
import com.adel.assistant.ui.theme.TextPrimary
import com.adel.assistant.ui.theme.TextSecondary

private data class LineDraft(
    val service: String = "",
    val amount: String = "",
    val note: String = ""
)

@Composable
fun InvoiceScreen(
    color: Color,
    onBack: () -> Unit,
    preselected: List<ProjectEntry> = emptyList()
) {
    val context = LocalContext.current
    val (ty, tm, td) = CalendarStore.todayJalali()
    val numKb = KeyboardOptions(keyboardType = KeyboardType.Number)

    var mode by remember { mutableStateOf(if (preselected.isNotEmpty()) 1 else 0) } // 0 manual 1 from projects
    var employer by remember { mutableStateOf("") }
    var letterNo by remember { mutableStateOf("") }
    var received by remember { mutableStateOf("") }
    var cardNo by remember { mutableStateOf("6063 7312 4761 5033") }
    var iban by remember { mutableStateOf("IR 4106 0036 0370 0200 8273 2001") }
    var lines by remember {
        mutableStateOf(
            if (preselected.isNotEmpty()) preselected.map { p ->
                LineDraft(
                    service = p.description.ifBlank { p.name },
                    amount = (p.amount * 1_000_000.0).let { v ->
                        if (kotlin.math.abs(v - v.toLong()) < 1e-9) v.toLong().toString() else v.toString()
                    },
                    note = listOf(p.year, p.month, p.day).filter { it.isNotBlank() }.joinToString("/")
                )
            } else listOf(LineDraft())
        )
    }
    var searchName by remember { mutableStateOf("") }
    var searchEmployer by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(preselected.map { it.row }.toSet()) }
    var status by remember { mutableStateOf("") }

    if (preselected.isNotEmpty() && employer.isBlank()) {
        employer = preselected.firstOrNull()?.employer.orEmpty()
    }

    val searchResults = remember(searchName, searchEmployer, mode) {
        if (mode != 1) emptyList()
        else ProjectStore.all(context).filter { p ->
            if (p.name.isBlank() || p.name == "پروژه") return@filter false
            val okN = searchName.isBlank() || p.name.contains(searchName, true)
            val okE = searchEmployer.isBlank() || p.employer.contains(searchEmployer, true)
            okN && okE
        }.take(50)
    }

    fun applySelectedProjects() {
        val all = ProjectStore.all(context)
        val picks = all.filter { it.row in selected }
        if (picks.isEmpty()) {
            status = "موردی انتخاب نشده"
            return
        }
        employer = picks.firstOrNull { it.employer.isNotBlank() }?.employer ?: employer
        // مبلغ پروژه‌ها در Store به میلیون است → تبدیل به ریال برای فاکتور
        lines = picks.map { p ->
            LineDraft(
                service = p.description.ifBlank { p.name },
                amount = (p.amount * 1_000_000.0).let { v ->
                    if (kotlin.math.abs(v - v.toLong()) < 1e-9) v.toLong().toString() else v.toString()
                },
                note = listOf(p.year, p.month, p.day).filter { it.isNotBlank() }.joinToString("/")
            )
        }
        // جمع دریافتی‌های ثبت‌شده روی پروژه‌های انتخابی (میلیون → ریال)
        val sumSettled = picks.sumOf { it.settled } * 1_000_000.0
        received = if (kotlin.math.abs(sumSettled - sumSettled.toLong()) < 1e-9)
            sumSettled.toLong().toString() else sumSettled.toString()
        status = "${picks.size} پروژه انتخاب شد — جمع و مانده محاسبه شد؛ ویرایش و خروجی بزن"
    }

    fun buildData(): InvoiceData? {
        val parsed = lines.mapNotNull { l ->
            val amt = l.amount.toDoubleOrNullFa() ?: return@mapNotNull null
            if (l.service.isBlank() && amt == 0.0) return@mapNotNull null
            InvoiceLine(l.service.ifBlank { "—" }, amt, l.note)
        }
        if (parsed.isEmpty()) {
            status = "حداقل یک ردیف خدمات لازم است"
            return null
        }
        return InvoiceData(
            employer = employer.trim(),
            letterNo = letterNo.trim(),
            lines = parsed,
            received = received.toDoubleOrNullFa() ?: 0.0,
            cardNo = cardNo.trim(),
            iban = iban.trim(),
            dateLabel = "%04d/%02d/%02d".format(ty, tm, td)
        )
    }

    val totalPreview = lines.sumOf { it.amount.toDoubleOrNullFa() ?: 0.0 }
    val recvPreview = received.toDoubleOrNullFa() ?: 0.0
    val remainPreview = (totalPreview - recvPreview).coerceAtLeast(0.0)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .padding(horizontal = 16.dp)
    ) {
        ScreenTopBar(title = "صدور فاکتور", color = color, onBack = onBack)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            FilterChip(
                selected = mode == 0,
                onClick = { mode = 0 },
                label = { Text("دستی") }
            )
            FilterChip(
                selected = mode == 1,
                onClick = { mode = 1 },
                label = { Text("از پروژه‌ها") }
            )
        }
        Spacer(Modifier.height(8.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
        ) {
            if (mode == 1) {
                // دکمه صدور بالا — وقتی حداقل یک پروژه انتخاب شده
                if (selected.isNotEmpty()) {
                    item {
                        Button(
                            onClick = {
                                applySelectedProjects()
                                mode = 0
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = color),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("صدور فاکتور (${selected.size} پروژه انتخاب‌شده)")
                        }
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(
                            searchEmployer, { searchEmployer = it },
                            label = { Text("جستجو کارفرما") },
                            modifier = Modifier.weight(1f), singleLine = true
                        )
                        OutlinedTextField(
                            searchName, { searchName = it },
                            label = { Text("جستجو پروژه") },
                            modifier = Modifier.weight(1f), singleLine = true
                        )
                    }
                }
                items(searchResults, key = { it.row }) { p ->
                    val checked = p.row in selected
                    Surface(shape = RoundedCornerShape(10.dp), color = SurfaceColor, modifier = Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked, {
                                selected = if (it) selected + p.row else selected - p.row
                            })
                            Column(Modifier.weight(1f)) {
                                Text("${p.name} — ${p.employer}", color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "مبلغ: ${formatMoney(p.amount)} | مانده: ${formatMoney(p.remaining)} | ${p.year}/${p.month}/${p.day}",
                                    color = TextSecondary, style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
                if (selected.isNotEmpty()) {
                    item {
                        Button(
                            onClick = {
                                applySelectedProjects()
                                mode = 0
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = color),
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("صدور فاکتور (${selected.size} پروژه) — ویرایش و خروجی") }
                    }
                }
            }

            // فرم دستی / ویرایش فقط در mode 0 (بعد از انتخاب پروژه یا از اول دستی)
            if (mode == 0) {
            item {
                OutlinedTextField(
                    employer, { employer = it },
                    label = { Text("نام کارفرما") },
                    supportingText = { Text("در فاکتور: کارفرمای محترم …") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true
                )
            }
            item {
                OutlinedTextField(
                    letterNo, { letterNo = it },
                    label = { Text("شماره نامه") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true
                )
            }

            item {
                Text("شرح خدمات", fontWeight = FontWeight.Bold, color = TextPrimary)
            }
            itemsIndexed(lines) { index, line ->
                Surface(shape = RoundedCornerShape(12.dp), color = SurfaceColor, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(
                            line.service,
                            { v -> lines = lines.toMutableList().also { it[index] = line.copy(service = v) } },
                            label = { Text("شرح خدمات") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            line.amount,
                            { v -> lines = lines.toMutableList().also { it[index] = line.copy(amount = v) } },
                            label = { Text("مبلغ") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = numKb
                        )
                        OutlinedTextField(
                            line.note,
                            { v -> lines = lines.toMutableList().also { it[index] = line.copy(note = v) } },
                            label = { Text("توضیحات") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (lines.size > 1) {
                            TextButton(onClick = {
                                lines = lines.toMutableList().also { it.removeAt(index) }
                            }) {
                                Icon(Icons.Filled.Delete, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("حذف ردیف")
                            }
                        }
                    }
                }
            }
            item {
                OutlinedButton(
                    onClick = { lines = lines + LineDraft() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Add, null)
                    Spacer(Modifier.width(6.dp))
                    Text("ردیف جدید")
                }
            }

            item {
                OutlinedTextField(
                    received, { received = it },
                    label = { Text("دریافتی قبلی") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = numKb
                )
            }
            item {
                Surface(shape = RoundedCornerShape(12.dp), color = SurfaceColor, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("جمع کل: ${formatMoney(totalPreview)}", fontWeight = FontWeight.Bold, color = TextPrimary)
                        Text("دریافتی: ${formatMoney(recvPreview)}", color = TextSecondary)
                        Text("مانده پرداختی: ${formatMoney(remainPreview)}", fontWeight = FontWeight.Bold, color = color)
                    }
                }
            }
            item {
                OutlinedTextField(cardNo, { cardNo = it }, label = { Text("شماره کارت") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = numKb)
            }
            item {
                OutlinedTextField(iban, { iban = it }, label = { Text("شماره شبا") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true)
            }

            item {
                Button(
                    onClick = {
                        val data = buildData() ?: return@Button
                        val uri = InvoiceExport.exportPdfAndShare(context, data)
                        status = if (uri != null) "PDF ذخیره و اشتراک شد" else "خطا در PDF"
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = color),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("ثبت / PDF + اشتراک") }
            }
            item {
                OutlinedButton(
                    onClick = {
                        val data = buildData() ?: return@OutlinedButton
                        val uri = InvoiceExport.exportXlsx(context, data)
                        status = if (uri != null) "XLSX در Documents/AdelAssistant ذخیره شد" else "خطا در XLSX"
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("خروجی XLSX") }
            }
            } // end if (mode == 0)

            if (status.isNotBlank()) {
                item { Text(status, color = TextSecondary) }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}
