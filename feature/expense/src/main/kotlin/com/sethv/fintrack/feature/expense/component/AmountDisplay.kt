package com.sethv.fintrack.feature.expense.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import java.text.DecimalFormat

private val amountFormat = DecimalFormat("#,##0.00")

private val amountPattern = Regex("^\\d*\\.?\\d*$")

@Composable
fun AmountDisplay(
    amount: Double,
    onAmountChange: (Double) -> Unit,
    editMode: Boolean,
    modifier: Modifier = Modifier,
) {
    if (editMode) {
        // Seed ONCE from the parsed amount. Keying remember on `amount` reset the
        // text on every VM echo-back, corrupting mid-typing input ("12." became
        // "12.0" and moved the cursor).
        var text by rememberSaveable {
            mutableStateOf(if (amount == 0.0) "" else amount.toString())
        }

        OutlinedTextField(
            value = text,
            onValueChange = { value ->
                if (value.isEmpty() || value.matches(amountPattern)) {
                    text = value
                    onAmountChange(value.toDoubleOrNull() ?: 0.0)
                }
            },
            modifier = modifier.fillMaxWidth(),
            prefix = { Text("₹") },
            label = { Text("Amount") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
    } else {
        Text(
            text = "₹${amountFormat.format(amount)}",
            style = MaterialTheme.typography.displaySmall,
            textAlign = TextAlign.Center,
            modifier = modifier.fillMaxWidth(),
        )
    }
}
