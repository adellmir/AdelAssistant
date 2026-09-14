package com.adel.assistant.ui.screens

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.adel.assistant.ai.AgentReply
import com.adel.assistant.ai.AssistantAgent
import com.adel.assistant.ai.OnlineAiClient
import com.adel.assistant.data.AssistantChatStore
import com.adel.assistant.data.AssistantMemoryStore
import com.adel.assistant.data.AssistantPermission
import com.adel.assistant.data.AssistantPermissionStore
import com.adel.assistant.ui.ScreenTopBar
import com.adel.assistant.ui.theme.Background
import com.adel.assistant.ui.theme.Surface as SurfaceColor
import com.adel.assistant.ui.theme.TextPrimary
import com.adel.assistant.ui.theme.TextSecondary
import com.adel.assistant.ui.theme.ToolPrimary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun AssistantScreen(
    color: Color = ToolPrimary,
    onBack: () -> Unit,
    onNavigate: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var chats by remember { mutableStateOf(AssistantChatStore.chats(context)) }
    var current by remember { mutableStateOf(AssistantChatStore.ensureDefault(context)) }
    var messages by remember { mutableStateOf(AssistantChatStore.messages(context, current.id)) }
    var input by remember { mutableStateOf("") }
    var online by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showChats by remember { mutableStateOf(false) }
    var selectedFileName by remember { mutableStateOf<String?>(null) }

    fun selectChat(c: AssistantChatStore.Chat) {
        current = c
        messages = AssistantChatStore.messages(context, c.id)
        showChats = false
    }

    fun newChat() {
        val c = AssistantChatStore.create(context)
        chats = AssistantChatStore.chats(context)
        selectChat(c)
    }

    fun send(text: String) {
        val t = text.trim()
        if (t.isEmpty() || loading) return
        AssistantChatStore.addMessage(context, current.id, true, t)
        messages = AssistantChatStore.messages(context, current.id)
        input = ""
        if (t.contains("یادت باشه") || t.contains("یاد بگیر") || t.startsWith("به خاطر بسپار")) {
            AssistantMemoryStore.add(context, t.substringAfter("باشه", t).trim())
            val r = "ذخیره شد و در گفتگوهای بعدی قابل استفاده است. 🧠"
            AssistantChatStore.addMessage(context, current.id, false, r)
            messages = AssistantChatStore.messages(context, current.id)
            return
        }
        loading = true
        scope.launch {
            val reply = if (online) {
                withContext(Dispatchers.IO) {
                    OnlineAiClient.ask(
                        context,
                        messages.map { it.fromUser to it.text },
                        AssistantMemoryStore.load(context)
                    )
                }
            } else {
                runCatching { AssistantAgent.handle(context, t) }
                    .getOrElse { AgentReply("خطا در پردازش: ${it.message ?: it.javaClass.simpleName}") }
                    .also { it.navigateTo?.let(onNavigate) }
                    .text
            }
            AssistantChatStore.addMessage(context, current.id, false, reply)
            messages = AssistantChatStore.messages(context, current.id)
            loading = false
            listState.animateScrollToItem((messages.size - 1).coerceAtLeast(0))
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val n = uri.lastPathSegment?.substringAfterLast('/')?.substringAfterLast(':') ?: "فایل انتخاب‌شده"
            selectedFileName = n
            AssistantAgent.setSelectedFile(n)
            val r = "📎 فایل «$n» انتخاب شد."
            AssistantChatStore.addMessage(context, current.id, false, r)
            messages = AssistantChatStore.messages(context, current.id)
        }
    }

    Column(Modifier.fillMaxSize().background(Background)) {
        ScreenTopBar(title = "دستیار هوشمند", color = color, onBack = onBack)
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(current.title, Modifier.weight(1f), color = TextPrimary)
            IconButton(onClick = { showChats = true }) { Text("☰") }
            IconButton(onClick = { newChat() }) {
                Icon(Icons.Filled.Add, "گفتگوی جدید", tint = color)
            }
            IconButton(onClick = { online = !online }) {
                Text(if (online) "🌐" else "⚡")
            }
            IconButton(onClick = { showSettings = true }) {
                Icon(Icons.Filled.Settings, "تنظیمات", tint = color)
            }
        }
        if (online) {
            Text(
                "حالت تفکر آنلاین",
                color = color,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 14.dp)
            )
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(messages) { line ->
                val bg = if (line.fromUser) color.copy(alpha = .18f) else SurfaceColor
                Box(
                    Modifier.fillMaxWidth(),
                    contentAlignment = if (line.fromUser) Alignment.CenterEnd else Alignment.CenterStart
                ) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = bg,
                        modifier = Modifier.widthIn(max = 330.dp)
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            if (!line.fromUser) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Filled.SmartToy,
                                        null,
                                        tint = color,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        "دستیار",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = color
                                    )
                                }
                                Spacer(Modifier.height(4.dp))
                            }
                            Text(line.text, color = TextPrimary)
                        }
                    }
                }
            }
            if (loading) {
                item {
                    Text(
                        "در حال فکر کردن…",
                        color = TextSecondary,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        }
        selectedFileName?.let {
            Text(
                "📎 $it",
                color = TextSecondary,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(onClick = { picker.launch("*/*") }) {
                Icon(Icons.Filled.AttachFile, "انتخاب فایل", tint = color)
            }
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("چه کاری انجام بدهم؟", color = TextSecondary) },
                singleLine = true
            )
            IconButton(
                onClick = { send(input) },
                enabled = !loading,
                colors = IconButtonDefaults.iconButtonColors(containerColor = color)
            ) {
                Icon(Icons.Filled.Send, "ارسال", tint = Color.White)
            }
        }
    }

    if (showChats) {
        AlertDialog(
            onDismissRequest = { showChats = false },
            title = { Text("گفتگوها") },
            text = {
                Column {
                    chats.forEach { c ->
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = { selectChat(c) },
                                modifier = Modifier.weight(1f)
                            ) { Text(c.title) }
                            IconButton(
                                onClick = {
                                    AssistantChatStore.delete(context, c.id)
                                    chats = AssistantChatStore.chats(context)
                                    if (c.id == current.id) {
                                        val n = AssistantChatStore.ensureDefault(context)
                                        current = n
                                        messages = AssistantChatStore.messages(context, n.id)
                                    }
                                }
                            ) {
                                Icon(Icons.Filled.Delete, null)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showChats = false }) { Text("بستن") }
            }
        )
    }
    if (showSettings) {
        PermissionSettingsDialog(context, onDismiss = { showSettings = false })
    }
}

