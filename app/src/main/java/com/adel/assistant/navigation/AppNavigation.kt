package com.adel.assistant.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.adel.assistant.ui.HomeScreen
import com.adel.assistant.ui.dxf.DxfConverterScreen
import com.adel.assistant.ui.screens.AreaScreen
import com.adel.assistant.ui.screens.ChainageScreen
import com.adel.assistant.ui.screens.DailyReportScreen
import com.adel.assistant.ui.screens.GsiConverterScreen
import com.adel.assistant.ui.screens.InterpolateScreen
import com.adel.assistant.ui.screens.LocationScreen
import com.adel.assistant.ui.screens.ProjectEventsScreen
import com.adel.assistant.ui.screens.ProjectRegisterScreen
import com.adel.assistant.ui.screens.ReceivablesScreen
import com.adel.assistant.ui.screens.SimpleRecordScreen
import com.adel.assistant.ui.screens.TunnelFinanceSummaryScreen
import com.adel.assistant.ui.screens.TunnelPointsScreen
import com.adel.assistant.ui.screens.TunnelReceiptsScreen
import com.adel.assistant.ui.screens.TaskScreen
import com.adel.assistant.ui.screens.TunnelStatusScreen
import com.adel.assistant.ui.screens.TunnelWorklogScreen
import com.adel.assistant.ui.screens.InvoiceScreen
import com.adel.assistant.data.InvoiceLaunch
import com.adel.assistant.ui.screens.ViaClaudeScreen
import com.adel.assistant.ui.screens.FinanceStatusScreen
import com.adel.assistant.ui.screens.DatabaseBackupScreen
import com.adel.assistant.ui.screens.VolumeScreen
import com.adel.assistant.ui.screens.WorkCalendarScreen
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
        composable(Routes.SURVEY_TUNNEL_TASKS) {
            TaskScreen(
                title = "تسک‌های تونل",
                storeName = "tunnel_tasks",
                color = WorkPrimary,
                onBack = { navController.popBackStack() }
            )
        }

        // ---- نقشه‌برداری: پروژه‌ها ----
        composable(Routes.SURVEY_PROJECT_REGISTER) {
            ProjectRegisterScreen(color = WorkPrimary, onBack = { navController.popBackStack() })
        }
        composable(
            route = "${Routes.SURVEY_PROJECT_REGISTER}/{day}/{month}/{year}",
            arguments = listOf(
                navArgument("day") { type = NavType.StringType },
                navArgument("month") { type = NavType.StringType },
                navArgument("year") { type = NavType.StringType }
            )
        ) { entry ->
            ProjectRegisterScreen(
                color = WorkPrimary,
                onBack = { navController.popBackStack() },
                initialDay = entry.arguments?.getString("day"),
                initialMonth = entry.arguments?.getString("month"),
                initialYear = entry.arguments?.getString("year")
            )
        }
        composable(Routes.SURVEY_PROJECT_EVENTS) {
            ProjectEventsScreen(color = WorkPrimary, onBack = { navController.popBackStack() })
        }
        composable(Routes.SURVEY_PROJECT_CALENDAR) {
            WorkCalendarScreen(
                color = WorkPrimary,
                onBack = { navController.popBackStack() },
                onAddProject = { d, m, y ->
                    navController.navigate("${Routes.SURVEY_PROJECT_REGISTER}/$d/$m/$y")
                }
            )
        }

        composable(Routes.SURVEY_PROJECT_TASKS) {
            TaskScreen(
                title = "تسک‌های پروژه",
                storeName = "project_tasks",
                color = WorkPrimary,
                onBack = { navController.popBackStack() }
            )
        }

        // ---- مالی: تونل ----
        composable(Routes.FIN_TUNNEL_WORKLOG) {
            TunnelWorklogScreen(color = FinancePrimary, onBack = { navController.popBackStack() })
        }
        composable(Routes.FIN_TUNNEL_RECEIPTS) {
            TunnelReceiptsScreen(color = FinancePrimary, onBack = { navController.popBackStack() })
        }
        composable(Routes.FIN_TUNNEL_SUMMARY) {
            TunnelFinanceSummaryScreen(color = FinancePrimary, onBack = { navController.popBackStack() })
        }

        // ---- مالی: پروژه‌ها ----
        composable(Routes.FIN_PROJECT_INVOICE) {
            InvoiceScreen(
                color = FinancePrimary,
                onBack = { navController.popBackStack() },
                preselected = InvoiceLaunch.preselected.also { InvoiceLaunch.preselected = emptyList() }
            )
        }
        composable(Routes.FIN_PROJECT_RECEIPT) {
            SimpleRecordScreen(
                title = "ثبت دریافتی",
                color = FinancePrimary,
                csvName = "project_partial_payments",
                fields = listOf("نام پروژه", "مبلغ", "تاریخ"),
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.FIN_PROJECT_RECEIVABLES) {
            ReceivablesScreen(
                color = FinancePrimary,
                onBack = { navController.popBackStack() },
                onInvoice = { p ->
                    InvoiceLaunch.preselected = listOf(p)
                    navController.navigate(Routes.FIN_PROJECT_INVOICE)
                }
            )
        }
        composable(Routes.FIN_PROJECT_STATUS) {
            FinanceStatusScreen(color = FinancePrimary, onBack = { navController.popBackStack() })
        }

        // ---- ابزار ----
        composable(Routes.TOOL_DXF) {
            DxfConverterScreen(onBack = { navController.popBackStack() })
        }
        
        composable(Routes.TOOL_DXF_PREVIEW) {
            DxfPreviewScreen(color = ToolPrimary, onBack = { navController.popBackStack() })
        }
        composable(Routes.TOOL_TOTAL_STATION) {
            TotalStationDumpScreen(color = ToolPrimary, onBack = { navController.popBackStack() })
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
        composable(Routes.TOOL_BACKUP) {
            DatabaseBackupScreen(color = ToolPrimary, onBack = { navController.popBackStack() })
        }
    }
}
