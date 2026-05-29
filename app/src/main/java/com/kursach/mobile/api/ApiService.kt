package com.kursach.mobile.api

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface ApiService {
    @POST("api/auth/login")
    fun login(@Body request: LoginRequest): Call<AuthResponse>

    @GET("api/auth/me")
    fun me(): Call<AuthResponse>

    @POST("api/auth/logout")
    fun logout(): Call<MessageResponse>

    @GET("api/dashboard")
    fun dashboard(): Call<DashboardResponse>

    @POST("api/components/add")
    fun addComponent(@Body request: ComponentRequest): Call<MessageResponse>

    @POST("api/components/update")
    fun updateComponent(@Body request: UpdateComponentRequest): Call<MessageResponse>

    @POST("api/components/remove")
    fun removeComponent(@Body request: ComponentIdRequest): Call<MessageResponse>

    @POST("api/components/fix")
    fun fixComponent(@Body request: ComponentIdRequest): Call<MessageResponse>

    @POST("api/assignments/assign")
    fun assignComponent(@Body request: AssignComponentRequest): Call<MessageResponse>

    @POST("api/assignments/unassign")
    fun unassignComponent(@Body request: ComponentIdRequest): Call<MessageResponse>

    @POST("api/assignments/return")
    fun returnComponent(@Body request: ComponentIdRequest): Call<MessageResponse>

    @POST("api/assignments/return-broken")
    fun returnBrokenComponent(@Body request: ComponentIdRequest): Call<MessageResponse>

    @POST("api/users/add")
    fun addUser(@Body request: CreateUserRequest): Call<MessageResponse>

    @POST("api/users/delete")
    fun deleteUser(@Body request: DeleteUserRequest): Call<MessageResponse>

    @POST("api/db/reset")
    fun resetDatabase(): Call<MessageResponse>
}

data class LoginRequest(
    val username: String,
    val password: String
)

data class AuthResponse(
    val user: AuthUser
)

data class AuthUser(
    val id: Long,
    val username: String,
    val role: String
)

data class MessageResponse(
    val message: String
)

data class DashboardResponse(
    val user: AuthUser,
    val items: List<DashboardComponent>,
    val users: List<AuthUser>,
    val assignedEquipmentIds: List<Long>,
    val assignmentByEquipmentId: Map<String, String?>,
    val warehouseReport: WarehouseReport?
)

data class WarehouseReport(
    val totalEquipment: Int,
    val damagedEquipment: Int,
    val assignedEquipment: Int,
    val freeEquipment: Int,
    val equipment: List<DashboardComponent>
)

data class DashboardComponent(
    val id: Long,
    val name: String,
    val type: String,
    val serial: String,
    val status: String,
    val description: String? = null
)

data class ComponentRequest(
    val name: String,
    val type: String,
    val serial: String,
    val description: String,
    val status: String
)

data class UpdateComponentRequest(
    val id: Long,
    val name: String,
    val type: String,
    val serial: String,
    val status: String,
    val description: String
)

data class ComponentIdRequest(
    val id: Long
)

data class AssignComponentRequest(
    val id: Long,
    val userId: Long
)

data class CreateUserRequest(
    val username: String,
    val password: String,
    val role: String
)

data class DeleteUserRequest(
    val id: Long
)
