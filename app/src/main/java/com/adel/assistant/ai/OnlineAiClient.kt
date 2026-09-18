package com.adel.assistant.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object OnlineAiClient {
    private const val BACKEND_URL = "https://adelassistant-backend.fastapicloud.dev"

    /**
     * @param domainContext دانش‌نامه + اسنپ‌شات دادهٔ زنده (از DomainCatalog)
     */
    suspend fun chat(
        messages: List<Pair<String, String>>,
        memory: String = "",
        domainContext: String = ""
    ): String = withContext(Dispatchers.IO) {
        val url = URL("$BACKEND_URL/chat")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15000
            readTimeout = 95000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            setRequestProperty("Accept", "application/json")
        }

        try {
            val messageArray = org.json.JSONArray()
            // system-like first user message with domain (backend may not support system role)
            if (domainContext.isNotBlank()) {
                messageArray.put(JSONObject().apply {
                    put("role", "user")
                    put("content", "[CONTEXT_PROGRAM]\n$domainContext\n[/CONTEXT_PROGRAM]\nدستور: فقط با همین زمینه جواب بده. اگر عمل مشخص است مختصات/مسیر را عدد بده.")
                })
                messageArray.put(JSONObject().apply {
                    put("role", "assistant")
                    put("content", "متوجه شدم. بر اساس داده و عملیات AdelAssistant جواب می‌دهم.")
                })
            }
            messages.takeLast(16).forEach { (role, content) ->
                // skip thinking placeholder
                if (content.startsWith("⏳")) return@forEach
                messageArray.put(JSONObject().apply {
                    put("role", if (role == "assistant") "assistant" else "user")
                    put("content", content)
                })
            }
            val body = JSONObject().apply {
                put("messages", messageArray)
                put("memory", memory)
                if (domainContext.isNotBlank()) put("domain", domainContext.take(12000))
            }.toString()

            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val detail = runCatching { JSONObject(response).optString("detail") }.getOrNull()
                throw IllegalStateException(detail?.takeIf { it.isNotBlank() } ?: "خطای سرور ($code)")
            }
            val text = JSONObject(response).optString("text")
            if (text.isBlank()) throw IllegalStateException("پاسخ خالی از سرور دریافت شد")
            text
        } finally {
            connection.disconnect()
        }
    }
}
