package com.sethv.fintrack.feature.review

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sethv.fintrack.core.model.PendingTransaction
import com.sethv.fintrack.core.model.TransactionType
import com.sethv.fintrack.core.ui.component.TransactionItem
import com.sethv.fintrack.core.ui.theme.FinTrackSpacing
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PendingReviewScreen(
    onOpenItem: (Long) -> Unit,
    viewModel: PendingReviewViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is PendingReviewEvent.Accepted -> snackbarHostState.showSnackbar(
                    if (event.count == 1) "Transaction accepted" else "${event.count} transactions accepted",
                )
                is PendingReviewEvent.Rejected -> snackbarHostState.showSnackbar(
                    if (event.count == 1) "Transaction skipped" else "${event.count} transactions skipped",
                )
                is PendingReviewEvent.Error -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Review") }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (uiState.items.isNotEmpty()) {
                BottomActionBar(
                    itemCount = uiState.items.size,
                    onAcceptAll = viewModel::acceptAll,
                    onRejectAll = viewModel::rejectAll,
                )
            }
        },
    ) { paddingValues ->
        if (uiState.isEmpty) {
            EmptyState(modifier = Modifier.fillMaxSize().padding(paddingValues))
        } else {
            PendingList(
                items = uiState.items,
                contentPadding = paddingValues,
                onAccept = viewModel::accept,
                onReject = viewModel::reject,
                onOpen = onOpenItem,
            )
        }
    }
}

@Composable
private fun PendingList(
    items: List<PendingTransaction>,
    contentPadding: PaddingValues,
    onAccept: (PendingTransaction) -> Unit,
    onReject: (PendingTransaction) -> Unit,
    onOpen: (Long) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = FinTrackSpacing.Md,
            end = FinTrackSpacing.Md,
            top = contentPadding.calculateTopPadding() + FinTrackSpacing.Sm,
            bottom = contentPadding.calculateBottomPadding() + FinTrackSpacing.ListBottomFab,
        ),
        verticalArrangement = Arrangement.spacedBy(FinTrackSpacing.Sm),
    ) {
        item {
            Text(
                text = "${items.size} pending transaction${if (items.size == 1) "" else "s"}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(vertical = FinTrackSpacing.Xs),
            )
        }
        items(items = items, key = { it.id }) { item ->
            PendingCard(
                item = item,
                onAccept = { onAccept(item) },
                onReject = { onReject(item) },
                onOpen = { onOpen(item.id) },
            )
        }
    }
}

@Composable
private fun PendingCard(
    item: PendingTransaction,
    onAccept: () -> Unit,
    onReject: () -> Unit,
    onOpen: () -> Unit,
) {
    val accent = if (item.type == TransactionType.CREDIT) {
        MaterialTheme.colorScheme.tertiaryContainer
    } else {
        MaterialTheme.colorScheme.errorContainer
    }
    val onAccent = if (item.type == TransactionType.CREDIT) {
        MaterialTheme.colorScheme.onTertiaryContainer
    } else {
        MaterialTheme.colorScheme.onErrorContainer
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        onClick = onOpen,
    ) {
        Column(modifier = Modifier.padding(FinTrackSpacing.SmPlus)) {
            TransactionItem(transaction = item.toTransactionLike())
            HorizontalDivider(modifier = Modifier.padding(vertical = FinTrackSpacing.Sm))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(FinTrackSpacing.Sm),
            ) {
                OutlinedButton(
                    onClick = onReject,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Skip")
                }
                Button(
                    onClick = onAccept,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Accept")
                }
            }
            Spacer(modifier = Modifier.height(FinTrackSpacing.Xs))
            // Tiny accent strip so the user sees the type at a glance.
            Surface(color = accent, contentColor = onAccent, shape = MaterialTheme.shapes.extraSmall) {
                Text(
                    text = "${item.type.name} • ${formatTimestamp(item.dateTime)} • ${item.bank.ifBlank { "Unknown" }}",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = FinTrackSpacing.Sm, vertical = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun BottomActionBar(
    itemCount: Int,
    onAcceptAll: () -> Unit,
    onRejectAll: () -> Unit,
) {
    Surface(tonalElevation = 4.dp, color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = FinTrackSpacing.Md, vertical = FinTrackSpacing.SmPlus),
            horizontalArrangement = Arrangement.spacedBy(FinTrackSpacing.SmPlus),
        ) {
            OutlinedButton(
                onClick = onRejectAll,
                modifier = Modifier.weight(1f),
            ) {
                Text("Skip All")
            }
            Button(
                onClick = onAcceptAll,
                modifier = Modifier.weight(1f),
            ) {
                Text("Accept All ($itemCount)")
            }
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(FinTrackSpacing.SmPlus),
            modifier = Modifier.padding(FinTrackSpacing.Xl),
        ) {
            Icon(
                imageVector = Icons.Outlined.Inbox,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "No pending transactions",
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "New bank SMS will appear here for you to review.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private val timestampFormat = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault())

private fun formatTimestamp(epochMillis: Long): String =
    timestampFormat.format(Date(epochMillis))

/**
 * Adapts a PendingTransaction to the shared TransactionItem composable which expects
 * a Transaction. Pure copy — no behavior change.
 */
private fun PendingTransaction.toTransactionLike(): com.sethv.fintrack.core.model.Transaction =
    com.sethv.fintrack.core.model.Transaction(
        id = id,
        amount = amount,
        merchant = merchant,
        category = category,
        type = type,
        dateTime = dateTime,
        bank = bank,
        notes = notes,
        smsBody = smsBody,
        createdAt = createdAt,
    )