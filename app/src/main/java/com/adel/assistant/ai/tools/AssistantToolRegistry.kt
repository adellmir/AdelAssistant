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
        AssistantTool("navigation", "باز کردن صفحات", "navigation", description = "باز کردن یک صفحه از برنامه"),
        AssistantTool(TaskTools.CREATE, "ثبت تسک", "tasks", description = "ثبت یک تسک در تونل یا پروژه"),
        AssistantTool(TaskTools.LIST, "نمایش تسک‌ها", "tasks", description = "نمایش تسک‌های باز تونل یا پروژه"),
        AssistantTool(TaskTools.COMPLETE, "تکمیل تسک", "tasks", description = "انجام‌شده کردن یک تسک"),
        AssistantTool(TaskTools.DELETE, "حذف تسک", "delete", description = "حذف یک تسک پس از تأیید کاربر"),
        AssistantTool(SurveyTools.FIND_BY_CHAINAGE, "یافتن نقطه بر اساس کیلومتراژ", "survey_read", description = "یافتن نقطه دقیق/نزدیک و مختصات در یک کیلومتراژ از داده واقعی تونل"),
        AssistantTool(SurveyTools.FIND_BY_NAME, "یافتن نقطه بر اساس شماره", "survey_read", description = "خواندن اطلاعات واقعی یک نقطه تونل بر اساس شماره"),
        AssistantTool(SurveyTools.FIND_BY_RANGE, "نقاط یک بازه کیلومتراژی", "survey_read", description = "فهرست نقاط ثبت‌شده در یک بازه کیلومتراژی"),
        AssistantTool(SurveyTools.SEARCH, "جستجوی داده برداشت", "survey_read", description = "جستجوی شماره نقطه یا نوع نقطه در داده‌های تونل"),
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
