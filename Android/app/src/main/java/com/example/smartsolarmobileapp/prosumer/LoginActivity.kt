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
import android.widget.TextView
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
import com.example.smartsolarmobileapp.utils.RoleRouter
import com.example.smartsolarmobileapp.utils.SessionManager
import com.example.smartsolarmobileapp.utils.UiAlertUtils
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LoginActivity : AppCompatActivity() {

    private lateinit var tvLoginTitle: TextView
    private lateinit var tvLoginSubtitle: TextView
    private lateinit var toggleGroupRole: MaterialButtonToggleGroup
    private lateinit var btnRoleProsumer: MaterialButton
    private lateinit var btnRoleOperator: MaterialButton
    private lateinit var tilIdentifier: TextInputLayout
    private lateinit var tilPassword: TextInputLayout
    private lateinit var etIdentifier: EditText
    private lateinit var etPassword: EditText
    private lateinit var btnLogin: Button
    private lateinit var btnToRegister: Button
    private var layoutOperatorNotice: View? = null
    private lateinit var pbLogin: ProgressBar

    private var isOperatorMode = false

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
        tvLoginTitle = findViewById(R.id.tv_login_title)
        tvLoginSubtitle = findViewById(R.id.tv_login_subtitle)
        toggleGroupRole = findViewById(R.id.toggle_group_role)
        btnRoleProsumer = findViewById(R.id.btn_role_prosumer)
        btnRoleOperator = findViewById(R.id.btn_role_operator)
        tilIdentifier = findViewById(R.id.til_login_identifier)
        tilPassword = findViewById(R.id.til_login_password)
        etIdentifier = findViewById(R.id.et_login_identifier)
        etPassword = findViewById(R.id.et_login_password)
        btnLogin = findViewById(R.id.btn_login)
        btnToRegister = findViewById(R.id.btn_to_register)
        layoutOperatorNotice = findViewById(R.id.layout_operator_notice)
        pbLogin = findViewById(R.id.pb_login)

        // Prevent Material error exclamation mark from replacing the password toggle eye
        tilPassword.errorIconDrawable = null
        tilIdentifier.errorIconDrawable = null
    }

    private fun setupListeners() {
        toggleGroupRole.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                when (checkedId) {
                    R.id.btn_role_prosumer -> setLoginMode(isOperator = false)
                    R.id.btn_role_operator -> setLoginMode(isOperator = true)
                }
            }
        }

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
     * Toggles UI dynamically between Solar Prosumer and Grid Operator modes.
     */
    private fun setLoginMode(isOperator: Boolean) {
        isOperatorMode = isOperator
        tilIdentifier.error = null
        tilPassword.error = null

        if (isOperator) {
            tvLoginTitle.text = "Grid Operator Sign In"
            tvLoginSubtitle.text = "Field operations, on-site physical QR verification, and station dispatch."
            tilIdentifier.hint = "Operator Email or Staff ID"
            tilIdentifier.setStartIconDrawable(R.drawable.ic_email)
            btnToRegister.visibility = View.GONE
            layoutOperatorNotice?.visibility = View.VISIBLE
        } else {
            tvLoginTitle.text = "Prosumer Sign In"
            tvLoginSubtitle.text = "Access your clean energy trading portal, slot reservations, and microgrid transaction QR codes."
            tilIdentifier.hint = "NIC or Email Address"
            tilIdentifier.setStartIconDrawable(R.drawable.ic_badge_nic)
            btnToRegister.visibility = View.VISIBLE
            layoutOperatorNotice?.visibility = View.GONE
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
            tilIdentifier.error = if (isOperatorMode) "Operator Email or Staff ID is required" else "NIC or Email is required"
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

        // Option B: Allow Pending prosumer to access their dashboard with restricted actions.
        // Account activation status will be reactively synced and displayed via an Amber banner.

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
        val safeNic = user.nic.blankTo(user.email.blankTo(user.id ?: "operator"))
        val safeUser = user.copy(
            nic = safeNic,
            fullName = user.fullName.blankTo("Operator"),
            email = user.email.blankTo(safeNic),
            phone = user.phone.blankTo("-")
        )
        sessionManager.saveSession(token, safeUser)
        try {
            userDao.saveUserSession(safeUser, token)
        } catch (e: Exception) {
            // Session is already stored. A local database error must not close the app.
        }

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
            val isOperatorRole = cachedUser.role.equals("GridOperator", ignoreCase = true) || cachedUser.role.equals("Operator", ignoreCase = true)
            val isPasswordValid = if (isOperatorRole) {
                password == "Operator@123" || password == "Password123!"
            } else {
                password == "Password123!"
            }

            if (!isPasswordValid) {
                tilPassword.error = "Incorrect password"
                showErrorDialog("Incorrect Password", "The password you entered is incorrect. Please verify your credentials and try again.")
                return
            }

            val status = cachedUser.status ?: "Active"

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

    private fun String?.blankTo(fallback: String): String {
        return this?.takeIf { it.isNotBlank() } ?: fallback
    }

    private fun navigateToDashboard() {
        val destination = RoleRouter.homeActivity(sessionManager.getUserRole())
        startActivity(Intent(this, destination))
        finish()
    }
}
