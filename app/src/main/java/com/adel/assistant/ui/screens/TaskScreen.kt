package com.adel.assistant.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.adel.assistant.data.TaskItem
import com.adel.assistant.data.TaskStore
import com.adel.assistant.ui.ScreenTopBar
import com.adel.assistant.ui.theme.Background
import com.adel.assistant.ui.theme.TextPrimary
import java.io.OutputStreamWriter

@Composable
fun TaskScreen(
    title: String,
    storeName: String,
    color: Color,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var tasks by remember { mutableStateOf(TaskStore.load(context, storeName)) }
    var newTask by remember { mutableStateOf("") }
    var editingIndex by remember { mutableStateOf<Int?>(null) }
    var editText by remember { mutableStateOf("") }
    var showMenu by remember { mutableStateOf(false) }
    var showCompleted by remember { mutableStateOf(true) }

    fun persist(list: MutableList<TaskItem>) {
        tasks = list
        TaskStore.save(context, storeName, list)
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            context.contentResolver.openOutputStream(uri)?.use { stream ->
                OutputStreamWriter(stream).use { it.write(TaskStore.raw(context, storeName)) }
            }
            Toast.makeText(context, "فایل CSV ذخیره شد", Toast.LENGTH_SHORT).show()
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
                tasks = TaskStore.importRaw(context, storeName, reader.readText())
            }
            Toast.makeText(context, "تسک‌ها بارگذاری شدند", Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(Background).padding(horizontal = 20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.weight(1f)) {
                ScreenTopBar(title = title, color = color, onBack = onBack)
            }
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Filled.Settings, contentDescription = "تنظیمات", tint = color)
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("💾 ذخیره در CSV") },
                        onClick = { showMenu = false; exportLauncher.launch("$storeName.csv") }
                    )
                    DropdownMenuItem(
                        text = { Text("📂 بارگذاری از CSV") },
                        onClick = { showMenu = false; importLauncher.launch(arrayOf("text/csv", "text/plain", "text/*")) }
                    )
                    DropdownMenuItem(
                        text = { Text(if (showCompleted) "مخفی کردن انجام‌شده‌ها" else "نمایش انجام‌شده‌ها") },
                        onClick = { showCompleted = !showCompleted; showMenu = false }
                    )
                    DropdownMenuItem(
                        text = { Text("🗑 حذف تسک‌های انجام‌شده") },
                        onClick = {
                            persist(tasks.filterNot { it.completed }.toMutableList())
                            showMenu = false
                        }
                    )
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = newTask,
                onValueChange = { newTask = it },
                modifier = Modifier.weight(1f),
                label = { Text("تسک جدید") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    if (newTask.isNotBlank()) {
                        persist((tasks + TaskItem(newTask.trim())).toMutableList())
                        newTask = ""
                    }
                })
            )
            Spacer(Modifier.width(8.dp))
            FloatingActionButton(
                onClick = {
                    if (newTask.isNotBlank()) {
                        persist((tasks + TaskItem(newTask.trim())).toMutableList())
                        newTask = ""
                    }
                },
                containerColor = color,
                contentColor = Color.White
            ) { Icon(Icons.Filled.Add, contentDescription = "افزودن") }
        }

        Spacer(Modifier.height(12.dp))

        val visibleTasks = if (showCompleted) tasks else tasks.filterNot { it.completed }
        if (visibleTasks.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                Text("هنوز تسکی ثبت نشده است", color = Color.Gray)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
                itemsIndexed(visibleTasks, key = { _, item -> item.createdAt }) { _, task ->
                    val originalIndex = tasks.indexOfFirst { it.createdAt == task.createdAt }
                    Card(colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = task.completed,
                                onCheckedChange = { checked ->
                                    if (originalIndex >= 0) {
                                        val updated = tasks.toMutableList()
                                        updated[originalIndex] = task.copy(completed = checked)
                                        persist(updated)
                                    }
                                },
                                colors = CheckboxDefaults.colors(checkedColor = color)
                            )
                            Text(
                                text = task.title,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    textDecoration = if (task.completed) TextDecoration.LineThrough else TextDecoration.None
                                ),
                                color = if (task.completed) Color.Gray else Color(0xFF222222)
                            )
                            IconButton(onClick = {
                                if (originalIndex >= 0) {
                                    editingIndex = originalIndex
                                    editText = task.title
                                }
                            }) {
                                Icon(Icons.Filled.Edit, contentDescription = "ویرایش", tint = color)
                            }
                            IconButton(onClick = {
                                if (originalIndex >= 0) {
                                    val updated = tasks.toMutableList()
                                    updated.removeAt(originalIndex)
                                    persist(updated)
                                }
                            }) {
                                Icon(Icons.Filled.Delete, contentDescription = "حذف", tint = Color(0xFFB3261E))
                            }
                        }
                    }
                }
            }
        }
    }


    if (editingIndex != null) {
        AlertDialog(
            onDismissRequest = { editingIndex = null },
            title = { Text("ویرایش تسک") },
            text = {
                OutlinedTextField(
                    value = editText,
                    onValueChange = { editText = it },
                    label = { Text("متن تسک") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = false
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val idx = editingIndex
                    if (idx != null && editText.isNotBlank()) {
                        val updated = tasks.toMutableList()
                        if (idx in updated.indices) {
                            updated[idx] = updated[idx].copy(title = editText.trim())
                            persist(updated)
                        }
                    }
                    editingIndex = null
                }) { Text("ذخیره") }
            },
            dismissButton = {
                TextButton(onClick = { editingIndex = null }) { Text("انصراف") }
            }
        )
    }
}
