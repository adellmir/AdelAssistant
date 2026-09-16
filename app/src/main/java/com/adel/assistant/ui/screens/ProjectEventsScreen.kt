package com.adel.assistant.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.adel.assistant.data.ProjectEntry
import com.adel.assistant.data.ProjectStore
import com.adel.assistant.ui.ScreenTopBar
import com.adel.assistant.ui.theme.Background
import com.adel.assistant.ui.theme.Surface as SurfaceColor

@Composable
fun ProjectEventsScreen(color: Color, onBack: () -> Unit) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var employer by remember { mutableStateOf("") }
    var results by remember { mutableStateOf(listOf<ProjectEntry>()) }
    var editingRow by remember { mutableStateOf<String?>(null) }
    var editText by remember { mutableStateOf("") }
    var statusMsg by remember { mutableStateOf("") }

    fun search() {
        results = ProjectStore.search(context, name, employer)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .padding(horizontal = 20.dp)
    ) {
        ScreenTopBar(title = "ثبت وقایع پروژه", color = color, onBack = onBack)
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("نام پروژه") }, modifier = Modifier.weight(1f))
            OutlinedTextField(value = employer, onValueChange = { employer = it }, label = { Text("کارفرما") }, modifier = Modifier.weight(1f))
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = { search() }, colors = ButtonDefaults.buttonColors(containerColor = color), modifier = Modifier.fillMaxWidth()) {
            Text("جستجو")
        }

        if (statusMsg.isNotBlank()) {
            Text(statusMsg, style = MaterialTheme.typography.bodySmall, color = Color(0xFFAAB697), modifier = Modifier.padding(top = 4.dp))
        }

        Spacer(modifier = Modifier.height(12.dp))
        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(results.take(6)) { p ->
                Surface(shape = RoundedCornerShape(10.dp), color = SurfaceColor, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("${p.name} — ${p.employer}", style = MaterialTheme.typography.bodySmall)
                                Text("${p.day}/${p.month}", style = MaterialTheme.typography.bodySmall, color = Color(0xFF7C8A6B))
                            }
                            IconButton(onClick = {
                                editingRow = p.row
                                editText = p.description
                            }) {
                                Icon(Icons.Outlined.Edit, contentDescription = "ویرایش", tint = color)
                            }
                        }
                        if (editingRow == p.row) {
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedTextField(
                                value = editText, onValueChange = { editText = it },
                                label = { Text("متن واقعه") }, modifier = Modifier.fillMaxWidth()
                            )
                            Button(
                                onClick = {
                                    ProjectStore.save(context, p.copy(description = editText))
                                    editingRow = null
                                    statusMsg = "ثبت شد"
                                    search()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = color),
                                modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                            ) { Text("ثبت") }
                        } else {
                            Text(p.description, style = MaterialTheme.typography.bodySmall, color = Color(0xFF7C8A6B))
                        }
                    }
                }
            }
        }
    }
}
