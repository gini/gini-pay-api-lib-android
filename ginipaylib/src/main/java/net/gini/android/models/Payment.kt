package net.gini.android.models

import net.gini.android.response.PaymentResponse

data class Payment(
    val paidAt: String,
    val recipient: String,
    val iban: String,
    val bic: String,
    val amount: String,
    val purpose: String,
)

internal fun PaymentResponse.toPayment() = Payment(
    paidAt, recipient, iban, bic, amount, purpose
)