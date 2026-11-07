package com.adel.assistant.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adel.assistant.data.ProjectEventEntry
import com.adel.assistant.data.ProjectEventStore
import com.adel.assistant.data.ProjectStore
import com.adel.assistant.ui.ScreenTopBar
import com.adel.assistant.ui.appearanceCardColor
import com.adel.assistant.ui.appearanceScreenBg
import com.adel.assistant.ui.appearanceTextMuted
import com.adel.assistant.ui.appearanceTextPrimary
import com.adel.assistant.ui.appearanceTextSecondary
import com.adel.assistant.ui.components.AdelOutlinedTextField

@Composable
fun ProjectEventsScreen(color: Color, onBack: () -> Unit) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var employer by remember { mutableStateOf("") }
    var statusMsg by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf("search") }
    var focusName by remember { mutableStateOf("") }
    var focusEmployer by remember { mutableStateOf("") }
    var eventText by remember { mutableStateOf("") }
    var editingId by remember { mutableStateOf<String?>(null) }
    var tick by remember { mutableStateOf(0) }

    val nameQ = name.trim()
    val empQ = employer.trim()
    val allEvents: List<ProjectEventEntry> = remember(tick) { ProjectEventStore.all(context) }
    val projects = remember(tick) {
        try {
            ProjectStore.all(context)
        } catch (_: Exception) {
            emptyList()
        }
    }

    data class Group(val name: String, val employer: String, val events: List<ProjectEventEntry>)

    val groups: List<Group> = remember(nameQ, empQ, tick, allEvents, projects) {
        when {
            nameQ.isNotBlank() && empQ.isBlank() -> {
                val names = (projects.map { it.name.trim() } + allEvents.map { it.projectName.trim() })
                    .filter { it.contains(nameQ, ignoreCase = true) }
                    .distinct()
                names.map { nm ->
                    val emp = projects.firstOrNull { it.name.trim().equals(nm, true) }?.employer
                        ?: allEvents.firstOrNull { it.projectName.trim().equals(nm, true) }?.employer.orEmpty()
                    val evs = allEvents.filter { it.projectName.trim().equals(nm, true) }
                        .sortedByDescending { it.createdAt }
                    Group(nm, emp, evs)
                }.sortedByDescending { it.events.firstOrNull()?.createdAt ?: 0L }
            }
            empQ.isNotBlank() -> {
                val names = projects.filter { it.employer.contains(empQ, ignoreCase = true) }
                    .map { it.name.trim() }
                    .distinct()
                names.map { nm ->
                    val emp = projects.firstOrNull { it.name.trim().equals(nm, true) }?.employer.orEmpty()
                    val evs = allEvents.filter { it.projectName.trim().equals(nm, true) }
                        .sortedByDescending { it.createdAt }
                    Group(nm, emp, evs)
                }.sortedByDescending { it.events.firstOrNull()?.createdAt ?: 0L }
            }
            else -> emptyList()
        }
    }

    fun openRegister(nm: String, emp: String) {
        focusName = nm
        focusEmployer = emp
        name = nm
        employer = emp
        eventText = ""
        editingId = null
        mode = "register"
    }

    fun saveEvent() {
        val text = eventText.trim()
        if (focusName.isBlank()) {
            statusMsg = "نام پروژه لازم است"
            return
        }
        if (text.isBlank()) {
            statusMsg = "متن یادداشت خالی است"
            return
        }
        if (editingId != null) {
            val old = allEvents.firstOrNull { it.id == editingId } ?: return
            ProjectEventStore.save(context, old.copy(text = text))
            statusMsg = "ویرایش شد"
        } else {
            ProjectEventStore.save(
                context,
                ProjectEventEntry(
                    id = ProjectEventStore.nextId(context),
                    projectName = focusName.trim(),
                    employer = focusEmployer.trim(),
                    text = text
                )
            )
            statusMsg = "یادداشت ثبت شد"
        }
        eventText = ""
        editingId = null
        tick++
    }

    fun deleteId(id: String) {
        ProjectEventStore.delete(context, id)
        if (editingId == id) {
            editingId = null
            eventText = ""
        }
        statusMsg = "حذف شد"
        tick++
    }

    val focusList: List<ProjectEventEntry> = remember(focusName, tick) {
        ProjectEventStore.forProject(context, focusName)
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(appearanceScreenBg())
            .padding(horizontal = 16.dp)
    ) {
        ScreenTopBar(
            title = "یادداشت پروژه",
            color = color,
            onBack = {
                if (mode == "register") mode = "search" else onBack()
            }
        )
        Spacer(Modifier.height(8.dp))

        if (mode == "search") {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AdelOutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("نام پروژه") },
                    modifier = Modifier.weight(1f)
                )
                AdelOutlinedTextField(
                    value = employer,
                    onValueChange = { employer = it },
                    label = { Text("کارفرما") },
                    modifier = Modifier.weight(1f)
                )
            }
            if (statusMsg.isNotBlank()) {
                Text(
                    statusMsg,
                    color = appearanceTextSecondary(),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
            Spacer(Modifier.height(8.dp))
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f)
            ) {
                if (nameQ.isBlank() && empQ.isBlank()) {
                    item {
                        Text(
                            "نام پروژه یا کارفرما را بنویسید. فقط یادداشت‌های همین برنامه نمایش داده می‌شود (نه توضیحات جلسه ثبت پروژه).",
                            color = appearanceTextMuted(),
                            fontSize = 12.sp
                        )
                    }
                }
                items(groups, key = { it.name }) { g ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = appearanceCardColor(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(
                                    Modifier
                                        .weight(1f)
                                        .clickable { openRegister(g.name, g.employer) }
                                ) {
                                    Text(
                                        g.name,
                                        color = appearanceTextPrimary(),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                    if (g.employer.isNotBlank()) {
                                        Text(g.employer, color = appearanceTextSecondary(), fontSize = 11.sp)
                                    }
                                    Text(
                                        "${g.events.size} مورد ثبت‌شده",
                                        color = appearanceTextMuted(),
                                        fontSize = 11.sp
                                    )
                                }
                                IconButton(onClick = { openRegister(g.name, g.employer) }) {
                                    Icon(Icons.Filled.Add, contentDescription = null, tint = color)
                                }
                            }
                            g.events.take(12).forEach { e: ProjectEventEntry ->
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(top = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        e.text,
                                        color = appearanceTextPrimary(),
                                        fontSize = 12.sp,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 2
                                    )
                                    IconButton(
                                        onClick = {
                                            openRegister(g.name, g.employer)
                                            editingId = e.id
                                            eventText = e.text
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            Icons.Filled.Edit,
                                            contentDescription = null,
                                            tint = color,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = { deleteId(e.id) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            Icons.Filled.Delete,
                                            contentDescription = null,
                                            tint = Color(0xFFCF6679),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            Text("پروژه: $focusName", color = appearanceTextPrimary(), fontWeight = FontWeight.Bold)
            if (focusEmployer.isNotBlank()) {
                Text("کارفرما: $focusEmployer", color = appearanceTextSecondary(), fontSize = 12.sp)
            }
            Spacer(Modifier.height(8.dp))
            AdelOutlinedTextField(
                value = eventText,
                onValueChange = { eventText = it },
                label = { Text(if (editingId != null) "ویرایش یادداشت" else "متن یادداشت جدید") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Button(
                    onClick = { saveEvent() },
                    colors = ButtonDefaults.buttonColors(containerColor = color),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (editingId != null) "ذخیره ویرایش" else "ثبت")
                }
                OutlinedButton(onClick = { mode = "search"; editingId = null }) {
                    Text("بازگشت")
                }
            }
            if (statusMsg.isNotBlank()) {
                Text(
                    statusMsg,
                    color = appearanceTextSecondary(),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
            Spacer(Modifier.height(10.dp))
            Text("موارد ثبت‌شده (جدید → قدیم)", color = appearanceTextSecondary(), fontSize = 12.sp)
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .weight(1f)
                    .padding(top = 6.dp)
            ) {
                items(focusList, key = { it.id }) { e ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = appearanceCardColor(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                e.text,
                                color = appearanceTextPrimary(),
                                fontSize = 13.sp,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = {
                                editingId = e.id
                                eventText = e.text
                            }) {
                                Icon(Icons.Filled.Edit, contentDescription = null, tint = color)
                            }
                            IconButton(onClick = { deleteId(e.id) }) {
                                Icon(Icons.Filled.Delete, contentDescription = null, tint = Color(0xFFCF6679))
                            }
                        }
                    }
                }
            }
        }
    }
}
