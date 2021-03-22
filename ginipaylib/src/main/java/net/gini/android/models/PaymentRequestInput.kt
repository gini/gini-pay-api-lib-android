package net.gini.android.models

data class PaymentRequestInput(
    val sourceDocumentLocation: String,
    val paymentProvider: String,
    val recipient: String,
    val iban: String,
    val bic: String,
    val amount: String,
    val purpose: String,
)
