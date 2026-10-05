/**
 * Retrofit API interface defining authentication and registration endpoints.
 */
package com.example.smartsolarmobileapp.api

import com.example.smartsolarmobileapp.models.AuthResponse
import com.example.smartsolarmobileapp.models.LoginRequest
import com.example.smartsolarmobileapp.models.RegisterRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface AuthApi {

    /**
     * Submits a prosumer registration request to the central Web API.
     */
    @POST("auth/register")
    suspend fun register(
        @Body request: RegisterRequest
    ): Response<AuthResponse>

    /**
     * Authenticates user credentials against the central Web API.
     */
    @POST("auth/login")
    suspend fun login(
        @Body request: LoginRequest
    ): Response<AuthResponse>
}