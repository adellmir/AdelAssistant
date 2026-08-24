package com.adel.assistant.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.adel.assistant.ui.HomeScreen
import com.adel.assistant.ui.screens.AreaScreen
import com.adel.assistant.ui.screens.GsiConverterScreen
import com.adel.assistant.ui.screens.InterpolateScreen
import com.adel.assistant.ui.screens.LocationScreen
import com.adel.assistant.ui.screens.SimpleRecordScreen
import com.adel.assistant.ui.screens.ViaClaudeScreen
import com.adel.assistant.ui.screens.VolumeScreen
import com.adel.assistant.ui.theme.FinancePrimary
import com.adel.assistant.ui.theme.ToolPrimary
import com.adel.assistant.ui.theme.WorkPrimary

@Composable
fun AppNavigation() {
    val navController: NavHostController = rememberNavController()

    NavHost(navController = navController, startDestination = Routes.HOME) {

        composable(Routes.HOME) {
            HomeScreen(onNavigate = { route -> navController.navigate(route) })
        }

        // ---- نقشه‌برداری: تونل ----
        composable(Routes.SURVEY_TUNNEL_REPORT) {
            SimpleRecordScreen(
                title = "گزارش روزانه",
                color = WorkPrimary,
                csvName = "survey_tunnel_report",
                fields = listOf("تاریخ", "شرح پیشرفت امروز"),
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.SURVEY_TUNNEL_CHAINAGE) {
            ViaClaudeScreen(
                title = "کیلومتراژ/چینیج",
                color = WorkPrimary,
                description = "این محاسبه نیاز به فایل‌های PLAN.dxf و PROFILE.dxf دارد و فعلاً از طریق چت با کلود انجام می‌شود.",
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.SURVEY_TUNNEL_EVENTS) {
            SimpleRecordScreen(
                title = "ثبت وقایع تونل",
                color = WorkPrimary,
                csvName = "survey_tunnel_events",
                fields = listOf("تاریخ", "شرح واقعه"),
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.SURVEY_TUNNEL_STATUS) {
            SimpleRecordScreen(
                title = "وضعیت تونل",
                color = WorkPrimary,
                csvName = "survey_tunnel_status",
                fields = listOf("تاریخ", "پیشرفت حفاری", "کیلومتراژ شفت", "مانده حفاری"),
                onBack = { navController.popBackStack() }
            )
        }

        // ---- نقشه‌برداری: پروژه‌ها ----
        composable(Routes.SURVEY_PROJECT_REGISTER) {
            SimpleRecordScreen(
                title = "ثبت پروژه",
                color = WorkPrimary,
                csvName = "survey_project_register",
                fields = listOf("نام پروژه", "نام کارفرما", "تاریخ", "آدرس/توضیح"),
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.SURVEY_PROJECT_EVENTS) {
            SimpleRecordScreen(
                title = "ثبت وقایع پروژه",
                color = WorkPrimary,
                csvName = "survey_project_events",
                fields = listOf("نام پروژه", "تاریخ", "شرح واقعه"),
                onBack = { navController.popBackStack() }
            )
        }

        // ---- مالی: تونل ----
        composable(Routes.FIN_TUNNEL_WORKLOG) {
            SimpleRecordScreen(
                title = "کارکرد ماهانه (تونل)",
                color = FinancePrimary,
                csvName = "fin_tunnel_worklog",
                fields = listOf("ماه", "تعداد روز کارکرد", "توضیح"),
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.FIN_TUNNEL_RECEIPTS) {
            SimpleRecordScreen(
                title = "دریافتی‌های تونل",
                color = FinancePrimary,
                csvName = "fin_tunnel_receipts",
                fields = listOf("تاریخ", "مبلغ", "توضیح"),
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.FIN_TUNNEL_SUMMARY) {
            SimpleRecordScreen(
                title = "خلاصه مطالبات تونل",
                color = FinancePrimary,
                csvName = "fin_tunnel_summary",
                fields = listOf("توضیح"),
                readOnlyNote = "این صفحه فعلاً فهرست خام ثبت‌هاست؛ منطق محاسبه‌ی خودکار مطالبات بعداً اضافه می‌شود.",
                onBack = { navController.popBackStack() }
            )
        }

        // ---- مالی: پروژه‌ها ----
        composable(Routes.FIN_PROJECT_INVOICE) {
            SimpleRecordScreen(
                title = "صدور فاکتور",
                color = FinancePrimary,
                csvName = "fin_project_invoice",
                fields = listOf("نام کارفرما", "نام پروژه", "مبلغ", "تاریخ"),
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.FIN_PROJECT_RECEIPT) {
            SimpleRecordScreen(
                title = "ثبت دریافتی",
                color = FinancePrimary,
                csvName = "fin_project_receipt",
                fields = listOf("نام پروژه", "مبلغ", "تاریخ"),
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.FIN_PROJECT_RECEIVABLES) {
            SimpleRecordScreen(
                title = "مطالبات کلی",
                color = FinancePrimary,
                csvName = "fin_project_receivables",
                fields = listOf("توضیح"),
                readOnlyNote = "این صفحه فعلاً فهرست خام است؛ جدول ترکیبی تونل+آزاد بعداً اضافه می‌شود.",
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.FIN_PROJECT_STATUS) {
            SimpleRecordScreen(
                title = "وضعیت مالی",
                color = FinancePrimary,
                csvName = "fin_project_status",
                fields = listOf("توضیح"),
                readOnlyNote = "نمای کلی درآمد به تفکیک ماه/سال/کارفرما/پروژه بعداً اضافه می‌شود.",
                onBack = { navController.popBackStack() }
            )
        }

        // ---- ابزار ----
        composable(Routes.TOOL_DXF) {
            ViaClaudeScreen(
                title = "تبدیل به DXF",
                color = ToolPrimary,
                description = "آپلود فایل نقاط و گرفتن خروجی DXF فعلاً از طریق چت با کلود انجام می‌شود.",
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.TOOL_LINES) {
            ViaClaudeScreen(
                title = "ترسیم خطوط",
                color = ToolPrimary,
                description = "بازسازی خطوط پیوسته از روی ابر نقاط فعلاً از طریق چت با کلود انجام می‌شود.",
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.TOOL_GSI) {
            GsiConverterScreen(color = ToolPrimary, onBack = { navController.popBackStack() })
        }
        composable(Routes.TOOL_LOCATION) {
            LocationScreen(color = ToolPrimary, onBack = { navController.popBackStack() })
        }
        composable(Routes.TOOL_INTERPOLATE) {
            InterpolateScreen(color = ToolPrimary, onBack = { navController.popBackStack() })
        }
        composable(Routes.TOOL_AREA) {
            AreaScreen(color = ToolPrimary, onBack = { navController.popBackStack() })
        }
        composable(Routes.TOOL_VOLUME) {
            VolumeScreen(color = ToolPrimary, onBack = { navController.popBackStack() })
        }
    }
}
