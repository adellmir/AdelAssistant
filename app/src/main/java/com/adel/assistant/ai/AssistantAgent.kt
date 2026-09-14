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

data class AgentReply(val text: String, val navigateTo: String? = null, val action: String? = null)

/**
 * موتور فرمان محاوره‌ای AdelAssistant.
 * بر پایه واژه‌های کلیدی، مترادف‌ها، امتیازدهی چندکلمه‌ای و حافظه کوتاه گفتگو.
 */
object AssistantAgent {
    private enum class PendingType { TASK_CATEGORY, TASK_DELETE_CONFIRM, DISAMBIGUATE }
    private data class Pending(val type: PendingType, val title: String = "", val store: String? = null, val index: Int = -1, val choices: List<Intent> = emptyList())
    private data class Intent(val name: String, val route: String, val keywords: List<String>)
    private var pending: Pending? = null
    private var lastFileName: String? = null

    private val intents = listOf(
        Intent("گزارش روزانه تونل", Routes.SURVEY_TUNNEL_REPORT, listOf("گزارش روزانه","گزارش تونل","گزارش","روزانه")),
        Intent("چینیج", Routes.SURVEY_TUNNEL_CHAINAGE, listOf("چینیج","chainage","کیلومتر","زنجیره")),
        Intent("وضعیت تونل", Routes.SURVEY_TUNNEL_STATUS, listOf("وضعیت تونل","وضعیت","پیشروی تونل")),
        Intent("وقایع تونل", Routes.SURVEY_TUNNEL_EVENTS, listOf("وقایع تونل","رویداد تونل","اتفاقات تونل")),
        Intent("تسک تونل", Routes.SURVEY_TUNNEL_TASKS, listOf("تسک تونل","کارهای تونل","وظایف تونل","کار تونل")),
        Intent("ثبت پروژه", Routes.SURVEY_PROJECT_REGISTER, listOf("ثبت پروژه","پروژه جدید","اضافه کردن پروژه")),
        Intent("وقایع پروژه", Routes.SURVEY_PROJECT_EVENTS, listOf("وقایع پروژه","رویداد پروژه","اتفاقات پروژه")),
        Intent("تقویم کاری", Routes.SURVEY_PROJECT_CALENDAR, listOf("تقویم","تقویم کاری","برنامه کاری")),
        Intent("تسک پروژه", Routes.SURVEY_PROJECT_TASKS, listOf("تسک پروژه","کارهای پروژه","وظایف پروژه","کار پروژه")),
        Intent("کارکرد تونل", Routes.FIN_TUNNEL_WORKLOG, listOf("کارکرد","کارکرد تونل","کار تونل")),
        Intent("دریافتی تونل", Routes.FIN_TUNNEL_RECEIPTS, listOf("دریافتی تونل","دریافت تونل","پول تونل")),
        Intent("مطالبات تونل", Routes.FIN_TUNNEL_SUMMARY, listOf("مطالبات تونل","طلب تونل","مانده تونل","خلاصه تونل")),
        Intent("فاکتور", Routes.FIN_PROJECT_INVOICE, listOf("فاکتور","صورتحساب")),
        Intent("دریافتی پروژه", Routes.FIN_PROJECT_RECEIPT, listOf("دریافتی پروژه","دریافت پروژه","ثبت دریافتی")),
        Intent("مطالبات پروژه", Routes.FIN_PROJECT_RECEIVABLES, listOf("مطالبات پروژه","مطالبات","طلب پروژه","مانده پروژه","طلب من")),
        Intent("وضعیت مالی", Routes.FIN_PROJECT_STATUS, listOf("وضعیت مالی","مالی پروژه","خلاصه مالی")),
        Intent("مکان", Routes.TOOL_LOCATION, listOf("مکان","مختصات فعلی","gps","موقعیت")),
        Intent("درون‌یابی", Routes.TOOL_INTERPOLATE, listOf("درون یابی","درونیابی","فاصله دو نقطه","نقطه بین دو نقطه","شیب")),
        Intent("مساحت", Routes.TOOL_AREA, listOf("مساحت","محیط","area")),
        Intent("حجم", Routes.TOOL_VOLUME, listOf("حجم","احجام","volume")),
        Intent("نمایش نقشه DXF", Routes.TOOL_DXF_PREVIEW, listOf("نمایش نقشه","نمایش dxf","پیش نمایش","نقشه dxf","لایه نقشه")),
        Intent("مبدل فایل", Routes.TOOL_GSI, listOf("مبدل","تبدیل فایل","تبدیل","gsi","csv","dat","idx","txt","dxf")),
        Intent("تخلیه دوربین", Routes.TOOL_TOTAL_STATION, listOf("تخلیه","دوربین","total station","توتال")),
        Intent("پشتیبان‌گیری", Routes.TOOL_BACKUP, listOf("پشتیبان","بکاپ","backup"))
    )

