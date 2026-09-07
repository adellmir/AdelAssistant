package com.adel.assistant.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.adel.assistant.ui.HomeScreen
import com.adel.assistant.ui.screens.AreaScreen
import com.adel.assistant.ui.screens.ChainageScreen
import com.adel.assistant.ui.screens.DailyReportScreen
import com.adel.assistant.ui.screens.GsiConverterScreen
import com.adel.assistant.ui.screens.InterpolateScreen
import com.adel.assistant.ui.screens.LocationScreen
import com.adel.assistant.ui.screens.ProjectEventsScreen
import com.adel.assistant.ui.screens.ProjectRegisterScreen
<<<<<<< HEAD
import com.adel.assistant.ui.screens.ReceivablesScreen
import com.adel.assistant.ui.screens.SimpleRecordScreen
=======
import com.adel.assistant.ui.screens.SimpleRecordScreen
import com.adel.assistant.ui.screens.WorkCalendarScreen
>>>>>>> a863a8cb3f3f86fc23006826a02f77074a10ee04
import com.adel.assistant.ui.screens.TunnelPointsScreen
import com.adel.assistant.ui.screens.TunnelStatusScreen
import com.adel.assistant.ui.screens.ViaClaudeScreen
import com.adel.assistant.ui.screens.VolumeScreen
<<<<<<< HEAD
import com.adel.assistant.ui.screens.WorkCalendarScreen
=======
>>>>>>> a863a8cb3f3f86fc23006826a02f77074a10ee04
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
            DailyReportScreen(color = WorkPrimary, onBack = { navController.popBackStack() })
        }
        composable(Routes.SURVEY_TUNNEL_CHAINAGE) {
            ChainageScreen(color = WorkPrimary, onBack = { navController.popBackStack() })
        }
        composable(Routes.SURVEY_TUNNEL_EVENTS) {
            TunnelPointsScreen(color = WorkPrimary, onBack = { navController.popBackStack() })
        }
        composable(Routes.SURVEY_TUNNEL_STATUS) {
            TunnelStatusScreen(color = WorkPrimary, onBack = { navController.popBackStack() })
        }

        // ---- نقشه‌برداری: پروژه‌ها ----
