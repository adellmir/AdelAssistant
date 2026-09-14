package com.adel.assistant.data

import android.content.Context
import org.json.JSONArray
import java.io.File

object AssistantMemoryStore {
    private fun file(c:Context)=File(c.filesDir,"assistant/memory.json").also{it.parentFile?.mkdirs()}
    fun load(c:Context):MutableList<String>{val f=file(c);if(!f.exists())return mutableListOf();return runCatching{val a=JSONArray(f.readText());MutableList(a.length()){a.getString(it)}}.getOrElse{mutableListOf()}}
    fun add(c:Context,text:String){val t=text.trim();if(t.isBlank())return;val a=load(c);a.remove(t);a.add(0,t.take(300));while(a.size>100)a.removeLast();val j=JSONArray();a.forEach(j::put);file(c).writeText(j.toString())}
    fun clear(c:Context){file(c).delete()}
}
