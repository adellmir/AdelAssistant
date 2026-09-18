package com.adel.assistant.ai.tools

import android.content.Context
import com.adel.assistant.data.AssistantChatStore
import com.adel.assistant.data.AssistantMemoryStore
import com.adel.assistant.data.AssistantPermissionStore
import com.adel.assistant.data.CsvStore
import com.adel.assistant.data.MapOverlayStore
import com.adel.assistant.data.ProjectStore
import com.adel.assistant.data.TaskStore
import com.adel.assistant.data.TunnelFinanceStore
import com.adel.assistant.data.TunnelReportStore
import java.util.Locale

/**
 * دروازهٔ خواندن داده برای دستیار.
 * به جای اینکه Agent مستقیماً با فایل‌ها کار کند، تمام خواندن داده از اینجا عبور می‌کند.
 * این ابزار read-only است؛ تغییر داده‌ها در ActionTools و با سطح دسترسی جدا انجام می‌شود.
 */
object DatabaseTools {
    const val OVERVIEW = "database_overview"
    const val LIST = "database_datasets"
    const val SEARCH = "database_search"
    const val READ = "database_read"

    data class Dataset(val id: String, val title: String, val aliases: Set<String>)

    private val datasets = listOf(
        Dataset("projects", "پروژه‌ها", setOf("پروژه", "پروژه ها", "پروژه‌ها", "مطالبات", "کارفرما")),
        Dataset("tunnel_financial", "مالی تونل", setOf("مالی", "مالی تونل", "دریافت", "پرداخت", "کارکرد")),
        Dataset("survey_tunnel_report", "گزارش روزانه تونل", setOf("گزارش تونل", "گزارش روزانه", "گزارش")),
        Dataset("tunnel_points", "نقاط تونل", setOf("نقطه", "نقاط", "مختصات", "برداشت")),
        Dataset("tunnel_map_points", "نقاط روی نقشه", setOf("نقاط نقشه", "اورلی", "overlay", "map points")),
        Dataset("tunnel_tasks", "تسک‌های تونل", setOf("تسک تونل", "کار تونل", "وظایف تونل")),
        Dataset("project_tasks", "تسک‌های پروژه", setOf("تسک پروژه", "کار پروژه", "وظایف پروژه")),
        Dataset("project_partial_payments", "پرداخت‌های جزئی پروژه", setOf("پرداخت جزئی", "پرداخت های جزئی", "partial payments")),
        Dataset("assistant_memory", "حافظه دستیار", setOf("حافظه", "memory")),
        Dataset("assistant_chats", "گفتگوهای دستیار", setOf("چت", "گفتگو", "گفتگوها")),
        Dataset("assistant_permissions", "سطح دسترسی دستیار", setOf("دسترسی", "مجوز", "permission"))
    )

    fun datasetNames(): List<String> = datasets.map { it.id }

    fun resolveDataset(query: String): String? {
        val q = query.trim().lowercase(Locale.ROOT)
        datasets.firstOrNull { it.id == q }?.let { return it.id }
        return datasets.firstOrNull { d -> d.aliases.any { a -> q.contains(a.lowercase(Locale.ROOT)) } }?.id
    }

    fun overview(context: Context): String = buildString {
        appendLine("🗄️ نمای کلی داده‌های AdelAssistant")
        datasets.forEach { d ->
            val count = runCatching { count(context, d.id) }.getOrDefault(0)
            appendLine("• ${d.title}: $count رکورد")
        }
        appendLine("• فایل‌های داده: ${CsvStore.readAll(context, "projects").size} ردیف قابل‌خواندن در پروژه‌ها")
    }.trimEnd()

    fun listDatasets(): String = buildString {
        appendLine("مجموعه‌های داده‌ای که دستیار می‌تواند بخواند:")
        datasets.forEachIndexed { i, d -> appendLine("${i + 1}. ${d.id} — ${d.title}") }
    }.trimEnd()

    fun read(context: Context, dataset: String, limit: Int = 30): String {
        val id = resolveDataset(dataset) ?: datasetNames().firstOrNull { it == dataset } ?: return "مجموعه داده «$dataset» شناخته نشد."
        val rows = rows(context, id).take(limit.coerceIn(1, 100))
        if (rows.isEmpty()) return "در «${title(id)}» داده‌ای ثبت نشده است."
        return buildString {
            appendLine("📂 ${title(id)} — ${rows.size} رکورد")
            rows.forEachIndexed { i, row -> appendLine("${i + 1}. $row") }
        }.trimEnd()
    }

