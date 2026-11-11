package com.example.youpi

import java.util.regex.Pattern

data class YouPiTransaction(
    val amount: Double,
    val utr: String = "",
    val senderVpa: String = "",
    val rawText: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Intelligent Monetary Amount and UPI Transaction Extractor ported from YouPI-PG
 * (@youpi/core and apps/android-observer-app).
 * Filters out available balance and credit limit clauses so balance amounts
 * are never confused with payment amounts.
 */
object YouPiParser {

    val ALLOWED_BANKING_PACKAGES = setOf(
        // UPI & Wallet apps
        "com.phonepe.app", // PhonePe consumer
        "com.phonepe.app.business", // PhonePe Business
        "net.one97.paytm", // Paytm consumer
        "com.paytm.business", // Paytm for Business
        "com.paytmmall",
        "com.google.android.apps.nbu.paisa.user", // Google Pay
        "com.google.android.apps.nbu.paisa.merchant", // Google Pay Business
        "com.bharatpe.app", // BharatPe Merchant
        "in.org.npci.upiapp", // BHIM
        "com.dreamplug.androidapp", // CRED
        "money.jupiter", // Jupiter
        "com.epifi.paisa", // Fi Money
        "com.fampay.in", // FamPay
        "com.mobikwik_new", // MobiKwik
        "com.freecharge.android",
        "com.myairtelapp", // Airtel Payments
        "in.amazon.mshop.android.shopping", // Amazon Pay
        "com.nextbillion.groww",
        "indwin.c3.shareapp",
        "in.gokiwi.kiwitpap",
        "com.tatadigital.tcp",

        // Bank apps
        "com.sbi.lotusintouch", // YONO SBI
        "com.sbi.upi", // BHIM SBI Pay
        "com.snapwork.hdfc", // HDFC Bank
        "com.hdfcbank.payzapp", // PayZapp
        "com.csam.icici.bank.imobile", // iMobile ICICI
        "com.icicibank.pockets",
        "com.axis.mobile", // Axis Bank
        "com.upi.axispay",
        "com.msf.kbank.mobile", // Kotak 811
        "com.bankofbaroda.mconnect",
        "com.bankofbaroda.upi",
        "com.canarabank.mobility",
        "com.idfcfirstbank.optimus",
        "com.fedmobile",
        "com.infrasoft.uboi",
        "com.infrasofttech.indianbank",
        "com.mgs.induspsp",
        "com.fisglobal.bandhanupi.app",

        // SMS & Messaging apps
        "com.google.android.apps.messaging", // Google Messages
        "com.samsung.android.messaging", // Samsung Messages
        "com.android.mms", // AOSP SMS
        "com.truecaller" // Truecaller SMS
    )

    fun isBankingOrMessagingPackage(pkg: String?): Boolean {
        if (pkg.isNullOrBlank()) return false
        val lowerPkg = pkg.lowercase()
        if (ALLOWED_BANKING_PACKAGES.contains(lowerPkg)) return true
        return lowerPkg.contains("phonepe") ||
                lowerPkg.contains("paytm") ||
                lowerPkg.contains("bharatpe") ||
                lowerPkg.contains("paisa") ||
                lowerPkg.contains("bank") ||
                lowerPkg.contains("upi") ||
                lowerPkg.contains("sms") ||
                lowerPkg.contains("mms") ||
                lowerPkg.contains("messaging")
    }

    private val BALANCE_PATTERN = Pattern.compile(
        "(?:(?:avl\\b|avail\\b|available\\b|tot\\b|total\\b|ledger\\b|clear\\b|curr\\b|current\\b)\\s*(?:bal\\b|balance\\b|limit\\b)|\\bbal\\b|\\bbalance\\b|\\blimit\\b)[\\s:=-]*(?:(?:rs\\b\\.?|inr\\b)|₹)?[\\s:=-]*[\\d,]+(?:\\.\\d{1,2})?",
        Pattern.CASE_INSENSITIVE
    )

    private val DIRECT_CREDIT_PATTERN = Pattern.compile(
        "(?:credited\\s*(?:with|by|for|to)?|received\\s*(?:from\\s+[a-zA-Z0-9 ._-]+)?|deposit(?:ed)?|paid\\s*(?:to\\s*)?you|sent\\s*(?:to\\s*)?you|payment\\s+of|payment\\s+received|paid\\s+for\\s+your\\s+qr)\\s*[:=-]?\\s*(?:(?:rs\\b\\.?|inr\\b)|₹)?[\\s:=-]*([\\d,]+(?:\\.\\d{1,2})?)",
        Pattern.CASE_INSENSITIVE
    )

    private val DIRECT_CREDIT_SUFFIX_PATTERN = Pattern.compile(
        "(?:(?:rs\\b\\.?|inr\\b)|₹)[\\s:=-]*([\\d,]+(?:\\.\\d{1,2})?)\\s*(?:(?:has\\s+been|is)\\s+)?(?:credited|received|deposited|paid|प्राप्त)",
        Pattern.CASE_INSENSITIVE
    )

    private val STANDARD_PREFIX_PATTERN = Pattern.compile(
        "(?:(?:rs\\b\\.?|inr\\b)|₹)[\\s:=-]*([\\d,]+(?:\\.\\d{1,2})?)",
        Pattern.CASE_INSENSITIVE
    )

    private val STANDARD_SUFFIX_PATTERN = Pattern.compile(
        "(?:^|[^\\w.])([\\d,]+(?:\\.\\d{1,2})?)[\\s:=-]*(?:(?:rs\\b\\.?|inr\\b)|₹)",
        Pattern.CASE_INSENSITIVE
    )

    private val UTR_PATTERN = Pattern.compile(
        "(?:UPI\\s*(?:ref|reference|txn|id)?|Ref(?:erence)?\\s*(?:no|number)?|UTR|Txn\\s*Id|Transaction\\s*(?:Id|ID))[\\s:#-]*([0-9]{12}|[A-Za-z0-9]{12,})",
        Pattern.CASE_INSENSITIVE
    )

    private val VPA_PATTERN = Pattern.compile(
        "([a-zA-Z0-9._-]+@[a-zA-Z0-9]+)"
    )

    /**
     * Parses notification or SMS content and extracts amount, UTR, and VPA if this
     * represents an incoming payment credit.
     * Optionally accepts packageName to enable high-confidence parsing for PhonePe/UPI apps.
     */
    fun parsePaymentText(text: String?, packageName: String? = null): YouPiTransaction? {
        if (text.isNullOrBlank()) return null

        val lower = text.lowercase()
        val isPhonePe = (packageName != null && packageName.lowercase().contains("phonepe")) ||
                lower.contains("phonepe")

        // 1. Reject clear debit/outflow messages
        val isDebit = lower.contains("debited") ||
                (lower.contains("paid to") && !lower.contains("paid to you") && !lower.contains("paid you")) ||
                (lower.contains("sent to") && !lower.contains("sent to you")) ||
                lower.contains("withdrawn from")

        if (isDebit && !lower.contains("credited") && !lower.contains("received") && !lower.contains("paid you")) {
            return null
        }

        // 2. Must contain credit/payment confirmation words
        val isCredit = lower.contains("received") ||
                lower.contains("credited") ||
                lower.contains("deposit") ||
                lower.contains("paid you") ||
                lower.contains("paid to you") ||
                lower.contains("sent you") ||
                lower.contains("sent to you") ||
                lower.contains("payment of") ||
                lower.contains("payment received") ||
                lower.contains("money received") ||
                lower.contains("cashback received") ||
                lower.contains("successful") ||
                lower.contains("paid for your qr") ||
                lower.contains("has paid") ||
                lower.contains("प्राप्त") ||
                (isPhonePe && (lower.contains("payment") || lower.contains("qr") || lower.contains("paid") || lower.contains("₹") || lower.contains("rs")))

        if (!isCredit) return null

        // 3. Collapse duplicate whitespace
        val normalized = text.replace(Regex("\\s+"), " ")

        // 4. Strip balance mentions so account balance is not mistaken for payment
        val sanitized = BALANCE_PATTERN.matcher(normalized).replaceAll(" ").trim()

        var extractedAmount: Double? = null

        // Pattern A: Direct credit phrases ("credited with Rs 450", "received ₹200", "payment of ₹150", "received from X ₹150")
        val directMatch = DIRECT_CREDIT_PATTERN.matcher(sanitized)
        if (directMatch.find()) {
            val numStr = directMatch.group(1)?.replace(",", "")
            extractedAmount = numStr?.toDoubleOrNull()
        }

        // Pattern B: Suffix credit phrases ("₹200 received", "Rs 500 credited", "₹150 प्राप्त")
        if (extractedAmount == null) {
            val suffixCreditMatch = DIRECT_CREDIT_SUFFIX_PATTERN.matcher(sanitized)
            if (suffixCreditMatch.find()) {
                val numStr = suffixCreditMatch.group(1)?.replace(",", "")
                extractedAmount = numStr?.toDoubleOrNull()
            }
        }

        // Pattern C: Standard currency prefix (Rs. 450, ₹450, ₹ 450.00)
        if (extractedAmount == null) {
            val prefixMatch = STANDARD_PREFIX_PATTERN.matcher(sanitized)
            if (prefixMatch.find()) {
                val numStr = prefixMatch.group(1)?.replace(",", "")
                extractedAmount = numStr?.toDoubleOrNull()
            }
        }

        // Pattern D: Standard currency suffix (450 Rs, 450 INR)
        if (extractedAmount == null) {
            val suffixMatch = STANDARD_SUFFIX_PATTERN.matcher(sanitized)
            if (suffixMatch.find()) {
                val numStr = suffixMatch.group(1)?.replace(",", "")
                extractedAmount = numStr?.toDoubleOrNull()
            }
        }

        if (extractedAmount == null || extractedAmount <= 0.0) return null

        // Extract UTR if present
        var utr = ""
        val utrMatch = UTR_PATTERN.matcher(text)
        if (utrMatch.find()) {
            utr = utrMatch.group(1) ?: ""
        }

        // Extract VPA if present
        var vpa = ""
        val vpaMatch = VPA_PATTERN.matcher(text)
        if (vpaMatch.find()) {
            vpa = vpaMatch.group(1) ?: ""
        }

        return YouPiTransaction(
            amount = extractedAmount,
            utr = utr,
            senderVpa = vpa,
            rawText = text
        )
    }
}
