/**
 * Manages user session state, authentication tokens, and user credentials.
 */
package com.example.smartsolarmobileapp.utils

import android.content.Context
import android.content.SharedPreferences
import com.example.smartsolarmobileapp.api.ApiClient
import com.example.smartsolarmobileapp.models.User

class SessionManager(private val prefs: SharedPreferences) {

    companion object {
        const val PREF_NAME = "smart_solar_session"
        private const val KEY_IS_LOGGED_IN = "is_logged_in"
        private const val KEY_AUTH_TOKEN = "auth_token"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USER_NIC = "user_nic"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_USER_EMAIL = "user_email"
        private const val KEY_USER_PHONE = "user_phone"
        private const val KEY_USER_ROLE = "user_role"
        private const val KEY_USER_STATUS = "user_status"
    }

    constructor(context: Context) : this(
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    )

    init {
        // Sync token with ApiClient on initialization if logged in
        val token = getAuthToken()
        if (!token.isNullOrBlank()) {
            ApiClient.setAuthToken(token)
        }
    }

    /**
     * Persists the active user session and auth token.
     */
    fun saveSession(token: String?, user: User) {
        prefs.edit().apply {
            putBoolean(KEY_IS_LOGGED_IN, true)
            putString(KEY_AUTH_TOKEN, token)
            putString(KEY_USER_ID, user.id)
            putString(KEY_USER_NIC, user.nic)
            putString(KEY_USER_NAME, user.fullName)
            putString(KEY_USER_EMAIL, user.email)
            putString(KEY_USER_PHONE, user.phone)
            putString(KEY_USER_ROLE, user.role ?: "Prosumer")
            putString(KEY_USER_STATUS, user.status ?: "Active")
            apply()
        }
        ApiClient.setAuthToken(token)
    }

    /**
     * Retrieves the stored JWT authentication token.
     */
    fun getAuthToken(): String? {
        return prefs.getString(KEY_AUTH_TOKEN, null)
    }

    /**
     * Checks if a user is currently authenticated and has an active session.
     */
    fun isLoggedIn(): Boolean {
        return prefs.getBoolean(KEY_IS_LOGGED_IN, false)
    }

    /**
     * Reconstructs the active User object from session storage.
     */
    fun getUser(): User? {
        if (!isLoggedIn()) return null
        val nic = prefs.getString(KEY_USER_NIC, null) ?: return null
        return User(
            id = prefs.getString(KEY_USER_ID, null),
            nic = nic,
            fullName = prefs.getString(KEY_USER_NAME, "") ?: "",
            email = prefs.getString(KEY_USER_EMAIL, "") ?: "",
            phone = prefs.getString(KEY_USER_PHONE, "") ?: "",
            role = prefs.getString(KEY_USER_ROLE, "Prosumer"),
            status = prefs.getString(KEY_USER_STATUS, "Active")
        )
    }

    /**
     * Returns the NIC of the currently logged-in prosumer.
     */
    fun getUserNic(): String? {
        return prefs.getString(KEY_USER_NIC, null)
    }

    /**
     * Returns the full name of the currently logged-in prosumer.
     */
    fun getUserName(): String? {
        return prefs.getString(KEY_USER_NAME, null)
    }

    /**
     * Clears all session credentials upon logout.
     */
    fun clearSession() {
        prefs.edit().clear().apply()
        ApiClient.setAuthToken(null)
    }
}