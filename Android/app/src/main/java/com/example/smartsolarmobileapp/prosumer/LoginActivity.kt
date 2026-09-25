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
import androidx.lifecycle.lifecycleScope
import com.example.smartsolarmobileapp.R
import com.example.smartsolarmobileapp.api.ApiClient
import com.example.smartsolarmobileapp.database.DatabaseHelper
import com.example.smartsolarmobileapp.database.UserDao
import com.example.smartsolarmobileapp.models.LoginRequest
import com.example.smartsolarmobileapp.models.User
import com.example.smartsolarmobileapp.utils.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LoginActivity : AppCompatActivity() {

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
    }

    private fun initializeViews() {
        etIdentifier = findViewById(R.id.et_login_identifier)
        etPassword = findViewById(R.id.et_login_password)
        btnLogin = findViewById(R.id.btn_login)
        btnToRegister = findViewById(R.id.btn_to_register)
        pbLogin = findViewById(R.id.pb_login)
    }

    private fun setupListeners() {
        btnLogin.setOnClickListener {
            handleLogin()
        }

        btnToRegister.setOnClickListener {
            val intent = Intent(this, RegisterActivity::class.java)
            startActivity(intent)
        }
    }

    /**
     * Validates input fields and initiates authentication dispatch.
     */
    private fun handleLogin() {
        val identifier = etIdentifier.text.toString().trim()
        val password = etPassword.text.toString().trim()

        if (identifier.isBlank()) {
            etIdentifier.error = "NIC or Email is required"
            etIdentifier.requestFocus()
            return
        }

        if (password.isBlank()) {
            etPassword.error = "Password is required"
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
                        val user = loginResponse.user

                        if (user != null) {
                            validateAndProcessUser(loginResponse.token, user)
                        } else {
                            showErrorDialog(loginResponse.message ?: "Authentication failed.")
                        }
                    } else {
                        val errorMsg = response.errorBody()?.string()
                            ?: "Invalid credentials. Please verify your NIC/email and password."
                        showErrorDialog(errorMsg)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    setLoadingState(false)
                    handleOfflineLogin(identifier)
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
            AlertDialog.Builder(this)
                .setTitle("Account Pending Activation")
                .setMessage("Your account is pending activation by Backoffice.")
                .setPositiveButton("OK", null)
                .show()
            return
        }

        if (status.equals("Deactivated", ignoreCase = true)) {
            AlertDialog.Builder(this)
                .setTitle("Account Deactivated")
                .setMessage("Your account has been deactivated. Please contact support.")
                .setPositiveButton("OK", null)
                .show()
            return
        }

        // Active account: persist session in SharedPreferences and SQLite
        sessionManager.saveSession(token, user)
        userDao.saveUserSession(user, token)

        Toast.makeText(this, "Welcome back, ${user.fullName}!", Toast.LENGTH_SHORT).show()
        navigateToDashboard()
    }

    /**
     * Fallback for offline mode when local user session cache is available.
     */
    private fun handleOfflineLogin(identifier: String) {
        val cachedUser = userDao.getUserByNicOrEmail(identifier)

        if (cachedUser != null) {
            val status = cachedUser.status ?: "Active"
            if (status.equals("Pending", ignoreCase = true)) {
                AlertDialog.Builder(this)
                    .setTitle("Account Pending Activation")
                    .setMessage("Your account is pending activation by Backoffice.")
                    .setPositiveButton("OK", null)
                    .show()
                return
            }

            if (status.equals("Deactivated", ignoreCase = true)) {
                AlertDialog.Builder(this)
                    .setTitle("Account Deactivated")
                    .setMessage("Your account has been deactivated.")
                    .setPositiveButton("OK", null)
                    .show()
                return
            }

            sessionManager.saveSession(null, cachedUser)
            Toast.makeText(
                this,
                "Offline Mode: Welcome back, ${cachedUser.fullName}!",
                Toast.LENGTH_LONG
            ).show()
            navigateToDashboard()
        } else {
            AlertDialog.Builder(this)
                .setTitle("Connection Error")
                .setMessage("Unable to connect to the server. Please check your network connection.")
                .setPositiveButton("OK", null)
                .show()
        }
    }

    private fun setLoadingState(isLoading: Boolean) {
        pbLogin.visibility = if (isLoading) View.VISIBLE else View.GONE
        btnLogin.isEnabled = !isLoading
        btnToRegister.isEnabled = !isLoading
    }

    private fun showErrorDialog(message: String) {
        AlertDialog.Builder(this)
            .setTitle("Login Failed")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun navigateToDashboard() {
        val intent = Intent(this, ProsumerDashboardActivity::class.java)
        startActivity(intent)
        finish()
    }
}
