package com.adel.assistant.ai.tools

import com.adel.assistant.navigation.Routes

data class AssistantTool(
    val id: String,
    val title: String,
    val permission: String,
    val route: String? = null,
    val description: String = ""
)

object AssistantToolRegistry {
    val all = listOf(
        AssistantTool("navigation", "باز کردن صفحات", "navigation", description = "باز کردن هر صفحه از منوی برنامه"),
        AssistantTool("tasks", "مدیریت تسک‌ها", "tasks", description = "ثبت، نمایش، تکمیل و حذف تسک‌ها"),
        AssistantTool(DatabaseTools.OVERVIEW, "نمای کلی داده‌ها", "data_read", description = "آمار همه مجموعه‌های داده داخلی"),
        AssistantTool(DatabaseTools.LIST, "فهرست داده‌ها", "data_read", description = "فهرست تمام مجموعه‌هایی که دستیار می‌تواند بخواند"),
        AssistantTool(DatabaseTools.SEARCH, "جستجوی سراسری داده‌ها", "data_read", description = "جستجو در تمام داده‌های اصلی برنامه"),
        AssistantTool(DatabaseTools.READ, "خواندن داده داخلی", "data_read", description = "خواندن رکوردهای یک مجموعه داده مشخص"),
        AssistantTool(AssistantActionTools.REMEMBER, "حافظه", "memory_write", description = "ذخیره نکته برای گفتگوهای بعدی"),
        AssistantTool(AssistantActionTools.SETTLE_PROJECT, "تسویه پروژه", "finance_write", description = "علامت‌گذاری پروژه به‌عنوان تسویه‌شده"),
        AssistantTool(AssistantActionTools.UNSETTLE_PROJECT, "برگرداندن تسویه", "finance_write", description = "برگرداندن پروژه از حالت تسویه"),
        AssistantTool(AssistantActionTools.ADD_TUNNEL_RECEIPT, "ثبت دریافت تونل", "finance_write", description = "ثبت مبلغ دریافتی در جدول مالی تونل"),
        AssistantTool(AssistantActionTools.DELETE_PROJECT, "حذف پروژه", "delete", description = "حذف یک ردیف پروژه با تأیید کاربر"),
        AssistantTool(SurveyTools.FIND_BY_CHAINAGE, "یافتن نقطه با کیلومتراژ", "data_read"),
        AssistantTool(SurveyTools.FIND_BY_NAME, "یافتن نقطه با شماره", "data_read"),
        AssistantTool(SurveyTools.FIND_BY_RANGE, "یافتن نقاط در بازه", "data_read"),
        AssistantTool(SurveyTools.SEARCH, "جستجوی برداشت", "data_read"),
        AssistantTool("interpolation", "درون‌یابی مختصات", "calculations", Routes.TOOL_INTERPOLATE),
        AssistantTool("area", "محاسبه مساحت", "calculations", Routes.TOOL_AREA),
        AssistantTool("volume", "محاسبه حجم", "calculations", Routes.TOOL_VOLUME),
        AssistantTool("align", "ترانسفورم/هم‌راستاسازی", "calculations", Routes.TOOL_ALIGN),
        AssistantTool("dxf", "مبدل و نمایش DXF", "files_read", Routes.TOOL_DXF),
        AssistantTool("gsi", "مبدل GSI", "files_read", Routes.TOOL_GSI),
        AssistantTool("finance", "تحلیل مالی", "finance_write"),
        AssistantTool("delete", "حذف داده", "delete")
    )
}
