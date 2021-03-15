package net.gini.android.response

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class PaymentProviderResponse(
    @Json(name = "id") val id: String,
    @Json(name = "name") val name: String,
    @Json(name = "minAppVersion") val minAppVersion: AppVersionResponse,
)

@JsonClass(generateAdapter = true)
data class AppVersionResponse(
    @Json(name = "android") val android: String,
)