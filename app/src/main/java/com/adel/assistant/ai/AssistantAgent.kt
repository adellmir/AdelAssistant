package com.adel.assistant.ai

import android.content.Context
import com.adel.assistant.data.ProjectStore
import com.adel.assistant.data.TaskItem
import com.adel.assistant.data.TaskStore
import com.adel.assistant.data.TunnelFinanceStore
import com.adel.assistant.data.TunnelReportStore
import com.adel.assistant.data.formatMoney
import com.adel.assistant.navigation.Routes
import java.util.Locale

data class AgentReply(
    val text: String,
    val navigateTo: String? = null,
    val action: String? = null
)

/**
 * موتور دستیار داخلی AdelAssistant.
 * بدون API خارجی؛ درخواست محاوره‌ای را به Action واقعی برنامه تبدیل می‌کند.
 */
object AssistantAgent {
    private enum class PendingType { TASK_CATEGORY, TASK_DELETE_CONFIRM }
    private data class Pending(
        val type: PendingType,
        val title: String = "",
        val store: String? = null,
        val index: Int = -1
    )
    private var pending: Pending? = null

    /** فایل انتخاب‌شده از چت دستیار (نام نمایشی) */
    @Volatile
    private var selectedFileName: String? = null

    fun setSelectedFile(name: String?) {
        selectedFileName = name?.trim()?.ifBlank { null }
    }

    fun getSelectedFile(): String? = selectedFileName

    fun clearSelectedFile() {
        selectedFileName = null
    }

    fun handle(context: Context, userMessage: String): AgentReply {
        val msg = normalize(userMessage)
        if (msg.isBlank()) return AgentReply("پیامت را بنویس. مثلاً «فردا برای تونل تسک برداشت مقطع ثبت کن». ")

        resolvePending(context, msg)?.let { return it }
        if (isHelp(msg)) return AgentReply(helpText())
        fileIntent(msg)?.let { return it }
        navIntent(msg)?.let { return it }
        taskIntent(context, msg)?.let { return it }
        statsIntent(context, msg)?.let { return it }
        if (hasAny(msg, listOf("امروز", "برنامه امروز", "کارهای امروز"))) return AgentReply(todayPlan(context))

        return AgentReply("منظورت را کامل متوجه نشدم. می‌توانی محاوره‌ای بنویسی؛ مثلاً «برو درون‌یابی»، «کارهای باز تونل چیه؟»، «فردا برای پروژه تسک کنترل نقاط ثبت کن» یا «وضعیت مالی پروژه‌ها رو خلاصه کن». ")
    }

    /** درخواست‌های مربوط به فایل انتخاب‌شده در چت */
    private fun fileIntent(msg: String): AgentReply? {
        val wantsConvert = hasAny(msg, listOf("تبدیل", "dxf", "به dxf", "کنورت", "convert"))
        val wantsFileInfo = hasAny(msg, listOf("فایل", "این فایل", "فایل انتخاب"))
        if (!wantsConvert && !wantsFileInfo) return null
        val name = selectedFileName
        if (name.isNullOrBlank()) {
            return AgentReply("هنوز فایلی انتخاب نشده. از دکمه 📎 یک فایل انتخاب کن، بعد بگو «به DXF تبدیل کن».")
        }
        if (wantsConvert) {
            return AgentReply(
                "فایل «$name» انتخاب شده است. برای تبدیل به DXF صفحهٔ ترسیم/مبدل را باز می‌کنم؛ فایل را آنجا دوباره انتخاب یا نتیجه را بگیر.",
                navigateTo = Routes.TOOL_DXF
            )
        }
        return AgentReply("فایل فعال: «$name». می‌توانی بگویی «به DXF تبدیل کن» یا صفحهٔ مربوط را باز کن.")
    }

