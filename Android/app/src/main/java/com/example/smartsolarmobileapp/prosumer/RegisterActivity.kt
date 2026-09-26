/**
 * Handles solar prosumer registration with Sri Lankan NIC verification,
 * form validation, and server dispatch.
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
import com.example.smartsolarmobileapp.models.RegisterRequest
import com.example.smartsolarmobileapp.models.User
import com.example.smartsolarmobileapp.utils.ValidationUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RegisterActivity : AppCompatActivity() {

    private lateinit var etNic: EditText
    private lateinit var etName: EditText
    private lateinit var etEmail: EditText
    private lateinit var etPhone: EditText
    private lateinit var etPassword: EditText
    private lateinit var btnRegister: Button
    private lateinit var btnToLogin: Button
    private lateinit var pbRegister: ProgressBar

    private lateinit var dbHelper: DatabaseHelper
    private lateinit var userDao: UserDao

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        dbHelper = DatabaseHelper(this)
        userDao = UserDao(dbHelper)

        initializeViews()
        setupListeners()
    }

    private fun initializeViews() {
        etNic = findViewById(R.id.et_register_nic)
        etName = findViewById(R.id.et_register_name)
        etEmail = findViewById(R.id.et_register_email)
        etPhone = findViewById(R.id.et_register_phone)
        etPassword = findViewById(R.id.et_register_password)
        btnRegister = findViewById(R.id.btn_register)
        btnToLogin = findViewById(R.id.btn_to_login)
        pbRegister = findViewById(R.id.pb_register)
    }

    private fun setupListeners() {
        btnRegister.setOnClickListener {
            handleRegistration()
        }

        btnToLogin.setOnClickListener {
            navigateToLogin()
        }
    }

    /**
     * Validates user inputs against business constraints and dispatches registration.
     */
    private fun handleRegistration() {
        val nic = etNic.text.toString().trim()
        val name = etName.text.toString().trim()
        val email = etEmail.text.toString().trim()
        val phone = etPhone.text.toString().trim()
        val password = etPassword.text.toString().trim()

        // 1. NIC validation (primary domain identifier)
        if (!ValidationUtils.isValidNic(nic)) {
            etNic.error = "Invalid NIC format (9 digits + V/X or 12 digits required)"
            etNic.requestFocus()
            return
        }

        // 2. Full Name validation
        if (name.isBlank()) {
            etName.error = "Full Name is required"
            etName.requestFocus()
            return
        }

        // 3. Email validation
        if (!ValidationUtils.isValidEmail(email)) {
            etEmail.error = "Enter a valid email address"
            etEmail.requestFocus()
            return
        }

        // 4. Phone validation
        if (!ValidationUtils.isValidPhone(phone)) {
            etPhone.error = "Enter a valid phone number (e.g. 0771234567)"
            etPhone.requestFocus()
            return
        }

        // 5. Password length constraint
        if (!ValidationUtils.isValidPassword(password)) {
            etPassword.error = "Password must be at least 6 characters"
            etPassword.requestFocus()
            return
        }

        submitRegistration(nic, name, email, phone, password)
    }

    /**
     * Executes network registration on a background coroutine and caches the prosumer profile.
     */
    private fun submitRegistration(
        nic: String,
        name: String,
        email: String,
        phone: String,
        password: String
    ) {
        setLoadingState(true)

        val request = RegisterRequest(
            nic = nic,
            fullName = name,
            email = email,
            phone = phone,
            password = password,
            role = "Prosumer"
        )

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = ApiClient.authApi.register(request)

                withContext(Dispatchers.Main) {
                    setLoadingState(false)
                    if (response.isSuccessful && response.body()?.success != false) {
                        val registeredUser = response.body()?.user ?: User(
                            nic = nic,
                            fullName = name,
                            email = email,
                            phone = phone,
                            role = "Prosumer",
                            status = "Pending"
                        )
                        // Save in local SQLite
                        userDao.saveUserSession(registeredUser, null)
                        showRegistrationSuccessDialog(nic)
                    } else {
                        val errorMsg = response.body()?.message
                            ?: response.errorBody()?.string()
                            ?: "Registration rejected by server."

                        // Detect ngrok tunnel offline (ERR_NGROK_3200 / HTTP 502)
                        // or other gateway errors and fall back to local storage
                        if (isServerOfflineResponse(response.code(), errorMsg)) {
                            saveRegistrationLocally(nic, name, email, phone)
                        } else {
                            showErrorDialog(errorMsg)
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    setLoadingState(false)
                    // Network exception: save locally as Pending
                    saveRegistrationLocally(nic, name, email, phone)
                }
            }
        }
    }

    /**
     * Offline fallback: persists the registration in SQLite as Pending so the
     * prosumer can still access the app when the backend is unreachable.
     */
    private fun saveRegistrationLocally(nic: String, name: String, email: String, phone: String) {
        val localUser = User(
            nic = nic,
            fullName = name,
            email = email,
            phone = phone,
            role = "Prosumer",
            status = "Pending"
        )
        userDao.saveUserSession(localUser, null)

        AlertDialog.Builder(this@RegisterActivity)
            .setTitle("Registration Saved Locally")
            .setMessage("Server connection unavailable. Your registration with NIC $nic has been stored locally as PENDING and will be synchronized when online.")
            .setPositiveButton("Proceed to Login") { _, _ ->
                navigateToLogin()
            }
            .setCancelable(false)
            .show()
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
        pbRegister.visibility = if (isLoading) View.VISIBLE else View.GONE
        btnRegister.isEnabled = !isLoading
        btnToLogin.isEnabled = !isLoading
    }

    private fun showRegistrationSuccessDialog(nic: String) {
        AlertDialog.Builder(this)
            .setTitle("Registration Submitted")
            .setMessage("Your account has been registered with NIC: $nic.\n\nStatus: PENDING ACTIVATION\n\nA Backoffice administrator must activate your account before you can log in.")
            .setPositiveButton("Proceed to Login") { _, _ ->
                navigateToLogin()
            }
            .setCancelable(false)
            .show()
    }

    private fun showErrorDialog(message: String) {
        AlertDialog.Builder(this)
            .setTitle("Registration Failed")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun navigateToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        startActivity(intent)
        finish()
    }
}
