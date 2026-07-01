package com.sethv.fintrack.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.sethv.fintrack.feature.expense.ExpenseListScreen
import com.sethv.fintrack.feature.expense.ReviewScreen
import com.sethv.fintrack.feature.home.HomeScreen
import com.sethv.fintrack.feature.networth.NetWorthScreen

sealed class Route(val route: String) {
    data object Home : Route("home")

    data object ExpenseReview : Route("expense/review/{pendingId}") {
        const val ARG_PENDING_ID = "pendingId"
        fun createRoute(pendingId: Long): String = "expense/review/$pendingId"
    }

    data object ExpenseList : Route("expense/list")

    data object NetWorth : Route("networth")
}

@Composable
fun FinTrackNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = Route.Home.route,
        modifier = modifier,
    ) {
        composable(Route.Home.route) {
            HomeScreen(
                onNavigateToExpenseList = {
                    navController.navigate(Route.ExpenseList.route)
                },
                onNavigateToReview = { pendingId ->
                    navController.navigate(Route.ExpenseReview.createRoute(pendingId)) {
                        launchSingleTop = true
                    }
                },
            )
        }
        composable(
            route = Route.ExpenseReview.route,
            arguments = listOf(
                navArgument(Route.ExpenseReview.ARG_PENDING_ID) {
                    type = NavType.LongType
                },
            ),
        ) { backStackEntry ->
            val pendingId = backStackEntry.arguments?.getLong(Route.ExpenseReview.ARG_PENDING_ID)
                ?: return@composable
            ReviewScreen(
                pendingId = pendingId,
                onTransactionAccepted = { navController.popBackStack() },
                onTransactionRejected = { navController.popBackStack() },
            )
        }
        composable(Route.ExpenseList.route) {
            ExpenseListScreen(
                onNavigateBack = { navController.popBackStack() },
            )
        }
        composable(Route.NetWorth.route) {
            NetWorthScreen()
        }
    }
}
