package net.taler.merchantpos.payment

import net.taler.merchantpos.order.Order

data class Payment(
    val order: Order,
    val summary: String,
    val currency: String,
    val orderId: String? = null,
    val talerPayUri: String? = null,
    val paid: Boolean = false,
    val error: Boolean = false
)
