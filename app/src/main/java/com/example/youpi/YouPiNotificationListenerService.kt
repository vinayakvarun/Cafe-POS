package com.example.youpi

import android.app.Notification
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

/**
 * Listens for incoming UPI payment notifications from banking apps (PhonePe, Google Pay,
 * Paytm, BHIM, Bank SMS) and forwards them to YouPiPaymentManager.
 * Fully safe and crash-proof.
 */
class YouPiNotificationListenerService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        try {
            if (sbn == null) return

            val pkg = sbn.packageName ?: return

            // Filter for banking, UPI, merchant, and messaging apps
            if (!YouPiParser.isBankingOrMessagingPackage(pkg)) {
                return
            }

            val notification = sbn.notification ?: return
            val extras: Bundle = notification.extras ?: return

            val titleCs = extras.getCharSequence(Notification.EXTRA_TITLE)
            val textCs = extras.getCharSequence(Notification.EXTRA_TEXT)
            val bigTextCs = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
            val subTextCs = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)
            val summaryCs = extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT)
            val infoTextCs = extras.getCharSequence(Notification.EXTRA_INFO_TEXT)
            val titleBigCs = extras.getCharSequence(Notification.EXTRA_TITLE_BIG)
            val textLines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
            val ticker = notification.tickerText

            val sb = StringBuilder()
            if (!titleCs.isNullOrBlank()) sb.append(titleCs).append(". ")
            if (!titleBigCs.isNullOrBlank() && titleBigCs != titleCs) sb.append(titleBigCs).append(". ")
            if (!textCs.isNullOrBlank()) sb.append(textCs).append(". ")
            if (!bigTextCs.isNullOrBlank() && bigTextCs != textCs) sb.append(bigTextCs).append(". ")
            if (!subTextCs.isNullOrBlank()) sb.append(subTextCs).append(". ")
            if (!summaryCs.isNullOrBlank()) sb.append(summaryCs).append(". ")
            if (!infoTextCs.isNullOrBlank()) sb.append(infoTextCs).append(". ")
            if (!ticker.isNullOrBlank()) sb.append(ticker).append(". ")

            textLines?.forEach { line ->
                if (!line.isNullOrBlank()) sb.append(line).append(". ")
            }

            // Fallback: check all other CharSequence extras
            for (key in extras.keySet()) {
                try {
                    val value = extras.get(key)
                    if (value is CharSequence && !value.isNullOrBlank() && !sb.contains(value)) {
                        sb.append(value).append(" ")
                    }
                } catch (_: Throwable) {}
            }

            val combined = sb.toString().trim()
            if (combined.isBlank()) return

            Log.i(TAG, "Notification intercepted from [$pkg]: $combined")

            val tx = YouPiParser.parsePaymentText(combined, pkg)
            if (tx != null) {
                Log.i(TAG, "Parsed valid UPI payment from [$pkg]: ₹${tx.amount}, UTR: ${tx.utr}")
                val matched = YouPiPaymentManager.onTransactionDetected(tx)
                if (matched) {
                    Log.i(TAG, "Successfully matched active bill session for ₹${tx.amount}!")
                }

                // Auto-close/dismiss the PhonePe/UPI notification from Android status bar & notification shade
                if (YouPiPaymentManager.autoDismissNotifications && (matched || YouPiPaymentManager.autoDismissAllUpiCreditAlerts)) {
                    dismissNotification(sbn)
                }
            } else {
                Log.d(TAG, "Notification from [$pkg] was not an incoming credit or amount was not found")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Safe error catch in notification listener", e)
        }
    }

    private fun dismissNotification(sbn: StatusBarNotification) {
        try {
            if (sbn.isClearable) {
                cancelNotification(sbn.key)
                Log.i(TAG, "Successfully auto-dismissed notification: ${sbn.key}")
            }
        } catch (e: Throwable) {
            try {
                cancelNotification(sbn.packageName, sbn.tag, sbn.id)
                Log.i(TAG, "Auto-dismissed notification via (pkg, tag, id)")
            } catch (e2: Throwable) {
                Log.w(TAG, "Could not dismiss notification: ${e2.message}")
            }
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.i(TAG, "YouPI Notification Observer Connected successfully")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.i(TAG, "YouPI Notification Observer Disconnected")
    }

    companion object {
        private const val TAG = "YouPiNotifService"
    }
}
