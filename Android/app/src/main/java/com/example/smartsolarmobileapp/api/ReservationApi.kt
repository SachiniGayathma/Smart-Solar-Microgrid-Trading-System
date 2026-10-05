/**
 * Retrofit API interface for energy reservation lifecycle management.
 */
package com.example.smartsolarmobileapp.api

import com.example.smartsolarmobileapp.models.QRVerificationRequest
import com.example.smartsolarmobileapp.models.Reservation
import com.example.smartsolarmobileapp.models.ReservationDashboard
import com.example.smartsolarmobileapp.models.ReservationRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface ReservationApi {

    /**
     * Creates a new energy slot reservation for the authenticated prosumer.
     */
    @POST("reservations")
    suspend fun createReservation(
        @Body request: ReservationRequest
    ): Response<Reservation>

    /**
     * Searches or lists reservations for the current prosumer.
     */
    @GET("reservations")
    suspend fun searchReservations(
        @Query("status") status: String? = null,
        @Query("search") search: String? = null
    ): Response<List<Reservation>>

    /**
     * Retrieves a single reservation by ID.
     */
    @GET("reservations/{id}")
    suspend fun getReservationById(
        @Path("id") id: String
    ): Response<Reservation>

    /**
     * Updates an existing reservation to a different slot (requires 12-hour notice).
     */
    @PUT("reservations/{id}")
    suspend fun updateReservation(
        @Path("id") id: String,
        @Body request: ReservationRequest
    ): Response<Reservation>

    /**
     * Cancels an existing reservation (requires 12-hour notice).
     */
    @POST("reservations/{id}/cancel")
    suspend fun cancelReservation(
        @Path("id") id: String
    ): Response<Reservation>

    /**
     * Retrieves the cryptographically secure QR token for an approved reservation.
     */
    @GET("reservations/{id}/qr")
    suspend fun getQrToken(
        @Path("id") id: String
    ): Response<Map<String, String>>

    /**
     * Returns pending, approved-future, current, and history counts.
     * Operators receive counts for every booking.
     */
    @GET("Reservations/dashboard")
    suspend fun getDashboard(): Response<ReservationDashboard>

    /**
     * Approves a pending reservation and issues a QR token.
     */
    @POST("Reservations/{id}/approve")
    suspend fun approveReservation(
        @Path("id") id: String
    ): Response<Reservation>

    /**
     * Verifies a scanned QR token and marks the energy transfer completed.
     */
    @POST("Reservations/verify-qr")
    suspend fun verifyQr(
        @Body request: QRVerificationRequest
    ): Response<Reservation>

    /**
     * Marks a verified reservation as completed when verify-qr leaves it approved.
     */
    @PUT("Reservations/{id}/complete")
    suspend fun completeReservation(
        @Path("id") id: String
    ): Response<Reservation>
}