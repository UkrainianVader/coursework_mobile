package com.kursach.mobile.api

import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Body
import retrofit2.http.Path

/**
 * Retrofit service interface for backend API endpoints.
 * Add your endpoints here as needed.
 *
 * Example endpoints:
 * - Health check / ping
 * - User authentication
 * - Data retrieval
 */
interface ApiService {

    /**
     * Health check endpoint to verify backend connectivity.
     * Endpoint: GET /api/health
     */
    @GET("api/health")
    fun healthCheck(): Call<HealthCheckResponse>

    /**
     * Example: Fetch user data by ID
     * Endpoint: GET /api/users/{id}
     */
    @GET("api/users/{id}")
    fun getUserById(@Path("id") userId: String): Call<UserResponse>

    /**
     * Example: Create or update user
     * Endpoint: POST /api/users
     */
    @POST("api/users")
    fun createUser(@Body user: UserRequest): Call<UserResponse>

    // Add more endpoints as needed for your application
}

/**
 * Response model for health check.
 */
data class HealthCheckResponse(
    val status: String,
    val timestamp: Long
)

/**
 * Response model for user data.
 */
data class UserResponse(
    val id: String,
    val name: String,
    val email: String,
    val createdAt: Long
)

/**
 * Request model for creating/updating user.
 */
data class UserRequest(
    val name: String,
    val email: String
)
