package com.sethv.fintrack.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.Wallet
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.sethv.fintrack.MainViewModel
import com.sethv.fintrack.navigation.FinTrackNavHost
import com.sethv.fintrack.navigation.Route

data class BottomNavItem(
    val label: String,
    val icon: ImageVector,
    val route: String,
)

private val bottomNavItems = listOf(
    BottomNavItem("Home", Icons.Rounded.Wallet, Route.Home.route),
    BottomNavItem("Review", Icons.Rounded.Inbox, Route.PendingReview.route),
    BottomNavItem("Net Worth", Icons.Rounded.AccountBalance, Route.NetWorth.route),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinTrackApp(
    initialPendingId: Long? = null,
    onNavControllerReady: (NavHostController) -> Unit = {},
    mainViewModel: MainViewModel = hiltViewModel(),
) {
    val navController = rememberNavController()
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val pendingCount by mainViewModel.pendingCount.collectAsStateWithLifecycle()

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Sync tab selection with current top-level route.
    LaunchedEffect(currentRoute) {
        val index = bottomNavItems.indexOfFirst { it.route == currentRoute }
        if (index >= 0) selectedTab = index
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
                        icon = {
                            if (item.route == Route.PendingReview.route && pendingCount > 0) {
                                BadgedBox(badge = { Badge { Text(pendingCount.toString()) } }) {
                                    Icon(item.icon, contentDescription = item.label)
                                }
                            } else {
                                Icon(item.icon, contentDescription = item.label)
                            }
                        },
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