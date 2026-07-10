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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sethv.fintrack.core.ui.component.CategoryDonutChart
import com.sethv.fintrack.core.ui.component.DonutSlice
import com.sethv.fintrack.core.ui.component.EmptyState
import com.sethv.fintrack.core.ui.component.PermissionCard
import com.sethv.fintrack.core.ui.component.SectionHeader
import com.sethv.fintrack.core.ui.component.TransactionItem
import com.sethv.fintrack.core.ui.theme.FinTrackSpacing
import com.sethv.fintrack.core.ui.theme.LocalFinTrackColors
import com.sethv.fintrack.core.ui.util.Format

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToExpenseList: () -> Unit,
    onNavigateToReview: (Long) -> Unit,
    onNavigateToReviewTab: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
    scanSmsViewModel: ScanSmsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scanState by scanSmsViewModel.scanState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(scanSmsViewModel) {
        scanSmsViewModel.navEvents.collect { event ->
            if (event is ScanNavEvent.NavigateToReview) {
                onNavigateToReviewTab()
                scanSmsViewModel.onNavHandled()
            }
        }
    }

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
        topBar = { TopAppBar(title = { Text("FinTrack") }) },
    ) { paddingValues ->
        HomeContent(
            uiState = uiState,
            scanState = scanState,
            paddingValues = paddingValues,
            onRequestSmsPermission = { smsPermissionLauncher.launch(smsPermissions) },
            onRequestNotificationPermission = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            },
            onStartScan = scanSmsViewModel::startScan,
            onResetScanState = scanSmsViewModel::resetScanState,
            onNavigateToExpenseList = onNavigateToExpenseList,
            onNavigateToReviewTab = onNavigateToReviewTab,
        )
    }
}

@Composable
private fun HomeContent(
    uiState: HomeUiState,
    scanState: ScanState,
    paddingValues: PaddingValues,
    onRequestSmsPermission: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onStartScan: () -> Unit,
    onResetScanState: () -> Unit,
    onNavigateToExpenseList: () -> Unit,
    onNavigateToReviewTab: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues),
        contentPadding = PaddingValues(horizontal = FinTrackSpacing.Md, vertical = FinTrackSpacing.Md),
        verticalArrangement = Arrangement.spacedBy(FinTrackSpacing.Md),
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

        if (uiState.hasSmsPermission) {
            item {
                ScanPastSmsCard(
                    scanState = scanState,
                    onStartScan = onStartScan,
                    onResetScanState = onResetScanState,
                    onNavigateToReviewTab = onNavigateToReviewTab,
                )
            }
        }

        item { MonthlySummaryCard(uiState = uiState) }

        if (uiState.categoryBreakdown.isNotEmpty()) {
            item {
                SectionHeader(title = "Spending by Category")
            }
            item {
                CategoryBreakdownCard(breakdown = uiState.categoryBreakdown)
            }
        }

        item {
            SectionHeader(
                title = "Recent Transactions",
                trailing = {
                    androidx.compose.material3.TextButton(onClick = onNavigateToExpenseList) {
                        Text("View all")
                    }
                },
            )
        }

        if (uiState.recentTransactions.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Outlined.ReceiptLong,
                    title = "No transactions yet",
                    subtitle = "Expenses will appear here once detected from SMS.",
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        } else {
            items(items = uiState.recentTransactions, key = { it.id }) { transaction ->
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

        item { Spacer(modifier = Modifier.height(FinTrackSpacing.Md)) }
    }
}

@Composable
private fun ScanPastSmsCard(
    scanState: ScanState,
    onStartScan: () -> Unit,
    onResetScanState: () -> Unit,
    onNavigateToReviewTab: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(FinTrackSpacing.Md)) {
            when (scanState.status) {
                ScanStatus.IDLE -> {
                    Text(
                        text = "Import Past Transactions",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    Spacer(modifier = Modifier.height(FinTrackSpacing.Sm))
                    Text(
                        text = "Scan your SMS inbox to import historical bank transactions for review.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    Spacer(modifier = Modifier.height(FinTrackSpacing.Md))
                    Button(onClick = onStartScan) { Text("Scan Past SMS") }
                }
                ScanStatus.SCANNING -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(FinTrackSpacing.Md),
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                        Text(
                            text = "Scanning SMS...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
                ScanStatus.COMPLETED -> {
                    Text(
                        text = "Found ${scanState.transactionsFound} transaction${if (scanState.transactionsFound == 1) "" else "s"}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    Spacer(modifier = Modifier.height(FinTrackSpacing.Md))
                    Row(horizontalArrangement = Arrangement.spacedBy(FinTrackSpacing.Sm)) {
                        if (scanState.transactionsFound > 0) {
                            Button(onClick = onNavigateToReviewTab) { Text("Review All") }
                        }
                        OutlinedButton(onClick = onResetScanState) { Text("Done") }
                    }
                }
                ScanStatus.ERROR -> {
                    Text(
                        text = "Scan failed",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    Spacer(modifier = Modifier.height(FinTrackSpacing.Sm))
                    Text(
                        text = "Could not read SMS messages. Check permissions and try again.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    Spacer(modifier = Modifier.height(FinTrackSpacing.Md))
                    Button(onClick = {
                        onResetScanState()
                        onStartScan()
                    }) { Text("Retry") }
                }
            }
        }
    }
}

@Composable
private fun MonthlySummaryCard(uiState: HomeUiState) {
    val delta = uiState.monthlyTotal - uiState.previousMonthTotal
    val deltaLabel = when {
        uiState.previousMonthTotal == 0.0 && uiState.monthlyTotal == 0.0 -> "—"
        uiState.previousMonthTotal == 0.0 -> "—"
        delta > 0 -> "+${Format.currency(delta)} vs last month"
        delta < 0 -> "-${Format.currency(-delta)} vs last month"
        else -> "Same as last month"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(
                text = "Total Spending This Month",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = Format.currency(uiState.monthlyTotal),
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(modifier = Modifier.height(16.dp))
            
            val deltaColor = when {
                delta > 0 -> LocalFinTrackColors.current.debit
                delta < 0 -> LocalFinTrackColors.current.credit
                else -> MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
            }
            
            androidx.compose.material3.Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = deltaLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = deltaColor,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun CategoryBreakdownCard(breakdown: List<CategorySpending>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(FinTrackSpacing.Md)) {
            val slices = breakdown.mapIndexed { idx, s ->
                DonutSlice(
                    label = s.category.displayName,
                    value = s.percentage,
                    colorIndex = idx,
                )
            }
            CategoryDonutChart(
                slices = slices,
                centerLabel = "Total",
                centerSubLabel = "${breakdown.size} categories",
            )
        }
    }
}