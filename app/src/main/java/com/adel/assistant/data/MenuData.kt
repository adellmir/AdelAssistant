package com.adel.assistant.data

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.*
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
        icon = Icons.Filled.Map,
        color = WorkPrimary,
        tabs = listOf(
            MenuTab("تونل", listOf(
                MenuItem("گزارش روزانه", Icons.Filled.Assignment, Routes.SURVEY_TUNNEL_REPORT),
                MenuItem("نقاط حفاری", Icons.Filled.Place, Routes.SURVEY_TUNNEL_EXCAVATION),
                MenuItem("نقشه حفاری تونل", Icons.Filled.Map, Routes.SURVEY_TUNNEL_MAP),
                MenuItem("ثبت وقایع تونل", Icons.Filled.EventNote, Routes.SURVEY_TUNNEL_EVENTS),
                MenuItem("وضعیت", Icons.Filled.Insights, Routes.SURVEY_TUNNEL_STATUS),
                MenuItem("تسک‌ها", Icons.Filled.CheckCircle, Routes.SURVEY_TUNNEL_TASKS)
            )),
            MenuTab("پروژه‌ها", listOf(
                MenuItem("ثبت پروژه", Icons.Filled.AddLocationAlt, Routes.SURVEY_PROJECT_REGISTER),
                MenuItem("کارفرمایان و پروژه‌ها", Icons.Filled.People, Routes.SURVEY_PROJECT_CLIENTS),
                MenuItem("ثبت وقایع پروژه", Icons.Filled.EventNote, Routes.SURVEY_PROJECT_EVENTS),
                MenuItem("تقویم کاری", Icons.Filled.CalendarMonth, Routes.SURVEY_PROJECT_CALENDAR),
                MenuItem("تسک‌ها", Icons.Filled.CheckCircle, Routes.SURVEY_PROJECT_TASKS)
            ))
        )
    )

    private val finance = MenuSection(
        title = "مالی",
        icon = Icons.Filled.AttachMoney,
        color = FinancePrimary,
        tabs = listOf(
            MenuTab("تونل", listOf(
                MenuItem("کارکرد ماهانه", Icons.Filled.CalendarMonth, Routes.FIN_TUNNEL_WORKLOG),
                MenuItem("دریافتی‌ها", Icons.Filled.Payments, Routes.FIN_TUNNEL_RECEIPTS),
                MenuItem("خلاصه مطالبات", Icons.Filled.Summarize, Routes.FIN_TUNNEL_SUMMARY)
            )),
            MenuTab("پروژه‌ها", listOf(
                MenuItem("صدور فاکتور", Icons.Filled.ReceiptLong, Routes.FIN_PROJECT_INVOICE),
                MenuItem("ثبت دریافتی", Icons.Filled.Payments, Routes.FIN_PROJECT_RECEIPT),
                MenuItem("مطالبات کلی", Icons.Filled.AccountBalance, Routes.FIN_PROJECT_RECEIVABLES),
                MenuItem("وضعیت", Icons.Filled.Insights, Routes.FIN_PROJECT_STATUS)
            ))
        )
    )

    private val tools = MenuSection(
        title = "ابزار",
        icon = Icons.Filled.Build,
        color = ToolPrimary,
        tabs = listOf(
            MenuTab("", listOf(
                MenuItem("تبدیل به DXF", Icons.Filled.Architecture, Routes.TOOL_DXF),
                MenuItem("نمایش نقشه", Icons.Filled.Map, Routes.TOOL_DXF_PREVIEW),
                MenuItem("تخلیه دوربین", Icons.Filled.Bluetooth, Routes.TOOL_TOTAL_STATION),
                MenuItem("مبدل GSI", Icons.Filled.SwapHoriz, Routes.TOOL_GSI),
                MenuItem("مکان", Icons.Filled.MyLocation, Routes.TOOL_LOCATION),
                MenuItem("درون‌یابی", Icons.Filled.Functions, Routes.TOOL_INTERPOLATE),
                MenuItem("مساحت و محیط", Icons.Filled.SquareFoot, Routes.TOOL_AREA),
                MenuItem("محاسبه احجام", Icons.Filled.ViewInAr, Routes.TOOL_VOLUME),
                MenuItem("نامه‌نگاری", Icons.Filled.Mail, Routes.TOOL_LETTER),
                MenuItem("پشتیبان پایگاه", Icons.Filled.Storage, Routes.TOOL_BACKUP)
            ))
        )
    )

    // ترتیب برای نوار پایین RTL: راست=مالی، وسط=نقشه‌برداری، چپ=ابزار
    val sections = listOf(finance, survey, tools)
}
