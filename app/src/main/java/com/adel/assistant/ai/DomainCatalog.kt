package com.adel.assistant.ai

import android.content.Context
import com.adel.assistant.data.AppMenu
import com.adel.assistant.data.ProjectStore
import com.adel.assistant.data.TunnelFinanceStore
import com.adel.assistant.data.TunnelReportStore
import com.adel.assistant.data.formatMoney

/**
 * دانش‌نامهٔ فشردهٔ برنامه برای دستیار (آفلاین + تزریق به آنلاین).
 * شامل معنی داده‌ها و عملیات‌های قابل اجرا.
 */
object DomainCatalog {

    fun systemPrompt(context: Context): String = buildString {
        appendLine("تو دستیار تخصصی نرم‌افزار نقشه‌برداری AdelAssistant هستی.")
        appendLine("فقط بر اساس داده و عملیات همین برنامه جواب بده. اگر داده نبود صادقانه بگو.")
        appendLine("زبان پاسخ: فارسی، کوتاه و عملیاتی.")
        appendLine()
        appendLine("=== پایگاه داده ===")
        appendLine("- tunnel_points: نقاط محور تونل (شماره، X,Y,Z، کیلومتراژ km، اختلاف تراز، شیب، type). typeهایی مثل sh1=شفت۱، start/end=دهانه.")
        appendLine("- survey_tunnel_report: گزارش روزانه (تاریخ، شفت، سمت، شماره نقطه، طول cm، km، مختصات).")
        appendLine("- پروژه‌ها: نام، کارفرما، مبلغ، دریافتی، مانده، تسویه.")
        appendLine("- مالی تونل: کارکرد و دریافت.")
        appendLine("- تسک‌ها: tunnel_tasks و project_tasks.")
        appendLine()
        appendLine("=== منطق کیلومتراژ ===")
        appendLine("کیلومتر شفت از type=shN روی محور خوانده می‌شود (shaftFixedKm).")
        appendLine("پیشروی از شفت: km = fixedKm + direction(shaft,side) * (متر). سمت پیش‌فرض start.")
        appendLine("مختصات نقطه روی محور با درون‌یابی بین دو نقطهٔ مجاور بر اساس km.")
        appendLine("مثال: «۵۰ متر شفت ۱» → km شفت۱ + ۵۰m → مختصات درون‌یابی + لینک مسیریاب.")
        appendLine()
        appendLine("=== عملیات برنامه (می‌توانی پیشنهاد مسیر بدهی) ===")
        AppMenu.sections.forEach { sec ->
            sec.tabs.forEach { tab ->
                tab.items.forEach { item ->
                    val tabPart = if (tab.title.isNotBlank()) " / ${tab.title}" else ""
                    appendLine("- ${sec.title}$tabPart / ${item.title} → route=${item.route}")
                }
            }
        }
        appendLine()
        appendLine("=== دادهٔ زنده ===")
        appendLine(liveSnapshot(context))
        appendLine()
        appendLine("اگر کاربر مختصات یا مسیریاب خواست، مختصات UTM و در صورت امکان lat/lon بده.")
        appendLine("اگر مطمئن نیستی کدام صفحه، گزینه‌های محدود بده نه حدس آزاد.")
    }

    fun liveSnapshot(context: Context): String = buildString {
        try {
            val shafts = TunnelReportStore.allShafts(context)
            appendLine("شفت‌ها: " + if (shafts.isEmpty()) "خالی" else shafts.joinToString { "ش${it.name}@km=${"%.3f".format(it.fixedKm)}" })
            val pts = TunnelReportStore.allPoints(context)
            appendLine("تعداد نقاط محور: ${pts.size}")
            if (pts.isNotEmpty()) {
                appendLine("بازه km محور: ${"%.3f".format(pts.minOf { it.km })} … ${"%.3f".format(pts.maxOf { it.km })}")
            }
            val reps = TunnelReportStore.allEntries(context)
            appendLine("تعداد گزارش روزانه: ${reps.size}")
            reps.maxByOrNull { it.dateSortKey }?.let {
                appendLine("آخرین گزارش: ${it.year}/${it.month}/${it.day} شفت${it.shaft} سمت${it.side} طول=${it.lengthCm}cm km=${"%.3f".format(it.km)}")
            }
            val projects = ProjectStore.all(context)
            appendLine("پروژه‌ها: ${projects.size} | مانده کل: ${formatMoney(projects.sumOf { it.remaining.coerceAtLeast(0.0) })}")
            runCatching {
                val s = TunnelFinanceStore.summary(context)
                appendLine("مالی تونل — کارکرد: ${formatMoney(s.sumPayable)} دریافت: ${formatMoney(s.sumReceived)} مانده: ${formatMoney(s.remaining)}")
            }
        } catch (e: Exception) {
            appendLine("خطا در خواندن داده: ${e.message}")
        }
    }
}
