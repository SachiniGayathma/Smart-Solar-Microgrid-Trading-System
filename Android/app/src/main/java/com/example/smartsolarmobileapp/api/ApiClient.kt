/**
 * Central HTTP client provider configuring Retrofit, OkHttp, and serialization.
 */
package com.example.smartsolarmobileapp.api

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {

    // Default emulator loopback to host machine IIS/Kestrel backend
    private var baseUrl: String = "http://10.0.2.2:5000/api/"

    private var authToken: String? = null

    /**
     * Sets the active bearer token for authenticated requests.
     */
    fun setAuthToken(token: String?) {
        authToken = token
    }

    /**
     * Updates the API base URL dynamically for network or local testing.
     */
    fun setBaseUrl(url: String) {
        baseUrl = if (url.endsWith("/")) url else "$url/"
        retrofitInstance = null
    }

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient: OkHttpClient
        get() {
            val builder = OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .addInterceptor(loggingInterceptor)

            // Attach bearer token header if present
            builder.addInterceptor { chain ->
                val original = chain.request()
                val requestBuilder = original.newBuilder()
                authToken?.let { token ->
                    requestBuilder.header("Authorization", "Bearer $token")
                }
                chain.proceed(requestBuilder.build())
            }

            return builder.build()
        }

    private var retrofitInstance: Retrofit? = null

    private val retrofit: Retrofit
        get() {
            if (retrofitInstance == null) {
                retrofitInstance = Retrofit.Builder()
                    .baseUrl(baseUrl)
                    .client(okHttpClient)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
            }
            return retrofitInstance!!
        }

    val authApi: AuthApi by lazy {
        retrofit.create(AuthApi::class.java)
    }

    val userApi: UserApi by lazy {
        retrofit.create(UserApi::class.java)
    }
}