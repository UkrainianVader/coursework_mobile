package com.kursach.mobile

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.kursach.mobile.api.ApiClient
import com.kursach.mobile.api.ApiService
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val statusText = findViewById<TextView>(R.id.apiHint)

        // Initialize API client
        ApiClient.init(this)

        // Create API service instance
        val apiService = ApiClient.create(ApiService::class.java)

        // Example: Call health check endpoint
        statusText.text = "Checking backend connectivity..."

        apiService.healthCheck().enqueue(object : Callback<com.kursach.mobile.api.HealthCheckResponse> {
            override fun onResponse(
                call: Call<com.kursach.mobile.api.HealthCheckResponse>,
                response: Response<com.kursach.mobile.api.HealthCheckResponse>
            ) {
                if (response.isSuccessful) {
                    val status = response.body()?.status ?: "unknown"
                    statusText.text = "Backend status: $status"
                } else {
                    statusText.text = "Backend responded: ${response.code()}"
                }
            }

            override fun onFailure(call: Call<com.kursach.mobile.api.HealthCheckResponse>, t: Throwable) {
                statusText.text = "Connection failed: ${t.message}"
            }
        })
    }
}
