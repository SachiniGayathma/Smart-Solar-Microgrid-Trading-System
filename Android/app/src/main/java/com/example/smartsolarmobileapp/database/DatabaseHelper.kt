/**
 * Module: SE4040 Enterprise Application Development
 * Author: Person 3 (Solar Prosumer Android Specialist)
 * Purpose: SQLiteOpenHelper managing local SQLite database creation and schema migrations
 * Date: September 2026
 */
package com.example.smartsolarmobileapp.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * DatabaseHelper manages the local SQLite database for the Smart Solar Mobile Application.
 *
 * Implements local persistence mandated by SE4040:
 * 1. Active prosumer credentials and session persistence
 * 2. Offline caching of energy slot reservations and QR tokens
 * 3. Offline station references
 */
class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        const val DATABASE_NAME = "smart_solar.db"
        const val DATABASE_VERSION = 1

        // Table: Users (Local active session & profile cache)
        const val TABLE_USERS = "users"
        const val COL_USER_ID = "id"
        const val COL_USER_NIC = "nic"
        const val COL_USER_FULL_NAME = "full_name"
        const val COL_USER_EMAIL = "email"
        const val COL_USER_PHONE = "phone"
        const val COL_USER_ROLE = "role"
        const val COL_USER_STATUS = "status"
        const val COL_USER_AUTH_TOKEN = "auth_token"
        const val COL_USER_IS_LOGGED_IN = "is_logged_in"

        // Table: Reservations (Offline booking cache & QR tokens)
        const val TABLE_RESERVATIONS = "reservations"
        const val COL_RES_ID = "id"
        const val COL_RES_PROSUMER_NIC = "prosumer_nic"
        const val COL_RES_STATION_ID = "station_id"
        const val COL_RES_STATION_NAME = "station_name"
        const val COL_RES_SLOT_ID = "slot_id"
        const val COL_RES_SCHEDULED_AT = "scheduled_at"
        const val COL_RES_STATUS = "status"
        const val COL_RES_QR_TOKEN = "qr_token"
        const val COL_RES_SUMMARY = "summary"
        const val COL_RES_CREATED_AT = "created_at"
        const val COL_RES_UPDATED_AT = "updated_at"

        // Table: Stations (Offline station directory cache)
        const val TABLE_STATIONS = "stations"
        const val COL_STATION_ID = "id"
        const val COL_STATION_NAME = "name"
        const val COL_STATION_LATITUDE = "latitude"
        const val COL_STATION_LONGITUDE = "longitude"
        const val COL_STATION_CAPACITY_KWH = "capacity_kwh"
        const val COL_STATION_BATTERY_SLOTS = "battery_storage_slots"
        const val COL_STATION_SCHEDULE = "schedule"
        const val COL_STATION_STATUS = "status"
    }

    /**
     * Executes SQL statements to create all required database tables upon initial database setup.
     */
    override fun onCreate(db: SQLiteDatabase) {
        val createUsersTable = """
            CREATE TABLE $TABLE_USERS (
                $COL_USER_ID TEXT,
                $COL_USER_NIC TEXT PRIMARY KEY,
                $COL_USER_FULL_NAME TEXT NOT NULL,
                $COL_USER_EMAIL TEXT NOT NULL,
                $COL_USER_PHONE TEXT NOT NULL,
                $COL_USER_ROLE TEXT DEFAULT 'Prosumer',
                $COL_USER_STATUS TEXT DEFAULT 'Pending',
                $COL_USER_AUTH_TOKEN TEXT,
                $COL_USER_IS_LOGGED_IN INTEGER DEFAULT 0
            )
        """.trimIndent()

        val createReservationsTable = """
            CREATE TABLE $TABLE_RESERVATIONS (
                $COL_RES_ID TEXT PRIMARY KEY,
                $COL_RES_PROSUMER_NIC TEXT NOT NULL,
                $COL_RES_STATION_ID TEXT NOT NULL,
                $COL_RES_STATION_NAME TEXT,
                $COL_RES_SLOT_ID TEXT NOT NULL,
                $COL_RES_SCHEDULED_AT TEXT NOT NULL,
                $COL_RES_STATUS TEXT NOT NULL,
                $COL_RES_QR_TOKEN TEXT,
                $COL_RES_SUMMARY TEXT,
                $COL_RES_CREATED_AT TEXT,
                $COL_RES_UPDATED_AT TEXT
            )
        """.trimIndent()

        val createStationsTable = """
            CREATE TABLE $TABLE_STATIONS (
                $COL_STATION_ID TEXT PRIMARY KEY,
                $COL_STATION_NAME TEXT NOT NULL,
                $COL_STATION_LATITUDE REAL DEFAULT 0.0,
                $COL_STATION_LONGITUDE REAL DEFAULT 0.0,
                $COL_STATION_CAPACITY_KWH REAL DEFAULT 0.0,
                $COL_STATION_BATTERY_SLOTS INTEGER DEFAULT 0,
                $COL_STATION_SCHEDULE TEXT,
                $COL_STATION_STATUS TEXT DEFAULT 'Active'
            )
        """.trimIndent()

        db.execSQL(createUsersTable)
        db.execSQL(createReservationsTable)
        db.execSQL(createStationsTable)
    }

    /**
     * Handles schema migration when database version is incremented.
     */
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_USERS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_RESERVATIONS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_STATIONS")
        onCreate(db)
    }
}