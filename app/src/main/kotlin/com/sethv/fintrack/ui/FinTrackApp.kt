package com.sethv.fintrack.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.Wallet
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.sethv.fintrack.MainViewModel
import com.sethv.fintrack.core.ui.theme.FinTrackShape
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
    BottomNavItem("Cards", Icons.Rounded.CreditCard, Route.Cards.route),
    BottomNavItem("Net Worth", Icons.Rounded.AccountBalance, Route.NetWorth.route),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinTrackApp(
    initialPendingId: Long? = null,
    initialOpenCards: Boolean = false,
    onNavControllerReady: (NavHostController) -> Unit = {},
    mainViewModel: MainViewModel = hiltViewModel(),
) {
    val navController = rememberNavController()
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val pendingCount by mainViewModel.pendingCount.collectAsStateWithLifecycle()

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    LaunchedEffect(currentRoute) {
        val index = bottomNavItems.indexOfFirst { it.route == currentRoute }
        if (index >= 0) selectedTab = index
    }

    LaunchedEffect(navController) { onNavControllerReady(navController) }

    LaunchedEffect(initialPendingId) {
        initialPendingId?.let { id ->
            navController.navigate(Route.ExpenseReview.createRoute(id)) { launchSingleTop = true }
        }
    }

    LaunchedEffect(initialOpenCards) {
        if (initialOpenCards) {
            navController.navigate(Route.Cards.route) {
                popUpTo(Route.Home.route) { saveState = true }
                launchSingleTop = true
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            val hairline = MaterialTheme.colorScheme.outlineVariant
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                tonalElevation = 0.dp,
                modifier = Modifier
                    .drawBehind {
                        drawLine(
                            color = hairline,
                            start = Offset(0f, 0.5f),
                            end = Offset(size.width, 0.5f),
                            strokeWidth = 0.5.dp.toPx(),
                        )
                    },
            ) {
                bottomNavItems.forEachIndexed { index, item ->
                    val selected = selectedTab == index
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            if (selectedTab != index) {
                                selectedTab = index
                                navController.navigate(item.route) {
                                    popUpTo(Route.Home.route) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        icon = {
                            if (item.route == Route.PendingReview.route && pendingCount > 0) {
                                BadgedBox(badge = {
                                    Badge(containerColor = MaterialTheme.colorScheme.error) {
                                        Text(pendingCount.toString(), fontWeight = FontWeight.Bold)
                                    }
                                }) { Icon(item.icon, contentDescription = item.label) }
                            } else {
                                Icon(item.icon, contentDescription = item.label)
                            }
                        },
                        label = {
                            Text(
                                item.label,
                                style = MaterialTheme.typography.labelSmall,
                                letterSpacing = 0.4.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.onSurface,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                }
            }
        },
    ) { paddingValues ->
        FinTrackNavHost(navController = navController, modifier = Modifier.padding(paddingValues))
    }
}
