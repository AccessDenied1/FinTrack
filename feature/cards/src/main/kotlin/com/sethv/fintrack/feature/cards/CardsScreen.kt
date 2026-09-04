package com.sethv.fintrack.feature.cards

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CreditCardOff
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.DeleteOutline
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sethv.fintrack.core.model.CardBill
import com.sethv.fintrack.core.model.CreditCard
import com.sethv.fintrack.core.ui.component.AnimatedCurrency
import com.sethv.fintrack.core.ui.theme.FinTrackShape
import com.sethv.fintrack.core.ui.theme.FinTrackSpacing
import com.sethv.fintrack.core.ui.util.Format
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val dueDateFormat = DateTimeFormatter.ofPattern("dd MMM yyyy")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardsScreen(viewModel: CardsViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var renameTarget by remember { mutableStateOf<CreditCard?>(null) }
    var deleteTarget by remember { mutableStateOf<CreditCard?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is CardsEvent.MarkedPaid -> {
                    val result = snackbarHostState.showSnackbar(event.message, actionLabel = "UNDO")
                    if (result == SnackbarResult.ActionPerformed) viewModel.unmarkPaid(event.billId)
                }
                CardsEvent.Error -> snackbarHostState.showSnackbar("Could not update bill")
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "CARDS",
                        style = MaterialTheme.typography.titleSmall,
                        letterSpacing = 1.4.sp,
                        fontWeight = FontWeight.Black,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { paddingValues ->
        if (!uiState.hasCards) {
            EmptyCardsState(modifier = Modifier.fillMaxSize().padding(paddingValues))
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = FinTrackSpacing.Md, vertical = FinTrackSpacing.Md),
                verticalArrangement = Arrangement.spacedBy(FinTrackSpacing.Md),
            ) {
                item { OutstandingHeader(totalOutstanding = uiState.totalOutstanding, nearestDue = uiState.nearestDue) }
                uiState.sections.forEach { section ->
                    item(key = "header-${section.card.id}") {
                        CardSectionHeader(section = section, onRename = { renameTarget = section.card }, onDelete = { deleteTarget = section.card })
                    }
                    items(items = section.unpaid, key = { "bill-${it.id}" }) { bill ->
                        BillCard(bill = bill, card = section.card, onMarkPaid = { viewModel.markPaid(BillRow(bill, section.card)) })
                    }
                }
                if (uiState.paidHistory.isNotEmpty()) {
                    item(key = "paid-header") {
                        Text(
                            "PAID HISTORY",
                            style = MaterialTheme.typography.labelSmall,
                            letterSpacing = 0.8.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = FinTrackSpacing.Md, bottom = 4.dp),
                        )
                    }
                    items(items = uiState.paidHistory, key = { "paid-${it.bill.id}" }) { row -> PaidBillRow(row = row) }
                }
                item { Spacer(modifier = Modifier.height(FinTrackSpacing.Md)) }
            }
        }
    }

    renameTarget?.let { card ->
        RenameCardDialog(card = card, onDismiss = { renameTarget = null }, onConfirm = { newLabel -> viewModel.renameCard(card.id, newLabel); renameTarget = null })
    }
    deleteTarget?.let { card ->
        DeleteCardDialog(card = card, onDismiss = { deleteTarget = null }, onConfirm = { viewModel.deleteCard(card.id); deleteTarget = null })
    }
}

@Composable
private fun OutstandingHeader(totalOutstanding: Double, nearestDue: BillRow?) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = FinTrackShape.Large,
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(FinTrackSpacing.Lg)) {
            Text(
                text = "TOTAL OUTSTANDING",
                style = MaterialTheme.typography.labelSmall,
                letterSpacing = 0.8.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(6.dp))
            AnimatedCurrency(
                amount = totalOutstanding,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface,
            )
            nearestDue?.let {
                Spacer(modifier = Modifier.height(12.dp))
                val urgency = CardsViewModel.urgencyOf(it.bill)
                Surface(color = urgencyColor(urgency).copy(alpha = 0.12f), contentColor = urgencyColor(urgency), shape = FinTrackShape.Pill) {
                    Text(
                        text = nextDueLine(it),
                        style = MaterialTheme.typography.labelSmall,
                        letterSpacing = 0.4.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }
            }
        }
    }
}

private fun nextDueLine(row: BillRow): String {
    val days = CardsViewModel.daysRemaining(row.bill)
    return when {
        days < 0 -> "${row.card.displayName()} · OVERDUE ${-days}D · ${Format.currency(row.bill.totalDue)}"
        days == 0 -> "${row.card.displayName()} · DUE TODAY · ${Format.currency(row.bill.totalDue)}"
        else -> "${row.card.displayName()} · DUE IN ${days}D · ${Format.currency(row.bill.totalDue)}"
    }
}

