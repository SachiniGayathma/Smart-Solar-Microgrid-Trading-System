/**
 * Data Access Object for User entities in local SQLite database.
 */
package com.example.smartsolarmobileapp.database

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.example.smartsolarmobileapp.models.User

/**
 * UserDao handles all CRUD operations and session queries for User records in SQLite.
 *
 * @property dbHelper Instance of DatabaseHelper used to obtain readable/writable database connections.
 */
class UserDao(private val dbHelper: DatabaseHelper) {

    /**
     * Saves or replaces the currently logged-in prosumer session and auth token in SQLite.
     *
     * @param user The authenticated User instance.
     * @param token The JWT authorization bearer token received from the C# Web API.
     * @return Row ID of the inserted or updated record.
     */
    fun saveUserSession(user: User, token: String?): Long {
        val db: SQLiteDatabase = dbHelper.writableDatabase

        // Clear any previous active login flag
        val clearValues = ContentValues().apply {
            put(DatabaseHelper.COL_USER_IS_LOGGED_IN, 0)
        }
        db.update(DatabaseHelper.TABLE_USERS, clearValues, null, null)

        // Insert or replace the active user session
        val values = ContentValues().apply {
            put(DatabaseHelper.COL_USER_ID, user.id)
            put(DatabaseHelper.COL_USER_NIC, user.nic)
            put(DatabaseHelper.COL_USER_FULL_NAME, user.fullName)
            put(DatabaseHelper.COL_USER_EMAIL, user.email)
            put(DatabaseHelper.COL_USER_PHONE, user.phone)
            put(DatabaseHelper.COL_USER_ROLE, user.role ?: "Prosumer")
            put(DatabaseHelper.COL_USER_STATUS, user.status ?: "Pending")
            put(DatabaseHelper.COL_USER_AUTH_TOKEN, token)
            put(DatabaseHelper.COL_USER_IS_LOGGED_IN, 1)
        }

        return db.insertWithOnConflict(
            DatabaseHelper.TABLE_USERS,
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    /**
     * Retrieves the currently active logged-in User from SQLite.
     *
     * @return Active User instance or null if no user is currently logged in.
     */
    fun getActiveUser(): User? {
        val db: SQLiteDatabase = dbHelper.readableDatabase
        val query = "SELECT * FROM ${DatabaseHelper.TABLE_USERS} WHERE ${DatabaseHelper.COL_USER_IS_LOGGED_IN} = 1 LIMIT 1"
        val cursor: Cursor = db.rawQuery(query, null)

        var user: User? = null
        if (cursor.moveToFirst()) {
            user = extractUserFromCursor(cursor)
        }
        cursor.close()
        return user
    }

    /**
     * Retrieves the JWT bearer token for the currently active prosumer.
     *
     * @return Auth token string or null if none stored.
     */
    fun getAuthToken(): String? {
        val db: SQLiteDatabase = dbHelper.readableDatabase
        val query = "SELECT ${DatabaseHelper.COL_USER_AUTH_TOKEN} FROM ${DatabaseHelper.TABLE_USERS} WHERE ${DatabaseHelper.COL_USER_IS_LOGGED_IN} = 1 LIMIT 1"
        val cursor: Cursor = db.rawQuery(query, null)

        var token: String? = null
        if (cursor.moveToFirst()) {
            val tokenIndex = cursor.getColumnIndex(DatabaseHelper.COL_USER_AUTH_TOKEN)
            if (tokenIndex != -1) {
                token = cursor.getString(tokenIndex)
            }
        }
        cursor.close()
        return token
    }

    /**
     * Updates profile details (Full Name, Email, Phone) for a prosumer identified by NIC.
     *
     * @param nic The immutable National Identity Card number.
     * @param fullName Updated name.
     * @param email Updated email.
     * @param phone Updated phone number.
     * @return Number of rows updated.
     */
    fun updateUserProfile(nic: String, fullName: String, email: String, phone: String): Int {
        val db: SQLiteDatabase = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put(DatabaseHelper.COL_USER_FULL_NAME, fullName)
            put(DatabaseHelper.COL_USER_EMAIL, email)
            put(DatabaseHelper.COL_USER_PHONE, phone)
        }
        return db.update(
            DatabaseHelper.TABLE_USERS,
            values,
            "${DatabaseHelper.COL_USER_NIC} = ?",
            arrayOf(nic)
        )
    }

    /**
     * Updates the lifecycle status of a user (e.g. "Active", "Deactivated", "Pending").
     *
     * @param nic The user's NIC.
     * @param newStatus The updated status value.
     * @return Number of rows updated.
     */
    fun updateUserStatus(nic: String, newStatus: String): Int {
        val db: SQLiteDatabase = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put(DatabaseHelper.COL_USER_STATUS, newStatus)
        }
        return db.update(
            DatabaseHelper.TABLE_USERS,
            values,
            "${DatabaseHelper.COL_USER_NIC} = ?",
            arrayOf(nic)
        )
    }

    /**
     * Logs out the active user by resetting the login flag and clearing the auth token.
     *
     * @return Number of rows updated.
     */
    fun clearUserSession(): Int {
        val db: SQLiteDatabase = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put(DatabaseHelper.COL_USER_IS_LOGGED_IN, 0)
            putNull(DatabaseHelper.COL_USER_AUTH_TOKEN)
        }
        return db.update(DatabaseHelper.TABLE_USERS, values, null, null)
    }

    /**
     * Checks if there is an active session in local storage.
     */
    fun hasActiveSession(): Boolean {
        return getActiveUser() != null
    }

    /**
     * Helper to map an SQLite Cursor row to a User domain model.
     */
    private fun extractUserFromCursor(cursor: Cursor): User {
        val idIndex = cursor.getColumnIndex(DatabaseHelper.COL_USER_ID)
        val nicIndex = cursor.getColumnIndexOrThrow(DatabaseHelper.COL_USER_NIC)
        val nameIndex = cursor.getColumnIndexOrThrow(DatabaseHelper.COL_USER_FULL_NAME)
        val emailIndex = cursor.getColumnIndexOrThrow(DatabaseHelper.COL_USER_EMAIL)
        val phoneIndex = cursor.getColumnIndexOrThrow(DatabaseHelper.COL_USER_PHONE)
        val roleIndex = cursor.getColumnIndex(DatabaseHelper.COL_USER_ROLE)
        val statusIndex = cursor.getColumnIndex(DatabaseHelper.COL_USER_STATUS)

        return User(
            id = if (idIndex != -1) cursor.getString(idIndex) else null,
            nic = cursor.getString(nicIndex),
            fullName = cursor.getString(nameIndex),
            email = cursor.getString(emailIndex),
            phone = cursor.getString(phoneIndex),
            role = if (roleIndex != -1) cursor.getString(roleIndex) else "Prosumer",
            status = if (statusIndex != -1) cursor.getString(statusIndex) else "Pending"
        )
    }
}