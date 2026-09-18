package com.adel.assistant.ai

import android.content.Context
import com.adel.assistant.data.ProjectStore
import com.adel.assistant.data.AssistantMemoryStore
import com.adel.assistant.data.AssistantPermission
import com.adel.assistant.data.AssistantPermissionStore
import com.adel.assistant.data.TaskItem
import com.adel.assistant.data.TaskStore
import com.adel.assistant.data.TunnelFinanceStore
import com.adel.assistant.data.TunnelReportStore
import com.adel.assistant.data.formatMoney
import com.adel.assistant.data.AppMenu
import com.adel.assistant.navigation.Routes
import com.adel.assistant.ai.tools.AssistantActionTools
import com.adel.assistant.ai.tools.DatabaseTools
import java.util.Locale

data class AgentChoice(
    val label: String,
    val value: String
)

data class AgentReply(
    val text: String,
    val navigateTo: String? = null,
    val action: String? = null,
    val choices: List<AgentChoice> = emptyList()
)

/**
 * موتور دستیار داخلی AdelAssistant.
 * بدون API خارجی؛ درخواست محاوره‌ای را به Action واقعی برنامه تبدیل می‌کند.
 */
object AssistantAgent {
    private enum class PendingType { TASK_CATEGORY, TASK_DELETE_CONFIRM, MENU_CHOICE, ACTION_CONFIRM }
    private data class Pending(
        val type: PendingType,
        val title: String = "",
        val store: String? = null,
        val index: Int = -1,
        val actionId: String? = null,
        val extra: String = "",
        val choices: List<AgentChoice> = emptyList()
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
        databaseIntent(context, msg)?.let { return it }
        actionIntent(context, msg)?.let { return it }

        // «برو» یک فرمان صریح برای ناوبری است؛ در غیر این صورت کار را داخل چت انجام می‌دهیم.
        if (hasNavigationVerb(msg)) {
            navIntent(msg)?.let { return it }
        }

        taskIntent(context, msg)?.let { return it }
        statsIntent(context, msg)?.let { return it }
        if (hasAny(msg, listOf("امروز", "برنامه امروز", "کارهای امروز"))) return AgentReply(todayPlan(context))

        menuIntent(msg)?.let { return it }
        return AgentReply("منظورت را کامل متوجه نشدم. اسم بخش یا کاری که می‌خواهی را بگو؛ اگر چند معنی داشته باشد گزینه‌های قابل انتخاب نشان می‌دهم.")
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

    private fun databaseIntent(context: Context, msg: String): AgentReply? {
        val mentionsDb = hasAny(msg, listOf("پایگاه داده", "دیتابیس", "بانک اطلاعات", "اطلاعات برنامه", "داده های برنامه", "داده‌های برنامه", "سوابق برنامه", "کل اطلاعات"))
        val asksSearch = hasAny(msg, listOf("جستجو", "پیدا کن", "بگرد", "پیدا", "بررسی کن"))
        val asksOverview = hasAny(msg, listOf("نمای کلی", "خلاصه", "چند رکورد", "چه اطلاعاتی", "همه دیتابیس", "همه داده"))
        val asksRead = hasAny(msg, listOf("اطلاعات", "سوابق", "رکورد", "لیست", "نمایش", "بخوان"))
        val datasetMention = DatabaseTools.resolveDataset(msg) != null
        if (!mentionsDb && !asksSearch && !asksOverview && !datasetMention) return null
        if (AssistantPermissionStore.get(context, "data_read") == AssistantPermission.FORBIDDEN) {
            return AgentReply("دسترسی دستیار به داده‌های برنامه در تنظیمات مجوزها بسته است.")
        }
        if (asksOverview || (mentionsDb && !asksSearch && !asksRead)) return AgentReply(DatabaseTools.overview(context))
        if (asksSearch) {
            val q = extractDatabaseSearch(msg)
            if (q.isBlank()) return AgentReply("عبارت مورد جستجو را بعد از «جستجو کن» بگو؛ مثلاً «در همه اطلاعات جستجو کن 280». ")
            return AgentReply(DatabaseTools.search(context, q))
        }
        val dataset = DatabaseTools.resolveDataset(msg)
        if (dataset != null) return AgentReply(DatabaseTools.read(context, dataset))
        return AgentReply(DatabaseTools.listDatasets())
    }

    private fun extractDatabaseSearch(msg: String): String {
        var q = msg
        listOf(
            "در همه اطلاعات جستجو کن", "در همه اطلاعات جستجو", "در پایگاه داده جستجو کن",
            "در دیتابیس جستجو کن", "جستجو کن", "جستجو", "پیدا کن", "پیدا", "بگرد", "بررسی کن"
        ).forEach { q = q.replace(it, " ", ignoreCase = true) }
        return q.replace(Regex("\\s+"), " ").trim().trim(':', '-', '،', ' ')
    }

    private fun actionIntent(context: Context, msg: String): AgentReply? {
        // حافظه: عملیات کم‌خطر و بدون تأیید جداگانه انجام می‌شود.
        if (hasAny(msg, listOf("یادت باشه", "به خاطر بسپار", "ذخیره کن در حافظه", "به حافظه اضافه کن"))) {
            val text = msg.replace(Regex("(?i)یادت باشه|به خاطر بسپار|ذخیره کن در حافظه|به حافظه اضافه کن"), " ")
                .replace(Regex("\\s+"), " ").trim().take(300)
            if (text.isBlank()) return AgentReply("چه چیزی را در حافظه ذخیره کنم؟")
            if (AssistantPermissionStore.get(context, "memory_write") == AssistantPermission.FORBIDDEN) return AgentReply("دسترسی نوشتن حافظه بسته است.")
            return AgentReply(AssistantActionTools.remember(context, text))
        }

        if (hasAny(msg, listOf("تسویه پروژه", "پروژه را تسویه", "تسویه کن"))) {
            if (!permissionAllows(context, "finance_write")) return AgentReply("دسترسی عملیات مالی بسته است.")
            val p = findProject(context, msg) ?: return AgentReply("نام یا شماره ردیف پروژه را مشخص کن؛ مثلاً «پروژه ردیف 12 را تسویه کن». ")
            pending = Pending(PendingType.ACTION_CONFIRM, title = "تسویه پروژه «${p.name}»", store = p.row, actionId = AssistantActionTools.SETTLE_PROJECT)
            return AgentReply("⚠️ پروژه «${p.name}» تسویه شود؟", choices = confirmChoices())
        }

        if (hasAny(msg, listOf("برگردان تسویه", "لغو تسویه", "تسویه را برگرد"))) {
            if (!permissionAllows(context, "finance_write")) return AgentReply("دسترسی عملیات مالی بسته است.")
            val p = findProject(context, msg) ?: return AgentReply("نام یا شماره ردیف پروژه را مشخص کن.")
            pending = Pending(PendingType.ACTION_CONFIRM, title = "برگرداندن تسویه «${p.name}»", store = p.row, actionId = AssistantActionTools.UNSETTLE_PROJECT)
            return AgentReply("⚠️ تسویه پروژه «${p.name}» برگردانده شود؟", choices = confirmChoices())
        }

        if (hasAny(msg, listOf("حذف پروژه", "پروژه را حذف", "پاک کردن پروژه"))) {
            if (!permissionAllows(context, "delete")) return AgentReply("دسترسی حذف داده‌ها بسته است.")
            val p = findProject(context, msg) ?: return AgentReply("نام یا شماره ردیف پروژه را مشخص کن.")
            pending = Pending(PendingType.ACTION_CONFIRM, title = "حذف پروژه «${p.name}»", store = p.row, actionId = AssistantActionTools.DELETE_PROJECT)
            return AgentReply("⚠️ این عملیات دائمی است. پروژه «${p.name}» حذف شود؟", choices = confirmChoices())
        }

        if (hasAny(msg, listOf("ثبت دریافت تونل", "دریافت تونل", "مبلغ دریافت تونل"))) {
            if (!permissionAllows(context, "finance_write")) return AgentReply("دسترسی عملیات مالی بسته است.")
            val amount = extractAmount(msg) ?: return AgentReply("مبلغ دریافت را مشخص کن؛ مثلاً «ثبت دریافت تونل 5000000». ")
            val date = extractDate(msg)
            pending = Pending(
                PendingType.ACTION_CONFIRM,
                title = "ثبت دریافت تونل ${"%.0f".format(amount)}",
                actionId = AssistantActionTools.ADD_TUNNEL_RECEIPT,
                extra = "$amount|${date.orEmpty()}"
            )
            return AgentReply("⚠️ دریافت ${"%.0f".format(amount)} برای تونل ثبت شود؟", choices = confirmChoices())
        }
        return null
    }

    private fun confirmChoices() = listOf(AgentChoice("تأیید", "بله"), AgentChoice("لغو", "لغو"))

    private fun permissionAllows(context: Context, key: String): Boolean =
        AssistantPermissionStore.get(context, key) != AssistantPermission.FORBIDDEN

    private fun findProject(context: Context, msg: String): com.adel.assistant.data.ProjectEntry? {
        val digits = Regex("\\d+").find(msg)?.value
        if (digits != null) {
            ProjectStore.all(context).firstOrNull { it.row == digits }?.let { return it }
        }
        val cleaned = msg.replace(Regex("(?i)تسویه پروژه|پروژه را تسویه|تسویه کن|برگردان تسویه|لغو تسویه|تسویه را برگرد|حذف پروژه|پروژه را حذف|پاک کردن پروژه"), " ")
            .replace(Regex("\\s+"), " ").trim()
        if (cleaned.isBlank()) return null
        return ProjectStore.all(context).firstOrNull { it.name.contains(cleaned, true) || it.employer.contains(cleaned, true) }
    }

    private fun extractAmount(msg: String): Double? {
        val raw = Regex("(?<![A-Za-z])(?:[0-9۰-۹][0-9۰-۹,،.]*)(?![A-Za-z])").findAll(msg)
            .map { it.value.replace(",", "").replace("،", "").replace(".", "") }
            .mapNotNull { it.replace('۰','0').replace('۱','1').replace('۲','2').replace('۳','3').replace('۴','4').replace('۵','5').replace('۶','6').replace('۷','7').replace('۸','8').replace('۹','9').toDoubleOrNull() }
            .firstOrNull { it > 0 }
        return raw
    }

    private fun extractDate(msg: String): String? = Regex("\\d{2,4}[/\\-]\\d{1,2}[/\\-]\\d{1,2}").find(msg)?.value

    private fun resolvePending(context: Context, msg: String): AgentReply? {
        val p = pending ?: return null
        when (p.type) {
            PendingType.TASK_CATEGORY -> {
                val store = when (msg) {
                    "__task_tunnel" -> "tunnel_tasks"
                    "__task_project" -> "project_tasks"
                    else -> categoryStore(msg)
                }
                if (store != null) {
                    pending = null
                    return addTask(context, store, p.title)
                }
                if (isBoth(msg) || msg == "__task_both") {
                    pending = null
                    addTask(context, "tunnel_tasks", p.title)
                    addTask(context, "project_tasks", p.title)
                    return AgentReply("تسک «${p.title}» در هر دو بخش تونل و پروژه ثبت شد ✅")
                }
                val numeric = msg.toIntOrNull()
                if (numeric == 1) { pending = null; return addTask(context, "tunnel_tasks", p.title) }
                if (numeric == 2) { pending = null; return addTask(context, "project_tasks", p.title) }
                if (numeric == 3) {
                    pending = null
                    addTask(context, "tunnel_tasks", p.title)
                    addTask(context, "project_tasks", p.title)
                    return AgentReply("تسک «${p.title}» در هر دو بخش تونل و پروژه ثبت شد ✅")
                }
                if (isCancel(msg)) { pending = null; return AgentReply("عملیات لغو شد.") }
                return choiceReply("تسک «${p.title}» برای کدام بخش ثبت شود؟", taskCategoryChoices())
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
                return AgentReply("برای حذف «${p.title}» تأیید یا لغو کن.", choices = listOf(AgentChoice("بله", "بله"), AgentChoice("لغو", "لغو")))
            }
            PendingType.MENU_CHOICE -> {
                val exact = p.choices.firstOrNull { normalize(it.value) == msg || normalize(it.label) == msg }
                if (exact != null) {
                    pending = null
                    return executeChoice(context, msg, exact)
                }
                val numeric = msg.toIntOrNull()?.let { it - 1 }
                if (numeric != null && numeric in p.choices.indices) {
                    val c = p.choices[numeric]
                    pending = null
                    return executeChoice(context, c.value, c)
                }
                if (isCancel(msg)) { pending = null; return AgentReply("عملیات لغو شد.") }
                return choiceReply("یکی از گزینه‌ها را انتخاب کن:", p.choices)
            }
            PendingType.ACTION_CONFIRM -> {
                if (isCancel(msg)) {
                    pending = null
                    return AgentReply("عملیات لغو شد.")
                }
                if (!isConfirm(msg)) {
                    return AgentReply("برای اجرای «${p.title}» تأیید یا لغو کن.", choices = listOf(AgentChoice("تأیید", "بله"), AgentChoice("لغو", "لغو")))
                }
                val result = when (p.actionId) {
                    AssistantActionTools.SETTLE_PROJECT -> AssistantActionTools.settleProject(context, p.store.orEmpty())
                    AssistantActionTools.UNSETTLE_PROJECT -> AssistantActionTools.unsettleProject(context, p.store.orEmpty())
                    AssistantActionTools.DELETE_PROJECT -> AssistantActionTools.deleteProject(context, p.store.orEmpty())
                    AssistantActionTools.ADD_TUNNEL_RECEIPT -> {
                        val parts = p.extra.split("|", limit = 2)
                        val amount = parts.getOrNull(0)?.toDoubleOrNull() ?: 0.0
                        val date = parts.getOrNull(1).orEmpty().ifBlank { "" }
                        AssistantActionTools.addTunnelReceipt(context, amount, date)
                    }
                    else -> "عملیات ناشناخته است."
                }
                pending = null
                return AgentReply(result)
            }
        }
    }

    private fun executeChoice(context: Context, value: String, choice: AgentChoice): AgentReply {
        return when {
            value == "__task_tunnel" -> {
                pending = null
                AgentReply(taskStats(context, "tunnel_tasks"))
            }
            value == "__task_project" -> {
                pending = null
                AgentReply(taskStats(context, "project_tasks"))
            }
            value == "__task_both" -> {
                pending = null
                AgentReply(taskStats(context, null))
            }
            value.startsWith("route:") -> AgentReply("باشه، «${choice.label}» را باز می‌کنم.", value.removePrefix("route:"), "navigate")
            else -> AgentReply("گزینه «${choice.label}» انتخاب شد.")
        }
    }

    private fun choiceReply(text: String, choices: List<AgentChoice>): AgentReply = AgentReply(text, choices = choices)

    private fun taskCategoryChoices() = listOf(
        AgentChoice("تونل", "__task_tunnel"),
        AgentChoice("پروژه", "__task_project"),
        AgentChoice("هردو", "__task_both")
    )

    private fun normalize(s: String): String = s.trim()
        .replace('ي','ی').replace('ى','ی').replace('ك','ک').replace('ۀ','ه')
        .replace('ة','ه').replace('ؤ','و').replace('إ','ا').replace('أ','ا')
        .replace('ـ',' ')
        .replace(Regex("[\u064B-\u065F\u0670]"), "")
        .replace('‌',' ')
        .replace(Regex("[،؛؟!,.:/\\|()\\[\\]{}\"'`~]"), " ")
        .replace(Regex("\\s+"), " ").lowercase(Locale.ROOT).trim()

    private fun tokens(msg: String): Set<String> = normalize(msg).split(' ').filter { it.isNotBlank() }.toSet()
    private fun hasAny(msg: String, keys: List<String>) = keys.any { normalize(msg).contains(normalize(it)) }
    private fun isConfirm(msg: String) = normalize(msg) in setOf("بله","اره","آره","تایید","تأیید","ok","باشه","حتما")
    private fun isCancel(msg: String) = normalize(msg) in setOf("نه","لغو","بیخیال","کنسل","انصراف")
    private fun isBoth(msg: String) = normalize(msg) in setOf("هردو","هر دو","هر دو بخش","همه")
    private fun isHelp(msg: String) = hasAny(msg, listOf("کمک","راهنما","چی میتونی","چه کار میتونی","سلام","درود"))
    private fun hasNavigationVerb(msg: String) = hasAny(msg, listOf("برو","باز کن","وارد شو","ببر به","برو به","بازش کن"))

    private fun helpText() = """
🤖 دستیار AdelAssistant
می‌توانی محاوره‌ای درخواست بدهی. برای ناوبری صریح بگو «برو ...».
دستیار می‌تواند داده‌های داخلی برنامه را بخواند و برای عملیات حساس قبل از تغییر داده تأیید می‌گیرد.
اگر یک واژه چند معنی داشته باشد، گزینه‌های قابل کلیک می‌دهم و عدد هم به‌عنوان راه دوم پذیرفته می‌شود.
مثلاً: «تسک»، «پروژه»، «برو پروژه»، «برو درون‌یابی»، «کارهای باز تونل».
""".trimIndent()

    private data class MenuCandidate(val title: String, val route: String, val section: String, val tab: String)

    private fun menuCandidates(): List<MenuCandidate> {
        val result = mutableListOf<MenuCandidate>()
        AppMenu.sections.forEach { section ->
            section.tabs.forEach { tab ->
                tab.items.forEach { item ->
                    result += MenuCandidate(item.title, item.route, section.title, tab.title)
                }
            }
        }
        // مسیرهای موجود که در منوی فعلی عنوان مستقل ندارند.
        result += listOf(
            MenuCandidate("چینیج تونل", Routes.SURVEY_TUNNEL_CHAINAGE, "نقشه‌برداری", "تونل"),
            MenuCandidate("الاین مختصات", Routes.TOOL_ALIGN, "ابزار", ""),
            MenuCandidate("محاسبه احجام", Routes.TOOL_VOLUME, "ابزار", ""),
            MenuCandidate("نمایش نقشه", Routes.TOOL_DXF_PREVIEW, "ابزار", "")
        )
        return result.distinctBy { it.route }
    }

    private fun aliases(title: String): Set<String> {
        val base = tokens(title).toMutableSet()
        val map = mapOf(
            "تسک" to setOf("تسک","تسکها","تسک‌ها","کار","وظیفه","کارها"),
            "پروژه" to setOf("پروژه","پروژ","پروژهها","پروژه‌ها"),
            "تونل" to setOf("تونل","tunnel"),
            "گزارش" to setOf("گزارش","ریپورت","report"),
            "وقایع" to setOf("وقایع","رویداد","رویدادها","رخداد"),
            "تقویم" to setOf("تقویم","calendar"),
            "کارکرد" to setOf("کارکرد","صورت وضعیت","کردکرد"),
            "دریافتی" to setOf("دریافتی","دریافت","وصول"),
            "مطالبات" to setOf("مطالبات","طلب","مانده","بدهی"),
            "فاکتور" to setOf("فاکتور","صورتحساب","صورت حساب"),
            "ترسیم" to setOf("ترسیم","dxf","رسم"),
            "مبدل" to setOf("مبدل","تبدیل","کنورت","converter"),
            "تخلیه" to setOf("تخلیه","دوربین","total station","سندینگ","sanding"),
            "درون‌یابی" to setOf("درون‌یابی","درونیابی","درون یابی","interpolate"),
            "مساحت" to setOf("مساحت","محیط","area"),
            "حجم" to setOf("حجم","احجام","volume"),
            "مکان" to setOf("مکان","مختصات","لوکیشن","location"),
            "پشتیبان" to setOf("پشتیبان","بکاپ","backup")
        )
        base.toList().forEach { word -> map[word]?.let { base.addAll(it.map(::normalize)) } }
        return base
    }

    private fun scoreCandidate(msgTokens: Set<String>, c: MenuCandidate): Int {
        val titleTokens = aliases(c.title)
        var score = 0
        msgTokens.forEach { token ->
            if (token in titleTokens) score += 6
            else if (titleTokens.any { it.startsWith(token) || token.startsWith(it) }) score += 2
        }
        if (msgTokens.contains(normalize(c.tab)) && c.tab.isNotBlank()) score += 4
        if (msgTokens.contains(normalize(c.section))) score += 2
        return score
    }

    private fun menuIntent(msg: String): AgentReply? {
        val ts = tokens(msg)
        if (ts.isEmpty()) return null
        val candidates = menuCandidates().map { it to scoreCandidate(ts, it) }.filter { it.second >= 4 }.sortedByDescending { it.second }
        if (candidates.isEmpty()) return null
        val top = candidates.first().second
        val best = candidates.filter { it.second >= top - 2 }.take(6)
        if (best.size == 1 && top >= 6) return AgentReply("«${best.first().first.title}» را در نظر گرفتم. اگر می‌خواهی بازش کنم بگو «برو ${best.first().first.title}».")
        val choices = best.map { AgentChoice(it.first.title, "route:${it.first.route}") }.distinctBy { it.label }
        pending = Pending(PendingType.MENU_CHOICE, choices = choices)
        return choiceReply("چند گزینه نزدیک پیدا کردم؛ کدام را می‌خواهی؟", choices)
    }

    private fun navIntent(msg: String): AgentReply? {
        val ts = tokens(msg)
        val candidates = menuCandidates().map { it to scoreCandidate(ts, it) }.filter { it.second >= 4 }.sortedByDescending { it.second }
        if (candidates.isEmpty()) return null
        val top = candidates.first().second
        val best = candidates.filter { it.second >= top - 2 }.take(6)
        if (best.size == 1) {
            val c = best.first().first
            return AgentReply("باشه، «${c.title}» را باز می‌کنم.", c.route, "navigate")
        }
        val choices = best.map { AgentChoice(it.first.title, "route:${it.first.route}") }.distinctBy { it.label }
        pending = Pending(PendingType.MENU_CHOICE, choices = choices)
        return choiceReply("برای «${ts.joinToString(" ")}» چند مقصد پیدا کردم؛ کدام را باز کنم؟", choices)
    }

    private fun categoryStore(msg: String): String? = when {
        hasAny(msg, listOf("تونل","tunnel")) && !hasAny(msg, listOf("پروژه","project")) -> "tunnel_tasks"
        hasAny(msg, listOf("پروژه","project")) && !hasAny(msg, listOf("تونل","tunnel")) -> "project_tasks"
        else -> null
    }

    private fun taskIntent(context: Context, msg: String): AgentReply? {
        val store = categoryStore(msg)
        val onlyTaskWord = normalize(msg) in setOf("تسک", "تسکها", "تسک ها", "کار", "وظیفه", "کارها")
        if (onlyTaskWord) {
            pending = Pending(PendingType.MENU_CHOICE, choices = taskCategoryChoices())
            return choiceReply("تسک‌های کدام بخش را می‌خواهی؟", taskCategoryChoices())
        }
        val mentionsTask = hasAny(msg, listOf("تسک","وظیفه","کار باقی","کار باز","یادآور"))
        val create = hasAny(msg, listOf("ثبت کن","اضافه کن","بساز","ایجاد کن","تسک جدید"))
        val complete = hasAny(msg, listOf("انجام شده","انجام شده است","انجام شد","تیک بزن","تکمیل کن","تمام شد"))
        val delete = hasAny(msg, listOf("حذف کن","پاک کن","بنداز دور"))
        if (!mentionsTask && !create && !complete && !delete) return null

        if (delete) {
            if (AssistantPermissionStore.get(context, "delete") == AssistantPermission.FORBIDDEN) return AgentReply("دسترسی حذف تسک‌ها بسته است.")
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
            if (AssistantPermissionStore.get(context, "tasks") == AssistantPermission.FORBIDDEN) return AgentReply("دسترسی تغییر تسک‌ها بسته است.")
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
            if (AssistantPermissionStore.get(context, "tasks") == AssistantPermission.FORBIDDEN) return AgentReply("دسترسی ثبت تسک‌ها بسته است.")
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
