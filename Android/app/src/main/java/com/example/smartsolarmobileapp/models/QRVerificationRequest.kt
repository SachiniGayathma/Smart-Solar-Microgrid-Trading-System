/**
 * Body for POST /api/reservations/verify-qr.
 */
package com.example.smartsolarmobileapp.models

data class QRVerificationRequest(
    val qrToken: String
)
