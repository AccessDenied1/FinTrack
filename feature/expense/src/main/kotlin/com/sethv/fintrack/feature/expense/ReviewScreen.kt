package com.sethv.fintrack.feature.expense

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sethv.fintrack.core.model.ExpenseCategory
import com.sethv.fintrack.core.model.TransactionType
import com.sethv.fintrack.core.ui.theme.FinTrackShape
import com.sethv.fintrack.feature.expense.component.AmountDisplay
import com.sethv.fintrack.feature.expense.component.CategoryPicker
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(
    pendingId: Long,
    onTransactionAccepted: () -> Unit = {},
    onTransactionRejected: () -> Unit = {},
    viewModel: ReviewViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.accepted.collect { onTransactionAccepted() }
    }
    LaunchedEffect(Unit) {
        viewModel.rejected.collect { onTransactionRejected() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Review Transaction") },
                navigationIcon = {
                    // Reached via the bottom tab or a notification deep-link —
                    // the hardware/system back gesture is not always obvious.
                    IconButton(onClick = onTransactionRejected) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
            )
        },
    ) { paddingValues ->
        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            uiState.error != null && uiState.pendingTransaction == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = uiState.error ?: "Unknown error",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            else -> {
                ReviewContent(
                    uiState = uiState,
                    onAmountChange = viewModel::updateAmount,
                    onMerchantChange = viewModel::updateMerchant,
                    onCategoryChange = viewModel::updateCategory,
                    onTypeChange = viewModel::updateType,
                    onNotesChange = viewModel::updateNotes,
                    onAccept = viewModel::acceptTransaction,
                    onReject = viewModel::rejectTransaction,
                    modifier = Modifier.padding(paddingValues),
                )
            }
        }
    }
}

@Composable
private fun ReviewContent(
    uiState: ReviewUiState,
    onAmountChange: (Double) -> Unit,
    onMerchantChange: (String) -> Unit,
    onCategoryChange: (ExpenseCategory) -> Unit,
    onTypeChange: (TransactionType) -> Unit,
    onNotesChange: (String) -> Unit,
    onAccept: () -> Unit,
    onReject: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pending = uiState.pendingTransaction
    val dateFormatter = remember { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()) }
    var smsExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        OutlinedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = FinTrackShape.Medium,
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                AmountDisplay(
                    amount = uiState.amount,
                    onAmountChange = onAmountChange,
                    editMode = true,
                )

                // Direction override — SMS heuristics can't always tell a
                // refund from a charge, and the user must be able to fix it.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = uiState.type == TransactionType.DEBIT,
                        onClick = { onTypeChange(TransactionType.DEBIT) },
                        label = { Text("Spent") },
                        modifier = Modifier.weight(1f),
                    )
                    FilterChip(
                        selected = uiState.type == TransactionType.CREDIT,
                        onClick = { onTypeChange(TransactionType.CREDIT) },
                        label = { Text("Received") },
                        modifier = Modifier.weight(1f),
                    )
                }

                OutlinedTextField(
                    value = uiState.merchant,
                    onValueChange = onMerchantChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Merchant") },
                    singleLine = true,
                )

                if (pending != null) {
                    Text(
                        text = dateFormatter.format(Date(pending.dateTime)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (pending.bank.isNotBlank()) {
                            Text(
                                text = pending.bank,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        val isCredit = pending.type == TransactionType.CREDIT
                        Surface(
                            color = if (isCredit) {
                                MaterialTheme.colorScheme.tertiaryContainer
                            } else {
                                MaterialTheme.colorScheme.errorContainer
                            },
                            contentColor = if (isCredit) {
                                MaterialTheme.colorScheme.onTertiaryContainer
                            } else {
                                MaterialTheme.colorScheme.onErrorContainer
                            },
                            shape = MaterialTheme.shapes.extraSmall,
                        ) {
                            Text(
                                text = if (isCredit) "Received" else "Spent",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            )
                        }
                    }
                }
            }
        }

        OutlinedCard(modifier = Modifier.fillMaxWidth(), shape = FinTrackShape.Medium) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "CATEGORY",
                    style = MaterialTheme.typography.labelSmall,
                    letterSpacing = 0.7.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                CategoryPicker(
                    selectedCategory = uiState.category,
                    onCategorySelected = onCategoryChange,
                )
            }
        }

        OutlinedCard(modifier = Modifier.fillMaxWidth(), shape = FinTrackShape.Medium) {
            OutlinedTextField(
                value = uiState.notes,
                onValueChange = onNotesChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                label = { Text("Notes") },
                minLines = 2,
                shape = FinTrackShape.Small,
            )
        }

        if (pending?.smsBody?.isNotBlank() == true) {
            OutlinedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { smsExpanded = !smsExpanded },
                shape = FinTrackShape.Medium,
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Original SMS",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Icon(
                            imageVector = if (smsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (smsExpanded) "Collapse" else "Expand",
                        )
                    }
                    AnimatedVisibility(
                        visible = smsExpanded,
                        enter = expandVertically(),
                        exit = shrinkVertically(),
                    ) {
                        Text(
                            text = pending.smsBody,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }
        }

        if (uiState.error != null) {
            Text(
                text = uiState.error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = onAccept,
            modifier = Modifier.fillMaxWidth(),
            enabled = !uiState.isSaving && uiState.merchant.isNotBlank() && uiState.amount > 0,
            shape = FinTrackShape.Pill,
        ) {
            Text("Accept", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        }

        OutlinedButton(
            onClick = onReject,
            modifier = Modifier.fillMaxWidth(),
            enabled = !uiState.isSaving,
            shape = FinTrackShape.Pill,
        ) {
            Text("Reject")
        }
    }
}
