/**
 * Retrofit API interface for energy booking slot endpoints.
 */
package com.example.smartsolarmobileapp.api

import com.example.smartsolarmobileapp.models.Slot
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface SlotApi {

    /**
     * Retrieves bookable energy slots, optionally filtered by charging station ID.
     */
    @GET("slots")
    suspend fun getSlots(
        @Query("stationId") stationId: String? = null
    ): Response<List<Slot>>

    /**
     * Retrieves details for a specific 30-minute energy slot.
     */
    @GET("slots/{id}")
    suspend fun getSlotById(
        @Path("id") id: String
    ): Response<Slot>
}
