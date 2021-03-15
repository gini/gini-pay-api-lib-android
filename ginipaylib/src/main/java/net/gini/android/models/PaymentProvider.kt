package net.gini.android.models

import net.gini.android.response.PaymentProviderResponse

data class PaymentProvider(
    val id: String,
    val name: String,
    val appVersion: String,
)

fun PaymentProviderResponse.toPaymentProvider() = PaymentProvider(
    id, name, minAppVersion.android
)