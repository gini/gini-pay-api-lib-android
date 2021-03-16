package net.gini.android.requests

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class PaymentRequest(
    @Json(name = "sourceDocumentLocation") val sourceDocumentLocation: String,
    @Json(name = "paymentProvider") val paymentProvider: String,
    @Json(name = "recipient") val recipient: String,
    @Json(name = "iban") val iban: String,
    @Json(name = "bic") val bic: String,
    @Json(name = "amount") val amount: String,
    @Json(name = "purpose") val purpose: String,
)
