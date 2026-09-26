/**
 * Handles prosumer authentication, role routing, and session persistence.
 */
package com.example.smartsolarmobileapp.prosumer

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doOnTextChanged
import androidx.lifecycle.lifecycleScope
import com.example.smartsolarmobileapp.R
import com.example.smartsolarmobileapp.api.ApiClient
import com.example.smartsolarmobileapp.database.DatabaseHelper
import com.example.smartsolarmobileapp.database.UserDao
import com.example.smartsolarmobileapp.models.LoginRequest
import com.example.smartsolarmobileapp.models.User
import com.example.smartsolarmobileapp.utils.SessionManager
import com.example.smartsolarmobileapp.utils.UiAlertUtils
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LoginActivity : AppCompatActivity() {

    private lateinit var tilIdentifier: TextInputLayout
    private lateinit var tilPassword: TextInputLayout
    private lateinit var etIdentifier: EditText
    private lateinit var etPassword: EditText
    private lateinit var btnLogin: Button
    private lateinit var btnToRegister: Button
    private lateinit var pbLogin: ProgressBar

    private lateinit var sessionManager: SessionManager
    private lateinit var dbHelper: DatabaseHelper
    private lateinit var userDao: UserDao

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sessionManager = SessionManager(this)
        dbHelper = DatabaseHelper(this)
        userDao = UserDao(dbHelper)

        // If an active session already exists, proceed directly to Dashboard
        if (sessionManager.isLoggedIn()) {
            navigateToDashboard()
            return
        }

        setContentView(R.layout.activity_login)

        initializeViews()
        setupListeners()

        val notice = intent.getStringExtra("EXTRA_NOTICE")
        if (!notice.isNullOrBlank()) {
            window.decorView.post {
                UiAlertUtils.showToast(this, notice, UiAlertUtils.AlertType.SUCCESS)
            }
        }
    }

    private fun initializeViews() {
        tilIdentifier = findViewById(R.id.til_login_identifier)
        tilPassword = findViewById(R.id.til_login_password)
        etIdentifier = findViewById(R.id.et_login_identifier)
        etPassword = findViewById(R.id.et_login_password)
        btnLogin = findViewById(R.id.btn_login)
        btnToRegister = findViewById(R.id.btn_to_register)
        pbLogin = findViewById(R.id.pb_login)

        // Prevent Material error exclamation mark from replacing the password toggle eye
        tilPassword.errorIconDrawable = null
        tilIdentifier.errorIconDrawable = null
    }

    private fun setupListeners() {
        btnLogin.setOnClickListener {
            handleLogin()
        }

        btnToRegister.setOnClickListener {
            val intent = Intent(this, RegisterActivity::class.java)
            startActivity(intent)
        }

        etIdentifier.doOnTextChanged { _, _, _, _ ->
            tilIdentifier.error = null
        }

        etPassword.doOnTextChanged { _, _, _, _ ->
            tilPassword.error = null
        }
    }

    /**
     * Validates input fields and initiates authentication dispatch.
     */
    private fun handleLogin() {
        val identifier = etIdentifier.text.toString().trim()
        val password = etPassword.text.toString().trim()

        tilIdentifier.error = null
        tilPassword.error = null

        if (identifier.isBlank()) {
            tilIdentifier.error = "NIC or Email is required"
            etIdentifier.requestFocus()
            return
        }

        if (password.isBlank()) {
            tilPassword.error = "Password is required"
            etPassword.requestFocus()
            return
        }

        if (password.length < 6) {
            tilPassword.error = "Password must be at least 6 characters"
            etPassword.requestFocus()
            return
        }

        executeLogin(identifier, password)
    }

    /**
     * Sends login request to backend, verifies status, and handles session caching.
     */
    private fun executeLogin(identifier: String, password: String) {
        setLoadingState(true)

        val request = LoginRequest(
            identifier = identifier,
            password = password
        )

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = ApiClient.authApi.login(request)

                withContext(Dispatchers.Main) {
                    setLoadingState(false)
                    if (response.isSuccessful && response.body() != null) {
                        val loginResponse = response.body()!!
                        val user = loginResponse.getResolvedUser()

                        if (user != null) {
                            validateAndProcessUser(loginResponse.token, user)
                        } else {
                            showErrorDialog("Authentication Failed", loginResponse.message ?: "Authentication failed.")
                        }
                    } else {
                        val errorMsg = response.errorBody()?.string()
                            ?: "Invalid credentials. Please verify your NIC/email and password."

                        // Detect ngrok tunnel offline (ERR_NGROK_3200 / HTTP 502)
                        // or other gateway errors and fall back to offline mode
                        if (isServerOfflineResponse(response.code(), errorMsg)) {
                            handleOfflineLogin(identifier, password)
                        } else {
                            tilPassword.error = "Invalid credentials"
                            showErrorDialog("Login Failed", errorMsg)
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    setLoadingState(false)
                    handleOfflineLogin(identifier, password)
                }
            }
        }
    }

    /**
     * Checks account lifecycle status before granting session access.
     */
    private fun validateAndProcessUser(token: String?, user: User) {
        val status = user.status ?: "Active"

        if (status.equals("Pending", ignoreCase = true)) {
            UiAlertUtils.showModernDialog(
                this,
                "Account Pending Activation",
                "Your account is pending activation by Backoffice.",
                UiAlertUtils.AlertType.INFO
            )
            return
        }

        if (status.equals("Deactivated", ignoreCase = true)) {
            UiAlertUtils.showModernDialog(
                this,
                "Account Deactivated",
                "Your account has been deactivated. Please contact support.",
                UiAlertUtils.AlertType.ERROR
            )
            return
        }

        // Active account: persist session in SharedPreferences and SQLite
        sessionManager.saveSession(token, user)
        userDao.saveUserSession(user, token)

        UiAlertUtils.showToast(this, "Welcome back, ${user.fullName}!", UiAlertUtils.AlertType.SUCCESS)
        navigateToDashboard()
    }

    /**
     * Fallback for offline mode when local user session cache is available.
     */
    private fun handleOfflineLogin(identifier: String, password: String) {
        val cachedUser = userDao.getUserByNicOrEmail(identifier)

        if (cachedUser != null) {
            // Password verification check:
            // For testing and offline demo purposes until central C# Web API and MongoDB server are actively running.
            // Verified against standard registered/demo credentials.
            if (password != "Password123!") {
                tilPassword.error = "Incorrect password"
                showErrorDialog("Incorrect Password", "The password you entered is incorrect. Please verify your credentials and try again.")
                return
            }

            val status = cachedUser.status ?: "Active"
            if (status.equals("Pending", ignoreCase = true)) {
                UiAlertUtils.showModernDialog(
                    this,
                    "Account Pending Activation",
                    "Your account is pending activation by Backoffice.",
                    UiAlertUtils.AlertType.INFO
                )
                return
            }

            if (status.equals("Deactivated", ignoreCase = true)) {
                UiAlertUtils.showModernDialog(
                    this,
                    "Account Deactivated",
                    "Your account has been deactivated.",
                    UiAlertUtils.AlertType.ERROR
                )
                return
            }

            sessionManager.saveSession(null, cachedUser)
            UiAlertUtils.showToast(
                this,
                "Offline Mode: Welcome back, ${cachedUser.fullName}!",
                UiAlertUtils.AlertType.SUCCESS
            )
            navigateToDashboard()
        } else {
            UiAlertUtils.showModernDialog(
                this,
                "Account Not Found",
                "No registered account found with identifier '$identifier' in local storage or on the server. Please check your NIC/email or register first.",
                UiAlertUtils.AlertType.WARNING
            )
        }
    }

    /**
     * Detects whether an HTTP response indicates the backend server is offline.
     * Covers ngrok tunnel down (ERR_NGROK_3200), reverse proxy errors (502/503/504),
     * and connection-refused HTML pages.
     */
    private fun isServerOfflineResponse(httpCode: Int, errorBody: String?): Boolean {
        if (httpCode in listOf(502, 503, 504)) return true
        if (errorBody == null) return false
        val offlineIndicators = listOf(
            "ERR_NGROK", "ngrok", "tunnel", "Bad Gateway",
            "Service Unavailable", "Gateway Timeout"
        )
        return offlineIndicators.any { errorBody.contains(it, ignoreCase = true) }
    }

    private fun setLoadingState(isLoading: Boolean) {
        if (isLoading) {
            pbLogin.visibility = View.VISIBLE
            btnLogin.isEnabled = false
            btnLogin.text = "Signing In..."
            btnToRegister.isEnabled = false
        } else {
            pbLogin.visibility = View.GONE
            btnLogin.isEnabled = true
            btnLogin.text = "Sign In"
            btnToRegister.isEnabled = true
        }
    }

    private fun showErrorDialog(title: String, message: String) {
        UiAlertUtils.showModernDialog(
            this,
            title,
            message,
            UiAlertUtils.AlertType.ERROR
        )
    }

    private fun navigateToDashboard() {
        val intent = Intent(this, ProsumerDashboardActivity::class.java)
        startActivity(intent)
        finish()
    }
}
