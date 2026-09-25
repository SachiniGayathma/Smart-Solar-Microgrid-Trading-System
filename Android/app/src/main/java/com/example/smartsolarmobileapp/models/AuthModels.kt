/**
 * Request and response data transfer objects for authentication workflows.
 */
package com.example.smartsolarmobileapp.models

/**
 * Payload sent to register a new solar prosumer account.
 */
data class RegisterRequest(
    val nic: String,
    val fullName: String,
    val email: String,
    val phone: String,
    val password: String,
    val role: String = "Prosumer"
)

/**
 * Payload sent to authenticate an existing user.
 */
data class LoginRequest(
    val identifier: String, // NIC or Email
    val password: String
)

/**
 * Response payload returned from the authentication service.
 */
data class AuthResponse(
    val success: Boolean = true,
    val message: String? = null,
    val token: String? = null,
    val user: User? = null
)
