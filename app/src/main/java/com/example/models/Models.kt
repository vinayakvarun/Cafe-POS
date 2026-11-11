package com.example.models

import com.google.firebase.firestore.Exclude

data class BusinessInfo(
    val name: String = "",
    val address: String = "",
    val upiId: String = "",
    val payeeName: String = ""
)

data class MenuItem(
    val id: String = "",
    val category: String = "",
    val name: String = "",
    val price: Double = 0.0,
    val taxType: String = TAX_DEFAULT,
    val customTaxRate: Double = 5.0,
    val imageUrl: String = ""
) {
    companion object {
        const val TAX_DEFAULT = "DEFAULT" // Standard 5% GST
        const val TAX_NO_TAX = "NO_TAX"   // 0% Tax
        const val TAX_CUSTOM = "CUSTOM"   // Custom tax percentage
    }

    @get:Exclude
    val effectiveTaxRate: Double
        get() = when (taxType) {
            TAX_NO_TAX -> 0.0
            TAX_CUSTOM -> if (customTaxRate >= 0.0) customTaxRate else 0.0
            else -> 5.0
        }

    @get:Exclude
    val taxLabel: String
        get() = when (taxType) {
            TAX_NO_TAX -> "No Tax (0%)"
            TAX_CUSTOM -> {
                val rateStr = if (customTaxRate % 1.0 == 0.0) "${customTaxRate.toInt()}%" else "$customTaxRate%"
                "Tax: $rateStr"
            }
            else -> "GST (5%)"
        }
}

data class OrderItem(
    val item: MenuItem = MenuItem(),
    val quantity: Int = 1
) {
    @get:Exclude
    val subtotal: Double
        get() = item.price * quantity

    @get:Exclude
    val taxAmount: Double
        get() = subtotal * (item.effectiveTaxRate / 100.0)

    @get:Exclude
    val totalAmount: Double
        get() = subtotal + taxAmount
}

data class Bill(
    val id: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val customerPhone: String = "",
    val items: List<OrderItem> = emptyList(),
    val subTotal: Double = 0.0,
    val gst: Double = 0.0,
    val total: Double = 0.0,
    val paymentStatus: String = "PAID",
    val utrNumber: String = ""
)

