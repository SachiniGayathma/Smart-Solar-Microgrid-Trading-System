/**
 * Module: SE4040 Enterprise Application Development
 * Author: Person 3 (Solar Prosumer Android Specialist)
 * Purpose: Data model representing a 30-minute bookable time window at a solar station
 * Date: September 2026
 */
package com.example.smartsolarmobileapp.models

/**
 * Slot data model representing an energy trading window bookable within 7 days.
 *
 * @property id Unique slot identifier string
 * @property stationId Reference ID of the associated solar microgrid station
 * @property startTime ISO 8601 formatted start timestamp (e.g. "2026-10-01T10:00:00Z")
 * @property endTime ISO 8601 formatted end timestamp (e.g. "2026-10-01T10:30:00Z")
 * @property availableCapacity Remaining battery slot capacity available for reservation
 * @property status Availability status ("Available", "Full")
 */
data class Slot(
    val id: String,
    val stationId: String,
    val startTime: String,
    val endTime: String,
    val availableCapacity: Int = 1,
    val status: String = "Available"
)