    private fun resolvePending(context: Context, msg: String): AgentReply? {
        val p = pending ?: return null
        when (p.type) {
            PendingType.TASK_CATEGORY -> {
                val store = categoryStore(msg)
                if (store != null) {
                    pending = null
                    return addTask(context, store, p.title)
                }
                if (isCancel(msg)) { pending = null; return AgentReply("عملیات لغو شد.") }
                return AgentReply("برای بخش «تونل» یا «پروژه» ثبت شود؟")
            }
            PendingType.TASK_DELETE_CONFIRM -> {
                if (isConfirm(msg)) {
                    val all = TaskStore.load(context, p.store!!)
                    if (p.index in all.indices) {
                        val removed = all.removeAt(p.index)
                        TaskStore.save(context, p.store, all)
                        pending = null
                        return AgentReply("تسک «${removed.title}» حذف شد 🗑️")
                    }
                    pending = null
                    return AgentReply("تسک دیگر پیدا نشد.")
                }
                if (isCancel(msg)) { pending = null; return AgentReply("حذف لغو شد.") }
                return AgentReply("برای حذف «${p.title}» فقط «بله» یا «لغو» بنویس.")
            }
        }
    }

    private fun normalize(s: String): String = s.trim().replace('ي','ی').replace('ك','ک')
        .replace(Regex("\\s+"), " ").lowercase(Locale.US)
    private fun hasAny(msg: String, keys: List<String>) = keys.any { msg.contains(it) }
    private fun isConfirm(msg: String) = msg in setOf("بله","اره","آره","تایید","تأیید","ok","باشه")
    private fun isCancel(msg: String) = msg in setOf("نه","لغو","بیخیال","کنسل")
    private fun isHelp(msg: String) = hasAny(msg, listOf("کمک","راهنما","چی میتونی","چه کار میتونی","سلام","درود"))

    private fun helpText() = """
🤖 دستیار AdelAssistant
می‌توانی محاوره‌ای درخواست بدهی:
• «برو بخش درون‌یابی»
• «کارهای باز تونل چیه؟»
• «فردا برای تونل تسک برداشت مقطع ثبت کن»
• «تسک کنترل نقاط رو انجام‌شده بزن»
• «تسک برداشت مقطع رو حذف کن»
• «آمار کلی / وضعیت مالی پروژه‌ها»
• «بیشترین مطالبات پروژه‌ها رو بگو»
• «امروز چه کارهایی دارم؟»
""".trimIndent()

    private fun navIntent(msg: String): AgentReply? {
        if (!hasAny(msg, listOf("برو","باز کن","صفحه","قسمت","بخش","نمایش بده"))) return null
        val map = listOf(
            listOf("گزارش روزانه","گزارش تونل") to Routes.SURVEY_TUNNEL_REPORT,
            listOf("چینیج","کیلومتر") to Routes.SURVEY_TUNNEL_CHAINAGE,
            listOf("وضعیت تونل") to Routes.SURVEY_TUNNEL_STATUS,
            listOf("وقایع تونل","رویداد تونل") to Routes.SURVEY_TUNNEL_EVENTS,
            listOf("تسک تونل","کارهای تونل") to Routes.SURVEY_TUNNEL_TASKS,
            listOf("ثبت پروژه") to Routes.SURVEY_PROJECT_REGISTER,
            listOf("وقایع پروژه","رویداد پروژه") to Routes.SURVEY_PROJECT_EVENTS,
            listOf("تقویم") to Routes.SURVEY_PROJECT_CALENDAR,
            listOf("تسک پروژه","کارهای پروژه") to Routes.SURVEY_PROJECT_TASKS,
            listOf("کارکرد") to Routes.FIN_TUNNEL_WORKLOG,
            listOf("دریافتی تونل") to Routes.FIN_TUNNEL_RECEIPTS,
            listOf("خلاصه تونل","مطالبات تونل") to Routes.FIN_TUNNEL_SUMMARY,
            listOf("فاکتور") to Routes.FIN_PROJECT_INVOICE,
            listOf("ثبت دریافتی","دریافتی پروژه") to Routes.FIN_PROJECT_RECEIPT,
            listOf("مطالبات") to Routes.FIN_PROJECT_RECEIVABLES,
            listOf("وضعیت مالی") to Routes.FIN_PROJECT_STATUS,
            listOf("مکان","مختصات فعلی") to Routes.TOOL_LOCATION,
            listOf("درون","فاصله دو نقطه") to Routes.TOOL_INTERPOLATE,
            listOf("مساحت","محیط") to Routes.TOOL_AREA,
            listOf("حجم","احجام") to Routes.TOOL_VOLUME,
            listOf("نمایش نقشه","پیش نمایش") to Routes.TOOL_DXF_PREVIEW,
            listOf("ترسیم","dxf") to Routes.TOOL_DXF,
            listOf("مبدل","gsi","تبدیل فایل") to Routes.TOOL_GSI,
            listOf("تخلیه","دوربین") to Routes.TOOL_TOTAL_STATION,
            listOf("پشتیبان","بکاپ") to Routes.TOOL_BACKUP
        )
        map.firstOrNull { (keys, _) -> keys.any { msg.contains(it) } }?.let {
            return AgentReply("باشه، صفحه مربوط را باز می‌کنم.", it.second, "navigate")
        }
        return null
    }

