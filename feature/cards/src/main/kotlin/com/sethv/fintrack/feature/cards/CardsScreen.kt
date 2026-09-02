package com.sethv.fintrack.feature.cards

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CreditCardOff
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sethv.fintrack.core.model.CardBill
import com.sethv.fintrack.core.model.CreditCard
import com.sethv.fintrack.core.ui.component.AnimatedCurrency
import com.sethv.fintrack.core.ui.theme.FinTrackSpacing
import com.sethv.fintrack.core.ui.util.Format
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

private val dueDateFormat = DateTimeFormatter.ofPattern("dd MMM yyyy")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardsScreen(
    viewModel: CardsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var renameTarget by remember { mutableStateOf<CreditCard?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is CardsEvent.MarkedPaid -> {
                    val result = snackbarHostState.showSnackbar(
                        message = event.message,
                        actionLabel = "UNDO",
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        viewModel.unmarkPaid(event.billId)
                    }
                }
                CardsEvent.Error -> snackbarHostState.showSnackbar("Could not update bill")
            }
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Credit Cards") }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { paddingValues ->
        if (!uiState.hasCards) {
            EmptyCardsState(modifier = Modifier.fillMaxSize().padding(paddingValues))
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    horizontal = FinTrackSpacing.Md,
                    vertical = FinTrackSpacing.Md,
                ),
                verticalArrangement = Arrangement.spacedBy(FinTrackSpacing.Md),
            ) {
                item {
                    OutstandingHeader(
                        totalOutstanding = uiState.totalOutstanding,
                        nearestDue = uiState.nearestDue,
                    )
                }

                uiState.sections.forEach { section ->
                    item(key = "header-${section.card.id}") {
                        CardSectionHeader(
                            section = section,
                            onRename = { renameTarget = section.card },
                        )
                    }
                    items(
                        items = section.unpaid,
                        key = { "bill-${it.id}" },
                    ) { bill ->
                        BillCard(
                            bill = bill,
                            card = section.card,
                            onMarkPaid = { viewModel.markPaid(BillRow(bill, section.card)) },
                        )
                    }
                }

                if (uiState.paidHistory.isNotEmpty()) {
                    item(key = "paid-header") {
                        Text(
                            text = "Paid history",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = FinTrackSpacing.Sm),
                        )
                    }
                    items(items = uiState.paidHistory, key = { "paid-${it.bill.id}" }) { row ->
                        PaidBillRow(row = row)
                    }
                }

                item { Spacer(modifier = Modifier.height(FinTrackSpacing.Md)) }
            }
        }
    }

    renameTarget?.let { card ->
        RenameCardDialog(
            card = card,
            onDismiss = { renameTarget = null },
            onConfirm = { newLabel ->
                viewModel.renameCard(card.id, newLabel)
                renameTarget = null
            },
        )
    }
}

@Composable
private fun OutstandingHeader(totalOutstanding: Double, nearestDue: BillRow?) {
    val gradient = Brush.verticalGradient(
        listOf(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.tertiaryContainer,
        ),
    )
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind { drawRect(gradient) },
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(
                text = "Total outstanding",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
            )
            AnimatedCurrency(
                amount = totalOutstanding,
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )

            val urgency = nearestDue?.let { CardsViewModel.urgencyOf(it.bill) }
            AnimatedVisibility(visible = nearestDue != null && urgency != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = urgencyColor(urgency!!).copy(alpha = 0.18f),
                        contentColor = urgencyColor(urgency),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Text(
                            text = nextDueLine(nearestDue!!),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                }
            }
        }
    }
}

private fun nextDueLine(row: BillRow): String {
    val days = CardsViewModel.daysRemaining(row.bill)
    return when {
        days < 0 -> "${row.card.displayName()} overdue by ${-days}d — ${Format.currency(row.bill.totalDue)}"
        days == 0 -> "${row.card.displayName()} due TODAY — ${Format.currency(row.bill.totalDue)}"
        else -> "${row.card.displayName()} due in ${days}d — ${Format.currency(row.bill.totalDue)}"
    }
}

@Composable
private fun CardSectionHeader(section: CardSection, onRename: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = FinTrackSpacing.Sm),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(FinTrackSpacing.Sm),
        ) {
            Icon(
                imageVector = Icons.Rounded.CreditCard,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = section.card.displayName() +
                    if (section.card.label.isNotBlank()) " (${section.card.label})" else "",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
        }
        IconButton(onClick = onRename, modifier = Modifier.size(28.dp)) {
            Icon(
                imageVector = Icons.Rounded.Edit,
                contentDescription = "Rename ${section.card.displayName()}",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BillCard(bill: CardBill, card: CreditCard, onMarkPaid: () -> Unit) {
    val urgency = CardsViewModel.urgencyOf(bill)
    val days = CardsViewModel.daysRemaining(bill)

    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(
            modifier = Modifier
                .padding(FinTrackSpacing.Md)
                .animateContentSize(),
        ) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text(
                        text = bill.statementLabel.ifBlank { "Statement" },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = Format.currency(bill.totalDue),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black,
                    )
                    if (bill.minDue > 0 && bill.minDue < bill.totalDue) {
                        Text(
                            text = "Min ${Format.currency(bill.minDue)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                DueChip(urgency = urgency, days = days, dueDate = bill.dueDate)
            }
            Spacer(modifier = Modifier.height(FinTrackSpacing.Md))
            Button(
                onClick = onMarkPaid,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (urgency == DueUrgency.OVERDUE) "I've paid (overdue)" else "Mark as paid")
            }
        }
    }
}

@Composable
private fun DueChip(urgency: DueUrgency, days: Int, dueDate: Long) {
    val color = urgencyColor(urgency)
    Surface(
        color = color.copy(alpha = 0.15f),
        contentColor = color,
        shape = RoundedCornerShape(10.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(text = urgencyLabel(days), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Text(
                text = formatDate(dueDate),
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

private fun urgencyColor(urgency: DueUrgency?): Color = when (urgency) {
    DueUrgency.OVERDUE -> Color(0xFFC62828)
    DueUrgency.TODAY -> Color(0xFFE65100)
    DueUrgency.WITHIN_3_DAYS -> Color(0xFFEF6C00)
    DueUrgency.WITHIN_A_WEEK -> Color(0xFFF9A825)
    else -> Color(0xFF2E7D32)
}

private fun urgencyLabel(days: Int): String = when {
    days < 0 -> "OVERDUE ${-days}d"
    days == 0 -> "DUE TODAY"
    else -> "DUE IN ${days}d"
}

@Composable
private fun PaidBillRow(row: BillRow) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
        Row(
            modifier = Modifier.padding(FinTrackSpacing.Md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.size(FinTrackSpacing.Md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = row.card.displayName(),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = formatDate(row.bill.dueDate),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = Format.currency(row.bill.paidAmount ?: row.bill.totalDue),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun EmptyCardsState(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(FinTrackSpacing.SmPlus),
            modifier = Modifier.padding(FinTrackSpacing.Xl),
        ) {
            Icon(
                imageVector = Icons.Outlined.CreditCardOff,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "No credit cards yet",
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "When a bank SMS mentions your card statement or bill due date, it will appear here automatically.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun RenameCardDialog(card: CreditCard, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember(card.id) { mutableStateOf(card.label) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename ${card.displayName()}") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(24) },
                placeholder = { Text("e.g. Travel card, Amazon ICICI…") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

private fun formatDate(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(dueDateFormat)