@Composable
private fun PermissionSettingsDialog(context: Context, onDismiss: () -> Unit) {
    var url by remember { mutableStateOf(OnlineAiClient.backendUrl(context)) }
    var tick by remember { mutableStateOf(0) }
    val labels = mapOf(
        "navigation" to "باز کردن صفحات",
        "tasks" to "مدیریت تسک‌ها",
        "calculations" to "محاسبات",
        "files_read" to "خواندن فایل",
        "finance_write" to "تغییرات مالی",
        "delete" to "حذف داده",
        "online_ai" to "هوش مصنوعی آنلاین"
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تنظیمات دستیار") },
        text = {
            Column {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("آدرس سرور AI") },
                    singleLine = true
                )
                Spacer(Modifier.height(10.dp))
                Text("مجوزها", style = MaterialTheme.typography.titleSmall)
                labels.forEach { (k, l) ->
                    var v by remember(tick, k) { mutableStateOf(AssistantPermissionStore.get(context, k)) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(l, Modifier.weight(1f))
                        DropdownPermission(v) { nv ->
                            AssistantPermissionStore.set(context, k, nv)
                            tick++
                        }
                    }
                }
                Text(
                    "کلید API را داخل برنامه قرار نده؛ فقط آدرس بک‌اند را وارد کن.",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                OnlineAiClient.setBackendUrl(context, url)
                onDismiss()
            }) { Text("ذخیره") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("لغو") }
        }
    )
}

@Composable
private fun DropdownPermission(value: AssistantPermission, onChange: (AssistantPermission) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }) {
            Text(
                when (value) {
                    AssistantPermission.AUTO -> "خودکار"
                    AssistantPermission.ASK -> "تأیید"
                    AssistantPermission.FORBIDDEN -> "ممنوع"
                }
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            AssistantPermission.values().forEach { v ->
                DropdownMenuItem(
                    text = {
                        Text(
                            when (v) {
                                AssistantPermission.AUTO -> "خودکار"
                                AssistantPermission.ASK -> "تأیید قبل اجرا"
                                AssistantPermission.FORBIDDEN -> "ممنوع"
                            }
                        )
                    },
                    onClick = {
                        onChange(v)
                        open = false
                    }
                )
            }
        }
    }
}
