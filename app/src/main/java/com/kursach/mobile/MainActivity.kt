package com.kursach.mobile

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.core.widget.doAfterTextChanged
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.kursach.mobile.api.ApiClient
import com.kursach.mobile.api.ApiService
import com.kursach.mobile.api.AuthResponse
import com.kursach.mobile.api.LoginRequest
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class MainActivity : AppCompatActivity() {
    private lateinit var apiUrlLayout: TextInputLayout
    private lateinit var apiUrlInput: TextInputEditText
    private lateinit var usernameInput: TextInputEditText
    private lateinit var passwordInput: TextInputEditText
    private lateinit var statusText: androidx.appcompat.widget.AppCompatTextView

    private val api by lazy { ApiClient.create(ApiService::class.java) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        apiUrlLayout = findViewById(R.id.apiUrlLayout)
        apiUrlInput = findViewById(R.id.apiUrlInput)
        usernameInput = findViewById(R.id.loginUserInput)
        passwordInput = findViewById(R.id.loginPasswordInput)
        statusText = findViewById(R.id.statusText)

        val savedUrl = ApiClient.init(this)
        apiUrlInput.setText(savedUrl.removePrefix("http://").removePrefix("https://").removeSuffix("/"))
        apiUrlInput.doAfterTextChanged {
            persistServerAddress(showError = false)
        }
        apiUrlInput.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                persistServerAddress(showError = true)
            }
        }

        findViewById<MaterialButton>(R.id.loginButton).setOnClickListener {
            login()
        }

        setStatus("Введіть IP сервера і увійдіть.")
    }

    override fun onStart() {
        super.onStart()
        api.me().enqueue(object : Callback<AuthResponse> {
            override fun onResponse(call: Call<AuthResponse>, response: Response<AuthResponse>) {
                if (response.isSuccessful && response.body() != null) {
                    openTable(response.body()!!.user.username)
                }
            }

            override fun onFailure(call: Call<AuthResponse>, t: Throwable) {
                setStatus(t.message ?: "Готово.")
            }
        })
    }

    private fun persistServerAddress(showError: Boolean): Boolean {
        val rawUrl = apiUrlInput.text?.toString()?.trim().orEmpty()
        if (rawUrl.isBlank()) {
            if (showError) {
                apiUrlLayout.error = "Введіть IP або адресу сервера."
            }
            return false
        }

        val normalizedUrl = normalizeServerAddress(rawUrl)
        if (normalizedUrl == null) {
            if (showError) {
                apiUrlLayout.error = "Невірна адреса сервера."
            }
            return false
        }

        apiUrlLayout.error = null
        ApiClient.init(this, normalizedUrl)
        setStatus("Сервер: ${normalizedUrl.removePrefix("http://").removePrefix("https://").removeSuffix("/")}")
        return true
    }

    private fun login() {
        if (!persistServerAddress(showError = true)) {
            return
        }

        val username = usernameInput.text?.toString()?.trim().orEmpty()
        val password = passwordInput.text?.toString().orEmpty()

        if (username.isBlank() || password.isBlank()) {
            setStatus("Введіть логін і пароль.")
            return
        }

        api.login(LoginRequest(username = username, password = password))
            .enqueue(object : Callback<AuthResponse> {
                override fun onResponse(call: Call<AuthResponse>, response: Response<AuthResponse>) {
                    if (response.isSuccessful && response.body() != null) {
                        openTable(response.body()!!.user.username)
                    } else {
                        setStatus("Помилка входу (${response.code()}).")
                    }
                }

                override fun onFailure(call: Call<AuthResponse>, t: Throwable) {
                    setStatus(t.message ?: "Не вдалося виконати вхід.")
                }
            })
    }

    private fun openTable(username: String) {
        startActivity(
            Intent(this, TableActivity::class.java)
                .putExtra(TableActivity.EXTRA_USERNAME, username)
        )
        finish()
    }

    private fun setStatus(message: String) {
        statusText.text = message
    }

    private fun normalizeServerAddress(rawValue: String): String? {
        val trimmed = rawValue.trim().ifEmpty { return null }
        val withScheme = when {
            trimmed.startsWith("http://", ignoreCase = true) -> trimmed
            trimmed.startsWith("https://", ignoreCase = true) -> trimmed
            else -> "http://$trimmed"
        }
        val withTrailingSlash = if (withScheme.endsWith("/")) withScheme else "$withScheme/"
        return withTrailingSlash.toHttpUrlOrNull()?.toString()
    }
}
