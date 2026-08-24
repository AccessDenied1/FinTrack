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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sethv.fintrack.core.model.ExpenseCategory
import com.sethv.fintrack.core.model.Transaction
import com.sethv.fintrack.core.model.TransactionType
import com.sethv.fintrack.core.ui.component.EmptyState
import com.sethv.fintrack.core.ui.component.TransactionItem
import com.sethv.fintrack.core.ui.component.categoryIcon
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
    viewModel: ExpenseListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedTransaction by remember { mutableStateOf<Transaction?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Expenses") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            SummaryCard(
                totalAmount = uiState.totalAmount,
                transactionCount = uiState.transactions.size,
                modifier = Modifier.padding(horizontal = FinTrackSpacing.Md, vertical = FinTrackSpacing.Sm),
            )

            SearchField(
                query = uiState.searchQuery,
                onQueryChange = viewModel::setSearchQuery,
                modifier = Modifier.padding(horizontal = FinTrackSpacing.Md, vertical = FinTrackSpacing.Sm),
            )

            CategoryFilterRow(
                selectedCategory = uiState.selectedCategory,
                onCategorySelected = viewModel::setFilter,
                modifier = Modifier.padding(horizontal = FinTrackSpacing.Md, vertical = FinTrackSpacing.Sm),
            )

            if (uiState.transactions.isEmpty()) {
                EmptyState(
                    icon = Icons.Outlined.ReceiptLong,
                    title = if (uiState.searchQuery.isNotBlank()) "No matches" else "No expenses yet",
                    subtitle = if (uiState.searchQuery.isNotBlank()) {
                        "Try a different merchant, note or category."
                    } else {
                        "Accepted transactions will appear here."
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                TransactionList(
                    transactions = uiState.transactions,
                    onTransactionClick = { selectedTransaction = it },
                )
            }
        }
    }

    selectedTransaction?.let { transaction ->
        TransactionDetailSheet(
            transaction = transaction,
            onDismiss = { selectedTransaction = null },
            onDelete = {
                viewModel.deleteTransaction(transaction.id)
                selectedTransaction = null
            },
            snackbarHostState = snackbarHostState,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TransactionList(
    transactions: List<Transaction>,
    onTransactionClick: (Transaction) -> Unit,
) {
    // Group into month sections so long imported histories stay scannable.
    val zone = ZoneId.systemDefault()
    val monthFormatter = DateTimeFormatter.ofPattern("MMMM yyyy")
    val grouped: Map<LocalDate, List<Transaction>> = transactions.groupBy { txn ->
        Instant.ofEpochMilli(txn.dateTime).atZone(zone).toLocalDate().withDayOfMonth(1)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            horizontal = FinTrackSpacing.Md,
            vertical = FinTrackSpacing.Sm,
        ),
        verticalArrangement = Arrangement.spacedBy(FinTrackSpacing.Sm),
    ) {
        grouped.forEach { (month, monthTransactions) ->
            item(key = "month-${month}") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = FinTrackSpacing.Sm, bottom = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = month.format(monthFormatter),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = Format.currency(
                            monthTransactions
                                .filter { it.type == TransactionType.DEBIT }
                                .sumOf { it.amount },
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(
                count = monthTransactions.size,
                key = { idx -> monthTransactions[idx].id },
            ) { idx ->
                val transaction = monthTransactions[idx]
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateItemPlacement(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                    onClick = { onTransactionClick(transaction) },
                ) {
                    TransactionItem(transaction = transaction)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TransactionDetailSheet(
    transaction: Transaction,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    val scope = rememberCoroutineScope()
    var confirmDelete by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = FinTrackSpacing.Xl)
                .padding(bottom = FinTrackSpacing.Xl),
            verticalArrangement = Arrangement.spacedBy(FinTrackSpacing.Md),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = categoryIcon(transaction.category),
                    contentDescription = transaction.category.displayName,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.height(FinTrackSpacing.Md))
                Column {
                    Text(
                        text = transaction.merchant,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = transaction.category.displayName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            val isCredit = transaction.type == TransactionType.CREDIT
            Text(
                text = if (isCredit) {
                    "+${Format.currency(transaction.amount)}"
                } else {
                    "-${Format.currency(transaction.amount)}"
                },
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Black,
                color = if (isCredit) {
                    LocalFinTrackColors.current.credit
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )

            DetailRow(label = "Date", value = formatDate(transaction.dateTime))
            if (transaction.bank.isNotBlank()) {
                DetailRow(label = "Bank", value = transaction.bank)
            }
            if (transaction.notes.isNotBlank()) {
                DetailRow(label = "Notes", value = transaction.notes)
            }
            if (transaction.smsBody.isNotBlank()) {
                DetailRow(label = "Source SMS", value = transaction.smsBody)
            }

            Button(
                onClick = {
                    if (!confirmDelete) {
                        confirmDelete = true
                    } else {
                        onDelete()
                        scope.launch {
                            snackbarHostState.showSnackbar("Transaction deleted")
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(imageVector = Icons.Filled.Delete, contentDescription = null)
                Spacer(modifier = Modifier.height(FinTrackSpacing.Sm))
                Text(if (confirmDelete) "Tap again to permanently delete" else "Delete")
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text("Search merchant, notes, bank…") },
        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Filled.Close, contentDescription = "Clear search")
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
    )
}

@Composable
private fun SummaryCard(totalAmount: Double, transactionCount: Int, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(modifier = Modifier.padding(FinTrackSpacing.Md)) {
            Text(
                text = "Total Spending",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                text = Format.currency(totalAmount),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                text = "$transactionCount transaction${if (transactionCount == 1) "" else "s"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryFilterRow(
    selectedCategory: ExpenseCategory?,
    onCategorySelected: (ExpenseCategory?) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(FinTrackSpacing.Sm),
        verticalArrangement = Arrangement.spacedBy(FinTrackSpacing.Sm),
    ) {
        FilterChip(
            selected = selectedCategory == null,
            onClick = { onCategorySelected(null) },
            label = { Text("All") },
        )
        ExpenseCategory.entries.forEach { category ->
            FilterChip(
                selected = selectedCategory == category,
                onClick = {
                    onCategorySelected(if (selectedCategory == category) null else category)
                },
                label = { Text(category.displayName) },
            )
        }
    }
}

private val detailDateFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm")

private fun formatDate(timestamp: Long): String =
    Instant.ofEpochMilli(timestamp)
        .atZone(ZoneId.systemDefault())
        .format(detailDateFormatter)
