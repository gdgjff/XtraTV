package com.github.andreyasadchy.xtra.model.id

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class DeviceCodeResponse(
    @SerialName("device_code")
    val deviceCode: String? = null,
    @SerialName("user_code")
    val userCode: String? = null,
    @SerialName("verification_uri")
    val verificationUri: String? = null,
    val interval: Int? = null,
    @SerialName("expires_in")
    val expiresIn: Int? = null,
)