/**
 * Module: SE4040 Enterprise Application Development
 * Author: Person 3 (Solar Prosumer Android Specialist)
 * Purpose: Data Access Object for Reservation entities in local SQLite database
 * Date: September 2026
 */
package com.example.smartsolarmobileapp.database

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.example.smartsolarmobileapp.models.Reservation

/**
 * ReservationDao provides offline caching, CRUD operations, and metric calculations
 * for prosumer energy slot reservations stored in SQLite.
 *
 * @property dbHelper Instance of DatabaseHelper for SQLite access.
 */
class ReservationDao(private val dbHelper: DatabaseHelper) {

    /**
     * Inserts or replaces a single Reservation record in SQLite.
     *
     * @param reservation The Reservation instance to cache.
     * @return Row ID of the inserted record.
     */
    fun insertOrUpdateReservation(reservation: Reservation): Long {
        val db: SQLiteDatabase = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put(DatabaseHelper.COL_RES_ID, reservation.id ?: java.util.UUID.randomUUID().toString())
            put(DatabaseHelper.COL_RES_PROSUMER_NIC, reservation.prosumerNic)
            put(DatabaseHelper.COL_RES_STATION_ID, reservation.stationId)
            put(DatabaseHelper.COL_RES_STATION_NAME, reservation.stationName)
            put(DatabaseHelper.COL_RES_SLOT_ID, reservation.slotId)
            put(DatabaseHelper.COL_RES_SCHEDULED_AT, reservation.scheduledAt)
            put(DatabaseHelper.COL_RES_STATUS, reservation.status)
            put(DatabaseHelper.COL_RES_QR_TOKEN, reservation.qrToken)
            put(DatabaseHelper.COL_RES_SUMMARY, reservation.summary)
            put(DatabaseHelper.COL_RES_CREATED_AT, reservation.createdAt)
            put(DatabaseHelper.COL_RES_UPDATED_AT, reservation.updatedAt)
        }