    fun search(context: Context, query: String, limit: Int = 40): String {
        val q = query.trim().lowercase(Locale.ROOT)
        if (q.isBlank()) return "عبارت جستجو را مشخص کن."
        val results = mutableListOf<String>()
        datasets.forEach { d ->
            rows(context, d.id).forEach { row ->
                if (row.lowercase(Locale.ROOT).contains(q)) {
                    results += "[${d.title}] $row"
                }
            }
        }
        if (results.isEmpty()) return "برای «$query» در داده‌های برنامه نتیجه‌ای پیدا نشد."
        return buildString {
            appendLine("🔎 نتیجه جستجوی سراسری «$query» — ${results.size.coerceAtMost(limit)} مورد")
            results.take(limit.coerceIn(1, 100)).forEachIndexed { i, row -> appendLine("${i + 1}. $row") }
            if (results.size > limit) appendLine("… ${results.size - limit} مورد دیگر")
        }.trimEnd()
    }

    private fun count(context: Context, id: String): Int = when (id) {
        "projects" -> ProjectStore.all(context).size
        "tunnel_financial" -> TunnelFinanceStore.all(context).size
        "survey_tunnel_report" -> TunnelReportStore.allEntries(context).size
        "tunnel_points" -> TunnelReportStore.allPoints(context).size
        "tunnel_map_points" -> MapOverlayStore.all(context).size
        "tunnel_tasks" -> TaskStore.load(context, id).size
        "project_tasks" -> TaskStore.load(context, id).size
        "project_partial_payments" -> CsvStore.readAll(context, id).size
        "assistant_memory" -> AssistantMemoryStore.load(context).size
        "assistant_chats" -> AssistantChatStore.chats(context).size
        "assistant_permissions" -> AssistantPermissionStore.all(context).size
        else -> 0
    }

    private fun rows(context: Context, id: String): List<String> = when (id) {
        "projects" -> ProjectStore.all(context).map {
            "ردیف=${it.row} | تاریخ=${it.year}/${it.month}/${it.day} | پروژه=${it.name} | مبلغ=${money(it.amount)} | دریافتی=${money(it.settled)} | مانده=${money(it.remaining)} | کارفرما=${it.employer} | تلفن=${it.phone} | توضیح=${it.description}"
        }
        "tunnel_financial" -> TunnelFinanceStore.all(context).map {
            "تاریخ=${it.year}/${it.month} | روز=${it.days} | مبلغ واحد=${money(it.unitPrice)} | کل=${money(it.totalAmount)} | کسورات=${money(it.totalDeductions)} | صورت=${money(it.payable)} | درآمد=${money(it.income)} | دریافتی=${money(it.receiveAmount ?: 0.0)} | تاریخ دریافت=${it.receiveDate} | یادداشت=${it.note}"
        }
        "survey_tunnel_report" -> TunnelReportStore.allEntries(context).map {
            "تاریخ=${it.year}/${it.month}/${it.day} | شفت=${it.shaft} | سمت=${it.side} | نقطه=${it.pointNo} | طول=${it.lengthCm}cm | km=${it.km} | پیشروی روز=${it.dailyProgress} | پیشروی شفت=${it.shaftProgress} | مانده=${it.remaining} | XYZ=${it.x},${it.y},${it.z} | انحراف=${it.deviation} | ریزش=${it.collapse}"
        }
        "tunnel_points" -> TunnelReportStore.allPoints(context).map {
            "نقطه=${it.pointNo} | km=${it.km} | X=${it.x} | Y=${it.y} | Z=${it.z} | نوع=${it.type}"
        }
        "tunnel_map_points" -> MapOverlayStore.all(context).map {
            "id=${it.id} | X=${it.x} | Y=${it.y} | Z=${it.z} | D=${it.d} | km=${it.km} | source=${it.source}"
        }
        "tunnel_tasks", "project_tasks" -> TaskStore.load(context, id).map {
            "عنوان=${it.title} | انجام‌شده=${it.completed} | ایجاد=${it.createdAt} | تاریخ=${it.dueDate ?: ""} | ساعت=${it.dueTime ?: ""}"
        }
        "project_partial_payments" -> CsvStore.readAll(context, id).map { it.joinToString(" | ") }
        "assistant_memory" -> AssistantMemoryStore.load(context).mapIndexed { i, text -> "#$i | $text" }
        "assistant_chats" -> AssistantChatStore.chats(context).map { "id=${it.id} | عنوان=${it.title} | ایجاد=${it.createdAt}" }
        "assistant_permissions" -> AssistantPermissionStore.all(context).map { "${it.key}=${it.value.name}" }
        else -> emptyList()
    }

    private fun title(id: String): String = datasets.firstOrNull { it.id == id }?.title ?: id
    private fun money(v: Double): String = String.format(Locale.US, "%.0f", v)
}
