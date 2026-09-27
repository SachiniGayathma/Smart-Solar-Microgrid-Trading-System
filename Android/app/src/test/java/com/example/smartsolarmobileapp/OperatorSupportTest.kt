package com.example.smartsolarmobileapp

import com.example.smartsolarmobileapp.models.AuthResponse
import com.example.smartsolarmobileapp.utils.GeoUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OperatorSupportTest {

    @Test
    fun loginPayloadMapsFlatApiFieldsOntoUser() {
        val response = AuthResponse(
            token = "jwt",
            id = "abc",
            nic = "199812345V",
            fullName = "Grid Operator",
            email = "operator@smartsolar.local",
            role = "GridOperator",
            status = "Active"
        )

        val user = response.toUser()
        assertEquals("199812345V", user?.nic)
        assertEquals("GridOperator", user?.role)
        assertTrue(com.example.smartsolarmobileapp.utils.RoleRouter.isOperatorRole("Operator"))
        assertTrue(com.example.smartsolarmobileapp.utils.RoleRouter.isOperatorRole("GridOperator"))
        assertEquals("Active", user?.status)
    }

    @Test
    fun loginPayloadWithoutNicIsRejected() {
        assertNull(AuthResponse(token = "jwt").toUser())
    }

    @Test
    fun nearbyDistanceIsShortInsideColombo() {
        val km = GeoUtils.distanceKm(6.9271, 79.8612, 6.8649, 79.8997)
        assertTrue(km in 5.0..15.0)
    }
}
