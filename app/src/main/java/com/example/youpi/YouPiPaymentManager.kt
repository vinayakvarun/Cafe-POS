package com.example.youpi

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs

data class YouPiPaymentState(
    val isListening: Boolean = false,
    val isPaymentReceived: Boolean = false,
    val expectedAmount: Double = 0.0,
    val receivedAmount: Double = 0.0,
    val transactionRef: String = "",
    val upiId: String = "",
    val utr: String = "",
    val senderVpa: String = "",
    val timestamp: Long = 0L,
    val message: String = ""
)

/**
 * Coordinates UPI payment verification between the POS Screen, incoming Bank/UPI Notifications,
 * SMS observer, and instant test simulation.
 */
object YouPiPaymentManager {
    private const val TAG = "YouPiPaymentManager"

    // Configuration preferences
    @Volatile
    var autoDismissNotifications: Boolean = true

    @Volatile
    var autoDismissAllUpiCreditAlerts: Boolean = true

    @Volatile
    var autoCloseDialogOnSuccess: Boolean = true

    @Volatile
    var autoCloseDelaySeconds: Int = 2

    private val _paymentState = MutableStateFlow(YouPiPaymentState())
    val paymentState: StateFlow<YouPiPaymentState> = _paymentState.asStateFlow()

    private fun logInfo(msg: String) {
        try {
            Log.i(TAG, msg)
        } catch (_: Throwable) {
            println("[$TAG] $msg")
        }
    }

    private fun logDebug(msg: String) {
        try {
            Log.d(TAG, msg)
        } catch (_: Throwable) {
            println("[$TAG] $msg")
        }
    }

    /**
     * Checks if received amount matches expected bill amount with practical tolerances:
     * - Exact or within 2 paise
     * - Rounding within 1 Rupee (e.g., GST rounding / fraction differences)
     * - Overpayment / customer tip
     * - Truncated integer match (e.g., 250 vs 250.00)
     */
    fun isAmountMatch(received: Double, expected: Double): Boolean {
        if (expected <= 0.0) return received > 0.0
        // Exact match or within 0.02 (2 paise)
        if (abs(received - expected) < 0.02) return true
        // Paise rounding difference (up to 1 Rupee difference)
        if (abs(received - expected) <= 1.05) return true
        // Overpayment or tip (up to 50% extra)
        if (received >= expected && received <= expected * 1.5) return true
        // Truncated integer match (e.g. 250 vs 250.00)
        if (received.toLong() == expected.toLong() && expected > 0.0) return true
        return false
    }

    /**
     * Call when a new bill or QR code is presented to the customer.
     */
    fun startSession(expectedAmount: Double, transactionRef: String, upiId: String) {
        logInfo("Starting YouPI session for ₹$expectedAmount, ref: $transactionRef")
        _paymentState.value = YouPiPaymentState(
            isListening = true,
            isPaymentReceived = false,
            expectedAmount = expectedAmount,
            receivedAmount = 0.0,
            transactionRef = transactionRef,
            upiId = upiId,
            timestamp = System.currentTimeMillis()
        )
    }

    /**
     * Stop listening when bill is completed or dismissed.
     */
    fun stopSession() {
        logInfo("Stopping YouPI session")
        _paymentState.value = YouPiPaymentState()
    }

    /**
     * Process an incoming transaction from NotificationListenerService or SmsReceiver.
     * Returns true if this transaction matched and fulfilled the active payment session.
     */
    fun onTransactionDetected(tx: YouPiTransaction): Boolean {
        val current = _paymentState.value
        if (!current.isListening) {
            logDebug("Ignored transaction because no active session is listening")
            return false
        }

        val amountMatches = isAmountMatch(tx.amount, current.expectedAmount)

        if (amountMatches) {
            logInfo("Payment matched! Received: ₹${tx.amount}, Expected: ₹${current.expectedAmount}, UTR: ${tx.utr}")
            _paymentState.value = current.copy(
                isPaymentReceived = true,
                receivedAmount = tx.amount,
                utr = if (tx.utr.isNotBlank()) tx.utr else generateFallbackUtr(),
                senderVpa = tx.senderVpa,
                timestamp = System.currentTimeMillis(),
                message = "Payment Recived Succeess"
            )
            return true
        } else {
            logDebug("Amount mismatch: expected ₹${current.expectedAmount}, got ₹${tx.amount}")
            return false
        }
    }

    /**
     * Simulates customer payment immediately.
     * Essential for in-store testing, emulator verification, and customer demonstrations.
     */
    fun simulatePayment(amountOverride: Double? = null) {
        val current = _paymentState.value
        val amount = amountOverride ?: if (current.expectedAmount > 0.0) current.expectedAmount else 100.0
        val simulatedUtr = generateFallbackUtr()
        val simulatedVpa = "customer.pay@okhdfcbank"

        logInfo("Simulating UPI payment of ₹$amount (UTR: $simulatedUtr)")
        _paymentState.value = current.copy(
            isListening = true,
            isPaymentReceived = true,
            expectedAmount = if (current.expectedAmount > 0.0) current.expectedAmount else amount,
            receivedAmount = amount,
            utr = simulatedUtr,
            senderVpa = simulatedVpa,
            timestamp = System.currentTimeMillis(),
            message = "Payment Recived Succeess"
        )
    }

    private fun generateFallbackUtr(): String {
        val randomDigits = (10000000L..99999999L).random()
        return "4129$randomDigits"
    }
}
