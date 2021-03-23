package net.gini.android.requests

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import net.gini.android.models.PaymentRequestInput
import net.gini.android.models.ResolvePaymentInput

@JsonClass(generateAdapter = true)
internal data class ResolvePaymentBody(
    @Json(name = "recipient") val recipient: String,
    @Json(name = "iban") val iban: String,
    @Json(name = "bic") val bic: String,
    @Json(name = "amount") val amount: String,
    @Json(name = "purpose") val purpose: String,
)

internal fun ResolvePaymentInput.toResolvePaymentBody() = ResolvePaymentBody(
    recipient, iban, bic, amount, purpose
)