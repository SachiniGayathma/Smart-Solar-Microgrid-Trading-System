/**
 * Retrofit API interface for energy reservation lifecycle management.
 */
package com.example.smartsolarmobileapp.api

import com.example.smartsolarmobileapp.models.Reservation
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
    @POST("Reservations")
    suspend fun createReservation(
        @Body request: ReservationRequest
    ): Response<Reservation>

    /**
     * Searches or lists reservations for the current prosumer.
     */
    @GET("Reservations")
    suspend fun searchReservations(
        @Query("status") status: String? = null,
        @Query("search") search: String? = null
    ): Response<List<Reservation>>

    /**
     * Retrieves a single reservation by ID.
     */
    @GET("Reservations/{id}")
    suspend fun getReservationById(
        @Path("id") id: String
    ): Response<Reservation>

    /**
     * Updates an existing reservation to a different slot (requires 12-hour notice).
     */
    @PUT("Reservations/{id}")
    suspend fun updateReservation(
        @Path("id") id: String,
        @Body request: ReservationRequest
    ): Response<Reservation>

    /**
     * Cancels an existing reservation (requires 12-hour notice).
     */
    @POST("Reservations/{id}/cancel")
    suspend fun cancelReservation(
        @Path("id") id: String
    ): Response<Reservation>

    /**
     * Retrieves the cryptographically secure QR token for an approved reservation.
     */
    @GET("Reservations/{id}/qr")
    suspend fun getQrToken(
        @Path("id") id: String
    ): Response<Map<String, String>>
}