    fun setSelectedFile(fileName: String?) { if (!fileName.isNullOrBlank()) lastFileName = fileName }

    fun handle(context: Context, userMessage: String): AgentReply {
        val msg = normalize(userMessage)
        if (msg.isBlank()) return AgentReply("پیامت را بنویس؛ لازم نیست دقیقاً نام برنامه را بگویی.")
        resolvePending(context, msg)?.let { return it }
        if (isHelp(msg)) return AgentReply(helpText())

        // فایل انتخاب‌شده + فرمان تبدیل
        if (hasAny(msg, listOf("تبدیل","کانورت","convert")) && lastFileName != null) {
            val target = detectExtension(msg)
            return AgentReply(if (target != null) "فایل «$lastFileName» برای تبدیل به .$target آماده است. صفحه مبدل را باز می‌کنم." else "فایل «$lastFileName» انتخاب شده است. بگو به چه فرمتی تبدیل شود؛ مثلاً «به DXF تبدیل کن». ", Routes.TOOL_GSI, "file_convert")
        }

        // مطالبات بدون تعیین بخش = پاسخ ترکیبی؛ مطالبات همراه ماه/سال = بازه‌ای
        if (hasAny(msg, listOf("مطالبه","مطالبات","طلب","مانده"))) {
            if (hasPersianDate(msg)) return AgentReply("بازه زمانی «${extractDatePhrase(msg)}» تشخیص داده شد؛ صفحه مطالبات پروژه را برای بررسی بازه‌ای باز می‌کنم.", Routes.FIN_PROJECT_RECEIVABLES, "receivables_period")
            if (hasAny(msg, listOf("تونل","شفت"))) return AgentReply(tunnelStats(context), Routes.FIN_TUNNEL_SUMMARY)
            if (hasAny(msg, listOf("پروژه","کارفرما"))) return AgentReply(projectStats(context), Routes.FIN_PROJECT_RECEIVABLES)
            return AgentReply("📌 مطالبات را جداگانه بررسی کردم:\n\n${tunnelStats(context)}\n\n${projectStats(context)}")
        }

        taskIntent(context, msg)?.let { return it }
        statsIntent(context, msg)?.let { return it }
        if (hasAny(msg, listOf("امروز","برنامه امروز","کارهای امروز"))) return AgentReply(todayPlan(context))
        navIntent(msg)?.let { return it }
        return AgentReply(suggest(msg))
    }

    private fun navIntent(msg: String): AgentReply? {
        val navWords = listOf("برو","باز کن","بازش کن","صفحه","قسمت","بخش","نمایش بده","میخوام","لازم دارم","محاسبه کن","نشون بده")
        val scores = intents.map { it to score(msg, it.keywords) }.filter { it.second > 0 }.sortedByDescending { it.second }
        if (scores.isEmpty()) return null
        val best = scores.first(); val second = scores.getOrNull(1)
        val hasStrong = best.second >= 2 || best.first.keywords.any { normalize(it) == msg }
        if (!hasStrong && !hasAny(msg, navWords)) return null
        if (second != null && best.second - second.second <= 0 && best.second >= 2) {
            pending = Pending(PendingType.DISAMBIGUATE, choices = listOf(best.first, second.first))
            return AgentReply("منظورت کدام است؟ ۱) ${best.first.name}  ۲) ${second.first.name}")
        }
        return AgentReply("باشه، «${best.first.name}» را باز می‌کنم.", best.first.route, "navigate")
    }