    private fun categoryStore(msg: String): String? = when {
        hasAny(msg, listOf("تونل","tunnel")) && !msg.contains("پروژه") -> "tunnel_tasks"
        hasAny(msg, listOf("پروژه","project")) && !msg.contains("تونل") -> "project_tasks"
        else -> null
    }

    private fun taskIntent(context: Context, msg: String): AgentReply? {
        val store = categoryStore(msg)
        val mentionsTask = hasAny(msg, listOf("تسک","وظیفه","کار باقی","کار باز","یادآور"))
        val create = hasAny(msg, listOf("ثبت کن","اضافه کن","بساز","ایجاد کن","تسک جدید"))
        val complete = hasAny(msg, listOf("انجام شده","انجام شده است","انجام شد","تیک بزن","تکمیل کن","تمام شد"))
        val delete = hasAny(msg, listOf("حذف کن","پاک کن","بنداز دور"))
        if (!mentionsTask && !create && !complete && !delete) return null

        if (delete) {
            val title = extractActionTitle(msg, listOf("تسک","حذف کن","پاک کن"))
            if (title.isBlank()) return AgentReply("نام تسکی که باید حذف شود را بگو.")
            val targets = if (store != null) listOf(store) else listOf("tunnel_tasks","project_tasks")
            for (s in targets) {
                val all = TaskStore.load(context,s)
                val i = all.indexOfFirst { it.title.contains(title, true) }
                if (i >= 0) {
                    pending = Pending(PendingType.TASK_DELETE_CONFIRM, all[i].title, s, i)
                    return AgentReply("⚠️ تسک «${all[i].title}» حذف شود؟ بله / لغو")
                }
            }
            return AgentReply("تسکی با این عنوان پیدا نشد: $title")
        }

        if (complete) {
            val title = extractActionTitle(msg, listOf("تسک","انجام شد","انجام‌شده","تیک بزن","تکمیل کن","تمام شد"))
            if (title.isBlank()) return AgentReply("نام تسکی که باید انجام شود را بگو.")
            val targets = if (store != null) listOf(store) else listOf("tunnel_tasks","project_tasks")
            for (s in targets) {
                val all = TaskStore.load(context,s)
                val i = all.indexOfFirst { !it.completed && it.title.contains(title, true) }
                if (i >= 0) {
                    all[i] = all[i].copy(completed = true); TaskStore.save(context,s,all)
                    return AgentReply("تسک «${all[i].title}» انجام‌شده شد ✅")
                }
            }
            return AgentReply("تسک باز با این عنوان پیدا نشد.")
        }

        if (create) {
            val title = extractCreateTitle(msg)
            if (title.isBlank()) return AgentReply("عنوان تسک را بگو؛ مثلاً «برای تونل تسک برداشت مقطع ثبت کن». ")
            if (store == null) {
                pending = Pending(PendingType.TASK_CATEGORY, title)
                return AgentReply("تسک «$title» آماده است. برای تونل ثبت شود یا پروژه؟")
            }
            return addTask(context,store,title)
        }

        return AgentReply(taskStats(context,store))
    }

    private fun addTask(context: Context, store: String, title: String): AgentReply {
        val all = TaskStore.load(context,store)
        all.add(0,TaskItem(title=title)); TaskStore.save(context,store,all)
        return AgentReply("تسک «$title» در بخش ${if(store=="tunnel_tasks") "تونل" else "پروژه"} ثبت شد ✅")
    }