@Composable
private fun CardSectionHeader(section: CardSection, onRename: () -> Unit, onDelete: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier.size(28.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh, FinTrackShape.Small),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.CreditCard, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
            }
            Text(
                text = section.card.displayName() + if (section.card.label.isNotBlank()) " · ${section.card.label}" else "",
                style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            IconButton(onClick = onRename, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Rounded.Edit, contentDescription = "Rename", modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete", modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BillCard(bill: CardBill, card: CreditCard, onMarkPaid: () -> Unit) {
    val urgency = CardsViewModel.urgencyOf(bill)
    val days = CardsViewModel.daysRemaining(bill)
    OutlinedCard(modifier = Modifier.fillMaxWidth(), shape = FinTrackShape.Medium) {
        Column(modifier = Modifier.padding(FinTrackSpacing.Md).animateContentSize()) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text(text = bill.statementLabel.ifBlank { "STATEMENT" }.uppercase(), style = MaterialTheme.typography.labelSmall, letterSpacing = 0.6.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(text = Format.currency(bill.totalDue), style = MaterialTheme.typography.headlineSmall.copy(fontFamily = FontFamily.Monospace), fontWeight = FontWeight.Black)
                    if (bill.minDue > 0 && bill.minDue < bill.totalDue) {
                        Text(text = "Min ${Format.currency(bill.minDue)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                DueChip(urgency = urgency, days = days, dueDate = bill.dueDate)
            }
            Spacer(modifier = Modifier.height(14.dp))
            Button(
                onClick = onMarkPaid, shape = FinTrackShape.Pill, modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            ) { Text(if (urgency == DueUrgency.OVERDUE) "I've paid — overdue" else "Mark as paid", fontWeight = FontWeight.SemiBold) }
        }
    }
}

@Composable
private fun DueChip(urgency: DueUrgency, days: Int, dueDate: Long) {
    val color = urgencyColor(urgency)
    Surface(color = color.copy(alpha = 0.1f), contentColor = color, shape = FinTrackShape.Pill) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            Text(text = urgencyLabel(days), style = MaterialTheme.typography.labelSmall, letterSpacing = 0.6.sp, fontWeight = FontWeight.Black)
            Text(text = formatDate(dueDate), style = MaterialTheme.typography.labelSmall, letterSpacing = 0.2.sp)
        }
    }
}

private fun urgencyColor(urgency: DueUrgency?): Color = when (urgency) {
    DueUrgency.OVERDUE -> Color(0xFFA12B2F)
    DueUrgency.TODAY -> Color(0xFF8A6E3A)
    DueUrgency.WITHIN_3_DAYS -> Color(0xFF6B4A2E)
    DueUrgency.WITHIN_A_WEEK -> Color(0xFF3A5B75)
    else -> Color(0xFF0E7A4C)
}

private fun urgencyLabel(days: Int): String = when {
    days < 0 -> "OVERDUE ${-days}D"
    days == 0 -> "DUE TODAY"
    else -> "DUE IN ${days}D"
}

@Composable
private fun PaidBillRow(row: BillRow) {
    OutlinedCard(modifier = Modifier.fillMaxWidth(), shape = FinTrackShape.Medium) {
        Row(modifier = Modifier.padding(FinTrackSpacing.Md), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = row.card.displayName(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(text = formatDate(row.bill.dueDate), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(text = Format.currency(row.bill.paidAmount ?: row.bill.totalDue), style = MaterialTheme.typography.titleSmall.copy(fontFamily = FontFamily.Monospace), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun EmptyCardsState(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(FinTrackSpacing.Xl)) {
            Box(modifier = Modifier.size(56.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh, FinTrackShape.Medium), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.CreditCardOff, contentDescription = null, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(text = "No credit cards yet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text(text = "When a bank SMS mentions your card statement or bill due date, it will appear here automatically.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun RenameCardDialog(card: CreditCard, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember(card.id) { mutableStateOf(card.label) }
    AlertDialog(
        onDismissRequest = onDismiss, shape = FinTrackShape.Medium,
        title = { Text("Rename ${card.displayName()}", fontWeight = FontWeight.Bold) },
        text = {
            OutlinedTextField(value = text, onValueChange = { text = it.take(24) }, placeholder = { Text("e.g. Travel card") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = FinTrackShape.Small)
        },
        confirmButton = { TextButton(onClick = { onConfirm(text) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun DeleteCardDialog(card: CreditCard, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss, shape = FinTrackShape.Medium,
        title = { Text("Delete card?", fontWeight = FontWeight.Bold) },
        text = { Text("Remove ${card.displayName()} and all its statement history? This cannot be undone.") },
        confirmButton = { TextButton(onClick = onConfirm, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Delete") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun formatDate(epochMillis: Long): String = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(dueDateFormat)
