package com.sethv.fintrack

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.navigation.NavHostController
import com.sethv.fintrack.core.ui.theme.FinTrackTheme
import com.sethv.fintrack.navigation.Route
import com.sethv.fintrack.service.notification.TransactionNotifierImpl
import com.sethv.fintrack.ui.FinTrackApp
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private var navController: NavHostController? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val initialPendingId = extractPendingTransactionId(intent)
        setContent {
            FinTrackTheme {
                FinTrackApp(
                    initialPendingId = initialPendingId,
                    onNavControllerReady = { controller ->
                        navController = controller
                    },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        extractPendingTransactionId(intent)?.let { pendingId ->
            navController?.navigate(Route.ExpenseReview.createRoute(pendingId)) {
                launchSingleTop = true
            }
        }
    }

    private fun extractPendingTransactionId(intent: Intent?): Long? {
        if (intent?.action != TransactionNotifierImpl.ACTION_REVIEW_TRANSACTION) return null
        val pendingId = intent.getLongExtra(TransactionNotifierImpl.EXTRA_PENDING_TRANSACTION_ID, -1L)
        return pendingId.takeIf { it >= 0L }
    }
}