    private fun score(msg: String, keywords: List<String>): Int {
        val tokens = msg.split(" ").filter { it.length >= 2 }.toSet()
        var score = 0
        keywords.forEach { key ->
            val k = normalize(key)
            if (msg.contains(k)) score += if (k.contains(" ")) 4 else 2
            else score += k.split(" ").count { it.length >= 2 && tokens.contains(it) }
        }
        return score
    }

    private fun suggest(msg: String): String {
        val near = intents.map { it to score(msg, it.keywords) }.filter { it.second > 0 }.sortedByDescending { it.second }.take(3)
        return if (near.isNotEmpty()) "دقیق متوجه نشدم. شاید منظورت یکی از این‌ها باشد:\n" + near.mapIndexed { i, p -> "${i + 1}) ${p.first.name}" }.joinToString("\n") + "\n\nمی‌توانی شماره یا نامش را بگویی." else "منظورت را متوجه نشدم. لازم نیست دقیقاً نام گزینه را بگویی؛ مثلاً «فاصله دو نقطه رو حساب کن»، «پول‌هایی که طلب دارم»، «کارهای تونلم رو نشون بده» یا «فایل رو به DXF تبدیل کن»."
    }

    private fun resolvePending(context: Context, msg: String): AgentReply? {
        val p = pending ?: return null
        when (p.type) {
            PendingType.TASK_CATEGORY -> { val store = categoryStore(msg); if (store != null) { pending = null; return addTask(context, store, p.title) }; if (isCancel(msg)) { pending = null; return AgentReply("عملیات لغو شد.") }; return AgentReply("برای «تونل» یا «پروژه» ثبت شود؟") }
            PendingType.TASK_DELETE_CONFIRM -> { if (isConfirm(msg)) { val all = TaskStore.load(context, p.store!!); if (p.index in all.indices) { val removed = all.removeAt(p.index); TaskStore.save(context,p.store,all); pending=null; return AgentReply("تسک «${removed.title}» حذف شد 🗑️") }; pending=null; return AgentReply("تسک دیگر پیدا نشد.") }; if (isCancel(msg)) { pending=null; return AgentReply("حذف لغو شد.") }; return AgentReply("برای حذف «${p.title}» فقط «بله» یا «لغو» بنویس.") }
            PendingType.DISAMBIGUATE -> { val n = msg.toIntOrNull(); val choice = when { n != null && n in 1..p.choices.size -> p.choices[n-1]; else -> p.choices.firstOrNull { score(msg,it.keywords)>0 } }; if (choice != null) { pending=null; return AgentReply("باشه، «${choice.name}» را باز می‌کنم.", choice.route, "navigate") }; return AgentReply("یکی از گزینه‌ها را انتخاب کن: " + p.choices.mapIndexed { i,it -> "${i+1}) ${it.name}" }.joinToString(" | ")) }
        }
    }

    private fun normalize(s: String): String = s.trim().replace('ي','ی').replace('ك','ک').replace('ۀ','ه').replace(Regex("[؟?!،,؛;]"), " ").replace(Regex("\\s+"), " ").lowercase(Locale.US)
    private fun hasAny(msg: String, keys: List<String>) = keys.any { msg.contains(it) }
    private fun isConfirm(msg: String) = msg in setOf("بله","اره","آره","تایید","تأیید","ok","باشه")
    private fun isCancel(msg: String) = msg in setOf("نه","لغو","بیخیال","کنسل")
    private fun isHelp(msg: String) = hasAny(msg, listOf("کمک","راهنما","چی میتونی","چه کار میتونی","سلام","درود"))
    private fun hasPersianDate(msg:String) = Regex("(فروردین|اردیبهشت|خرداد|تیر|مرداد|شهریور|مهر|آبان|آذر|دی|بهمن|اسفند)\\s*(۱۳|14|15)?\\d{2}").containsMatchIn(msg) || hasAny(msg,listOf("امروز","دیروز","فردا","این ماه","ماه قبل"))
    private fun extractDatePhrase(msg:String) = Regex("(فروردین|اردیبهشت|خرداد|تیر|مرداد|شهریور|مهر|آبان|آذر|دی|بهمن|اسفند)\\s*(۱۳|14|15)?\\d{2}").find(msg)?.value ?: "بازه درخواستی"
    private fun detectExtension(msg:String):String? = listOf("dxf","csv","dat","idx","txt","gsi","kml").firstOrNull { Regex("(^|\\s|\\.)$it($|\\s)").containsMatchIn(msg) }

