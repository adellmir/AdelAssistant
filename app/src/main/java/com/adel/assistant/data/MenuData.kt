package com.adel.assistant.data

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.adel.assistant.navigation.Routes
import com.adel.assistant.ui.theme.FinancePrimary
import com.adel.assistant.ui.theme.ToolPrimary
import com.adel.assistant.ui.theme.WorkPrimary

data class MenuItem(
    val title: String,
    val icon: ImageVector,
    val route: String
)

data class MenuTab(
    val title: String,
    val items: List<MenuItem>
)

data class MenuSection(
    val title: String,
    val icon: ImageVector,
    val color: Color,
    val tabs: List<MenuTab>
)

object AppMenu {

    private val survey = MenuSection(
        title = "نقشه‌برداری",
        icon = Icons.Outlined.Map,
        color = WorkPrimary,
        tabs = listOf(
            MenuTab("تونل", listOf(
                MenuItem("گزارش روزانه", Icons.Outlined.Assignment, Routes.SURVEY_TUNNEL_REPORT),
                MenuItem("نقاط حفاری", Icons.Outlined.Place, Routes.SURVEY_TUNNEL_EXCAVATION),
                MenuItem("نقاط تونل", Icons.Outlined.EventNote, Routes.SURVEY_TUNNEL_EVENTS),
                MenuItem("وضعیت", Icons.Outlined.Insights, Routes.SURVEY_TUNNEL_STATUS),
                MenuItem("تسک‌ها", Icons.Outlined.CheckCircle, Routes.SURVEY_TUNNEL_TASKS)
            )),
            MenuTab("پروژه‌ها", listOf(
                MenuItem("ثبت پروژه", Icons.Outlined.AddLocationAlt, Routes.SURVEY_PROJECT_REGISTER),
                MenuItem("کارفرمایان و پروژه‌ها", Icons.Outlined.People, Routes.SURVEY_PROJECT_CLIENTS),
                MenuItem("ثبت وقایع پروژه", Icons.Outlined.EventNote, Routes.SURVEY_PROJECT_EVENTS),
                MenuItem("تقویم کاری", Icons.Outlined.CalendarMonth, Routes.SURVEY_PROJECT_CALENDAR),
                MenuItem("تسک‌ها", Icons.Outlined.CheckCircle, Routes.SURVEY_PROJECT_TASKS)
            ))
        )
    )

    private val finance = MenuSection(
        title = "مالی",
        icon = Icons.Outlined.Payments,
        color = FinancePrimary,
        tabs = listOf(
            MenuTab("تونل", listOf(
                MenuItem("کارکرد ماهانه", Icons.Outlined.CalendarMonth, Routes.FIN_TUNNEL_WORKLOG),
                MenuItem("دریافتی‌ها", Icons.Outlined.Payments, Routes.FIN_TUNNEL_RECEIPTS),
                MenuItem("خلاصه مطالبات", Icons.Outlined.Summarize, Routes.FIN_TUNNEL_SUMMARY)
            )),
            MenuTab("پروژه‌ها", listOf(
                MenuItem("صدور فاکتور", Icons.Outlined.ReceiptLong, Routes.FIN_PROJECT_INVOICE),
                MenuItem("ثبت دریافتی", Icons.Outlined.Payments, Routes.FIN_PROJECT_RECEIPT),
                MenuItem("مطالبات کلی", Icons.Outlined.AccountBalance, Routes.FIN_PROJECT_RECEIVABLES),
                MenuItem("وضعیت", Icons.Outlined.Insights, Routes.FIN_PROJECT_STATUS)
            ))
        )
    )

    private val tools = MenuSection(
        title = "ابزار",
        icon = Icons.Outlined.Build,
        color = ToolPrimary,
        tabs = listOf(
            MenuTab("", listOf(
                MenuItem("ترسیم نقشه", Icons.Outlined.Architecture, Routes.TOOL_DXF),
                MenuItem("نمایش نقشه", Icons.Outlined.Map, Routes.TOOL_DXF_PREVIEW),
                MenuItem("تخلیه دوربین", Icons.Outlined.Bluetooth, Routes.TOOL_TOTAL_STATION),
                MenuItem("مبدل GSI", Icons.Outlined.SwapHoriz, Routes.TOOL_GSI),
                MenuItem("مکان", Icons.Outlined.MyLocation, Routes.TOOL_LOCATION),
                MenuItem("درون‌یابی", Icons.Outlined.Functions, Routes.TOOL_INTERPOLATE),
                MenuItem("مساحت و محیط", Icons.Outlined.SquareFoot, Routes.TOOL_AREA),
                MenuItem("محاسبه احجام", Icons.Outlined.ViewInAr, Routes.TOOL_VOLUME),
                MenuItem("نامه‌نگاری", Icons.Outlined.Mail, Routes.TOOL_LETTER),
                MenuItem("پشتیبان پایگاه", Icons.Outlined.Storage, Routes.TOOL_BACKUP)
            ))
        )
    )

    // ترتیب برای نوار پایین RTL: راست=مالی، وسط=نقشه‌برداری، چپ=ابزار
    val sections = listOf(finance, survey, tools)
}
