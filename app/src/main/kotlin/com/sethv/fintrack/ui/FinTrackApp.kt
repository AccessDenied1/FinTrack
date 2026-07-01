package com.sethv.fintrack.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Wallet
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.sethv.fintrack.navigation.FinTrackNavHost
import com.sethv.fintrack.navigation.Route

data class BottomNavItem(
    val label: String,
    val icon: ImageVector,
    val route: String,
)

val bottomNavItems = listOf(
    BottomNavItem("Expenses", Icons.Rounded.Wallet, Route.Home.route),
    BottomNavItem("Net Worth", Icons.Rounded.AccountBalance, Route.NetWorth.route),
)

@Composable
fun FinTrackApp(
    initialPendingId: Long? = null,
    onNavControllerReady: (NavHostController) -> Unit = {},
) {
    val navController = rememberNavController()
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Sync tab selection with current route
    LaunchedEffect(currentRoute) {
        when (currentRoute) {
            Route.Home.route -> selectedTab = 0
            Route.NetWorth.route -> selectedTab = 1
        }
    }

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

    Scaffold(
        bottomBar = {
            NavigationBar {
                bottomNavItems.forEachIndexed { index, item ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = {
                            if (selectedTab != index) {
                                selectedTab = index
                                navController.navigate(item.route) {
                                    popUpTo(Route.Home.route) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label) },
                    )
                }
            }
        },
    ) { paddingValues ->
        FinTrackNavHost(
            navController = navController,
            modifier = Modifier.padding(paddingValues),
        )
    }
}
