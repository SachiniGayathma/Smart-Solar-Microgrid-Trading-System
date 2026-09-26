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
 * Supports both central Web API flat response format and nested user object.
 */
data class AuthResponse(
    val success: Boolean = true,
    val message: String? = null,
    val token: String? = null,
    val user: User? = null,
    // Flat fields returned directly by central C# Web API
    val id: String? = null,
    val nic: String? = null,
    val fullName: String? = null,
    val email: String? = null,
    val phone: String? = null,
    val role: String? = null,
    val status: String? = null
) {
    /**
     * Resolves the authenticated user whether returned in a nested 'user' object or flat at top-level.
     */
    fun getResolvedUser(): User? {
        if (user != null) return user
        if (!nic.isNullOrBlank() || !email.isNullOrBlank() || !fullName.isNullOrBlank()) {
            return User(
                id = id,
                nic = nic ?: "",
                fullName = fullName ?: "",
                email = email ?: "",
                phone = phone ?: "",
                role = role ?: "Prosumer",
                status = status ?: "Pending"
            )
        }
        return null
    }
}
