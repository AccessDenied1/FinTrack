package com.sethv.fintrack.core.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.sethv.fintrack.core.model.ExpenseCategory
import com.sethv.fintrack.core.model.Transaction
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun TransactionItem(
    transaction: Transaction,
    modifier: Modifier = Modifier,
) {
    ListItem(
        modifier = modifier,
        leadingContent = {
            Icon(
                imageVector = categoryIcon(transaction.category),
                contentDescription = transaction.category.displayName,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        headlineContent = {
            Text(
                text = transaction.merchant,
                style = MaterialTheme.typography.titleMedium,
            )
        },
        supportingContent = {
            Text(
                text = formatDate(transaction.dateTime),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingContent = {
            Column {
                Text(
                    text = formatAmount(transaction.amount),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                AssistChip(
                    onClick = {},
                    label = {
                        Text(
                            text = transaction.category.displayName,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    },
                )
            }
        },
    )
}

private fun categoryIcon(category: ExpenseCategory): ImageVector = when (category) {
    ExpenseCategory.FOOD -> Icons.Default.Restaurant
    ExpenseCategory.GROCERIES -> Icons.Default.ShoppingCart
    ExpenseCategory.SHOPPING -> Icons.Default.ShoppingBag
    ExpenseCategory.FUEL -> Icons.Default.LocalGasStation
    ExpenseCategory.TRANSPORT -> Icons.Default.DirectionsCar
    ExpenseCategory.BILLS -> Icons.Default.Receipt
    ExpenseCategory.ENTERTAINMENT -> Icons.Default.Movie
    ExpenseCategory.HEALTHCARE -> Icons.Default.LocalHospital
    ExpenseCategory.TRAVEL -> Icons.Default.Flight
    ExpenseCategory.RENT -> Icons.Default.Home
    ExpenseCategory.SUBSCRIPTION -> Icons.Default.Subscriptions
    ExpenseCategory.OTHERS -> Icons.Default.Category
}

private fun formatAmount(amount: Double): String {
    val formatted = if (amount % 1.0 == 0.0) {
        amount.toLong().toString()
    } else {
        String.format(Locale.getDefault(), "%.2f", amount)
    }
    return "₹$formatted"
}

private fun formatDate(timestamp: Long): String {
    val formatter = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.getDefault())
    return Instant.ofEpochMilli(timestamp)
        .atZone(ZoneId.systemDefault())
        .format(formatter)
}
