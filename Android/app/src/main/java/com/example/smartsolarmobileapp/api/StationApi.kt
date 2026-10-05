/**
 * Retrofit API interface for retrieving microgrid charging station hub data.
 */
package com.example.smartsolarmobileapp.api

import com.example.smartsolarmobileapp.models.Station
import com.example.smartsolarmobileapp.models.UpdateStationAvailabilityRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.Path

interface StationApi {

    /**
     * Retrieves active community microgrid charging hubs.
     */
    @GET("stations")
    suspend fun getStations(): Response<List<Station>>

    @GET("stations")
    suspend fun getActiveStations(): Response<List<Station>>

    /**
     * Retrieves station details for a specific hub by ID.
     */
    @GET("stations/{id}")
    suspend fun getStationById(
        @Path("id") id: String
    ): Response<Station>

    /**
     * Updates remaining battery storage slots at a station.
     */
    @PATCH("stations/{id}/availability")
    suspend fun updateAvailability(
        @Path("id") id: String,
        @Body request: UpdateStationAvailabilityRequest
    ): Response<Station>
}
