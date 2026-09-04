package com.sethv.fintrack.feature.networth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sethv.fintrack.core.ui.component.AnimatedCurrency
import com.sethv.fintrack.core.ui.theme.FinTrackShape
import com.sethv.fintrack.core.ui.theme.FinTrackSpacing
import com.sethv.fintrack.core.ui.util.Format

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetWorthScreen(viewModel: NetWorthViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    if (uiState.showSetBalanceDialog) {
        SetBalanceDialog(currentBalance = uiState.netWorth.initialBalance, onDismiss = viewModel::dismissSetBalanceDialog, onConfirm = viewModel::setInitialBalance)
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("NET WORTH", style = MaterialTheme.typography.titleSmall, letterSpacing = 1.4.sp, fontWeight = FontWeight.Black) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier.fillMaxSize().padding(paddingValues).verticalScroll(rememberScrollState()).padding(FinTrackSpacing.Md),
            verticalArrangement = Arrangement.spacedBy(FinTrackSpacing.Md),
        ) {
            if (!uiState.hasSetInitialBalance) {
                SetInitialBalancePrompt(onClick = viewModel::showSetBalanceDialog)
            }
            CurrentBalanceCard(balance = uiState.netWorth.currentBalance)
            InitialBalanceCard(balance = uiState.netWorth.initialBalance, onEditClick = viewModel::showSetBalanceDialog)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(FinTrackSpacing.Sm)) {
                SummaryCard(title = "Income", amount = uiState.netWorth.totalCredits, isPositive = true, modifier = Modifier.weight(1f))
                SummaryCard(title = "Expenses", amount = uiState.netWorth.totalDebits, isPositive = false, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SetInitialBalancePrompt(onClick: () -> Unit) {
    OutlinedCard(modifier = Modifier.fillMaxWidth(), shape = FinTrackShape.Medium) {
        Column(modifier = Modifier.padding(FinTrackSpacing.Md)) {
            Text(text = "SET STARTING BALANCE", style = MaterialTheme.typography.labelSmall, letterSpacing = 0.8.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(6.dp))
            Text(text = "Enter your current bank balance to start tracking net worth accurately.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(12.dp))
            Button(onClick = onClick, shape = FinTrackShape.Pill) { Text("Set Initial Balance") }
        }
    }
}

@Composable
private fun CurrentBalanceCard(balance: Double) {
    OutlinedCard(modifier = Modifier.fillMaxWidth(), shape = FinTrackShape.Large) {
        Column(modifier = Modifier.padding(FinTrackSpacing.Lg), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "NET WORTH", style = MaterialTheme.typography.labelSmall, letterSpacing = 0.8.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(6.dp))
            AnimatedCurrency(amount = balance, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun InitialBalanceCard(balance: Double, onEditClick: () -> Unit) {
    OutlinedCard(modifier = Modifier.fillMaxWidth(), shape = FinTrackShape.Medium) {
        Row(modifier = Modifier.fillMaxWidth().padding(FinTrackSpacing.Md), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(text = "INITIAL BALANCE", style = MaterialTheme.typography.labelSmall, letterSpacing = 0.6.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = Format.currency(balance), style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace), fontWeight = FontWeight.Bold)
            }
            IconButton(onClick = onEditClick) { Icon(Icons.Rounded.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp)) }
        }
    }
}

@Composable
private fun SummaryCard(title: String, amount: Double, isPositive: Boolean, modifier: Modifier = Modifier) {
    val accent = if (isPositive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    OutlinedCard(modifier = modifier, shape = FinTrackShape.Medium) {
        Column(modifier = Modifier.padding(FinTrackSpacing.Md)) {
            Text(text = title.uppercase(), style = MaterialTheme.typography.labelSmall, letterSpacing = 0.6.sp, fontWeight = FontWeight.Bold, color = accent)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = Format.currency(amount), style = MaterialTheme.typography.titleSmall.copy(fontFamily = FontFamily.Monospace), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun SetBalanceDialog(currentBalance: Double, onDismiss: () -> Unit, onConfirm: (Double) -> Unit) {
    var text by remember { mutableStateOf(if (currentBalance == 0.0) "" else currentBalance.toString()) }
    val amountPattern = remember { Regex("^\\d*\\.?\\d*$") }
    AlertDialog(
        onDismissRequest = onDismiss, shape = FinTrackShape.Medium,
        title = { Text("Set Initial Balance", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(text = "Enter your current total bank balance", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = text, onValueChange = { v -> if (v.isEmpty() || v.matches(amountPattern)) text = v },
                    prefix = { Text("₹") }, label = { Text("Balance") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(), shape = FinTrackShape.Small,
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(text.toDoubleOrNull() ?: 0.0) }, enabled = text.isNotBlank() && (text.toDoubleOrNull() ?: -1.0) >= 0.0, shape = FinTrackShape.Pill) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
