package com.sethv.fintrack.feature.home

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sethv.fintrack.core.ui.component.AnimatedCurrency
import com.sethv.fintrack.core.ui.component.CategoryDonutChart
import com.sethv.fintrack.core.ui.component.DonutSlice
import com.sethv.fintrack.core.ui.component.EmptyState
import com.sethv.fintrack.core.ui.component.PermissionCard
import com.sethv.fintrack.core.ui.component.SectionHeader
import com.sethv.fintrack.core.ui.component.TransactionItem
import com.sethv.fintrack.core.ui.component.WeeklyBarsChart
import com.sethv.fintrack.core.ui.theme.FinTrackShape
import com.sethv.fintrack.core.ui.theme.FinTrackSpacing
import com.sethv.fintrack.core.ui.theme.LocalFinTrackColors
import com.sethv.fintrack.core.ui.util.Format
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToExpenseList: () -> Unit,
    onNavigateToReview: (Long) -> Unit,
    onNavigateToReviewTab: () -> Unit = {},
    onNavigateToCards: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
    scanSmsViewModel: ScanSmsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scanState by scanSmsViewModel.scanState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showScanSheet by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var confirmDeleteAll by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(scanSmsViewModel) {
        scanSmsViewModel.navEvents.collect { event ->
            if (event is ScanNavEvent.NavigateToReview) {
                onNavigateToReviewTab()
                scanSmsViewModel.onNavHandled()
            }
        }
    }

    val smsPermissions = arrayOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS)

    fun hasSmsPermission(): Boolean = smsPermissions.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
    fun hasNotificationPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }

    val smsPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        viewModel.onPermissionResult(results.values.all { it })
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.onNotificationPermissionResult(granted)
    }

    LaunchedEffect(Unit) {
        val smsGranted = hasSmsPermission()
        val notificationGranted = hasNotificationPermission()
        viewModel.updatePermissions(smsGranted, notificationGranted)
        if (!smsGranted) smsPermissionLauncher.launch(smsPermissions)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notificationGranted) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "FINTRACK",
                            style = MaterialTheme.typography.titleSmall,
                            letterSpacing = 1.6.sp,
                            fontWeight = FontWeight.Black,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.primary, FinTrackShape.Pill)
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        ) {
                            Text(
                                text = "LEDGER",
                                style = MaterialTheme.typography.labelSmall,
                                letterSpacing = 0.8.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                ),
                actions = {
                    if (uiState.hasSmsPermission) {
                        IconButton(onClick = { showScanSheet = true }) {
                            Icon(Icons.Outlined.History, contentDescription = "Import past SMS", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Delete all data") },
                                leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                                onClick = { showMenu = false; confirmDeleteAll = true },
                            )
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { paddingValues ->
        HomeContent(
            uiState = uiState, paddingValues = paddingValues,
            onRequestSmsPermission = { smsPermissionLauncher.launch(smsPermissions) },
            onRequestNotificationPermission = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            },
            onNavigateToExpenseList = onNavigateToExpenseList,
            onNavigateToCards = onNavigateToCards,
            onPreviousMonth = viewModel::onPreviousMonth,
            onNextMonth = viewModel::onNextMonth,
            onCurrentMonthSelected = viewModel::onCurrentMonthSelected,
        )
    }

    if (showScanSheet) {
        ModalBottomSheet(onDismissRequest = { showScanSheet = false }) {
            Column(modifier = Modifier.padding(horizontal = FinTrackSpacing.Md)) {
                ScanPastSmsCard(
                    scanState = scanState, onStartScan = scanSmsViewModel::startScan,
                    onResetScanState = scanSmsViewModel::resetScanState,
                    onNavigateToReviewTab = { showScanSheet = false; onNavigateToReviewTab() },
                    modifier = Modifier.padding(bottom = FinTrackSpacing.Xl),
                )
            }
        }
    }

    if (confirmDeleteAll) {
        AlertDialog(
            onDismissRequest = { confirmDeleteAll = false },
            title = { Text("Delete all data?", fontWeight = FontWeight.Bold) },
            text = { Text("Removes every transaction, pending item, credit card, statement and your starting balance. Cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = { confirmDeleteAll = false; viewModel.deleteAllData(); scope.launch { snackbarHostState.showSnackbar("All data deleted") } },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    shape = FinTrackShape.Pill,
                ) { Text("Delete everything") }
            },
            dismissButton = { TextButton(onClick = { confirmDeleteAll = false }) { Text("Cancel") } },
            shape = FinTrackShape.Medium,
        )
    }
}

@Composable
private fun HomeContent(
    uiState: HomeUiState, paddingValues: PaddingValues,
    onRequestSmsPermission: () -> Unit, onRequestNotificationPermission: () -> Unit,
    onNavigateToExpenseList: () -> Unit, onNavigateToCards: () -> Unit,
    onPreviousMonth: () -> Unit, onNextMonth: () -> Unit, onCurrentMonthSelected: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(paddingValues),
        contentPadding = PaddingValues(horizontal = FinTrackSpacing.Md, vertical = FinTrackSpacing.Md),
        verticalArrangement = Arrangement.spacedBy(FinTrackSpacing.Md),
    ) {
        if (!uiState.hasSmsPermission) {
            item { PermissionCard(title = "SMS Permission Required", description = "Allow SMS access to automatically detect bank transactions.", onGrantClick = onRequestSmsPermission) }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !uiState.hasNotificationPermission) {
            item { PermissionCard(title = "Notifications Required", description = "Allow notifications to review new transactions as they arrive.", onGrantClick = onRequestNotificationPermission) }
        }

        item {
            MonthlySummaryCard(
                uiState = uiState, onPreviousMonth = onPreviousMonth,
                onNextMonth = onNextMonth, onCurrentMonthSelected = onCurrentMonthSelected,
            )
        }

        if (uiState.dailySpendingTrend.any { it > 0.0 }) {
            item { WeeklyTrendCard(trend = uiState.dailySpendingTrend) }
        }

        val upcomingBill = uiState.upcomingCardBill
        if (upcomingBill != null) {
            val days = java.util.concurrent.TimeUnit.MILLISECONDS.toDays(upcomingBill.dueDate - System.currentTimeMillis()).toInt()
            if (days <= 7) {
                item(key = "bill-alert") { CardBillAlertCard(bill = upcomingBill, days = days, onClick = onNavigateToCards) }
            }
        }

        if (uiState.categoryBreakdown.isNotEmpty()) {
            item { SectionHeader(title = "Spending by Category") }
            item { CategoryBreakdownCard(breakdown = uiState.categoryBreakdown) }
        }

        item {
            SectionHeader(title = "Recent Transactions", trailing = {
                TextButton(onClick = onNavigateToExpenseList) { Text("View all", fontWeight = FontWeight.SemiBold) }
            })
        }

        if (uiState.recentTransactions.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.AutoMirrored.Outlined.ReceiptLong, title = "No transactions yet",
                    subtitle = "Expenses will appear here once detected from SMS.", modifier = Modifier.fillMaxWidth(),
                )
            }
        } else {
            itemsIndexed(items = uiState.recentTransactions, key = { _, txn -> txn.id }) { index, transaction ->
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth().staggeredEntrance(index),
                    shape = FinTrackShape.Medium,
                    colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) { TransactionItem(transaction = transaction) }
            }
        }
        item { Spacer(modifier = Modifier.height(FinTrackSpacing.Md)) }
    }
}

