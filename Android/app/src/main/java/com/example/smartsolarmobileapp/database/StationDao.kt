/**
 * Data Access Object for Station entities in local SQLite database.
 */
package com.example.smartsolarmobileapp.database

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.example.smartsolarmobileapp.models.Station

class StationDao(private val dbHelper: DatabaseHelper) {

    /**
     * Saves or replaces a list of stations into the local SQLite table.
     */
    fun insertOrUpdateStations(stations: List<Station>): Int {
        val db: SQLiteDatabase = dbHelper.writableDatabase
        var count = 0
        db.beginTransaction()
        try {
            for (station in stations) {
                val values = ContentValues().apply {
                    put(DatabaseHelper.COL_STATION_ID, station.id)
                    put(DatabaseHelper.COL_STATION_NAME, station.name)
                    put(DatabaseHelper.COL_STATION_LATITUDE, station.latitude)
                    put(DatabaseHelper.COL_STATION_LONGITUDE, station.longitude)
                    put(DatabaseHelper.COL_STATION_CAPACITY_KWH, station.capacityKwh)
                    put(DatabaseHelper.COL_STATION_BATTERY_SLOTS, station.batteryStorageSlots)
                    put(DatabaseHelper.COL_STATION_SCHEDULE, station.schedule)
                    put(DatabaseHelper.COL_STATION_STATUS, station.status)
                }
                val result = db.insertWithOnConflict(
                    DatabaseHelper.TABLE_STATIONS,
                    null,
                    values,
                    SQLiteDatabase.CONFLICT_REPLACE
                )
                if (result != -1L) count++
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return count
    }

    /**
     * Retrieves all active charging stations sorted alphabetically by name.
     */
    fun getActiveStations(): List<Station> {
        val db: SQLiteDatabase = dbHelper.readableDatabase
        val list = mutableListOf<Station>()
        val cursor: Cursor = db.query(
            DatabaseHelper.TABLE_STATIONS,
            null,
            "${DatabaseHelper.COL_STATION_STATUS} = ?",
            arrayOf("Active"),
            null,
            null,
            "${DatabaseHelper.COL_STATION_NAME} ASC"
        )
        while (cursor.moveToNext()) {
            list.add(extractStationFromCursor(cursor))
        }
        cursor.close()
        return list
    }

    /**
     * Retrieves a single station by its unique ID.
     */
    fun getStationById(id: String): Station? {
        val db: SQLiteDatabase = dbHelper.readableDatabase
        val cursor: Cursor = db.query(
            DatabaseHelper.TABLE_STATIONS,
            null,
            "${DatabaseHelper.COL_STATION_ID} = ?",
            arrayOf(id),
            null,
            null,
            null
        )
        var station: Station? = null
        if (cursor.moveToFirst()) {
            station = extractStationFromCursor(cursor)
        }
        cursor.close()
        return station
    }

    private fun extractStationFromCursor(cursor: Cursor): Station {
        val idIndex = cursor.getColumnIndexOrThrow(DatabaseHelper.COL_STATION_ID)
        val nameIndex = cursor.getColumnIndexOrThrow(DatabaseHelper.COL_STATION_NAME)
        val latIndex = cursor.getColumnIndexOrThrow(DatabaseHelper.COL_STATION_LATITUDE)
        val lngIndex = cursor.getColumnIndexOrThrow(DatabaseHelper.COL_STATION_LONGITUDE)
        val capIndex = cursor.getColumnIndexOrThrow(DatabaseHelper.COL_STATION_CAPACITY_KWH)
        val slotsIndex = cursor.getColumnIndexOrThrow(DatabaseHelper.COL_STATION_BATTERY_SLOTS)
        val schedIndex = cursor.getColumnIndexOrThrow(DatabaseHelper.COL_STATION_SCHEDULE)
        val statusIndex = cursor.getColumnIndexOrThrow(DatabaseHelper.COL_STATION_STATUS)

        return Station(
            id = cursor.getString(idIndex),
            name = cursor.getString(nameIndex),
            latitude = cursor.getDouble(latIndex),
            longitude = cursor.getDouble(lngIndex),
            capacityKwh = cursor.getDouble(capIndex),
            batteryStorageSlots = cursor.getInt(slotsIndex),
            schedule = cursor.getString(schedIndex) ?: "08:00 - 18:00",
            status = cursor.getString(statusIndex) ?: "Active"
        )
    }
}
