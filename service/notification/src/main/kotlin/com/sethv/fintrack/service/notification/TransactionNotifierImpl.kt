package com.sethv.fintrack.service.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.sethv.fintrack.core.common.util.Format
import com.sethv.fintrack.core.model.PendingTransaction
import com.sethv.fintrack.core.model.TransactionType
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

class TransactionNotifierImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : TransactionNotifier {

    override fun showTransactionNotification(pendingTransaction: PendingTransaction) {
        createChannel(CHANNEL_ID, CHANNEL_NAME)

        val verb = when (pendingTransaction.type) {
            TransactionType.CREDIT -> "received"
            TransactionType.DEBIT -> "spent"
        }
        val contentText = buildString {
            append("${rupees(pendingTransaction.amount)} $verb at ${pendingTransaction.merchant}")
            append("\nCategory: ${pendingTransaction.category.displayName}")
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            pendingTransaction.id.toInt(),
            createDeepLinkIntent(ACTION_REVIEW_TRANSACTION) {
                putExtra(EXTRA_PENDING_TRANSACTION_ID, pendingTransaction.id)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        post(pendingTransaction.id.toInt(), CHANNEL_ID, "New transaction detected", contentText, pendingIntent)
    }

    override fun showCardBillAlert(
        bankName: String,
        lastFour: String,
        totalDue: Double,
        minDue: Double?,
        dueDate: Long,
        billId: Long,
    ) {
        createChannel(CARDS_CHANNEL_ID, CARDS_CHANNEL_NAME)

        val dueLabel = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(dueDate))
        val contentText = buildString {
            append("$bankName •• $lastFour — ${rupees(totalDue)}")
            append("\nDue by $dueLabel")
            if (minDue != null && minDue > 0 && minDue < totalDue) {
                append(" (min ${rupees(minDue)})")
            }
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            BILL_NOTIFICATION_BASE + billId.toInt(),
            createDeepLinkIntent(ACTION_OPEN_CARDS),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        post(
            BILL_NOTIFICATION_BASE + billId.toInt(),
            CARDS_CHANNEL_ID,
            "Credit card bill generated",
            contentText,
            pendingIntent,
        )
    }

    override fun showBillPaidConfirmation(bankName: String, lastFour: String, paidAmount: Double) {
        createChannel(CARDS_CHANNEL_ID, CARDS_CHANNEL_NAME)
        val text = "$bankName •• $lastFour — ${rupees(paidAmount)} received. Bill marked as paid."

        val pendingIntent = PendingIntent.getActivity(
            context,
            PAID_NOTIFICATION_ID,
            createDeepLinkIntent(ACTION_OPEN_CARDS),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        post(PAID_NOTIFICATION_ID, CARDS_CHANNEL_ID, "Payment received 🎉", text, pendingIntent)
    }

    
    private fun rupees(amount: Double): String = Format.currency(amount)

    private fun post(id: Int, channelId: String, title: String, text: String, intent: PendingIntent?) {
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(intent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (se: SecurityException) {
            // POST_NOTIFICATIONS denied on Android 13+; not fatal.
            android.util.Log.w(TAG, "Notification not posted — permission denied", se)
        }
    }

    private fun createChannel(id: String, name: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channel = NotificationChannel(id, name, NotificationManager.IMPORTANCE_HIGH)
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    private inline fun createDeepLinkIntent(action: String, extras: Intent.() -> Unit = {}): Intent {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: Intent(Intent.ACTION_MAIN)

        return launchIntent.apply {
            this.action = action
            extras()
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
    }

    companion object {
        private const val TAG = "FinTrack.Notifier"
        const val CHANNEL_ID = "transaction_alerts"
        private const val CHANNEL_NAME = "Transaction Alerts"

        const val CARDS_CHANNEL_ID = "card_alerts"
        private const val CARDS_CHANNEL_NAME = "Card Bills & Payments"

        const val ACTION_REVIEW_TRANSACTION = "com.sethv.fintrack.action.REVIEW_TRANSACTION"
        const val EXTRA_PENDING_TRANSACTION_ID = "extra_pending_transaction_id"

        const val ACTION_OPEN_CARDS = "com.sethv.fintrack.action.OPEN_CARDS"

        private const val BILL_NOTIFICATION_BASE = 100_000
        private const val PAID_NOTIFICATION_ID = 200_001
    }
}


