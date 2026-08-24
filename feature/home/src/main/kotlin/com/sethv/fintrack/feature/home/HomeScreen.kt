package com.sethv.fintrack.feature.home

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sethv.fintrack.core.ui.component.AnimatedCurrency
import com.sethv.fintrack.core.ui.component.CategoryDonutChart
import com.sethv.fintrack.core.ui.component.DonutSlice
import com.sethv.fintrack.core.ui.component.EmptyState
import com.sethv.fintrack.core.ui.component.PermissionCard
import com.sethv.fintrack.core.ui.component.SectionHeader
import com.sethv.fintrack.core.ui.component.SparkLine
import com.sethv.fintrack.core.ui.component.TransactionItem
import com.sethv.fintrack.core.ui.theme.FinTrackSpacing
import com.sethv.fintrack.core.ui.theme.LocalFinTrackColors
import com.sethv.fintrack.core.ui.util.Format
import kotlinx.coroutines.delay
import java.time.format.DateTimeFormatter

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
            onPreviousMonth = viewModel::onPreviousMonth,
            onNextMonth = viewModel::onNextMonth,
            onCurrentMonthSelected = viewModel::onCurrentMonthSelected,
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
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onCurrentMonthSelected: () -> Unit,
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

        item { MonthlySummaryCard(
            uiState = uiState,
            onPreviousMonth = onPreviousMonth,
            onNextMonth = onNextMonth,
            onCurrentMonthSelected = onCurrentMonthSelected,
        ) }

        if (uiState.dailySpendingTrend.any { it > 0.0 }) {
            item {
                WeeklyTrendCard(trend = uiState.dailySpendingTrend)
            }
        }

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
            itemsIndexed(
                items = uiState.recentTransactions,
                key = { _, txn -> txn.id },
            ) { index, transaction ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .staggeredEntrance(index),
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
private fun WeeklyTrendCard(trend: List<Double>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(FinTrackSpacing.Md)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Daily spending",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = "Last 7 days • ${Format.currency(trend.sum())}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.height(FinTrackSpacing.Sm))
            SparkLine(
                values = trend,
                lineColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
            )
        }
    }
}

@Composable
private fun MonthlySummaryCard(
    uiState: HomeUiState,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onCurrentMonthSelected: () -> Unit,
) {
    val delta = uiState.monthlyTotal - uiState.previousMonthTotal

    // Comparison wording: current month is like-for-like (same day-span);
    // past months compare against the full previous calendar month.
    val comparisonSuffix = if (uiState.isCurrentMonth) " vs same period last month" else " vs last month"
    val deltaLabel = when {
        uiState.previousMonthTotal == 0.0 && uiState.monthlyTotal == 0.0 -> "—"
        uiState.previousMonthTotal == 0.0 -> "—"
        delta > 0 -> "+${Format.currency(delta)}$comparisonSuffix"
        delta < 0 -> "-${Format.currency(-delta)}$comparisonSuffix"
        else -> "Same as last month"
    }

    val gradient = Brush.verticalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.secondaryContainer,
        ),
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind { drawRect(gradient) },
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            MonthNavigator(
                selectedMonth = uiState.selectedMonth,
                isCurrentMonth = uiState.isCurrentMonth,
                onPreviousMonth = onPreviousMonth,
                onNextMonth = onNextMonth,
                onCurrentMonthSelected = onCurrentMonthSelected,
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = if (uiState.isCurrentMonth) "Spending this month" else "Spending",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
            )
            AnimatedCurrency(
                amount = uiState.monthlyTotal,
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

            Spacer(modifier = Modifier.height(16.dp))

            QuickStatsRow(uiState = uiState)
        }
    }
}

@Composable
private fun MonthNavigator(
    selectedMonth: java.time.YearMonth?,
    isCurrentMonth: Boolean,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onCurrentMonthSelected: () -> Unit,
) {
    val displayed = selectedMonth ?: java.time.YearMonth.now()
    val formatter = remember { DateTimeFormatter.ofPattern("MMMM yyyy") }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPreviousMonth) {
            Icon(
                imageVector = Icons.Rounded.KeyboardArrowLeft,
                contentDescription = "Previous month",
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(FinTrackSpacing.Sm),
            modifier = Modifier.clickable(onClick = onCurrentMonthSelected),
        ) {
            Text(
                text = displayed.format(formatter).uppercase(),
                style = MaterialTheme.typography.labelLarge,
                letterSpacing = 1.5.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            if (isCurrentMonth) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(color = MaterialTheme.colorScheme.primary, shape = CircleShape),
                )
            }
        }
        IconButton(onClick = onNextMonth, enabled = !isCurrentMonth) {
            Icon(
                imageVector = Icons.Rounded.KeyboardArrowRight,
                contentDescription = "Next month",
                tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = if (isCurrentMonth) 0.3f else 1f),
            )
        }
    }
}

@Composable
private fun QuickStatsRow(uiState: HomeUiState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        QuickStat(label = "Per day", value = Format.currency(uiState.avgPerDay))
        StatDivider()
        QuickStat(label = "Biggest", value = Format.currency(uiState.biggestExpense))
        StatDivider()
        QuickStat(label = "Txns", value = uiState.monthTxnCount.toString())
    }
}

@Composable
private fun QuickStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
        )
    }
}

@Composable
private fun StatDivider() {
    Box(
        modifier = Modifier
            .height(28.dp)
            .width(1.dp)
            .background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f)),
    )
}

/**
 * Fade + rise entrance, delayed by [index] * 45ms — gives the recent list a
 * cascading reveal. Runs once per composition of each item.
 */
@Composable
private fun Modifier.staggeredEntrance(index: Int): Modifier {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(index * 45L)
        progress.animateTo(1f, animationSpec = tween(durationMillis = 320))
    }
    return graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * 40f
    }
}

@Composable
private fun CategoryBreakdownCard(breakdown: List<CategorySpending>) {
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
