package com.adel.assistant.ai

import android.content.Context
import com.adel.assistant.data.AssistantPermissionStore
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONArray
import org.json.JSONObject

object OnlineAiClient {
    private const val PREF="assistant_online"
    private const val URL_KEY="backend_url"
    fun backendUrl(c:Context):String=c.getSharedPreferences(PREF,0).getString(URL_KEY,"")?.trim().orEmpty()
    fun setBackendUrl(c:Context,url:String){c.getSharedPreferences(PREF,0).edit().putString(URL_KEY,url.trim()).apply()}

    fun ask(context:Context,messages:List<Pair<Boolean,String>>,memory:List<String>):String{
        if(AssistantPermissionStore.get(context,"online_ai")==com.adel.assistant.data.AssistantPermission.FORBIDDEN) return "دسترسی هوش مصنوعی آنلاین در تنظیمات بسته شده است."
        val endpoint=backendUrl(context); if(endpoint.isBlank()) return "برای حالت تفکر آنلاین، آدرس سرور را در ⚙️ تنظیمات دستیار وارد کن."
        val payload=JSONObject().put("messages",JSONArray().apply{messages.forEach{put(JSONObject().put("role",if(it.first)"user" else "assistant").put("content",it.second))}}).put("memory",JSONArray(memory))
        val conn=(URL(endpoint).openConnection() as HttpURLConnection).apply{requestMethod="POST";connectTimeout=15000;readTimeout=45000;doOutput=true;setRequestProperty("Content-Type","application/json")}
        return try{conn.outputStream.use{it.write(payload.toString().toByteArray(Charsets.UTF_8))};val code=conn.responseCode;val body=(if(code in 200..299)conn.inputStream else conn.errorStream).bufferedReader().use{it.readText()};if(code !in 200..299)"خطای سرور آنلاین ($code)" else JSONObject(body).optString("text").ifBlank{"پاسخ معتبری از سرور دریافت نشد."}}catch(e:Exception){"اتصال به هوش مصنوعی آنلاین برقرار نشد: ${e.message?:e.javaClass.simpleName}"}finally{conn.disconnect()}
    }
}
