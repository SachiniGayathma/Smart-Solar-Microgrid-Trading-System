/**
 * Module: SE4040 Enterprise Application Development
 * Author: Person 3 (Solar Prosumer Android Specialist)
 * Purpose: Data model representing a Community Microgrid Battery Charging Hub
 * Date: September 2026
 */
package com.example.smartsolarmobileapp.models

/**
 * Station data model representing a solar microgrid node with battery storage capacity.
 *
 * @property id MongoDB ObjectId string of the station
 * @property name Human-readable hub name (e.g. "Colombo Central Microgrid Hub")
 * @property latitude GPS latitude coordinate
 * @property longitude GPS longitude coordinate
 * @property capacityKwh Maximum solar storage capacity in kiloWatt-hours
 * @property batteryStorageSlots Total physical slots for battery charging/drop-off
 * @property schedule Operating hours (e.g. "08:00 - 18:00")
 * @property status Operational state ("Active" or "Deactivated")
 */
data class Station(
    val id: String,
    val name: String,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val capacityKwh: Double = 0.0,
    val batteryStorageSlots: Int = 0,
    val schedule: String = "08:00 - 18:00",
    val status: String = "Active"
)
