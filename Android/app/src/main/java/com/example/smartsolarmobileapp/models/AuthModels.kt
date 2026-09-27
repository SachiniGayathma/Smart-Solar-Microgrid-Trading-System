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
    val id: String? = null,
    val nic: String? = null,
    val fullName: String? = null,
    val email: String? = null,
    val phone: String? = null,
    val role: String? = null,
    val status: String? = null
) {
    /**
     * Resolves the authenticated user from either a nested user object or the flat Web API payload.
     */
    fun getResolvedUser(): User? {
        if (user != null && user.nic.isNotBlank()) {
            return user
        }
        if (!nic.isNullOrBlank() || !email.isNullOrBlank() || !fullName.isNullOrBlank()) {
            return User(
                id = id,
                nic = nic ?: "",
                fullName = fullName ?: "",
                email = email ?: "",
                phone = phone ?: user?.phone ?: "",
                role = role ?: user?.role ?: "Prosumer",
                status = status ?: user?.status ?: "Pending"
            )
        }
        return user
    }

    fun toUser(): User? = getResolvedUser()
}
