/**
 * Validation utilities for Prosumer registration, NIC verification, and input constraints.
 */
package com.example.smartsolarmobileapp.utils

/**
 * ValidationUtils provides static utility methods to validate user inputs
 * prior to dispatching network requests to the C# Web API.
 */
object ValidationUtils {

    // Regex for Sri Lankan National Identity Card (NIC):
    // Old format: 9 digits followed by 'V', 'v', 'X', or 'x' (e.g. 991234567V)
    // New format: Exactly 12 numeric digits (e.g. 200012345678)
    private val OLD_NIC_REGEX = Regex("^[0-9]{9}[vVxX]$")
    private val NEW_NIC_REGEX = Regex("^[0-9]{12}$")

    // Standard RFC-compliant email regular expression
    private val EMAIL_REGEX = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

    // Sri Lankan & international phone format (e.g. 0771234567, +94771234567)
    private val PHONE_REGEX = Regex("^(?:\\+94|0)?[0-9]{9,10}$")

    /**
     * Validates whether a provided string conforms to the required Sri Lankan NIC format.
     *
     * @param nic The NIC string to evaluate.
     * @return True if matches either the 9-character or 12-digit format, false otherwise.
     */
    fun isValidNic(nic: String?): Boolean {
        if (nic.isNullOrBlank()) return false
        val trimmed = nic.trim()
        return OLD_NIC_REGEX.matches(trimmed) || NEW_NIC_REGEX.matches(trimmed)
    }

    /**
     * Validates whether a string is a well-formed email address.
     *
     * @param email The email address to check.
     * @return True if valid email format, false otherwise.
     */
    fun isValidEmail(email: String?): Boolean {
        if (email.isNullOrBlank()) return false
        return EMAIL_REGEX.matches(email.trim())
    }

    /**
     * Validates contact phone numbers for mobile registration.
     *
     * @param phone The telephone string.
     * @return True if valid phone format, false otherwise.
     */
    fun isValidPhone(phone: String?): Boolean {
        if (phone.isNullOrBlank()) return false
        return PHONE_REGEX.matches(phone.trim())
    }

    /**
     * Validates that the password meets minimum security requirements (at least 6 characters).
     *
     * @param password The raw password string.
     * @return True if length >= 6, false otherwise.
     */
    fun isValidPassword(password: String?): Boolean {
        if (password == null) return false
        return password.length >= 6
    }
}
