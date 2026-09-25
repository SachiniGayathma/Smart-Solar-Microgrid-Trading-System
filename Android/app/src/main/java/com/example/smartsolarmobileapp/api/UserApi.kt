/**
 * Retrofit API interface for user profile retrieval, updates, and account deactivation.
 */
package com.example.smartsolarmobileapp.api

import com.example.smartsolarmobileapp.models.UpdateProfileRequest
import com.example.smartsolarmobileapp.models.User
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT

interface UserApi {

    /**
     * Retrieves the profile of the authenticated prosumer.
     */
    @GET("users/me")
    suspend fun getProfile(): Response<User>

    /**
     * Updates editable profile attributes for the authenticated prosumer.
     */
    @PUT("users/me")
    suspend fun updateProfile(
        @Body request: UpdateProfileRequest
    ): Response<User>

    /**
     * Requests deactivation of the authenticated prosumer's account.
     */
    @POST("users/me/deactivate")
    suspend fun deactivateAccount(): Response<ResponseBody>
}
