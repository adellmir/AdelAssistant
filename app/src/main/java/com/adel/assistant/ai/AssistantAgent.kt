package com.adel.assistant.ai

import android.content.Context
import com.adel.assistant.data.ProjectStore
import com.adel.assistant.data.TaskStore
import com.adel.assistant.data.TunnelFinanceStore
import com.adel.assistant.data.TunnelReportStore
import com.adel.assistant.data.formatMoney
import com.adel.assistant.navigation.Routes
import java.util.Locale

data class AgentReply(
    val text: String,
    val navigateTo: String? = null
)

/**
 * دستیار مبتنی بر فهم قصد (Rule + کلیدواژه) — محاسبات از Storeهای واقعی برنامه.
 * بدون API خارجی؛ مناسب آمار، تسک، ناوبری.
 */
object AssistantAgent {

    fun handle(context: Context, userMessage: String): AgentReply {
        val msg = normalize(userMessage)
        if (msg.isBlank()) return AgentReply("پیامت را بنویس؛ مثلاً «آمار کلی» یا «مانده مطالبات».")

        // ناوبری
        navIntent(msg)?.let { return it }

        // تسک‌ها
        if (hasAny(msg, listOf("تسک", "کار باقی", "کار باز", "وظیفه"))) {
            taskIntent(context, msg)?.let { return it }
        }

        // آمار / خلاصه / وضعیت
        if (hasAny(msg, listOf("آمار", "خلاصه", "وضعیت", "گزارش", "چقدر", "چند", "مانده", "مطالبه", "دریافت", "کارکرد", "درآمد", "پیشروی"))) {
            return statsIntent(context, msg)
        }

        // سلام و راهنما
        if (hasAny(msg, listOf("سلام", "درود", "هی", "کمک", "راهنما", "چی میتونی", "چه کار"))) {
            return AgentReply(helpText())
        }

        // پیش‌فرض: آمار کلی + راهنما کوتاه
        return AgentReply(
            buildString {
                appendLine(statsOverview(context))
                appendLine()
                append("اگر دقیق‌تر می‌خواهی بگو: «آمار تونل»، «آمار پروژه‌ها»، «تسک‌های باز»، «مانده مطالبات».")
            }
        )
    }

    private fun normalize(s: String): String =
        s.trim()
            .replace('ي', 'ی')
            .replace('ك', 'ک')
            .replace(Regex("\\s+"), " ")
            .lowercase(Locale.US)

    private fun hasAny(msg: String, keys: List<String>): Boolean =
        keys.any { msg.contains(it) }

    private fun helpText(): String = """
سلام، من دستیار AdelAssistant هستم.
می‌توانم از داده‌های خود برنامه آمار بدهم و تسک را مدیریت کنم.

نمونه‌ها:
• آمار کلی
• آمار تونل / مانده تونل
• آمار پروژه‌ها / مانده مطالبات
• تسک‌های باز تونل
• تسک پروژه: کنترل نقاط
• انجام شد: کنترل نقاط
• برو گزارش روزانه
• برو مطالبات
""".trimIndent()

    // ---------- ناوبری ----------
    private fun navIntent(msg: String): AgentReply? {
        if (!hasAny(msg, listOf("برو", "باز کن", "بباز", "صفحه", "بریم"))) return null
        val map = listOf(
            listOf("گزارش", "روزانه") to Routes.SURVEY_TUNNEL_REPORT,
            listOf("چینیج", "کیلومتر") to Routes.SURVEY_TUNNEL_CHAINAGE,
            listOf("نقاط تونل", "وقایع تونل") to Routes.SURVEY_TUNNEL_EVENTS,
            listOf("تسک تونل") to Routes.SURVEY_TUNNEL_TASKS,
            listOf("ثبت پروژه") to Routes.SURVEY_PROJECT_REGISTER,
            listOf("تقویم") to Routes.SURVEY_PROJECT_CALENDAR,
            listOf("تسک پروژه") to Routes.SURVEY_PROJECT_TASKS,
            listOf("کارکرد") to Routes.FIN_TUNNEL_WORKLOG,
            listOf("دریافتی تونل") to Routes.FIN_TUNNEL_RECEIPTS,
            listOf("خلاصه تونل", "مطالبات تونل") to Routes.FIN_TUNNEL_SUMMARY,
            listOf("فاکتور") to Routes.FIN_PROJECT_INVOICE,
            listOf("مطالبات") to Routes.FIN_PROJECT_RECEIVABLES,
            listOf("وضعیت مالی") to Routes.FIN_PROJECT_STATUS,
            listOf("مبدل", "gsi") to Routes.TOOL_GSI,
            listOf("ترسیم", "dxf") to Routes.TOOL_DXF,
            listOf("نمایش نقشه", "پیش نمایش") to Routes.TOOL_DXF_PREVIEW,
            listOf("درون") to Routes.TOOL_INTERPOLATE,
            listOf("مساحت") to Routes.TOOL_AREA,
            listOf("تخلیه", "دوربین") to Routes.TOOL_TOTAL_STATION,
            listOf("پشتیبان") to Routes.TOOL_BACKUP
        )
        for ((keys, route) in map) {
            if (keys.any { msg.contains(it) }) {
                return AgentReply("صفحه مربوط را باز می‌کنم.", navigateTo = route)
            }
        }
        return null
    }