@Composable
private fun ScanPastSmsCard(
    scanState: ScanState, onStartScan: () -> Unit, onResetScanState: () -> Unit,
    onNavigateToReviewTab: () -> Unit, modifier: Modifier = Modifier,
) {
    OutlinedCard(modifier = modifier.fillMaxWidth(), shape = FinTrackShape.Medium) {
        Column(modifier = Modifier.padding(FinTrackSpacing.Md)) {
            when (scanState.status) {
                ScanStatus.IDLE -> {
                    Text(text = "Import Past Transactions", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(text = "Scan your SMS inbox to import historical bank transactions for review.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(14.dp))
                    Button(onClick = onStartScan, shape = FinTrackShape.Pill) { Text("Scan Past SMS") }
                }
                ScanStatus.SCANNING -> {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(FinTrackSpacing.Md)) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                        Text(text = "Scanning SMS...", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                ScanStatus.COMPLETED -> {
                    Text(
                        text = if (scanState.transactionsFound == 0) "No new transactions found"
                        else "Found ${scanState.transactionsFound} transaction${if (scanState.transactionsFound == 1) "" else "s"}",
                        style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(FinTrackSpacing.Sm)) {
                        if (scanState.transactionsFound > 0) Button(onClick = onNavigateToReviewTab, shape = FinTrackShape.Pill) { Text("Review All") }
                        OutlinedButton(onClick = onResetScanState, shape = FinTrackShape.Pill) { Text("Done") }
                    }
                }
                ScanStatus.ERROR -> {
                    Text(text = "Scan failed", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(text = "Could not read SMS messages. Check permissions and try again.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(onClick = { onResetScanState(); onStartScan() }, shape = FinTrackShape.Pill) { Text("Retry") }
                }
            }
        }
    }
}

@Composable
private fun CardBillAlertCard(bill: com.sethv.fintrack.core.model.CardBill, days: Int, onClick: () -> Unit) {
    val accent = when {
        days < 0 -> MaterialTheme.colorScheme.error
        days <= 3 -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }
    OutlinedCard(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = FinTrackShape.Medium,
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Box(modifier = Modifier.width(4.dp).height(72.dp).background(accent, FinTrackShape.Small))
            Row(
                modifier = Modifier.padding(FinTrackSpacing.Md).weight(1f),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(FinTrackSpacing.Md),
            ) {
                Icon(Icons.Rounded.CreditCard, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "BILL DUE ${if (days < 0) "OVERDUE" else "IN ${days}D"}", style = MaterialTheme.typography.labelSmall, letterSpacing = 0.6.sp, fontWeight = FontWeight.Bold, color = accent)
                    Text(text = Format.currency(bill.totalDue), style = MaterialTheme.typography.titleSmall.copy(fontFamily = FontFamily.Monospace), fontWeight = FontWeight.Bold)
                }
                Surface(color = accent, shape = FinTrackShape.Pill) {
                    Text(text = "Pay", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp))
                }
            }
        }
    }
}

@Composable
private fun WeeklyTrendCard(trend: List<Double>) {
    val dayLabels = remember(trend) {
        (6L downTo 0L).map { d ->
            java.time.LocalDate.now().minusDays(d).dayOfWeek
                .getDisplayName(java.time.format.TextStyle.NARROW, java.util.Locale.getDefault()).uppercase()
        }
    }
    val weekTotal = trend.sum()
    OutlinedCard(modifier = Modifier.fillMaxWidth(), shape = FinTrackShape.Medium) {
        Column(modifier = Modifier.padding(FinTrackSpacing.Md)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(text = "DAILY SPENDING", style = MaterialTheme.typography.labelSmall, letterSpacing = 0.7.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(text = Format.currency(weekTotal), style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(modifier = Modifier.height(12.dp))
            WeeklyBarsChart(values = trend, dayLabels = dayLabels, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun MonthlySummaryCard(
    uiState: HomeUiState, onPreviousMonth: () -> Unit, onNextMonth: () -> Unit, onCurrentMonthSelected: () -> Unit,
) {
    val delta = uiState.monthlyTotal - uiState.previousMonthTotal
    val comparisonSuffix = if (uiState.isCurrentMonth) " vs same period last month" else " vs last month"
    val deltaLabel = when {
        uiState.previousMonthTotal == 0.0 && uiState.monthlyTotal == 0.0 -> "—"
        uiState.previousMonthTotal == 0.0 -> "—"
        delta > 0 -> "+${Format.currency(delta)}$comparisonSuffix"
        delta < 0 -> "−${Format.currency(-delta)}$comparisonSuffix"
        else -> "Same as last month"
    }
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = FinTrackShape.Large,
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(FinTrackSpacing.Lg)) {
            MonthNavigator(selectedMonth = uiState.selectedMonth, isCurrentMonth = uiState.isCurrentMonth, onPreviousMonth = onPreviousMonth, onNextMonth = onNextMonth, onCurrentMonthSelected = onCurrentMonthSelected)
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = "SPENDING", style = MaterialTheme.typography.labelSmall, letterSpacing = 0.8.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            AnimatedCurrency(amount = uiState.monthlyTotal, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
            Spacer(modifier = Modifier.height(10.dp))
            val deltaColor = when {
                delta > 0 -> LocalFinTrackColors.current.debit
                delta < 0 -> LocalFinTrackColors.current.credit
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = FinTrackShape.Pill) {
                Text(
                    text = deltaLabel, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold,
                    color = deltaColor, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
            QuickStatsRow(uiState = uiState)
        }
    }
}

@Composable
private fun MonthNavigator(
    selectedMonth: java.time.YearMonth?, isCurrentMonth: Boolean,
    onPreviousMonth: () -> Unit, onNextMonth: () -> Unit, onCurrentMonthSelected: () -> Unit,
) {
    val displayed = selectedMonth ?: java.time.YearMonth.now()
    val formatter = remember { DateTimeFormatter.ofPattern("MMM yyyy") }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPreviousMonth, modifier = Modifier.size(28.dp)) {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = "Previous month", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.clickable(onClick = onCurrentMonthSelected)) {
            Text(text = displayed.format(formatter).uppercase(), style = MaterialTheme.typography.labelMedium, letterSpacing = 1.2.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
            if (isCurrentMonth) Box(modifier = Modifier.size(6.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
        }
        IconButton(onClick = onNextMonth, enabled = !isCurrentMonth, modifier = Modifier.size(28.dp)) {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "Next month", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (isCurrentMonth) 0.35f else 1f))
        }
    }
}

@Composable
private fun QuickStatsRow(uiState: HomeUiState) {
    Row(modifier = Modifier.fillMaxWidth()) {
        QuickStat(label = "Today", value = Format.currency(uiState.todaySpending), modifier = Modifier.weight(1f))
        Box(modifier = Modifier.width(0.5.dp).height(36.dp).background(MaterialTheme.colorScheme.outlineVariant).align(Alignment.CenterVertically))
        QuickStat(label = "Per day", value = Format.currency(uiState.avgPerDay), modifier = Modifier.weight(1f))
        Box(modifier = Modifier.width(0.5.dp).height(36.dp).background(MaterialTheme.colorScheme.outlineVariant).align(Alignment.CenterVertically))
        QuickStat(label = "Biggest", value = Format.currency(uiState.biggestExpense), modifier = Modifier.weight(1f))
        Box(modifier = Modifier.width(0.5.dp).height(36.dp).background(MaterialTheme.colorScheme.outlineVariant).align(Alignment.CenterVertically))
        if (uiState.isCurrentMonth) QuickStat(label = "Projected", value = Format.currency(uiState.monthEndProjection), modifier = Modifier.weight(1f))
        else QuickStat(label = "Txns", value = uiState.monthTxnCount.toString(), modifier = Modifier.weight(1f))
    }
}

@Composable
private fun QuickStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, style = MaterialTheme.typography.labelLarge.copy(fontFamily = FontFamily.Monospace), fontWeight = FontWeight.Bold, maxLines = 1)
        Text(text = label.uppercase(), style = MaterialTheme.typography.labelSmall, letterSpacing = 0.6.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Modifier.staggeredEntrance(index: Int): Modifier {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(index * 40L)
        progress.animateTo(1f, animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioMediumBouncy))
    }
    return graphicsLayer { alpha = progress.value; translationY = (1f - progress.value) * 24f }
}

@Composable
private fun CategoryBreakdownCard(breakdown: List<CategorySpending>) {
    OutlinedCard(modifier = Modifier.fillMaxWidth(), shape = FinTrackShape.Medium) {
        Column(modifier = Modifier.padding(FinTrackSpacing.Md)) {
            val slices = breakdown.mapIndexed { idx, s -> DonutSlice(label = s.category.displayName, value = s.percentage, colorIndex = idx) }
            CategoryDonutChart(slices = slices, centerLabel = "TOTAL", centerSubLabel = "${breakdown.size} categories")
        }
    }
}
