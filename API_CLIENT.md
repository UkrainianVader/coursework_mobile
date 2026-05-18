# API Client Setup Guide

This document explains the HTTP API client skeleton integrated into the Kursach Mobile app.

## Architecture

The API client is built on **Retrofit 2** with **OkHttp 3** for robust, type-safe HTTP communication:

```
MainActivity
    └── ApiClient (Singleton)
            └── Retrofit Instance
                    ├── OkHttpClient (logging, timeouts)
                    └── Gson Converter (JSON serialization)
                            └── ApiService (interface with endpoints)
```

## File Structure

```
app/src/main/java/com/kursach/mobile/api/
├── ApiClient.kt       # Singleton Retrofit configuration
├── ApiService.kt      # Retrofit service interface + response models
└── (models can be split into separate files as needed)
```

## Configuration

### 1. Backend URL (local.properties)

Create `local.properties` in the project root:

```bash
cp local.properties.example local.properties
```

Then edit `local.properties`:

```properties
sdk.dir=C:\Users\<your-user>\AppData\Local\Android\Sdk
API_BASE_URL=http://10.0.2.2:3000
```

**Note on emulator networking:**
- `10.0.2.2` in Android emulator = host machine `localhost`
- For physical devices: use your actual server IP or domain name

### 2. Initialize Client in MainActivity

```kotlin
// At app startup (in onCreate or your app's initialization code)
ApiClient.init(this)

// Create service instance
val apiService = ApiClient.create(ApiService::class.java)
```

## Usage Examples

### Health Check

```kotlin
apiService.healthCheck().enqueue(object : Callback<HealthCheckResponse> {
    override fun onResponse(call: Call<HealthCheckResponse>, response: Response<HealthCheckResponse>) {
        if (response.isSuccessful) {
            val status = response.body()?.status
            // Handle success
        }
    }

    override fun onFailure(call: Call<HealthCheckResponse>, t: Throwable) {
        // Handle error
    }
})
```

### Fetch User

```kotlin
apiService.getUserById("user-123").enqueue(object : Callback<UserResponse> {
    override fun onResponse(call: Call<UserResponse>, response: Response<UserResponse>) {
        if (response.isSuccessful) {
            val user = response.body()
            // Update UI with user data
        }
    }

    override fun onFailure(call: Call<UserResponse>, t: Throwable) {
        // Handle error
    }
})
```

### Create User

```kotlin
val newUser = UserRequest(name = "John Doe", email = "john@example.com")
apiService.createUser(newUser).enqueue(object : Callback<UserResponse> {
    override fun onResponse(call: Call<UserResponse>, response: Response<UserResponse>) {
        if (response.isSuccessful) {
            val createdUser = response.body()
            // Handle success
        }
    }

    override fun onFailure(call: Call<UserResponse>, t: Throwable) {
        // Handle error
    }
})
```

## Adding New Endpoints

1. **Define endpoint in ApiService.kt:**

```kotlin
@GET("api/products")
fun getProducts(): Call<List<ProductResponse>>

@POST("api/orders")
fun createOrder(@Body order: OrderRequest): Call<OrderResponse>
```

2. **Define request/response models:**

```kotlin
data class ProductResponse(val id: String, val name: String, val price: Double)
data class OrderRequest(val productId: String, val quantity: Int)
data class OrderResponse(val orderId: String, val status: String)
```

3. **Use in your activity/fragment:**

```kotlin
val apiService = ApiClient.create(ApiService::class.java)
apiService.getProducts().enqueue(...)
```

## Gradle Dependencies

The following dependencies are included for HTTP communication:

```gradle
implementation("com.squareup.retrofit2:retrofit:2.9.0")
implementation("com.squareup.retrofit2:converter-gson:2.9.0")
implementation("com.squareup.okhttp3:okhttp:4.11.0")
implementation("com.squareup.okhttp3:logging-interceptor:4.11.0")
implementation("com.google.code.gson:gson:2.10.1")
```

## Permissions

Internet permission is required in `AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.INTERNET" />
```

This is already configured in the app manifest.

## Debugging

OkHttp logging is configured at `Body` level in development:

```kotlin
HttpLoggingInterceptor.Level.BODY  // Logs full request/response bodies
```

Disable or set to `BASIC` in production to reduce log noise:

```kotlin
// In ApiClient.kt, modify:
level = HttpLoggingInterceptor.Level.BASIC
```

## Next Steps

1. Set up `local.properties` with your Android SDK path and API base URL
2. Build the project: `.\gradlew.bat build`
3. Run APK export: `.\scripts\export-apk.ps1 -BuildType debug`
4. Add your backend endpoints to `ApiService.kt`
5. Update `MainActivity` or create service layers to consume endpoints
6. Test API connectivity and error handling on emulator or device
