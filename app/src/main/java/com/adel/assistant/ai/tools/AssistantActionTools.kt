package com.adel.assistant.ai.tools

import android.content.Context
import com.adel.assistant.data.AssistantMemoryStore
import com.adel.assistant.data.ProjectStore
import com.adel.assistant.data.TunnelFinanceStore

/** عملیات تغییر‌دهندهٔ داده. عملیات حساس باید قبل از اجرا توسط Agent تأیید شوند. */
object AssistantActionTools {
    const val REMEMBER = "remember"
    const val SETTLE_PROJECT = "settle_project"
    const val UNSETTLE_PROJECT = "unsettle_project"
    const val ADD_TUNNEL_RECEIPT = "add_tunnel_receipt"
    const val DELETE_PROJECT = "delete_project"

    fun remember(context: Context, text: String): String {
        AssistantMemoryStore.add(context, text)
        return "به حافظه دستیار اضافه شد: «${text.trim()}»"
    }

    fun settleProject(context: Context, row: String): String {
        val p = ProjectStore.all(context).firstOrNull { it.row == row }
            ?: return "پروژه با ردیف $row پیدا نشد."
        ProjectStore.markSettled(context, row)
        return "پروژه «${p.name}» تسویه‌شده علامت خورد."
    }

    fun unsettleProject(context: Context, row: String): String {
        val p = ProjectStore.all(context).firstOrNull { it.row == row }
            ?: return "پروژه با ردیف $row پیدا نشد."
        ProjectStore.markUnsettled(context, row)
        return "تسویه پروژه «${p.name}» برگشت داده شد."
    }

    fun addTunnelReceipt(context: Context, amount: Double, date: String, note: String = ""): String {
        if (amount <= 0.0) return "مبلغ دریافت باید بیشتر از صفر باشد."
        val ok = TunnelFinanceStore.addReceipt(context, amount, date, note)
        return if (ok) "دریافت ${"%.0f".format(amount)} برای تونل ثبت شد." else "جایی برای ثبت دریافت جدید در جدول مالی تونل پیدا نشد."
    }

    fun deleteProject(context: Context, row: String): String {
        val p = ProjectStore.all(context).firstOrNull { it.row == row }
            ?: return "پروژه با ردیف $row پیدا نشد."
        ProjectStore.delete(context, row)
        return "پروژه «${p.name}» حذف شد."
    }
}
