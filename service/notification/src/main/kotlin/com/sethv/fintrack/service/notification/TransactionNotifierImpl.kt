package com.sethv.fintrack.service.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.sethv.fintrack.core.model.PendingTransaction
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class TransactionNotifierImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : TransactionNotifier {

    override fun showTransactionNotification(pendingTransaction: PendingTransaction) {
        createNotificationChannel()

        val contentText = buildString {
            append("₹${formatAmount(pendingTransaction.amount)} spent at ${pendingTransaction.merchant}")
            append("\nCategory: ${pendingTransaction.category.displayName}")
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            pendingTransaction.id.toInt(),
            createReviewIntent(pendingTransaction.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("New transaction detected")
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        NotificationManagerCompat.from(context).notify(
            pendingTransaction.id.toInt(),
            notification,
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH,
        )
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    private fun createReviewIntent(pendingId: Long): Intent {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: Intent(Intent.ACTION_MAIN)

        return launchIntent.apply {
            action = ACTION_REVIEW_TRANSACTION
            putExtra(EXTRA_PENDING_TRANSACTION_ID, pendingId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
    }

    private fun formatAmount(amount: Double): String =
        if (amount % 1.0 == 0.0) {
            amount.toLong().toString()
        } else {
            String.format("%.2f", amount)
        }

    companion object {
        const val CHANNEL_ID = "transaction_alerts"
        private const val CHANNEL_NAME = "Transaction Alerts"
        const val ACTION_REVIEW_TRANSACTION = "com.sethv.fintrack.action.REVIEW_TRANSACTION"
        const val EXTRA_PENDING_TRANSACTION_ID = "extra_pending_transaction_id"
    }
}