<<<<<<< HEAD
        // «ثبت پروژه» می‌تواند با تاریخ پیش‌فرض دلخواه هم صدا زده شود (مثلاً از تقویم کاری)
        composable(
            route = "${Routes.SURVEY_PROJECT_REGISTER}?day={day}&month={month}&year={year}",
            arguments = listOf(
                navArgument("day") { type = NavType.StringType; defaultValue = "" },
                navArgument("month") { type = NavType.StringType; defaultValue = "" },
                navArgument("year") { type = NavType.StringType; defaultValue = "" }
            )
        ) { backStackEntry ->
            ProjectRegisterScreen(
                color = WorkPrimary,
                onBack = { navController.popBackStack() },
                initialDay = backStackEntry.arguments?.getString("day"),
                initialMonth = backStackEntry.arguments?.getString("month"),
                initialYear = backStackEntry.arguments?.getString("year")
            )
=======
        composable(Routes.SURVEY_PROJECT_REGISTER) {
            ProjectRegisterScreen(color = WorkPrimary, onBack = { navController.popBackStack() })
        }
        composable(Routes.SURVEY_PROJECT_EVENTS) {
            ProjectEventsScreen(color = WorkPrimary, onBack = { navController.popBackStack() })
        }
        composable(Routes.SURVEY_PROJECT_CALENDAR) {
            WorkCalendarScreen(color = WorkPrimary, onBack = { navController.popBackStack() })
        }

        // ---- مالی: تونل ----
        composable(Routes.FIN_TUNNEL_WORKLOG) {
            SimpleRecordScreen(
                title = "کارکرد ماهانه (تونل)", color = FinancePrimary, csvName = "tunnel_monthly_work",
                fields = listOf("ماه", "سال", "کارکرد", "مبلغ واحد", "کسورات"),
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.FIN_TUNNEL_RECEIPTS) {
            SimpleRecordScreen(
                title = "دریافتی‌های تونل", color = FinancePrimary, csvName = "tunnel_receipts",
                fields = listOf("تاریخ", "مبلغ", "توضیحات"),
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.FIN_TUNNEL_SUMMARY) {
            SimpleRecordScreen(
                title = "خلاصه مطالبات تونل", color = FinancePrimary, csvName = "tunnel_receipts",
                fields = listOf("توضیح"),
                readOnlyNote = "این اطلاعات اکنون در «دریافتی‌های تونل» نمایش داده می‌شود.",
                onBack = { navController.popBackStack() }
            )
        }

        // ---- مالی: پروژه‌ها ----
        composable(Routes.FIN_PROJECT_INVOICE) {
            ViaClaudeScreen(title = "صدور فاکتور", color = FinancePrimary, description = "این صفحه در حال تکمیل است.", onBack = { navController.popBackStack() })
        }
        composable(Routes.FIN_PROJECT_RECEIPT) {
            SimpleRecordScreen(
                title = "ثبت دریافتی", color = FinancePrimary, csvName = "project_partial_payments",
                fields = listOf("نام پروژه", "مبلغ", "تاریخ"),
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.FIN_PROJECT_RECEIVABLES) {
            ViaClaudeScreen(title = "مطالبات کلی", color = FinancePrimary, description = "این صفحه در حال تکمیل است.", onBack = { navController.popBackStack() })
        }
        composable(Routes.FIN_PROJECT_STATUS) {
            ViaClaudeScreen(title = "وضعیت مالی", color = FinancePrimary, description = "این صفحه در حال تکمیل است.", onBack = { navController.popBackStack() })
        }

        // ---- ابزار ----
        composable(Routes.TOOL_DXF) {
            ViaClaudeScreen(title = "تبدیل به DXF", color = ToolPrimary, description = "آپلود فایل نقاط و گرفتن خروجی DXF فعلاً از طریق چت با کلود انجام می‌شود.", onBack = { navController.popBackStack() })
        }
        composable(Routes.TOOL_LINES) {
            ViaClaudeScreen(title = "ترسیم خطوط", color = ToolPrimary, description = "بازسازی خطوط پیوسته از روی ابر نقاط فعلاً از طریق چت با کلود انجام می‌شود.", onBack = { navController.popBackStack() })
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
}        }

        // ---- نقشه‌برداری / پروژه‌ها ----
        composable(Routes.SURVEY_PROJECT_REGISTER) {
            PlaceholderScreen(title = "ثبت پروژه", color = WorkPrimary, onBack = { navController.popBackStack() })
>>>>>>> a863a8cb3f3f86fc23006826a02f77074a10ee04
        }
        composable(Routes.SURVEY_PROJECT_EVENTS) {
            ProjectEventsScreen(color = WorkPrimary, onBack = { navController.popBackStack() })
        }
        composable(Routes.SURVEY_PROJECT_CALENDAR) {
            WorkCalendarScreen(
                color = WorkPrimary,
                onBack = { navController.popBackStack() },
                onAddProject = { d, m, y ->
                    navController.navigate("${Routes.SURVEY_PROJECT_REGISTER}?day=$d&month=$m&year=$y")
                }
            )
        }

        // ---- مالی: تونل ----
        composable(Routes.FIN_TUNNEL_WORKLOG) {
            SimpleRecordScreen(
                title = "کارکرد ماهانه (تونل)", color = FinancePrimary, csvName = "tunnel_monthly_work",
                fields = listOf("ماه", "سال", "کارکرد", "مبلغ واحد", "کسورات"),
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.FIN_TUNNEL_RECEIPTS) {
            SimpleRecordScreen(
                title = "دریافتی‌های تونل", color = FinancePrimary, csvName = "tunnel_receipts",
                fields = listOf("تاریخ", "مبلغ", "توضیحات"),
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.FIN_TUNNEL_SUMMARY) {
            SimpleRecordScreen(
                title = "خلاصه مطالبات تونل", color = FinancePrimary, csvName = "tunnel_receipts",
                fields = listOf("توضیح"),
                readOnlyNote = "این اطلاعات اکنون در «دریافتی‌های تونل» نمایش داده می‌شود.",
                onBack = { navController.popBackStack() }
            )
        }

        // ---- مالی: پروژه‌ها ----
        composable(Routes.FIN_PROJECT_INVOICE) {
            ViaClaudeScreen(title = "صدور فاکتور", color = FinancePrimary, description = "این صفحه در حال تکمیل است.", onBack = { navController.popBackStack() })
        }
        composable(Routes.FIN_PROJECT_RECEIPT) {
            SimpleRecordScreen(
                title = "ثبت دریافتی", color = FinancePrimary, csvName = "project_partial_payments",
                fields = listOf("نام پروژه", "مبلغ", "تاریخ"),
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.FIN_PROJECT_RECEIVABLES) {
            ReceivablesScreen(color = FinancePrimary, onBack = { navController.popBackStack() })
        }
        composable(Routes.FIN_PROJECT_STATUS) {
            ViaClaudeScreen(title = "وضعیت مالی", color = FinancePrimary, description = "این صفحه در حال تکمیل است.", onBack = { navController.popBackStack() })
        }

        // ---- ابزار ----
        composable(Routes.TOOL_DXF) {
            ViaClaudeScreen(title = "تبدیل به DXF", color = ToolPrimary, description = "آپلود فایل نقاط و گرفتن خروجی DXF فعلاً از طریق چت با کلود انجام می‌شود.", onBack = { navController.popBackStack() })
        }
        composable(Routes.TOOL_LINES) {
            ViaClaudeScreen(title = "ترسیم خطوط", color = ToolPrimary, description = "بازسازی خطوط پیوسته از روی ابر نقاط فعلاً از طریق چت با کلود انجام می‌شود.", onBack = { navController.popBackStack() })
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