    // ---------- تسک ----------
    private fun taskIntent(context: Context, msg: String): AgentReply? {
        val tunnel = hasAny(msg, listOf("تونل", "tunnel"))
        val project = hasAny(msg, listOf("پروژه", "project"))
        val store = when {
            tunnel && !project -> "tunnel_tasks"
            project && !tunnel -> "project_tasks"
            else -> null
        }
        val label = when (store) {
            "tunnel_tasks" -> "تونل"
            "project_tasks" -> "پروژه"
            else -> null
        }

        // انجام شد
        if (hasAny(msg, listOf("انجام شد", "انجامش بده", "تیک", "کامل شد", "تمام شد"))) {
            val title = extractAfter(msg, listOf("انجام شد", "انجامش بده", "تیک بزن", "کامل شد"))
                ?.trim()?.trimStart(':', ' ', '-')
            if (title.isNullOrBlank()) {
                return AgentReply("کدام تسک انجام شود؟ مثلاً: انجام شد: برداشت مقطع")
            }
            val targets = if (store != null) listOf(store) else listOf("tunnel_tasks", "project_tasks")
            for (s in targets) {
                val all = TaskStore.load(context, s)
                val idx = all.indexOfFirst { !it.completed && it.title.contains(title, ignoreCase = true) }
                if (idx >= 0) {
                    val updated = all.toMutableList()
                    updated[idx] = updated[idx].copy(completed = true)
                    TaskStore.save(context, s, updated)
                    val sec = if (s == "tunnel_tasks") "تونل" else "پروژه"
                    return AgentReply("تسک «${updated[idx].title}» در بخش $sec انجام‌شده شد ✅")
                }
            }
            return AgentReply("تسک باز با این متن پیدا نشد: $title")
        }

        // افزودن
        if (hasAny(msg, listOf("اضافه", "ثبت", "بساز", "یادداشت", "تسک:"))) {
            if (store == null) {
                return AgentReply("برای کدام بخش؟ بگو «تسک تونل: ...» یا «تسک پروژه: ...»")
            }
            val title = extractTaskTitle(msg) ?: return AgentReply("متن تسک را بعد از دو نقطه بنویس. مثال: تسک تونل: برداشت مقطع")
            val all = TaskStore.load(context, store)
            all.add(0, com.adel.assistant.data.TaskItem(title = title))
            TaskStore.save(context, store, all)
            return AgentReply("تسک «$title» در بخش $label ثبت شد ✅")
        }

        // لیست / آمار تسک
        return AgentReply(taskStats(context, store))
    }

