package com.adel.assistant.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object AssistantChatStore {
    data class Chat(val id: String, val title: String, val createdAt: Long)
    data class Message(val chatId: String, val fromUser: Boolean, val text: String, val at: Long)

    private fun dir(context: Context): File = File(context.filesDir, "assistant").also { it.mkdirs() }
    private fun chatsFile(context: Context) = File(dir(context), "chats.json")
    private fun messagesFile(context: Context) = File(dir(context), "messages.json")

    fun chats(context: Context): MutableList<Chat> {
        val f = chatsFile(context); if (!f.exists()) return mutableListOf()
        return runCatching {
            val a = JSONArray(f.readText())
            MutableList(a.length()) { i -> val o=a.getJSONObject(i); Chat(o.getString("id"),o.getString("title"),o.getLong("createdAt")) }
        }.getOrElse { mutableListOf() }
    }
    fun create(context: Context, title: String = "گفتگوی جدید"): Chat {
        val c=Chat(System.currentTimeMillis().toString(),title,System.currentTimeMillis()); val all=chats(context); all.add(0,c); saveChats(context,all); return c
    }
    fun ensureDefault(context: Context): Chat = chats(context).firstOrNull() ?: create(context)
    fun delete(context: Context,id:String){ saveChats(context,chats(context).filterNot{it.id==id}); saveMessages(context, allMessages(context).filterNot{it.chatId==id}) }
    fun messages(context: Context,id:String): MutableList<Message>{
        val f=messagesFile(context); if(!f.exists()) return mutableListOf()
        return runCatching { val a=JSONArray(f.readText()); MutableList(a.length()){i->val o=a.getJSONObject(i); Message(o.getString("chatId"),o.getBoolean("fromUser"),o.getString("text"),o.getLong("at"))}.filter{it.chatId==id}.toMutableList() }.getOrElse{mutableListOf()}
    }
    fun addMessage(context: Context,id:String,fromUser:Boolean,text:String){ val all=allMessages(context); all.add(Message(id,fromUser,text,System.currentTimeMillis())); saveMessages(context,all) }
    fun rename(context:Context,id:String,title:String){ saveChats(context,chats(context).map{if(it.id==id)it.copy(title=title.take(60))else it}) }
    private fun allMessages(context:Context):MutableList<Message>{
        val f=messagesFile(context); if(!f.exists()) return mutableListOf(); return runCatching{val a=JSONArray(f.readText()); MutableList(a.length()){i->val o=a.getJSONObject(i);Message(o.getString("chatId"),o.getBoolean("fromUser"),o.getString("text"),o.getLong("at"))}}.getOrElse{mutableListOf()}
    }
    private fun saveChats(context:Context,list:List<Chat>){val a=JSONArray();list.forEach{a.put(JSONObject().put("id",it.id).put("title",it.title).put("createdAt",it.createdAt))};chatsFile(context).writeText(a.toString())}
    private fun saveMessages(context:Context,list:List<Message>){val a=JSONArray();list.forEach{a.put(JSONObject().put("chatId",it.chatId).put("fromUser",it.fromUser).put("text",it.text).put("at",it.at))};messagesFile(context).writeText(a.toString())}
}
