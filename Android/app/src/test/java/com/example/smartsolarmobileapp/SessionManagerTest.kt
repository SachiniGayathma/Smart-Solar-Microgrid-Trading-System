/**
 * Unit tests validating session persistence, auth token caching, and user lifecycle states.
 */
package com.example.smartsolarmobileapp

import android.content.SharedPreferences
import com.example.smartsolarmobileapp.models.User
import com.example.smartsolarmobileapp.utils.SessionManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SessionManagerTest {

    private lateinit var fakePrefs: FakeSharedPreferences
    private lateinit var sessionManager: SessionManager

    @Before
    fun setUp() {
        fakePrefs = FakeSharedPreferences()
        sessionManager = SessionManager(fakePrefs)
    }

    @Test
    fun initially_isNotLoggedIn() {
        assertFalse("New session should not be logged in", sessionManager.isLoggedIn())
        assertNull("Auth token should be null initially", sessionManager.getAuthToken())
        assertNull("User should be null initially", sessionManager.getUser())
        assertNull("NIC should be null initially", sessionManager.getUserNic())
        assertNull("Full name should be null initially", sessionManager.getUserName())
    }

    @Test
    fun saveSession_persistsUserAndToken() {
        val testUser = User(
            id = "user-12345",
            nic = "200012345678",
            fullName = "Kasun Perera",
            email = "kasun@solar.lk",
            phone = "0771234567",
            role = "Prosumer",
            status = "Active"
        )
        val token = "jwt.test.token"

        sessionManager.saveSession(token, testUser)

        assertTrue("User should be logged in after saving session", sessionManager.isLoggedIn())
        assertEquals("Token must match", token, sessionManager.getAuthToken())
        assertEquals("NIC must match", "200012345678", sessionManager.getUserNic())
        assertEquals("Name must match", "Kasun Perera", sessionManager.getUserName())

        val retrievedUser = sessionManager.getUser()
        assertNotNull("Retrieved user should not be null", retrievedUser)
        assertEquals("User ID must match", "user-12345", retrievedUser?.id)
        assertEquals("User NIC must match", "200012345678", retrievedUser?.nic)
        assertEquals("User Name must match", "Kasun Perera", retrievedUser?.fullName)
        assertEquals("User Email must match", "kasun@solar.lk", retrievedUser?.email)
        assertEquals("User Phone must match", "0771234567", retrievedUser?.phone)
        assertEquals("User Role must match", "Prosumer", retrievedUser?.role)
        assertEquals("User Status must match", "Active", retrievedUser?.status)
    }

    @Test
    fun clearSession_removesAllSessionData() {
        val testUser = User(
            nic = "951234567V",
            fullName = "Nimal Silva",
            email = "nimal@solar.lk",
            phone = "0719876543"
        )
        sessionManager.saveSession("token-xyz", testUser)
        assertTrue(sessionManager.isLoggedIn())

        sessionManager.clearSession()

        assertFalse("Session should be logged out after clearSession", sessionManager.isLoggedIn())
        assertNull("Token should be cleared", sessionManager.getAuthToken())
        assertNull("User should be null after clearing session", sessionManager.getUser())
        assertNull("NIC should be cleared", sessionManager.getUserNic())
        assertNull("Name should be cleared", sessionManager.getUserName())
    }

    @Test
    fun saveSession_updatesExistingSessionDetails() {
        val initialUser = User(
            nic = "200012345678",
            fullName = "Kasun Perera",
            email = "kasun@solar.lk",
            phone = "0771234567"
        )
        sessionManager.saveSession("token-1", initialUser)

        val updatedUser = initialUser.copy(
            fullName = "Kasun P. Fernando",
            phone = "0779998888"
        )
        sessionManager.saveSession("token-2", updatedUser)

        assertEquals("Token should be updated", "token-2", sessionManager.getAuthToken())
        assertEquals("Name should be updated", "Kasun P. Fernando", sessionManager.getUserName())
        assertEquals("Phone should be updated", "0779998888", sessionManager.getUser()?.phone)
    }

    /**
     * In-memory test double for SharedPreferences allowing hermetic JVM testing.
     */
    private class FakeSharedPreferences : SharedPreferences {
        val values = mutableMapOf<String, Any?>()

        override fun getAll(): MutableMap<String, *> = values
        override fun getString(key: String?, defValue: String?): String? = values[key] as? String ?: defValue
        @Suppress("UNCHECKED_CAST")
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? = values[key] as? MutableSet<String> ?: defValues
        override fun getInt(key: String?, defValue: Int): Int = values[key] as? Int ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = values[key] as? Long ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = values[key] as? Float ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = values[key] as? Boolean ?: defValue
        override fun contains(key: String?): Boolean = values.containsKey(key)
        override fun edit(): SharedPreferences.Editor = FakeEditor(this)
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

        class FakeEditor(private val parent: FakeSharedPreferences) : SharedPreferences.Editor {
            private val temp = mutableMapOf<String, Any?>()
            private var clearRequested = false

            override fun putString(key: String?, value: String?): SharedPreferences.Editor {
                key?.let { temp[it] = value }
                return this
            }
            override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor {
                key?.let { temp[it] = values }
                return this
            }
            override fun putInt(key: String?, value: Int): SharedPreferences.Editor {
                key?.let { temp[it] = value }
                return this
            }
            override fun putLong(key: String?, value: Long): SharedPreferences.Editor {
                key?.let { temp[it] = value }
                return this
            }
            override fun putFloat(key: String?, value: Float): SharedPreferences.Editor {
                key?.let { temp[it] = value }
                return this
            }
            override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor {
                key?.let { temp[it] = value }
                return this
            }
            override fun remove(key: String?): SharedPreferences.Editor {
                key?.let { temp.remove(it); parent.values.remove(it) }
                return this
            }
            override fun clear(): SharedPreferences.Editor {
                clearRequested = true
                return this
            }
            override fun commit(): Boolean {
                apply()
                return true
            }
            override fun apply() {
                if (clearRequested) {
                    parent.values.clear()
                    clearRequested = false
                }
                parent.values.putAll(temp)
                temp.clear()
            }
        }
    }
}
