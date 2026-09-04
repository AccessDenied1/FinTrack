package com.sethv.fintrack.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sethv.fintrack.core.model.Transaction
import com.sethv.fintrack.core.model.TransactionType
import com.sethv.fintrack.core.ui.theme.FinTrackShape
import com.sethv.fintrack.core.ui.theme.FinTrackSpacing
import com.sethv.fintrack.core.ui.theme.LocalFinTrackColors
import com.sethv.fintrack.core.ui.util.Format
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Precision ledger row — editorial, tabular, hairline.
 * Amount is mono; leading mark is a 40dp rounded square with hairline,
 * not a bubbly circle. Trailing amount is the visual anchor.
 */
@Composable
fun TransactionItem(
    transaction: Transaction,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = FinTrackSpacing.Md, vertical = FinTrackSpacing.SmPlus),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = FinTrackShape.Small,
                )
                .border(
                    width = FinTrackSpacing.Hairline,
                    color = MaterialTheme.colorScheme.outlineVariant,
                    shape = FinTrackShape.Small,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = categoryIcon(transaction.category),
                contentDescription = transaction.category.displayName,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = transaction.merchant,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = transaction.category.displayName.uppercase(Locale.getDefault()),
                    style = MaterialTheme.typography.labelSmall,
                    letterSpacing = 0.6.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Box(
                    modifier = Modifier
                        .padding(horizontal = 6.dp)
                        .size(2.dp)
                        .background(
                            MaterialTheme.colorScheme.outlineVariant,
                            RoundedCornerShape(999.dp),
                        ),
                )
                Text(
                    text = formatDate(transaction.dateTime),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        val isCredit = transaction.type == TransactionType.CREDIT
        val finColors = LocalFinTrackColors.current
        Text(
            text = if (isCredit) "+${Format.currency(transaction.amount)}" else "−${Format.currency(transaction.amount)}",
            style = MaterialTheme.typography.titleSmall.copy(
                fontFamily = FontFamily.Monospace,
                letterSpacing = (-0.3).sp,
            ),
            fontWeight = FontWeight.Bold,
            color = if (isCredit) finColors.credit else MaterialTheme.colorScheme.onSurface,
        )
    }
}

private val dateFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd MMM", Locale.getDefault())

private fun formatDate(timestamp: Long): String =
    Instant.ofEpochMilli(timestamp)
        .atZone(ZoneId.systemDefault())
        .format(dateFormatter)