        return db.insertWithOnConflict(
            DatabaseHelper.TABLE_RESERVATIONS,
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    /**
     * Batch inserts or updates a list of reservations within a single atomic database transaction.
     *
     * @param reservations List of Reservation records fetched from C# Web API.
     */
    fun insertOrUpdateReservations(reservations: List<Reservation>) {
        val db: SQLiteDatabase = dbHelper.writableDatabase
        db.beginTransaction()
        try {
            for (res in reservations) {
                insertOrUpdateReservation(res)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /**
     * Retrieves a single reservation by its unique ID.
     */
    fun getReservationById(id: String): Reservation? {
        val db: SQLiteDatabase = dbHelper.readableDatabase
        val cursor: Cursor = db.query(
            DatabaseHelper.TABLE_RESERVATIONS,
            null,
            "${DatabaseHelper.COL_RES_ID} = ?",
            arrayOf(id),
            null,
            null,
            null
        )

        var reservation: Reservation? = null
        if (cursor.moveToFirst()) {
            reservation = extractReservationFromCursor(cursor)
        }
        cursor.close()
        return reservation
    }

    /**
     * Retrieves all reservations belonging to a prosumer identified by NIC, sorted descending by scheduled time.
     */
    fun getReservationsByProsumer(nic: String): List<Reservation> {
        val db: SQLiteDatabase = dbHelper.readableDatabase
        val list = mutableListOf<Reservation>()
        val cursor: Cursor = db.query(
            DatabaseHelper.TABLE_RESERVATIONS,
            null,
            "${DatabaseHelper.COL_RES_PROSUMER_NIC} = ?",
            arrayOf(nic),
            null,
            null,
            "${DatabaseHelper.COL_RES_SCHEDULED_AT} DESC"
        )

        while (cursor.moveToNext()) {
            list.add(extractReservationFromCursor(cursor))
        }
        cursor.close()
        return list
    }

    /**
     * Retrieves reservations filtered by one or more lifecycle statuses (e.g. listOf("Pending", "Approved")).
     */
    fun getReservationsByStatus(nic: String, statuses: List<String>): List<Reservation> {
        if (statuses.isEmpty()) return emptyList()
        val db: SQLiteDatabase = dbHelper.readableDatabase
        val list = mutableListOf<Reservation>()

        val placeholders = statuses.joinToString(",") { "?" }
        val selection = "${DatabaseHelper.COL_RES_PROSUMER_NIC} = ? AND ${DatabaseHelper.COL_RES_STATUS} IN ($placeholders)"
        val args = arrayOf(nic, *statuses.toTypedArray())

        val cursor: Cursor = db.query(
            DatabaseHelper.TABLE_RESERVATIONS,
            null,
            selection,
            args,
            null,
            null,
            "${DatabaseHelper.COL_RES_SCHEDULED_AT} DESC"
        )

        while (cursor.moveToNext()) {
            list.add(extractReservationFromCursor(cursor))
        }
        cursor.close()
        return list
    }

    /**
     * Updates the status of a specific reservation (e.g. from "Approved" to "Cancelled").
     */
    fun updateReservationStatus(id: String, newStatus: String): Int {
        val db: SQLiteDatabase = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put(DatabaseHelper.COL_RES_STATUS, newStatus)
            put(DatabaseHelper.COL_RES_UPDATED_AT, java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).format(java.util.Date()))
        }
        return db.update(
            DatabaseHelper.TABLE_RESERVATIONS,
            values,
            "${DatabaseHelper.COL_RES_ID} = ?",
            arrayOf(id)
        )
    }

    /**
     * Returns the count of pending reservations for dashboard metrics.
     */
    fun getPendingCount(nic: String): Int {
        val db: SQLiteDatabase = dbHelper.readableDatabase
        val cursor: Cursor = db.rawQuery(
            "SELECT COUNT(*) FROM ${DatabaseHelper.TABLE_RESERVATIONS} WHERE ${DatabaseHelper.COL_RES_PROSUMER_NIC} = ? AND ${DatabaseHelper.COL_RES_STATUS} = 'Pending'",
            arrayOf(nic)
        )
        var count = 0
        if (cursor.moveToFirst()) {
            count = cursor.getInt(0)
        }
        cursor.close()
        return count
    }

    /**
     * Returns the count of approved future reservations for dashboard metrics.
     */
    fun getApprovedFutureCount(nic: String): Int {
        val db: SQLiteDatabase = dbHelper.readableDatabase
        val cursor: Cursor = db.rawQuery(
            "SELECT COUNT(*) FROM ${DatabaseHelper.TABLE_RESERVATIONS} WHERE ${DatabaseHelper.COL_RES_PROSUMER_NIC} = ? AND ${DatabaseHelper.COL_RES_STATUS} = 'Approved'",
            arrayOf(nic)
        )
        var count = 0
        if (cursor.moveToFirst()) {
            count = cursor.getInt(0)
        }
        cursor.close()
        return count
    }

    /**
     * Deletes a reservation from local cache.
     */
    fun deleteReservation(id: String): Int {
        val db: SQLiteDatabase = dbHelper.writableDatabase
        return db.delete(
            DatabaseHelper.TABLE_RESERVATIONS,
            "${DatabaseHelper.COL_RES_ID} = ?",
            arrayOf(id)
        )
    }

    /**
     * Helper to map an SQLite Cursor row into a Reservation instance.
     */
    private fun extractReservationFromCursor(cursor: Cursor): Reservation {
        val idIndex = cursor.getColumnIndexOrThrow(DatabaseHelper.COL_RES_ID)
        val nicIndex = cursor.getColumnIndexOrThrow(DatabaseHelper.COL_RES_PROSUMER_NIC)
        val stationIdIndex = cursor.getColumnIndexOrThrow(DatabaseHelper.COL_RES_STATION_ID)
        val stationNameIndex = cursor.getColumnIndex(DatabaseHelper.COL_RES_STATION_NAME)
        val slotIdIndex = cursor.getColumnIndexOrThrow(DatabaseHelper.COL_RES_SLOT_ID)
        val scheduledAtIndex = cursor.getColumnIndexOrThrow(DatabaseHelper.COL_RES_SCHEDULED_AT)
        val statusIndex = cursor.getColumnIndexOrThrow(DatabaseHelper.COL_RES_STATUS)
        val qrTokenIndex = cursor.getColumnIndex(DatabaseHelper.COL_RES_QR_TOKEN)
        val summaryIndex = cursor.getColumnIndex(DatabaseHelper.COL_RES_SUMMARY)
        val createdAtIndex = cursor.getColumnIndex(DatabaseHelper.COL_RES_CREATED_AT)
        val updatedAtIndex = cursor.getColumnIndex(DatabaseHelper.COL_RES_UPDATED_AT)

        return Reservation(
            id = cursor.getString(idIndex),
            prosumerNic = cursor.getString(nicIndex),
            stationId = cursor.getString(stationIdIndex),
            stationName = if (stationNameIndex != -1) cursor.getString(stationNameIndex) else null,
            slotId = cursor.getString(slotIdIndex),
            scheduledAt = cursor.getString(scheduledAtIndex),
            status = cursor.getString(statusIndex),
            qrToken = if (qrTokenIndex != -1) cursor.getString(qrTokenIndex) else null,
            summary = if (summaryIndex != -1) cursor.getString(summaryIndex) else null,
            createdAt = if (createdAtIndex != -1) cursor.getString(createdAtIndex) else null,
            updatedAt = if (updatedAtIndex != -1) cursor.getString(updatedAtIndex) else null
        )
    }
}