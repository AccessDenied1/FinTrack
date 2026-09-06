package com.sethv.fintrack.feature.cards

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CreditCardOff
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sethv.fintrack.core.model.CardBill
import com.sethv.fintrack.core.model.CreditCard
import com.sethv.fintrack.core.model.ExpenseCategory
import com.sethv.fintrack.core.ui.component.CategoryDonutChart
import com.sethv.fintrack.core.ui.component.DonutSlice
import com.sethv.fintrack.core.ui.component.WeeklyBarsChart
import com.sethv.fintrack.core.ui.theme.FinTrackShape
import com.sethv.fintrack.core.ui.theme.FinTrackSpacing
import com.sethv.fintrack.core.ui.theme.bankColor
import com.sethv.fintrack.core.ui.theme.colorForCategoryIndex
import com.sethv.fintrack.core.ui.util.Format
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val dueDateFormat = DateTimeFormatter.ofPattern("dd MMM yyyy")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun CardsScreen(viewModel: CardsViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var renameTarget by remember { mutableStateOf<CreditCard?>(null) }
    var deleteTarget by remember { mutableStateOf<CreditCard?>(null) }
    var sheetRow by remember { mutableStateOf<BillRow?>(null) }

    val cards = uiState.cards
    val selectedCard = cards.find { it.id == uiState.selectedCardId }
    // The PagerState lambda is created once, so it must read the card count
    // through a holder that refreshes on every recomposition.
    val cardsRef = rememberUpdatedState(cards)
    val pagerState = rememberPagerState { cardsRef.value.size.coerceAtLeast(1) }

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

    // Default selection: first card when nothing is selected yet.
    LaunchedEffect(cards, uiState.selectedCardId) {
        if (selectedCard == null && cards.isNotEmpty()) viewModel.onSelectCard(cards.first().id)
    }
    // Pager swipe → selection.
    LaunchedEffect(pagerState.currentPage) {
        val cardsNow = uiState.cards
        val index = pagerState.currentPage
        if (index in cardsNow.indices) viewModel.onSelectCard(cardsNow[index].id)
    }
    // Selection → pager page (initial pick, deletion of the selected card).
    LaunchedEffect(uiState.selectedCardId) {
        val index = uiState.cards.indexOfFirst { it.id == uiState.selectedCardId }
        if (index in uiState.cards.indices && index != pagerState.currentPage) {
            pagerState.animateScrollToPage(index)
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
                if (cards.isNotEmpty()) {
                    item(key = "pager") {
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxWidth().height(200.dp),
                        ) { page ->
                            cards.getOrNull(page)?.let { card ->
                                CardPage(card = card, insights = uiState.insights[card.id])
                            }
                        }
                    }
                    selectedCard?.let { sc ->
                        item(key = "selected-actions") {
                            SelectedCardActions(card = sc, onRename = { renameTarget = sc }, onDelete = { deleteTarget = sc })
                        }
                        item(key = "hero") {
                            CardHero(insights = uiState.insights[sc.id])
                        }
                    }
                    val insights = selectedCard?.let { uiState.insights[it.id] }
                    insights?.let { ins ->
                        item(key = "due") {
                            DueCalendarSection(
                                bills = ins.dueCalendar,
                                onOpen = { bill -> sheetRow = BillRow(bill, selectedCard) },
                            )
                        }
                        item(key = "spend") {
                            SpendCard(insights = ins)
                        }
                    }
                    val sectionUnpaid = uiState.sections.find { it.card.id == uiState.selectedCardId }?.unpaid.orEmpty()
                    if (sectionUnpaid.isNotEmpty() && selectedCard != null) {
                        item(key = "bills-header") {
                            Text(
                                "STATEMENTS — ${selectedCard.bankName.uppercase()}",
                                style = MaterialTheme.typography.labelSmall,
                                letterSpacing = 0.8.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        items(items = sectionUnpaid, key = { "bill-${it.id}" }) { bill ->
                            BillCard(
                                bill = bill,
                                card = selectedCard,
                                onMarkPaid = { viewModel.markPaid(BillRow(bill, selectedCard)) },
                                onOpenStatement = { sheetRow = BillRow(bill, selectedCard) },
                            )
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
                            )
                        }
                        items(items = uiState.paidHistory, key = { "paid-${it.bill.id}" }) { row -> PaidBillRow(row = row) }
                    }
                    item { Spacer(modifier = Modifier.height(FinTrackSpacing.Md)) }
                }
            }
        }
    }

    sheetRow?.let { row ->
        ModalBottomSheet(
            onDismissRequest = { sheetRow = null },
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            StatementDetailSheet(
                row = row,
                spendByCategory = uiState.insights[row.card.id]?.spendByCategory.orEmpty(),
            )
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
private fun CardPage(card: CreditCard, insights: CardInsights?) {
    val shape = RoundedCornerShape(16.dp)
    Box(modifier = Modifier.fillMaxSize().padding(vertical = FinTrackSpacing.SmPlus).background(bankColor(card.bankName), shape)) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = card.bankName.uppercase() + if (card.label.isNotBlank()) " · ${card.label}" else "",
                    style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.0.sp),
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White.copy(alpha = 0.7f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "•• ${card.lastFour}",
                    style = MaterialTheme.typography.headlineSmall.copy(fontFamily = FontFamily.Monospace),
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                if (insights?.limit != null) {
                    Text(
                        text = "Limit ${Format.currency(insights.limit!!)}",
                        style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
                        color = Color.White.copy(alpha = 0.85f),
                    )
                }
                Text(
                    text = "Outstanding ${Format.currency(insights?.outstanding ?: 0.0)}",
                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
            }
        }
    }
}

