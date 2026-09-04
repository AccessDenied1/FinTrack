package com.sethv.fintrack.feature.expense

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sethv.fintrack.core.model.ExpenseCategory
import com.sethv.fintrack.core.model.Transaction
import com.sethv.fintrack.core.model.TransactionType
import com.sethv.fintrack.core.ui.component.EmptyState
import com.sethv.fintrack.core.ui.component.TransactionItem
import com.sethv.fintrack.core.ui.component.categoryIcon
import com.sethv.fintrack.core.ui.theme.FinTrackShape
import com.sethv.fintrack.core.ui.theme.FinTrackSpacing
import com.sethv.fintrack.core.ui.theme.LocalFinTrackColors
import com.sethv.fintrack.core.ui.util.Format
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ExpenseListScreen(
    onNavigateToReview: (Long) -> Unit = {},
    onNavigateBack: () -> Unit = {},
    onAddTransaction: () -> Unit = {},
    viewModel: ExpenseListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedTransaction by remember { mutableStateOf<Transaction?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("EXPENSES", style = MaterialTheme.typography.titleSmall, letterSpacing = 1.4.sp, fontWeight = FontWeight.Black) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddTransaction, shape = FinTrackShape.Pill,
                containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary,
            ) { Icon(Icons.Filled.Add, contentDescription = "Add transaction") }
        },
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            SummaryCard(totalAmount = uiState.totalAmount, transactionCount = uiState.transactions.size, modifier = Modifier.padding(horizontal = FinTrackSpacing.Md, vertical = FinTrackSpacing.Sm))
            SearchField(query = uiState.searchQuery, onQueryChange = viewModel::setSearchQuery, modifier = Modifier.padding(horizontal = FinTrackSpacing.Md, vertical = FinTrackSpacing.Sm))
            CategoryFilterRow(selectedCategory = uiState.selectedCategory, onCategorySelected = viewModel::setFilter, modifier = Modifier.padding(horizontal = FinTrackSpacing.Md, vertical = FinTrackSpacing.Sm))
            if (uiState.transactions.isEmpty()) {
                EmptyState(
                    icon = Icons.AutoMirrored.Outlined.ReceiptLong,
                    title = if (uiState.searchQuery.isNotBlank()) "No matches" else "No expenses yet",
                    subtitle = if (uiState.searchQuery.isNotBlank()) "Try a different merchant, note or category." else "Accepted transactions will appear here.",
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                TransactionList(transactions = uiState.transactions, onTransactionClick = { selectedTransaction = it })
            }
        }
    }

    selectedTransaction?.let { transaction ->
        TransactionDetailSheet(
            transaction = transaction, onDismiss = { selectedTransaction = null },
            onDelete = { viewModel.deleteTransaction(transaction.id); selectedTransaction = null }, snackbarHostState = snackbarHostState,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TransactionList(transactions: List<Transaction>, onTransactionClick: (Transaction) -> Unit) {
    val zone = ZoneId.systemDefault()
    val monthFormatter = DateTimeFormatter.ofPattern("MMM yyyy")
    val grouped: Map<LocalDate, List<Transaction>> = transactions.groupBy { txn ->
        Instant.ofEpochMilli(txn.dateTime).atZone(zone).toLocalDate().withDayOfMonth(1)
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = FinTrackSpacing.Md, vertical = FinTrackSpacing.Sm),
        verticalArrangement = Arrangement.spacedBy(FinTrackSpacing.Sm),
    ) {
        grouped.forEach { (month, monthTransactions) ->
            item(key = "month-${month}") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = FinTrackSpacing.Sm, bottom = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text = month.format(monthFormatter).uppercase(), style = MaterialTheme.typography.labelSmall, letterSpacing = 0.7.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = Format.currency(monthTransactions.filter { it.type == TransactionType.DEBIT }.sumOf { it.amount }),
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(count = monthTransactions.size, key = { idx -> monthTransactions[idx].id }) { idx ->
                val transaction = monthTransactions[idx]
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth().animateItemPlacement(),
                    shape = FinTrackShape.Medium,
                    onClick = { onTransactionClick(transaction) },
                ) { TransactionItem(transaction = transaction) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TransactionDetailSheet(transaction: Transaction, onDismiss: () -> Unit, onDelete: () -> Unit, snackbarHostState: SnackbarHostState) {
    val scope = rememberCoroutineScope()
    var confirmDelete by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = FinTrackSpacing.Xl).padding(bottom = FinTrackSpacing.Xl), verticalArrangement = Arrangement.spacedBy(FinTrackSpacing.Md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = categoryIcon(transaction.category), contentDescription = transaction.category.displayName, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.padding(start = 12.dp))
                Column {
                    Text(text = transaction.merchant, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(text = transaction.category.displayName.uppercase(), style = MaterialTheme.typography.labelSmall, letterSpacing = 0.6.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            val isCredit = transaction.type == TransactionType.CREDIT
            Text(
                text = if (isCredit) "+${Format.currency(transaction.amount)}" else "−${Format.currency(transaction.amount)}",
                style = MaterialTheme.typography.headlineSmall.copy(fontFamily = FontFamily.Monospace), fontWeight = FontWeight.Black,
                color = if (isCredit) LocalFinTrackColors.current.credit else MaterialTheme.colorScheme.onSurface,
            )
            DetailRow(label = "Date", value = formatDate(transaction.dateTime))
            if (transaction.bank.isNotBlank()) DetailRow(label = "Bank", value = transaction.bank)
            if (transaction.notes.isNotBlank()) DetailRow(label = "Notes", value = transaction.notes)
            if (transaction.smsBody.isNotBlank()) DetailRow(label = "Source SMS", value = transaction.smsBody)
            Button(
                onClick = {
                    if (!confirmDelete) confirmDelete = true else { onDelete(); scope.launch { snackbarHostState.showSnackbar("Transaction deleted") } }
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer),
                shape = FinTrackShape.Pill, modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.padding(start = 8.dp))
                Text(if (confirmDelete) "Tap again to delete" else "Delete", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column {
        Text(text = label.uppercase(), style = MaterialTheme.typography.labelSmall, letterSpacing = 0.6.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(2.dp))
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = query, onValueChange = onQueryChange, modifier = modifier.fillMaxWidth(),
        placeholder = { Text("Search merchant, notes, bank…", style = MaterialTheme.typography.bodySmall) },
        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
        trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { onQueryChange("") }) { Icon(Icons.Filled.Close, contentDescription = "Clear", modifier = Modifier.size(16.dp)) } },
        singleLine = true, shape = FinTrackShape.Medium,
    )
}

@Composable
private fun SummaryCard(totalAmount: Double, transactionCount: Int, modifier: Modifier = Modifier) {
    OutlinedCard(modifier = modifier.fillMaxWidth(), shape = FinTrackShape.Medium) {
        Column(modifier = Modifier.padding(FinTrackSpacing.Md)) {
            Text(text = "TOTAL SPENDING", style = MaterialTheme.typography.labelSmall, letterSpacing = 0.7.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = Format.currency(totalAmount), style = MaterialTheme.typography.headlineSmall.copy(fontFamily = FontFamily.Monospace), fontWeight = FontWeight.Black)
            Text(text = "$transactionCount transaction${if (transactionCount == 1) "" else "s"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryFilterRow(selectedCategory: ExpenseCategory?, onCategorySelected: (ExpenseCategory?) -> Unit, modifier: Modifier = Modifier) {
    FlowRow(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FilterChip(
            selected = selectedCategory == null, onClick = { onCategorySelected(null) }, label = { Text("All", style = MaterialTheme.typography.labelSmall) },
            shape = FinTrackShape.Pill, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primaryContainer),
        )
        ExpenseCategory.entries.forEach { category ->
            FilterChip(
                selected = selectedCategory == category,
                onClick = { onCategorySelected(if (selectedCategory == category) null else category) },
                label = { Text(category.displayName, style = MaterialTheme.typography.labelSmall) },
                shape = FinTrackShape.Pill,
            )
        }
    }
}

private val detailDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm")
private fun formatDate(timestamp: Long): String = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).format(detailDateFormatter)
