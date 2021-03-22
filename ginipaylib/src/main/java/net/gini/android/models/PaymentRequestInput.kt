package net.gini.android.models

data class PaymentRequestInput(
    val paymentProvider: String,
    val recipient: String,
    val iban: String,
    val bic: String,
    val amount: String,
    val purpose: String,
    val sourceDocumentLocation: String? = null,
)