@Composable
private fun SelectedCardActions(card: CreditCard, onRename: () -> Unit, onDelete: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = card.displayName() + if (card.label.isNotBlank()) " · ${card.label}" else "",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )
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

@Composable
private fun CardHero(insights: CardInsights?) {
    val outstanding = insights?.outstanding ?: 0.0
    val limit = insights?.limit
    val utilization = insights?.utilization
    val utilPct = utilization?.let { "%.0f".format(it * 100f) }
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = FinTrackShape.Large,
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(FinTrackSpacing.Lg)) {
            Text(
                text = "OUTSTANDING",
                style = MaterialTheme.typography.labelSmall,
                letterSpacing = 0.8.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = Format.currency(outstanding),
                style = MaterialTheme.typography.headlineSmall.copy(fontFamily = FontFamily.Monospace),
                fontWeight = FontWeight.Black,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = buildString {
                    append("Limit ").append(limit?.let { Format.currency(it) } ?: "—")
                    append(" • Util ")
                    append(utilPct ?: "—").append("%")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { (utilization ?: 0f).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(FinTrackShape.Pill),
                color = utilizationColor(utilization),
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = utilPct?.let { "$it% of limit used" } ?: "Set a limit in Settings to track utilization",
                style = MaterialTheme.typography.labelSmall,
                letterSpacing = 0.4.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun utilizationColor(utilization: Float?): Color = when {
    utilization == null -> MaterialTheme.colorScheme.outline
    utilization < 0.75f -> Color(0xFF0E7A4C)
    utilization <= 0.90f -> Color(0xFF8A6E3A)
    else -> Color(0xFFA12B2F)
}

@Composable
private fun DueCalendarSection(bills: List<CardBill>, onOpen: (CardBill) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "DUE",
                style = MaterialTheme.typography.labelSmall,
                letterSpacing = 0.8.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = if (bills.isEmpty()) "Nothing due" else "${bills.size} statement${if (bills.size > 1) "s" else ""}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.height(FinTrackSpacing.Sm))
        if (bills.isEmpty()) {
            Text(
                text = "No open statements for this card.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(FinTrackSpacing.Sm)) {
                items(items = bills, key = { "due-${it.id}" }) { bill ->
                    DueCalendarChip(bill = bill, onClick = { onOpen(bill) })
                }
            }
        }
    }
}

@Composable
private fun DueCalendarChip(bill: CardBill, onClick: () -> Unit) {
    val urgency = CardsViewModel.urgencyOf(bill)
    val color = urgencyColor(urgency)
    Surface(
        color = color.copy(alpha = 0.1f),
        contentColor = color,
        shape = FinTrackShape.Pill,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(text = bill.statementLabel.ifBlank { "STATEMENT" }.uppercase(), style = MaterialTheme.typography.labelSmall, letterSpacing = 0.6.sp, fontWeight = FontWeight.Black)
            Text(text = formatDate(bill.dueDate), style = MaterialTheme.typography.labelSmall, letterSpacing = 0.2.sp)
            Text(text = Format.currency(bill.totalDue), style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace), fontWeight = FontWeight.Bold)
            Text(text = "View statement", style = MaterialTheme.typography.labelSmall, letterSpacing = 0.2.sp, color = color.copy(alpha = 0.8f))
        }
    }
}

@Composable
private fun SpendCard(insights: CardInsights) {
    OutlinedCard(modifier = Modifier.fillMaxWidth(), shape = FinTrackShape.Medium) {
        Column(modifier = Modifier.padding(FinTrackSpacing.Md)) {
            Text(
                text = "THIS STATEMENT",
                style = MaterialTheme.typography.labelSmall,
                letterSpacing = 0.8.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(FinTrackSpacing.SmPlus))
            val spend = insights.spendByCategory
            val total = spend.values.sum()
            val slices = spend.entries.mapIndexed { idx, (category, amount) ->
                DonutSlice(
                    label = category.displayName,
                    value = if (total > 0.0) (amount / total * 100f).toFloat() else 0f,
                    colorIndex = idx,
                )
            }
            CategoryDonutChart(slices = slices, centerLabel = "TOTAL", centerSubLabel = Format.currency(insights.outstanding))
            Spacer(modifier = Modifier.height(FinTrackSpacing.MdPlus))
            val week = insights.spendTrend.takeLast(7)
            val labels = remember(week) { last7WeekdayLabels() }
            WeeklyBarsChart(values = week, dayLabels = labels, modifier = Modifier.fillMaxWidth())
        }
    }
}

private fun last7WeekdayLabels(): List<String> = (6L downTo 0L).map { d ->
    LocalDate.now().minusDays(d).dayOfWeek
        .getDisplayName(java.time.format.TextStyle.NARROW, java.util.Locale.getDefault()).uppercase()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BillCard(bill: CardBill, card: CreditCard, onMarkPaid: () -> Unit, onOpenStatement: () -> Unit) {
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
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(FinTrackSpacing.Sm)) {
                Button(
                    onClick = onMarkPaid,
                    shape = FinTrackShape.Pill,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                ) { Text(if (urgency == DueUrgency.OVERDUE) "I've paid — overdue" else "Mark as paid", fontWeight = FontWeight.SemiBold) }
                OutlinedButton(onClick = onOpenStatement, shape = FinTrackShape.Pill, modifier = Modifier.weight(1f)) {
                    Text("View statement", fontWeight = FontWeight.SemiBold)
                }
            }
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
private fun StatementDetailSheet(row: BillRow, spendByCategory: Map<ExpenseCategory, Double>, modifier: Modifier = Modifier) {
    val days = CardsViewModel.daysRemaining(row.bill)
    val urgency = CardsViewModel.urgencyOf(row.bill)
    Column(
        modifier = modifier
            .padding(horizontal = FinTrackSpacing.Lg)
            .padding(bottom = FinTrackSpacing.Lg),
        verticalArrangement = Arrangement.spacedBy(FinTrackSpacing.Md),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = row.card.displayName().uppercase(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.6.sp,
            )
            Text(
                text = "${row.bill.statementLabel.ifBlank { "Statement" }} · Due ${formatDate(row.bill.dueDate)} · ${urgencyLabel(days)}",
                style = MaterialTheme.typography.bodySmall,
                color = urgencyColor(urgency),
                fontWeight = FontWeight.SemiBold,
            )
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(FinTrackSpacing.Xl)) {
            SheetStat(label = "TOTAL DUE", value = Format.currency(row.bill.totalDue), highlight = true)
            SheetStat(label = "MIN DUE", value = Format.currency(row.bill.minDue))
        }
        HorizontalDivider()
        Column {
            Text(
                text = "SPEND BY CATEGORY",
                style = MaterialTheme.typography.labelSmall,
                letterSpacing = 0.8.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(FinTrackSpacing.Sm))
            if (spendByCategory.isEmpty()) {
                Text(
                    text = "No card spend recorded in this statement window yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                val total = spendByCategory.values.sum()
                spendByCategory.entries.toList().sortedByDescending { it.value }.mapIndexed { idx, (category, amount) ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(modifier = Modifier.size(8.dp).background(colorForCategoryIndex(idx), FinTrackShape.Pill))
                        Text(text = category.displayName, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        Text(
                            text = Format.currency(amount),
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "${"%.0f".format(amount / total * 100)}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(FinTrackSpacing.Sm))
            Text(
                text = "Transactions grouped by category.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SheetStat(label: String, value: String, highlight: Boolean = false) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            letterSpacing = 0.8.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall.copy(fontFamily = FontFamily.Monospace),
            fontWeight = if (highlight) FontWeight.Black else FontWeight.Bold,
            color = if (highlight) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
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
