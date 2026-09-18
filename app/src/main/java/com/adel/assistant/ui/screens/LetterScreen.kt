package com.adel.assistant.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.adel.assistant.data.CalendarStore
import com.adel.assistant.data.LetterData
import com.adel.assistant.data.LetterExport
import com.adel.assistant.ui.ScreenTopBar
import com.adel.assistant.ui.theme.Background
import com.adel.assistant.ui.theme.Surface as SurfaceColor
import com.adel.assistant.ui.theme.TextPrimary
import com.adel.assistant.ui.theme.TextSecondary

@Composable
fun LetterScreen(color: Color, onBack: () -> Unit) {
    val context = LocalContext.current
    val (jy, jm, jd) = remember { CalendarStore.todayJalali() }
    var dateLabel by remember {
        mutableStateOf("%04d/%02d/%02d".format(jy, jm, jd))
    }
    var letterNo by remember { mutableStateOf("") }
    var employer by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("شماره، تاریخ، کارفرما و متن نامه را وارد کن") }

    fun buildData() = LetterData(
        letterNo = letterNo.trim(),
        dateLabel = dateLabel.trim(),
        employer = employer.trim(),
        body = body.trim()
    )

    Column(
        Modifier
            .fillMaxSize()
            .background(Background)
            .padding(horizontal = 16.dp)
    ) {
        ScreenTopBar(title = "نامه‌نگاری", color = color, onBack = onBack)

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                value = dateLabel,
                onValueChange = { dateLabel = it },
                label = { Text("تاریخ (مثال ۱۴۰۵/۰۶/۲۴)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = letterNo,
                onValueChange = { letterNo = it },
                label = { Text("شماره نامه") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = employer,
                onValueChange = { employer = it },
                label = { Text("نام کارفرما") },
                placeholder = { Text("کارفرمای محترم …", color = TextSecondary) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = body,
                onValueChange = { body = it },
                label = { Text("متن نامه") },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 180.dp),
                minLines = 8,
                maxLines = 20
            )

            Surface(shape = RoundedCornerShape(10.dp), color = SurfaceColor, modifier = Modifier.fillMaxWidth()) {
,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = {
                        val uri = LetterExport.exportXlsx(context, buildData())
                        status = if (uri != null) "XLSX در Documents/AdelAssistant ذخیره شد"
                        else "خطا در صدور XLSX — قالب را بررسی کن"
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = color),
                    modifier = Modifier.weight(1f)
                ) { Text("خروجی XLSX") }
                OutlinedButton(
                    onClick = {
                        val uri = LetterExport.exportPdfAndShare(context, buildData())
                        status = if (uri != null) "PDF ذخیره شد"
                        else "خطا در صدور PDF"
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("خروجی PDF") }
            }

            Text(status, color = TextPrimary, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(16.dp))
        }
    }
}
