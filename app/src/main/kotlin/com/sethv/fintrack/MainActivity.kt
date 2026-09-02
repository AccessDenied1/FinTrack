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

    // Notification deep-links already surfaced once must not re-fire: the
    // stored Intent survives configuration changes / recreation, and the cold-
    // start path would otherwise navigate again on every rotation.
    private val handledDeepLinkIds = mutableSetOf<Long>()

    // Warm-start link that arrived before the Compose nav graph was ready.
    private var pendingDeepLinkId: Long? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val initialPendingId = extractPendingTransactionId(intent)
        val openCards = isCardDeepLink(intent)
        setContent {
            FinTrackTheme {
                FinTrackApp(
                    initialPendingId = initialPendingId,
                    initialOpenCards = openCards,
                    onNavControllerReady = { controller ->
                        navController = controller
                        pendingDeepLinkId?.let { pendingId ->
                            pendingDeepLinkId = null
                            navigateToReview(controller, pendingId)
                        }
                    },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (isCardDeepLink(intent)) {
            val controller = navController
            controller?.navigate(Route.Cards.route) {
                popUpTo(Route.Home.route) { saveState = true }
                launchSingleTop = true
            }
            return
        }

        val pendingId = extractPendingTransactionId(intent) ?: return
        val controller = navController
        if (controller != null) {
            navigateToReview(controller, pendingId)
        } else {
            // Activity still recreating — hold the link until the nav host is up.
            pendingDeepLinkId = pendingId
        }
    }

    private fun isCardDeepLink(intent: Intent?): Boolean =
        intent?.action == TransactionNotifierImpl.ACTION_OPEN_CARDS

    private fun navigateToReview(controller: NavHostController, pendingId: Long) {
        controller.navigate(Route.ExpenseReview.createRoute(pendingId)) {
            launchSingleTop = true
        }
    }

    private fun extractPendingTransactionId(intent: Intent?): Long? {
        if (intent?.action != TransactionNotifierImpl.ACTION_REVIEW_TRANSACTION) return null
        val pendingId = intent.getLongExtra(TransactionNotifierImpl.EXTRA_PENDING_TRANSACTION_ID, -1L)
        if (pendingId < 0L) return null
        // Set.add returns false when the id was already handled → filtered out.
        return pendingId.takeIf { handledDeepLinkIds.add(pendingId) }
    }
}
