package com.adel.assistant.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.adel.assistant.data.CsvStore
import com.adel.assistant.ui.ScreenTopBar
import com.adel.assistant.ui.theme.TextPrimary
import com.adel.assistant.ui.theme.TextSecondary
import com.adel.assistant.ui.theme.TextMuted
import com.adel.assistant.ui.theme.Background
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * صفحه‌ی عمومی ثبت رکورد: چند فیلد متنی + دکمه‌ی ذخیره + فهرست رکوردهای قبلی.
 * اگر readOnlyNote داده شود، فرم ورودی مخفی و فقط فهرست + یادداشت نشان داده می‌شود.
 */
@Composable
fun SimpleRecordScreen(
    title: String,
    color: Color,
    csvName: String,
    fields: List<String>,
    onBack: () -> Unit,
    readOnlyNote: String? = null
) {
    val context = LocalContext.current
    val numKb = KeyboardOptions(keyboardType = KeyboardType.Number)
    var values by remember { mutableStateOf(List(fields.size) { "" }) }
    var records by remember { mutableStateOf(CsvStore.readAll(context, csvName)) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .padding(horizontal = 20.dp)
    ) {
        ScreenTopBar(title = title, color = color, onBack = onBack)
        Spacer(modifier = Modifier.height(8.dp))

        if (readOnlyNote != null) {
            Text(readOnlyNote, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
            Spacer(modifier = Modifier.height(12.dp))
        } else {
            fields.forEachIndexed { index, label ->
                OutlinedTextField(
                    value = values[index],
                    onValueChange = { newVal ->
                        values = values.toMutableList().also { it[index] = newVal }
                    },
                    label = { Text(label) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = {
                    val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
                    val row = listOf(timestamp) + values
                    CsvStore.appendRow(context, csvName, row)
                    records = CsvStore.readAll(context, csvName)
                    values = List(fields.size) { "" }
                },
                colors = ButtonDefaults.buttonColors(containerColor = color),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("ذخیره")
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        Text("ثبت‌های قبلی", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(records.reversed()) { row ->
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color.White,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        row.joinToString("  •  "),
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}