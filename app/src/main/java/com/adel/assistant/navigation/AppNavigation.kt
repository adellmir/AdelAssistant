package com.adel.assistant.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.adel.assistant.ui.FinanceScreen
import com.adel.assistant.ui.HomeScreen
import com.adel.assistant.ui.PlaceholderScreen
import com.adel.assistant.ui.WorkScreen
import com.adel.assistant.ui.theme.FinancePrimary
import com.adel.assistant.ui.theme.WorkPrimary

@Composable
fun AppNavigation() {
    val navController: NavHostController = rememberNavController()

    NavHost(navController = navController, startDestination = Routes.HOME) {

        composable(Routes.HOME) {
            HomeScreen(
                onOpenWork = { navController.navigate(Routes.WORK) },
                onOpenFinance = { navController.navigate(Routes.FINANCE) }
            )
        }

        // ---- کاری ----
        composable(Routes.WORK) {
            WorkScreen(
                onBack = { navController.popBackStack() },
                onNavigate = { route -> navController.navigate(route) }
            )
        }
        composable(Routes.WORK_TUNNEL) {
            PlaceholderScreen(title = "تونل (کاری)", color = WorkPrimary, onBack = { navController.popBackStack() })
        }
        composable(Routes.WORK_FREE) {
            PlaceholderScreen(title = "آزاد (کاری)", color = WorkPrimary, onBack = { navController.popBackStack() })
        }
        composable(Routes.WORK_TASKS) {
            PlaceholderScreen(title = "تسک‌ها", color = WorkPrimary, onBack = { navController.popBackStack() })
        }

        // ---- مالی ----
        composable(Routes.FINANCE) {
            FinanceScreen(
                onBack = { navController.popBackStack() },
                onNavigate = { route -> navController.navigate(route) }
            )
        }
        composable(Routes.FINANCE_TUNNEL) {
            PlaceholderScreen(title = "تونل (مالی)", color = FinancePrimary, onBack = { navController.popBackStack() })
        }
        composable(Routes.FINANCE_FREE) {
            PlaceholderScreen(title = "آزاد (مالی)", color = FinancePrimary, onBack = { navController.popBackStack() })
        }
        composable(Routes.FINANCE_RECEIVABLES) {
            PlaceholderScreen(title = "مطالبات کلی", color = FinancePrimary, onBack = { navController.popBackStack() })
        }
    }
}