    private fun extractTaskTitle(msg: String): String? {
        val markers = listOf("تسک تونل:", "تسک پروژه:", "تسک:", "ثبت کن", "اضافه کن", "بساز")
        for (m in markers) {
            val i = msg.indexOf(m)
            if (i >= 0) {
                val rest = msg.substring(i + m.length).trim().trimStart(':', '-', ' ')
                if (rest.isNotBlank()) return rest.take(200)
            }
        }
        // «تسک تونل برداشت مقطع»
        val m = Regex("تسک\\s*(?:تونل|پروژه)?\\s*[:\\-]?\\s*(.+)").find(msg)
        return m?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.length > 1 }
    }

    private fun extractAfter(msg: String, keys: List<String>): String? {
        for (k in keys) {
            val i = msg.indexOf(k)
            if (i >= 0) return msg.substring(i + k.length)
        }
        return null
    }

    private fun taskStats(context: Context, store: String?): String {
        fun block(name: String, label: String): String {
            val all = TaskStore.load(context, name)
            val open = all.filter { !it.completed }
            val done = all.count { it.completed }
            val lines = open.take(5).mapIndexed { i, t -> "  ${i + 1}. ${t.title}" }
            return buildString {
                appendLine("📋 تسک‌های $label")
                appendLine("باز: ${open.size} | انجام‌شده: $done | کل: ${all.size}")
                if (lines.isNotEmpty()) {
                    appendLine("آخرین بازها:")
                    lines.forEach { appendLine(it) }
                } else appendLine("تسک بازی نیست.")
            }
        }
        return when (store) {
            "tunnel_tasks" -> block("tunnel_tasks", "تونل")
            "project_tasks" -> block("project_tasks", "پروژه")
            else -> block("tunnel_tasks", "تونل") + "\n" + block("project_tasks", "پروژه")
        }
    }

    // ---------- آمار ----------
    private fun statsIntent(context: Context, msg: String): AgentReply {
        val wantTunnel = hasAny(msg, listOf("تونل", "شفت", "پیشروی", "کارکرد"))
        val wantProject = hasAny(msg, listOf("پروژه", "مطالبه", "کارفرما", "فاکتور"))
        val wantTask = hasAny(msg, listOf("تسک", "وظیفه"))

        return when {
            wantTask && !wantTunnel && !wantProject -> AgentReply(taskStats(context, null))
            wantTunnel && !wantProject -> AgentReply(tunnelStats(context))
            wantProject && !wantTunnel -> AgentReply(projectStats(context))
            else -> AgentReply(statsOverview(context))
        }
    }

    fun statsOverview(context: Context): String {
        return buildString {
            appendLine("📊 آمار کلی AdelAssistant")
            appendLine()
            appendLine(tunnelStats(context).trim())
            appendLine()
            appendLine(projectStats(context).trim())
            appendLine()
            appendLine(taskStats(context, null).trim())
        }
    }

    private fun tunnelStats(context: Context): String {
        return try {
            val s = TunnelFinanceStore.summary(context)
            val points = try {
                TunnelReportStore.allPoints(context).size
            } catch (_: Exception) {
                -1
            }
            buildString {
                appendLine("🚇 تونل (مالی)")
                appendLine("جمع درآمد/کارکرد: ${formatMoney(s.sumPayable)}")
                appendLine("جمع دریافتی: ${formatMoney(s.sumReceived)}")
                appendLine("حسن‌انجام بلوکه: ${formatMoney(s.blockedRetention)}")
                appendLine("مانده دریافتی: ${formatMoney(s.remaining)}")
                if (points >= 0) appendLine("تعداد نقاط ثبت‌شده تونل: $points")
            }
        } catch (e: Exception) {
            "🚇 تونل: خطا در خواندن داده — ${e.message}"
        }
    }

    private fun projectStats(context: Context): String {
        return try {
            val all = ProjectStore.all(context)
            val open = all.filter { it.remaining > 0.0001 }
            val sumAmount = all.sumOf { it.amount }
            val sumSettled = all.sumOf { it.settled }
            val sumRemain = all.sumOf { it.remaining.coerceAtLeast(0.0) }
            // مبالغ پروژه در نمایش میلیون هستند (واحد ورود میلیون)
            buildString {
                appendLine("📁 پروژه‌ها")
                appendLine("تعداد کل: ${all.size} | با مانده: ${open.size}")
                appendLine("جمع کارکرد (میلیون): ${formatMoney(sumAmount)}")
                appendLine("جمع دریافتی (میلیون): ${formatMoney(sumSettled)}")
                appendLine("جمع مانده (میلیون): ${formatMoney(sumRemain)}")
                if (open.isNotEmpty()) {
                    appendLine("بیشترین مانده‌ها:")
                    open.sortedByDescending { it.remaining }.take(5).forEachIndexed { i, p ->
                        appendLine("  ${i + 1}. ${p.name} — ${formatMoney(p.remaining)} (کارفرما: ${p.employer})")
                    }
                }
            }
        } catch (e: Exception) {
            "📁 پروژه‌ها: خطا در خواندن داده — ${e.message}"
        }
    }
}
