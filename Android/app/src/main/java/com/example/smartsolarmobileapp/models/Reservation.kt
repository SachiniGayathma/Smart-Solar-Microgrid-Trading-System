/**
 * Module: SE4040 Enterprise Application Development
 * Author: Person 3 (Solar Prosumer Android Specialist)
 * Purpose: Data model representing a Prosumer energy slot booking and QR code payload
 * Date: September 2026
 */
package com.example.smartsolarmobileapp.models

/**
 * Reservation data model representing a solar energy drop-off or charging booking.
 *
 * @property id Unique reservation identifier string from server
 * @property prosumerNic NIC of the booking prosumer
 * @property stationId ID of the target charging station
 * @property stationName Display name of the charging station
 * @property slotId ID of the booked 30-minute time slot
 * @property scheduledAt ISO 8601 timestamp of the scheduled booking
 * @property status Lifecycle state: "Pending", "Approved", "Cancelled", "Completed"
 * @property qrToken Cryptographically secure token generated upon approval for QR scanning
 * @property summary Human-readable summary generated after booking actions
 * @property createdAt Record creation timestamp
 * @property updatedAt Last modification timestamp
 */
data class Reservation(
    val id: String? = null,
    val prosumerNic: String,
    val stationId: String,
    val stationName: String? = null,
    val slotId: String,
    val scheduledAt: String,
    val status: String = "Pending",
    val qrToken: String? = null,
    val summary: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null
)