package com.sethv.fintrack.feature.networth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.DecimalFormat

private val amountFormat = DecimalFormat("#,##0.00")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetWorthScreen(
    viewModel: NetWorthViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    if (uiState.showSetBalanceDialog) {
        SetBalanceDialog(
            currentBalance = uiState.netWorth.initialBalance,
            onDismiss = viewModel::dismissSetBalanceDialog,
            onConfirm = viewModel::setInitialBalance,
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Net Worth") })
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (!uiState.hasSetInitialBalance) {
                SetInitialBalancePrompt(onClick = viewModel::showSetBalanceDialog)
            }

            CurrentBalanceCard(balance = uiState.netWorth.currentBalance)

            InitialBalanceCard(
                balance = uiState.netWorth.initialBalance,
                onEditClick = viewModel::showSetBalanceDialog,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SummaryCard(
                    title = "Income",
                    amount = uiState.netWorth.totalCredits,
                    isPositive = true,
                    modifier = Modifier.weight(1f),
                )
                SummaryCard(
                    title = "Expenses",
                    amount = uiState.netWorth.totalDebits,
                    isPositive = false,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun SetInitialBalancePrompt(onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "Set Your Starting Balance",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Enter your current bank balance to start tracking your net worth accurately.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(onClick = onClick) {
                Text("Set Initial Balance")
            }
        }
    }
}

@Composable
private fun CurrentBalanceCard(balance: Double) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Current Balance",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = formatAmount(balance),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun InitialBalanceCard(balance: Double, onEditClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = "Initial Balance",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = formatAmount(balance),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            IconButton(onClick = onEditClick) {
                Icon(Icons.Rounded.Edit, contentDescription = "Edit initial balance")
            }
        }
    }
}

@Composable
private fun SummaryCard(
    title: String,
    amount: Double,
    isPositive: Boolean,
    modifier: Modifier = Modifier,
) {
    val containerColor = if (isPositive) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.errorContainer
    }
    val contentColor = if (isPositive) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onErrorContainer
    }

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = contentColor,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = formatAmount(amount),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = contentColor,
            )
        }
    }
}

@Composable
private fun SetBalanceDialog(
    currentBalance: Double,
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit,
) {
    var text by remember {
        mutableStateOf(if (currentBalance == 0.0) "" else currentBalance.toString())
    }
    val amountPattern = remember { Regex("^\\d*\\.?\\d*$") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set Initial Balance") },
        text = {
            Column {
                Text(
                    text = "Enter your current total bank balance",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { value ->
                        if (value.isEmpty() || value.matches(amountPattern)) {
                            text = value
                        }
                    },
                    prefix = { Text("₹") },
                    label = { Text("Balance") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(text.toDoubleOrNull() ?: 0.0) },
                enabled = text.isNotBlank() && (text.toDoubleOrNull() ?: -1.0) >= 0.0,
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

private fun formatAmount(amount: Double): String {
    val formatted = amountFormat.format(kotlin.math.abs(amount))
    val prefix = if (amount < 0) "-₹" else "₹"
    return "$prefix$formatted"
}
