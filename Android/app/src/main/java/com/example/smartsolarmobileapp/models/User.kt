/**
 * Module: SE4040 Enterprise Application Development
 * Author: Person 3 (Solar Prosumer Android Specialist)
 * Purpose: Data model representing a Prosumer / System User with NIC as primary identifier
 * Date: September 2026
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