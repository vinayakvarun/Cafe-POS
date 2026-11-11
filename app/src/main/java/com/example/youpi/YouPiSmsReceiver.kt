package com.example.youpi

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log

/**
 * BroadcastReceiver for incoming SMS messages to parse bank credit SMS messages.
 * Assembles multi-part SMS messages and parses UPI transaction credits.
 * Fully safe and crash-proof.
 */
class YouPiSmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        try {
            if (intent == null || Telephony.Sms.Intents.SMS_RECEIVED_ACTION != intent.action) {
                return
            }

            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
            if (messages.isEmpty()) return

            val sb = StringBuilder()
            var sender = ""

            for (sms in messages) {
                if (sms == null) continue
                if (sender.isEmpty()) {
                    sender = sms.originatingAddress ?: ""
                }
                sb.append(sms.messageBody ?: "")
            }

            val fullBody = sb.toString().trim()
            if (fullBody.isBlank()) return

            Log.d(TAG, "Captured incoming SMS from [$sender]: $fullBody")

            val tx = YouPiParser.parsePaymentText(fullBody)
            if (tx != null) {
                Log.i(TAG, "Parsed bank SMS credit from [$sender]: ₹${tx.amount}, UTR: ${tx.utr}")
                val matched = YouPiPaymentManager.onTransactionDetected(tx)
                if (matched) {
                    Log.i(TAG, "Successfully matched active bill session via SMS for ₹${tx.amount}!")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Safe error catch in SMS receiver", e)
        }
    }

    companion object {
        private const val TAG = "YouPiSmsReceiver"
    }
}
