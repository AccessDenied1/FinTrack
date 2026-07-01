package com.sethv.fintrack.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.sethv.fintrack.navigation.FinTrackNavHost
import com.sethv.fintrack.navigation.Route

@Composable
fun FinTrackApp(
    initialPendingId: Long? = null,
    onNavControllerReady: (NavHostController) -> Unit = {},
) {
    val navController = rememberNavController()

    LaunchedEffect(navController) {
        onNavControllerReady(navController)
    }

    LaunchedEffect(initialPendingId) {
        initialPendingId?.let { pendingId ->
            navController.navigate(Route.ExpenseReview.createRoute(pendingId)) {
                launchSingleTop = true
            }
        }
    }

    FinTrackNavHost(navController = navController)
}
