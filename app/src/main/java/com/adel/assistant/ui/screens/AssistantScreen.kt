package com.adel.assistant.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.adel.assistant.ai.AgentReply
import com.adel.assistant.ai.AssistantAgent
import com.adel.assistant.ai.OnlineAiClient
import com.adel.assistant.ui.ScreenTopBar
import com.adel.assistant.ui.theme.Background
import com.adel.assistant.ui.theme.Surface as SurfaceColor
import com.adel.assistant.ui.theme.TextPrimary
import com.adel.assistant.ui.theme.TextSecondary
import com.adel.assistant.ui.theme.ToolPrimary
import kotlinx.coroutines.launch

private data class ChatLine(val fromUser: Boolean, val text: String)

@Composable
fun AssistantScreen(
    color: Color = ToolPrimary,
    onBack: () -> Unit,
    onNavigate: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var input by remember { mutableStateOf("") }
    var onlineMode by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var selectedFileName by remember { mutableStateOf<String?>(null) }
    var messages by remember {
        mutableStateOf(
            listOf(
                ChatLine(
                    fromUser = false,
                    text = "سلام، من دستیار هوشمند AdelAssistant هستم. محاوره‌ای بگو چه کاری انجام بدهم؛ می‌توانم صفحه‌ها را باز کنم، تسک ثبت و تکمیل کنم و از داده‌های برنامه آمار بگیرم."
                )
            )
        )
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val name = uri.lastPathSegment?.substringAfterLast('/')?.substringAfterLast(':') ?: "فایل انتخاب‌شده"
            selectedFileName = name
            AssistantAgent.setSelectedFile(name)
            messages = messages + ChatLine(false, "📎 فایل «$name» انتخاب شد. حالا مثلاً بگو «به DXF تبدیل کن». ")
        }
    }
    fun send(text: String) {
        val t = text.trim()
        if (t.isEmpty() || sending) return
        messages = messages + ChatLine(true, t)
        input = ""
        if (onlineMode) {
            sending = true
            messages = messages + ChatLine(false, "⏳ در حال فکر کردن…")
            scope.launch {
                try {
                    val history = messages.dropLast(1).map { line ->
                        (if (line.fromUser) "user" else "assistant") to line.text
                    }
                    val answer = OnlineAiClient.chat(history)
                    messages = messages.dropLast(1) + ChatLine(false, answer)
                } catch (e: Exception) {
                    messages = messages.dropLast(1) + ChatLine(false, "❌ اتصال به هوش آنلاین ناموفق بود: ${e.message ?: e.javaClass.simpleName}")
                } finally {
                    sending = false
                    scope.launch {
                        runCatching { listState.animateScrollToItem(messages.lastIndex.coerceAtLeast(0)) }
                    }
                }
            }
            return
        }
        val reply: AgentReply = try {
            AssistantAgent.handle(context, t)
        } catch (e: Exception) {
            AgentReply("خطا در پردازش: ${e.message ?: e.javaClass.simpleName}")
        }
        messages = messages + ChatLine(false, reply.text)
        try { reply.navigateTo?.let { route -> onNavigate(route) } } catch (_: Exception) {}
        scope.launch { runCatching { listState.animateScrollToItem(messages.lastIndex.coerceAtLeast(0)) } }
    }

    val quick = listOf(
        "امروز چه کارهایی دارم؟",
        "کارهای باز تونل چیه؟",
        "آمار کلی",
        "برو درون‌یابی",
        "وضعیت مالی پروژه‌ها"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        ScreenTopBar(title = "دستیار هوشمند", color = color, onBack = onBack)

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                if (onlineMode) "🧠 حالت تفکر آنلاین" else "⚡ حالت داخلی برنامه",
                color = if (onlineMode) color else TextSecondary,
                style = MaterialTheme.typography.labelMedium
            )
            Switch(checked = onlineMode, onCheckedChange = { onlineMode = it }, enabled = !sending)
        }

        // پیشنهاد سریع
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            quick.forEach { q ->
                AssistChip(
                    onClick = { send(q) },
                    label = { Text(q, style = MaterialTheme.typography.labelSmall) }
                )
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(messages) { line ->
                val bg = if (line.fromUser) color.copy(alpha = 0.18f) else SurfaceColor
                val align = if (line.fromUser) Alignment.CenterEnd else Alignment.CenterStart
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = align) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = bg,
                        modifier = Modifier.widthIn(max = 320.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            if (!line.fromUser) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Filled.SmartToy,
                                        contentDescription = null,
                                        tint = color,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("دستیار", style = MaterialTheme.typography.labelSmall, color = color)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                            Text(line.text, color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }

        selectedFileName?.let { name ->
            Text(
                text = "📎 $name",
                color = TextSecondary,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(onClick = { filePicker.launch("*/*") }) {
                Icon(Icons.Filled.AttachFile, contentDescription = "انتخاب فایل", tint = color)
            }
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("مثلاً: برای تونل تسک برداشت مقطع ثبت کن…", color = TextSecondary) },
                singleLine = true
            )
            IconButton(
                onClick = { send(input) },
                enabled = !sending,
                colors = IconButtonDefaults.iconButtonColors(containerColor = color)
            ) {
                Icon(Icons.Filled.Send, contentDescription = "ارسال", tint = Color.White)
            }
        }
    }
}
