package com.example

import com.example.youpi.YouPiParser
import com.example.youpi.YouPiPaymentManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class YouPiIntegrationTest {

    @Test
    fun testParseGooglePayNotification() {
        val text = "₹450.00 received from rahul@okaxis on Google Pay. UPI ref 412984128471"
        val tx = YouPiParser.parsePaymentText(text)
        assertNotNull(tx)
        assertEquals(450.0, tx!!.amount, 0.001)
        assertEquals("412984128471", tx.utr)
    }

    @Test
    fun testParsePhonePeNotification() {
        val text = "PhonePe. Received ₹500 from Ramesh Kumar via UPI Ref 412984128471"
        val tx = YouPiParser.parsePaymentText(text)
        assertNotNull(tx)
        assertEquals(500.0, tx!!.amount, 0.001)
        assertEquals("412984128471", tx.utr)
    }

    @Test
    fun testParsePhonePeBusinessNotification() {
        val text = "₹250.00 received on PhonePe QR from customer 9876543210. Txn Id: 412984128471"
        val tx = YouPiParser.parsePaymentText(text)
        assertNotNull(tx)
        assertEquals(250.0, tx!!.amount, 0.001)
        assertEquals("412984128471", tx.utr)
    }

    @Test
    fun testParsePaytmNotification() {
        val text = "Paytm: Received ₹120.00 from Amit. Ref No 412984128471"
        val tx = YouPiParser.parsePaymentText(text)
        assertNotNull(tx)
        assertEquals(120.0, tx!!.amount, 0.001)
    }

    @Test
    fun testIgnoreDebitTransactions() {
        val debitText = "Paid to Starbucks ₹350 on PhonePe. Txn Ref 412984128471"
        val tx = YouPiParser.parsePaymentText(debitText)
        assertNull(tx)
    }

    @Test
    fun testParsePhonePeNotificationWithBalance() {
        val text = "A/c 1234 credited by Rs. 750.00 on 21-Oct. Ref: 412984128471. Avail Bal: Rs. 15,400.00"
        val tx = YouPiParser.parsePaymentText(text)
        assertNotNull(tx)
        // Should extract 750, NOT the balance 15400!
        assertEquals(750.0, tx!!.amount, 0.001)
        assertEquals("412984128471", tx.utr)
    }

    @Test
    fun testParseBankSms() {
        val text = "Dear Customer, INR 320.00 deposited to your A/c XX8921 via UPI txn 918273645120."
        val tx = YouPiParser.parsePaymentText(text)
        assertNotNull(tx)
        assertEquals(320.0, tx!!.amount, 0.001)
    }

    @Test
    fun testPaymentManagerLifecycleAndMatching() {
        YouPiPaymentManager.startSession(
            expectedAmount = 250.0,
            transactionRef = "BILL_TEST_123",
            upiId = "merchant@upi"
        )

        var state = YouPiPaymentManager.paymentState.value
        assertTrue(state.isListening)
        assertEquals(false, state.isPaymentReceived)

        // Simulate incoming live transaction from phonepe
        val detected = YouPiPaymentManager.onTransactionDetected(
            com.example.youpi.YouPiTransaction(
                amount = 250.0,
                utr = "412984128471",
                senderVpa = "customer@okaxis",
                rawText = "Received ₹250.00 from customer"
            )
        )
        assertTrue(detected)

        state = YouPiPaymentManager.paymentState.value
        assertTrue(state.isPaymentReceived)
        assertEquals(250.0, state.receivedAmount, 0.001)
        assertEquals("Payment Recived Succeess", state.message)
        assertEquals("412984128471", state.utr)

        YouPiPaymentManager.stopSession()
        state = YouPiPaymentManager.paymentState.value
        assertEquals(false, state.isListening)

        // Verify auto-dismiss and auto-close defaults
        assertTrue(YouPiPaymentManager.autoDismissNotifications)
        assertTrue(YouPiPaymentManager.autoCloseDialogOnSuccess)
        assertEquals(2, YouPiPaymentManager.autoCloseDelaySeconds)
    }

    @Test
    fun testParsePhonePeCommaAndFormattingVariations() {
        val textComma = "Received ₹1,250.00 from John Doe via PhonePe UPI. Ref 412984128471"
        val txComma = YouPiParser.parsePaymentText(textComma, "com.phonepe.app")
        assertNotNull(txComma)
        assertEquals(1250.0, txComma!!.amount, 0.001)

        val textSenderFirst = "Rahul Sharma has paid ₹150 for your QR"
        val txSenderFirst = YouPiParser.parsePaymentText(textSenderFirst, "com.phonepe.app.business")
        assertNotNull(txSenderFirst)
        assertEquals(150.0, txSenderFirst!!.amount, 0.001)

        val textHindi = "PhonePe पर ₹350 प्राप्त हुए"
        val txHindi = YouPiParser.parsePaymentText(textHindi, "com.phonepe.app")
        assertNotNull(txHindi)
        assertEquals(350.0, txHindi!!.amount, 0.001)
    }

    @Test
    fun testAmountMatchingTolerances() {
        // Exact match
        assertTrue(YouPiPaymentManager.isAmountMatch(150.0, 150.0))
        // 50 paise rounding difference
        assertTrue(YouPiPaymentManager.isAmountMatch(150.0, 150.50))
        assertTrue(YouPiPaymentManager.isAmountMatch(150.75, 150.0))
        // Integer match
        assertTrue(YouPiPaymentManager.isAmountMatch(250.0, 250.0))
    }
}