    private fun extractCreateTitle(msg: String): String {
        var s = msg
        listOf("برای تونل","برای پروژه","تسک","جدید","ثبت کن","اضافه کن","ایجاد کن","بساز","فردا","امروز","پس فردا").forEach { s=s.replace(it," ") }
        s=s.replace(Regex("\\s+")," ").trim().trim(':','-',' ')
        return s.take(160)
    }
    private fun extractActionTitle(msg: String, markers: List<String>): String {
        var s=msg
        markers.forEach { s=s.replace(it," ") }
        listOf("برای تونل","برای پروژه","رو","را","انجام کن","انجامش بده").forEach { s=s.replace(it," ") }
        return s.replace(Regex("\\s+")," ").trim().trim(':','-',' ').take(160)
    }

    private fun taskStats(context: Context, store: String?): String {
        fun block(name:String,label:String):String {
            val all=TaskStore.load(context,name); val open=all.filter{!it.completed}
            return buildString { appendLine("📋 تسک‌های $label"); appendLine("باز: ${open.size} | انجام‌شده: ${all.count{it.completed}} | کل: ${all.size}")
                open.take(8).forEachIndexed{i,t->appendLine("${i+1}. ☐ ${t.title}")}; if(open.isEmpty()) append("تسک بازی نیست.") }
        }
        return if(store=="tunnel_tasks") block(store,"تونل") else if(store=="project_tasks") block(store,"پروژه") else block("tunnel_tasks","تونل")+"\n\n"+block("project_tasks","پروژه")
    }

    private fun statsIntent(context: Context,msg:String):AgentReply? {
        val keys=listOf("آمار","خلاصه","وضعیت","چقدر","چند","مانده","مطالبه","دریافت","کارکرد","درآمد","پیشروی","بیشترین","میانگین")
        if(!hasAny(msg,keys)) return null
        return when {
            hasAny(msg,listOf("تسک","وظیفه"))->AgentReply(taskStats(context,categoryStore(msg)))
            hasAny(msg,listOf("تونل","شفت","پیشروی","کارکرد"))->AgentReply(tunnelStats(context))
            hasAny(msg,listOf("پروژه","مطالبه","کارفرما","فاکتور","دریافت"))->AgentReply(projectStats(context))
            else->AgentReply(statsOverview(context))
        }
    }

    fun statsOverview(context: Context)= "📊 آمار کلی AdelAssistant\n\n${tunnelStats(context)}\n\n${projectStats(context)}\n\n${taskStats(context,null)}"
    private fun tunnelStats(context: Context)=try { val s=TunnelFinanceStore.summary(context); val points=runCatching{TunnelReportStore.allPoints(context).size}.getOrNull(); buildString { appendLine("🚇 تونل"); appendLine("جمع کارکرد: ${formatMoney(s.sumPayable)}"); appendLine("جمع دریافتی: ${formatMoney(s.sumReceived)}"); appendLine("مانده: ${formatMoney(s.remaining)}"); points?.let{append("تعداد نقاط ثبت‌شده: $it")} } } catch(e:Exception){"🚇 خطا در خواندن داده تونل: ${e.message}"}
    private fun projectStats(context: Context)=try { val all=ProjectStore.all(context); val open=all.filter{it.remaining>0.0001}; buildString { appendLine("📁 پروژه‌ها"); appendLine("تعداد کل: ${all.size} | با مانده: ${open.size}"); appendLine("جمع کارکرد: ${formatMoney(all.sumOf{it.amount})}"); appendLine("جمع دریافتی: ${formatMoney(all.sumOf{it.settled})}"); appendLine("جمع مانده: ${formatMoney(all.sumOf{it.remaining.coerceAtLeast(0.0)})}"); open.sortedByDescending{it.remaining}.take(5).forEachIndexed{i,p->appendLine("${i+1}. ${p.name} — مانده ${formatMoney(p.remaining)}")} } } catch(e:Exception){"📁 خطا در خواندن پروژه‌ها: ${e.message}"}
    private fun todayPlan(context: Context):String = "📅 برنامه فعلی\n\n${taskStats(context,"tunnel_tasks")}\n\n${taskStats(context,"project_tasks")}" 
}
