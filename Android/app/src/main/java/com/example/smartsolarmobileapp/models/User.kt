/**
 * Data model representing a Prosumer / System User with NIC as primary identifier.
 */
package com.example.smartsolarmobileapp.models

/**
 * User data model mapped to MongoDB 'Users' collection and local SQLite 'users' table.
 *
 * @property id MongoDB ObjectId string (nullable for new registrations)
 * @property nic National Identity Card number used as primary business key
 * @property fullName Full legal name of the prosumer
 * @property email Registered email address
 * @property phone Contact telephone number
 * @property role Role designation ("Prosumer", "Backoffice", "GridOperator")
 * @property status Account lifecycle status ("Pending", "Active", "Deactivated")
 */
data class User(
    val id: String? = null,
    val nic: String,
    val fullName: String,
    val email: String,
    val phone: String,
    val role: String? = "Prosumer",
    val status: String? = "Pending"
)

/**
 * Request payload for updating prosumer profile details.
 *
 * @property fullName Updated full name of the prosumer
 * @property email Updated email address
 * @property phone Updated contact phone number
 */
data class UpdateProfileRequest(
    val fullName: String,
    val email: String,
    val phone: String
)