    private fun helpText() = """🤖 دستیار AdelAssistant
• لازم نیست نام دقیق برنامه را بگویی.
• «پول‌هایی که طلب دارم» → مطالبات
• «مطالبات خرداد ۱۴۰۵» → مطالبات بازه‌ای
• «فاصله بین دو نقطه» → درون‌یابی
• فایل را انتخاب کن و بگو «به DXF تبدیل کن»."""

    private fun categoryStore(msg: String): String? = when { hasAny(msg,listOf("تونل","tunnel")) && !msg.contains("پروژه") -> "tunnel_tasks"; hasAny(msg,listOf("پروژه","project")) && !msg.contains("تونل") -> "project_tasks"; else -> null }
    private fun taskIntent(context: Context,msg:String):AgentReply? { val store=categoryStore(msg); val mentions=hasAny(msg,listOf("تسک","وظیفه","کار باقی","کار باز","یادآور","کارها")); val create=hasAny(msg,listOf("ثبت کن","اضافه کن","بساز","ایجاد کن","تسک جدید")); val complete=hasAny(msg,listOf("انجام شده","انجام‌شده","تیک بزن","تکمیل کن","تمام شد")); val delete=hasAny(msg,listOf("حذف کن","پاک کن")); if(!mentions&&!create&&!complete&&!delete)return null
        if(delete){val title=extractActionTitle(msg,listOf("تسک","حذف کن","پاک کن"));if(title.isBlank())return AgentReply("نام تسک را بگو.");val targets=if(store!=null)listOf(store)else listOf("tunnel_tasks","project_tasks");for(s in targets){val all=TaskStore.load(context,s);val i=all.indexOfFirst{it.title.contains(title,true)};if(i>=0){pending=Pending(PendingType.TASK_DELETE_CONFIRM,all[i].title,s,i);return AgentReply("⚠️ تسک «${all[i].title}» حذف شود؟ بله / لغو")}};return AgentReply("تسکی با این عنوان پیدا نشد.")}
        if(complete){val title=extractActionTitle(msg,listOf("تسک","انجام شد","انجام‌شده","تیک بزن","تکمیل کن","تمام شد"));if(title.isBlank())return AgentReply("نام تسک را بگو.");val targets=if(store!=null)listOf(store)else listOf("tunnel_tasks","project_tasks");for(s in targets){val all=TaskStore.load(context,s);val i=all.indexOfFirst{!it.completed&&it.title.contains(title,true)};if(i>=0){all[i]=all[i].copy(completed=true);TaskStore.save(context,s,all);return AgentReply("تسک «${all[i].title}» انجام‌شده شد ✅")}};return AgentReply("تسک باز پیدا نشد.")}
        if(create){val title=extractCreateTitle(msg);if(title.isBlank())return AgentReply("عنوان تسک را بگو.");if(store==null){pending=Pending(PendingType.TASK_CATEGORY,title);return AgentReply("تسک «$title» آماده است. برای تونل یا پروژه؟")};return addTask(context,store,title)};return AgentReply(taskStats(context,store)) }
    private fun addTask(context: Context,store:String,title:String):AgentReply{val all=TaskStore.load(context,store);all.add(0,TaskItem(title=title));TaskStore.save(context,store,all);return AgentReply("تسک «$title» در بخش ${if(store=="tunnel_tasks")"تونل"else"پروژه"} ثبت شد ✅")}
    private fun extractCreateTitle(msg:String):String{var s=msg;listOf("برای تونل","برای پروژه","تسک","جدید","ثبت کن","اضافه کن","ایجاد کن","بساز","فردا","امروز","پس فردا").forEach{s=s.replace(it," ")};return s.replace(Regex("\\s+")," ").trim().trim(':','-',' ').take(160)}
    private fun extractActionTitle(msg:String,markers:List<String>):String{var s=msg;markers.forEach{s=s.replace(it," ")};listOf("برای تونل","برای پروژه","رو","را","انجام کن","انجامش بده").forEach{s=s.replace(it," ")};return s.replace(Regex("\\s+")," ").trim().trim(':','-',' ').take(160)}
    private fun taskStats(context:Context,store:String?):String{fun block(name:String,label:String):String{val all=TaskStore.load(context,name);val open=all.filter{!it.completed};return buildString{appendLine("📋 تسک‌های $label");appendLine("باز: ${open.size} | انجام‌شده: ${all.count{it.completed}} | کل: ${all.size}");open.take(8).forEachIndexed{i,t->appendLine("${i+1}. ☐ ${t.title}")};if(open.isEmpty())append("تسک بازی نیست.")}};return if(store=="tunnel_tasks")block(store,"تونل")else if(store=="project_tasks")block(store,"پروژه")else block("tunnel_tasks","تونل")+"\n\n"+block("project_tasks","پروژه")}
    private fun statsIntent(context:Context,msg:String):AgentReply?{val keys=listOf("آمار","خلاصه","وضعیت","چقدر","چند","مانده","دریافت","کارکرد","درآمد","پیشروی","بیشترین","میانگین");if(!hasAny(msg,keys))return null;return when{hasAny(msg,listOf("تسک","وظیفه","کارها"))->AgentReply(taskStats(context,categoryStore(msg)));hasAny(msg,listOf("تونل","شفت","پیشروی","کارکرد"))->AgentReply(tunnelStats(context));hasAny(msg,listOf("پروژه","کارفرما","فاکتور","دریافت"))->AgentReply(projectStats(context));else->AgentReply(statsOverview(context))}}
    fun statsOverview(context:Context)="📊 آمار کلی AdelAssistant\n\n${tunnelStats(context)}\n\n${projectStats(context)}\n\n${taskStats(context,null)}"
    private fun tunnelStats(context:Context)=try{val s=TunnelFinanceStore.summary(context);val points=runCatching{TunnelReportStore.allPoints(context).size}.getOrNull();buildString{appendLine("🚇 تونل");appendLine("جمع کارکرد: ${formatMoney(s.sumPayable)}");appendLine("جمع دریافتی: ${formatMoney(s.sumReceived)}");appendLine("مانده: ${formatMoney(s.remaining)}");points?.let{append("تعداد نقاط ثبت‌شده: $it")}}}catch(e:Exception){"🚇 خطا در خواندن داده تونل: ${e.message}"}
    private fun projectStats(context:Context)=try{val all=ProjectStore.all(context);val open=all.filter{it.remaining>0.0001};buildString{appendLine("📁 پروژه‌ها");appendLine("تعداد کل: ${all.size} | با مانده: ${open.size}");appendLine("جمع کارکرد: ${formatMoney(all.sumOf{it.amount})}");appendLine("جمع دریافتی: ${formatMoney(all.sumOf{it.settled})}");appendLine("جمع مانده: ${formatMoney(all.sumOf{it.remaining.coerceAtLeast(0.0)})}");open.sortedByDescending{it.remaining}.take(5).forEachIndexed{i,p->appendLine("${i+1}. ${p.name} — مانده ${formatMoney(p.remaining)}")}}}catch(e:Exception){"📁 خطا در خواندن پروژه‌ها: ${e.message}"}
    private fun todayPlan(context:Context)="📅 برنامه فعلی\n\n${taskStats(context,"tunnel_tasks")}\n\n${taskStats(context,"project_tasks")}" 
}
