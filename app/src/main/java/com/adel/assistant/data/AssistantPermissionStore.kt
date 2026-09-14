package com.adel.assistant.data

import android.content.Context
import org.json.JSONObject
import java.io.File

enum class AssistantPermission { AUTO, ASK, FORBIDDEN }
object AssistantPermissionStore {
    private val defaults=mapOf("navigation" to AssistantPermission.AUTO,"tasks" to AssistantPermission.AUTO,"calculations" to AssistantPermission.AUTO,"files_read" to AssistantPermission.ASK,"finance_write" to AssistantPermission.ASK,"delete" to AssistantPermission.ASK,"online_ai" to AssistantPermission.AUTO)
    private fun file(c:Context)=File(c.filesDir,"assistant/permissions.json").also{it.parentFile?.mkdirs()}
    fun get(c:Context,key:String):AssistantPermission{val o=runCatching{JSONObject(if(file(c).exists())file(c).readText() else "{}")} .getOrElse{JSONObject()};return runCatching{AssistantPermission.valueOf(o.optString(key,defaults[key]?.name?:"ASK"))}.getOrDefault(AssistantPermission.ASK)}
    fun set(c:Context,key:String,v:AssistantPermission){val o=runCatching{JSONObject(if(file(c).exists())file(c).readText() else "{}")} .getOrElse{JSONObject()};o.put(key,v.name);file(c).writeText(o.toString())}
    fun all(c:Context)=defaults.keys.associateWith{get(c,it)}
}
