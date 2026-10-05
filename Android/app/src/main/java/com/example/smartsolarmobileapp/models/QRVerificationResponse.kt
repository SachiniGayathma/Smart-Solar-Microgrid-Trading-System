/**
 * Result of verifying a prosumer QR code against the Web API.
 */
package com.example.smartsolarmobileapp.models

data class QRVerificationResponse(
    val verified: Boolean,
    val message: String,
    val reservation: Reservation? = null
)
