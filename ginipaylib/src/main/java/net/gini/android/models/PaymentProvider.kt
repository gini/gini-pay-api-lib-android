package net.gini.android.models

import net.gini.android.response.PaymentProviderResponse

/**
 * A payment provider is a Gini partner which integrated the GiniPay for Banks SDK into their mobile apps.
 */
data class PaymentProvider(
    val id: String,
    val name: String,
    /**
     * The minimal required app versions per platform
     */
    val appVersion: String,
)

internal fun PaymentProviderResponse.toPaymentProvider() = PaymentProvider(
    id, name, minAppVersion.android
)