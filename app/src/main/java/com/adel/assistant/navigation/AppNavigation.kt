package com.adel.assistant.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.adel.assistant.ui.HomeScreen
import com.adel.assistant.ui.PlaceholderScreen
import com.adel.assistant.ui.dxf.DxfConverterScreen
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

        // ---- نقشه‌برداری / تونل ----
        composable(Routes.SURVEY_TUNNEL_REPORT) {
            PlaceholderScreen(title = "گزارش روزانه تونل", color = WorkPrimary, onBack = { navController.popBackStack() })
        }
        composable(Routes.SURVEY_TUNNEL_CHAINAGE) {
            PlaceholderScreen(title = "کیلومتراژ / چینیج", color = WorkPrimary, onBack = { navController.popBackStack() })
        }
        composable(Routes.SURVEY_TUNNEL_EVENTS) {
            PlaceholderScreen(title = "ثبت وقایع تونل", color = WorkPrimary, onBack = { navController.popBackStack() })
        }
        composable(Routes.SURVEY_TUNNEL_STATUS) {
            PlaceholderScreen(title = "وضعیت تونل", color = WorkPrimary, onBack = { navController.popBackStack() })
        }

        // ---- نقشه‌برداری / پروژه‌ها ----
        composable(Routes.SURVEY_PROJECT_REGISTER) {
            PlaceholderScreen(title = "ثبت پروژه", color = WorkPrimary, onBack = { navController.popBackStack() })
        }
        composable(Routes.SURVEY_PROJECT_EVENTS) {
            PlaceholderScreen(title = "ثبت وقایع پروژه", color = WorkPrimary, onBack = { navController.popBackStack() })
        }
        composable(Routes.SURVEY_PROJECT_CALENDAR) {
            PlaceholderScreen(title = "تقویم کاری", color = WorkPrimary, onBack = { navController.popBackStack() })
        }

        // ---- مالی / تونل ----
        composable(Routes.FIN_TUNNEL_WORKLOG) {
            PlaceholderScreen(title = "کارکرد ماهانه تونل", color = FinancePrimary, onBack = { navController.popBackStack() })
        }
        composable(Routes.FIN_TUNNEL_RECEIPTS) {
            PlaceholderScreen(title = "دریافتی‌های تونل", color = FinancePrimary, onBack = { navController.popBackStack() })
        }
        composable(Routes.FIN_TUNNEL_SUMMARY) {
            PlaceholderScreen(title = "خلاصه مطالبات تونل", color = FinancePrimary, onBack = { navController.popBackStack() })
        }

        // ---- مالی / پروژه‌ها ----
        composable(Routes.FIN_PROJECT_INVOICE) {
            PlaceholderScreen(title = "صدور فاکتور", color = FinancePrimary, onBack = { navController.popBackStack() })
        }
        composable(Routes.FIN_PROJECT_RECEIPT) {
            PlaceholderScreen(title = "ثبت دریافتی پروژه", color = FinancePrimary, onBack = { navController.popBackStack() })
        }
        composable(Routes.FIN_PROJECT_RECEIVABLES) {
            PlaceholderScreen(title = "مطالبات کلی", color = FinancePrimary, onBack = { navController.popBackStack() })
        }
        composable(Routes.FIN_PROJECT_STATUS) {
            PlaceholderScreen(title = "وضعیت مالی پروژه", color = FinancePrimary, onBack = { navController.popBackStack() })
        }

        // ---- ابزار ----
        composable(Routes.TOOL_DXF) {
            DxfConverterScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.TOOL_LINES) {
            PlaceholderScreen(title = "ترسیم خطوط", color = ToolPrimary, onBack = { navController.popBackStack() })
        }
        composable(Routes.TOOL_GSI) {
            PlaceholderScreen(title = "مبدل GSI", color = ToolPrimary, onBack = { navController.popBackStack() })
        }
        composable(Routes.TOOL_LOCATION) {
            PlaceholderScreen(title = "مکان", color = ToolPrimary, onBack = { navController.popBackStack() })
        }
        composable(Routes.TOOL_INTERPOLATE) {
            PlaceholderScreen(title = "درون‌یابی", color = ToolPrimary, onBack = { navController.popBackStack() })
        }
        composable(Routes.TOOL_AREA) {
            PlaceholderScreen(title = "مساحت و محیط", color = ToolPrimary, onBack = { navController.popBackStack() })
        }
        composable(Routes.TOOL_VOLUME) {
            PlaceholderScreen(title = "محاسبه احجام", color = ToolPrimary, onBack = { navController.popBackStack() })
        }
    }
}
