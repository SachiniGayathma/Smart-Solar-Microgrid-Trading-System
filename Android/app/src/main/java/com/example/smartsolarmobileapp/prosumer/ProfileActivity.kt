/**
 * Manages solar prosumer profile details, live updates, and account deactivation requests.
 */
package com.example.smartsolarmobileapp.prosumer

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.smartsolarmobileapp.R
import com.example.smartsolarmobileapp.api.ApiClient
import com.example.smartsolarmobileapp.database.DatabaseHelper
import com.example.smartsolarmobileapp.database.UserDao
import com.example.smartsolarmobileapp.models.UpdateProfileRequest
import com.example.smartsolarmobileapp.models.User
import com.example.smartsolarmobileapp.utils.SessionManager
import com.example.smartsolarmobileapp.utils.UiAlertUtils
import com.example.smartsolarmobileapp.utils.ValidationUtils
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ProfileActivity : AppCompatActivity() {

    private lateinit var tvNic: TextView
    private lateinit var tvStatus: TextView
    private lateinit var tvDisplayName: TextView
    private lateinit var tvAvatarInitials: TextView
    private lateinit var etName: EditText
    private lateinit var etEmail: EditText
    private lateinit var etPhone: EditText
    private lateinit var btnUpdate: Button
    private lateinit var btnDeactivate: Button
    private lateinit var btnLogout: Button
    private lateinit var btnHeaderLogout: ImageButton
    private lateinit var bottomNav: BottomNavigationView
    private lateinit var pbProfile: ProgressBar
    private lateinit var swipeRefresh: androidx.swiperefreshlayout.widget.SwipeRefreshLayout

    private lateinit var sessionManager: SessionManager
    private lateinit var dbHelper: DatabaseHelper
    private lateinit var userDao: UserDao

    private var currentUser: User? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "My Profile"

        sessionManager = SessionManager(this)
        dbHelper = DatabaseHelper(this)
        userDao = UserDao(dbHelper)

        initializeViews()
        setupListeners()
        setupBottomNavigation()

        loadLocalProfile()
        fetchRemoteProfile()
    }

    override fun onResume() {
        super.onResume()
        bottomNav.selectedItemId = R.id.nav_profile
    }

    private fun initializeViews() {
        tvNic = findViewById(R.id.tv_profile_nic)
        tvStatus = findViewById(R.id.tv_profile_status)
        tvDisplayName = findViewById(R.id.tv_profile_display_name)
        tvAvatarInitials = findViewById(R.id.tv_avatar_initials)
        etName = findViewById(R.id.et_profile_name)
        etEmail = findViewById(R.id.et_profile_email)
        etPhone = findViewById(R.id.et_profile_phone)
        btnUpdate = findViewById(R.id.btn_update_profile)
        btnDeactivate = findViewById(R.id.btn_deactivate_profile)
        btnLogout = findViewById(R.id.btn_logout_profile)
        btnHeaderLogout = findViewById(R.id.btn_header_logout_profile)
        bottomNav = findViewById(R.id.bottom_nav_profile)
        pbProfile = findViewById(R.id.pb_profile)
        swipeRefresh = findViewById(R.id.swipe_refresh_profile)
        swipeRefresh.setColorSchemeColors(getColor(R.color.solar_green_primary))
        swipeRefresh.setOnRefreshListener {
            fetchRemoteProfile(isManual = true)
        }
    }

    private fun setupListeners() {
        findViewById<android.widget.ImageButton>(R.id.btn_back_profile)?.setOnClickListener {
            finish()
        }

        btnHeaderLogout.setOnClickListener {
            confirmLogout()
        }

        btnUpdate.setOnClickListener {
            handleProfileUpdate()
        }

        btnDeactivate.setOnClickListener {
            confirmAccountDeactivation()
        }

        btnLogout.setOnClickListener {
            confirmLogout()
        }
    }

    private fun setupBottomNavigation() {
        bottomNav.selectedItemId = R.id.nav_profile
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    val intent = Intent(this, ProsumerDashboardActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    }
                    startActivity(intent)
                    finish()
                    true
                }
                R.id.nav_bookings -> {
                    val intent = Intent(this, BookingListActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    }
                    startActivity(intent)
                    finish()
                    true
                }
                R.id.nav_profile -> {
                    // Already on Profile
                    true
                }
                else -> false
            }
        }
    }

    /**
     * Loads locally cached prosumer data immediately into the UI.
     */
    private fun loadLocalProfile() {
        val user = sessionManager.getUser() ?: userDao.getActiveUser()
        if (user == null) {
            Toast.makeText(this, "Session expired. Please log in again.", Toast.LENGTH_SHORT).show()
            navigateToLogin()
            return
        }

        currentUser = user
        displayUserData(user)
    }

    /**
     * Populates UI fields with user information, including avatar initials.
     */
    private fun displayUserData(user: User) {
        tvNic.text = "NIC: ${user.nic}"
        tvStatus.text = user.status ?: "Active"
        tvDisplayName.text = user.fullName
        etName.setText(user.fullName)
        etEmail.setText(user.email)
        etPhone.setText(user.phone)

        // Generate initials from full name (e.g., "Amara Perera" → "AP")
        val initials = user.fullName
            .split(" ")
            .filter { it.isNotBlank() }
            .take(2)
            .joinToString("") { it.first().uppercase() }
        tvAvatarInitials.text = if (initials.isNotBlank()) initials else "?"

        // Style status badge color based on status
        val statusColor = when {
            user.status.equals("Active", ignoreCase = true) -> R.color.solar_green_primary
            user.status.equals("Pending", ignoreCase = true) -> R.color.solar_amber_primary
            user.status.equals("Deactivated", ignoreCase = true) -> R.color.solar_status_cancelled
            else -> R.color.solar_green_primary
        }
        tvStatus.setTextColor(getColor(statusColor))
    }

    /**
     * Asynchronously queries the backend server for fresh profile attributes.
     */
    private fun fetchRemoteProfile(isManual: Boolean = false) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = ApiClient.userApi.getProfile()
                if (response.isSuccessful && response.body() != null) {
                    val remoteUser = response.body()!!
                    currentUser = remoteUser

                    withContext(Dispatchers.Main) {
                        swipeRefresh.isRefreshing = false
                        displayUserData(remoteUser)
                        sessionManager.saveSession(sessionManager.getAuthToken(), remoteUser)
                        userDao.saveUserSession(remoteUser, sessionManager.getAuthToken())
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        swipeRefresh.isRefreshing = false
                    }
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    swipeRefresh.isRefreshing = false
                }
            }
        }
    }

    /**
     * Validates input fields and submits updated profile details.
     */
    private fun handleProfileUpdate() {
        val name = etName.text.toString().trim()
        val email = etEmail.text.toString().trim()
        val phone = etPhone.text.toString().trim()

        if (name.isBlank()) {
            etName.error = "Full Name is required"
            etName.requestFocus()
            return
        }

        if (!ValidationUtils.isValidEmail(email)) {
            etEmail.error = "Enter a valid email address"
            etEmail.requestFocus()
            return
        }

        if (!ValidationUtils.isValidPhone(phone)) {
            etPhone.error = "Enter a valid phone number"
            etPhone.requestFocus()
            return
        }

        val nic = currentUser?.nic ?: sessionManager.getUserNic() ?: return

        executeProfileUpdate(nic, name, email, phone)
    }

    /**
     * Dispatches profile updates to the network and synchronizes local storage.
     */
    private fun executeProfileUpdate(nic: String, name: String, email: String, phone: String) {
        setLoadingState(true)

        val request = UpdateProfileRequest(
            fullName = name,
            email = email,
            phone = phone
        )

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = ApiClient.userApi.updateProfile(request)

                withContext(Dispatchers.Main) {
                    setLoadingState(false)
                    if (response.isSuccessful && response.body() != null) {
                        val updated = response.body()!!
                        currentUser = updated
                        sessionManager.saveSession(sessionManager.getAuthToken(), updated)
                        userDao.updateUserProfile(nic, name, email, phone)

                        displayUserData(updated)
                        UiAlertUtils.showToast(
                            this@ProfileActivity,
                            "Profile updated successfully!",
                            UiAlertUtils.AlertType.SUCCESS
                        )
                    } else {
                        val error = response.errorBody()?.string() ?: "Failed to update profile."
                        showErrorDialog(error)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    setLoadingState(false)
                    // Offline fallback: save changes in local SQLite
                    userDao.updateUserProfile(nic, name, email, phone)
                    val localUpdated = currentUser?.copy(fullName = name, email = email, phone = phone)
                        ?: User(nic = nic, fullName = name, email = email, phone = phone)
                    currentUser = localUpdated
                    sessionManager.saveSession(sessionManager.getAuthToken(), localUpdated)

                    displayUserData(localUpdated)
                    UiAlertUtils.showToast(
                        this@ProfileActivity,
                        "Offline Mode: Profile changes saved locally.",
                        UiAlertUtils.AlertType.INFO
                    )
                }
            }
        }
    }

    /**
     * Prompts the prosumer with a warning before executing deactivation.
     */
    private fun confirmAccountDeactivation() {
        UiAlertUtils.showModernDialog(
            context = this,
            title = "Deactivate Account",
            message = "Are you sure you want to deactivate your prosumer account? You will be logged out and cannot make bookings until Backoffice reactivates it.",
            type = UiAlertUtils.AlertType.WARNING,
            positiveButtonText = "Yes, Deactivate",
            onPositiveClick = { executeDeactivation() },
            negativeButtonText = "Cancel"
        )
    }

    /**
     * Displays a confirmation dialog before clearing credentials and ending the session.
     */
    private fun confirmLogout() {
        UiAlertUtils.showModernDialog(
            context = this,
            title = "Confirm Logout",
            message = "Are you sure you want to log out of your prosumer account?",
            type = UiAlertUtils.AlertType.WARNING,
            positiveButtonText = "Log Out",
            onPositiveClick = { executeLogout() },
            negativeButtonText = "Cancel"
        )
    }

    /**
     * Clears all session credentials, updates local state, and routes to login screen.
     */
    private fun executeLogout() {
        sessionManager.clearSession()
        userDao.clearUserSession()
        navigateToLogin()
    }

    /**
     * Deactivates the prosumer account, clears active sessions, and routes to login.
     */
    private fun executeDeactivation() {
        setLoadingState(true)

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                ApiClient.userApi.deactivateAccount()
            } catch (_: Exception) {
                // Proceed with local deactivation even if network fails
            }

            withContext(Dispatchers.Main) {
                val nic = currentUser?.nic ?: sessionManager.getUserNic()
                if (nic != null) {
                    userDao.updateUserStatus(nic, "Deactivated")
                }

                sessionManager.clearSession()
                userDao.clearUserSession()

                navigateToLogin()
            }
        }
    }

    private fun setLoadingState(isLoading: Boolean) {
        pbProfile.visibility = if (isLoading) View.VISIBLE else View.GONE
        btnUpdate.isEnabled = !isLoading
        btnDeactivate.isEnabled = !isLoading
    }

    private fun showErrorDialog(message: String) {
        UiAlertUtils.showModernDialog(
            context = this,
            title = "Update Failed",
            message = message,
            type = UiAlertUtils.AlertType.ERROR,
            positiveButtonText = "OK"
        )
    }

    private fun navigateToLogin() {
        val intent = Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("EXTRA_NOTICE", "Logged out successfully")
        }
        startActivity(intent)
        finish()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
