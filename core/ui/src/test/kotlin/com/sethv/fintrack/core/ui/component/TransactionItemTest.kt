package com.sethv.fintrack.core.ui.component

import androidx.compose.material3.Surface
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import com.sethv.fintrack.core.model.ExpenseCategory
import com.sethv.fintrack.core.model.Transaction
import com.sethv.fintrack.core.model.TransactionType
import com.sethv.fintrack.core.ui.theme.FinTrackTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class TransactionItemTest {

    @get:Rule
    val compose = createComposeRule()

    // 2025-05-15T12:00:00Z -> 15 May in the machine's IST zone.
    private val fixedInstant = 1747310400000L

    @Test
    fun `debit row shows merchant, category, amount and date`() {
        val transaction = Transaction(
            amount = 1234.5,
            merchant = "SWIGGY",
            category = ExpenseCategory.FOOD,
            type = TransactionType.DEBIT,
            dateTime = fixedInstant,
            bank = "HDFC",
        )

        compose.setContent {
            FinTrackTheme {
                Surface {
                    TransactionItem(transaction)
                }
            }
        }

        compose.onNodeWithText("SWIGGY").assertIsDisplayed()
        compose.onNodeWithText("FOOD").assertIsDisplayed()
        // Debit rows render "−₹1,234.5"; assert the sign-agnostic amount substring.
        compose.onNodeWithText("₹1,234.5", substring = true).assertIsDisplayed()
        compose.onNodeWithText("15 May").assertIsDisplayed()
    }
}
