/**
 * Unit tests verifying domain model integrity and status lifecycle transitions.
 */
package com.example.smartsolarmobileapp

import com.example.smartsolarmobileapp.models.Reservation
import com.example.smartsolarmobileapp.models.Slot
import com.example.smartsolarmobileapp.models.Station
import com.example.smartsolarmobileapp.models.User
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * ModelDataTest verifies that domain models have correct default values and immutability properties.
 */
class ModelDataTest {

    @Test
    fun userModel_defaultRoleAndStatus_areCorrect() {
        val user = User(
            nic = "200012345678",
            fullName = "Sarah Perera",
            email = "sarah@solar.lk",
            phone = "0771234567"
        )

        assertEquals("200012345678", user.nic)
        assertEquals("Sarah Perera", user.fullName)
        assertEquals("Prosumer", user.role)
        assertEquals("Pending", user.status)
        assertNull(user.id)
    }

    @Test
    fun stationModel_defaultStatus_isActive() {
        val station = Station(
            id = "station-001",
            name = "Colombo Central Microgrid Hub",
            capacityKwh = 150.0,
            batteryStorageSlots = 8
        )

        assertEquals("Active", station.status)
        assertEquals("08:00 - 18:00", station.schedule)
        assertEquals(8, station.batteryStorageSlots)
    }

    @Test
    fun slotModel_defaultCapacityAndStatus() {
        val slot = Slot(
            id = "slot-101",
            stationId = "station-001",
            startTime = "2026-10-01T09:00:00Z",
            endTime = "2026-10-01T09:30:00Z"
        )

        assertEquals(1, slot.availableCapacity)
        assertEquals("Available", slot.status)
    }

    @Test
    fun reservationModel_lifecycleTransition_worksViaCopy() {
        // Initial state created by prosumer
        val initialBooking = Reservation(
            id = "res-9001",
            prosumerNic = "200012345678",
            stationId = "station-001",
            stationName = "Colombo Central Hub",
            slotId = "slot-101",
            scheduledAt = "2026-10-01T09:00:00Z"
        )
        assertEquals("Pending", initialBooking.status)
        assertNull(initialBooking.qrToken)

        // After Backoffice / Grid Operator approval, status becomes "Approved" and qrToken is issued
        val approvedBooking = initialBooking.copy(
            status = "Approved",
            qrToken = "SECURE_QR_TOKEN_ABC123"
        )
        assertEquals("Approved", approvedBooking.status)
        assertNotNull(approvedBooking.qrToken)

        // If cancelled by prosumer (>= 12h rule)
        val cancelledBooking = approvedBooking.copy(
            status = "Cancelled"
        )
        assertEquals("Cancelled", cancelledBooking.status)
    }
}
