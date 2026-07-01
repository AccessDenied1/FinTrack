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
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sethv.fintrack.core.model.ExpenseCategory
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
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
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

                    if (pending.bank.isNotBlank()) {
                        Text(
                            text = pending.bank,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "Category",
                    style = MaterialTheme.typography.titleSmall,
                )
                CategoryPicker(
                    selectedCategory = uiState.category,
                    onCategorySelected = onCategoryChange,
                )
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = uiState.notes,
                onValueChange = onNotesChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                label = { Text("Notes") },
                minLines = 2,
            )
        }

        if (pending?.smsBody?.isNotBlank() == true) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { smsExpanded = !smsExpanded },
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
        ) {
            Text("Accept")
        }

        OutlinedButton(
            onClick = onReject,
            modifier = Modifier.fillMaxWidth(),
            enabled = !uiState.isSaving,
        ) {
            Text("Reject")
        }
    }
}
