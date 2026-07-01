package com.sethv.fintrack.feature.home

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sethv.fintrack.core.ui.component.PermissionCard
import com.sethv.fintrack.core.ui.component.TransactionItem
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToExpenseList: () -> Unit,
    onNavigateToReview: (Long) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val smsPermissions = arrayOf(
        Manifest.permission.RECEIVE_SMS,
        Manifest.permission.READ_SMS,
    )

    fun hasSmsPermission(): Boolean = smsPermissions.all { permission ->
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    fun hasNotificationPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    val smsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        val granted = results.values.all { it }
        viewModel.onPermissionResult(granted)
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        viewModel.onNotificationPermissionResult(granted)
    }

    LaunchedEffect(Unit) {
        val smsGranted = hasSmsPermission()
        val notificationGranted = hasNotificationPermission()
        viewModel.updatePermissions(smsGranted, notificationGranted)

        if (!smsGranted) {
            smsPermissionLauncher.launch(smsPermissions)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notificationGranted) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("FinTrack") },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onNavigateToExpenseList) {
                Icon(
                    imageVector = Icons.Default.List,
                    contentDescription = "View all expenses",
                )
            }
        },
    ) { paddingValues ->
        HomeContent(
            uiState = uiState,
            paddingValues = paddingValues,
            onRequestSmsPermission = { smsPermissionLauncher.launch(smsPermissions) },
            onRequestNotificationPermission = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            },
        )
    }
}

@Composable
private fun HomeContent(
    uiState: HomeUiState,
    paddingValues: PaddingValues,
    onRequestSmsPermission: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (!uiState.hasSmsPermission) {
            item {
                PermissionCard(
                    title = "SMS Permission Required",
                    description = "Allow SMS access to automatically detect bank transaction messages and track your expenses.",
                    onGrantClick = onRequestSmsPermission,
                )
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !uiState.hasNotificationPermission) {
            item {
                PermissionCard(
                    title = "Notification Permission Required",
                    description = "Allow notifications to review new transactions as they are detected.",
                    onGrantClick = onRequestNotificationPermission,
                )
            }
        }

        item {
            MonthlySummaryCard(monthlyTotal = uiState.monthlyTotal)
        }

        if (uiState.categoryBreakdown.isNotEmpty()) {
            item {
                Text(
                    text = "Spending by Category",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            item {
                CategoryBreakdownCard(breakdown = uiState.categoryBreakdown)
            }
        }

        item {
            Text(
                text = "Recent Transactions",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }

        if (uiState.recentTransactions.isEmpty()) {
            item {
                Text(
                    text = "No transactions yet. Expenses will appear here once detected from SMS.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            items(
                items = uiState.recentTransactions,
                key = { it.id },
            ) { transaction ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                ) {
                    TransactionItem(transaction = transaction)
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(72.dp))
        }
    }
}

@Composable
private fun MonthlySummaryCard(monthlyTotal: Double) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
        ) {
            Text(
                text = "This Month",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = formatMonthlyTotal(monthlyTotal),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                text = "Total spending",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

private fun formatMonthlyTotal(amount: Double): String {
    val formatted = if (amount % 1.0 == 0.0) {
        amount.toLong().toString()
    } else {
        String.format(Locale.getDefault(), "%.2f", amount)
    }
    return "₹$formatted"
}

@Composable
private fun CategoryBreakdownCard(
    breakdown: List<CategorySpending>,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            breakdown.forEach { spending ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = spending.category.displayName,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                        )
                        LinearProgressIndicator(
                            progress = { spending.percentage / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp, end = 16.dp),
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = formatMonthlyTotal(spending.amount),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "${String.format(Locale.getDefault(), "%.0f", spending.percentage)}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
