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
import com.example.smartsolarmobileapp.utils.ValidationUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ProfileActivity : AppCompatActivity() {

    private lateinit var tvNic: TextView
    private lateinit var tvStatus: TextView
    private lateinit var etName: EditText
    private lateinit var etEmail: EditText
    private lateinit var etPhone: EditText
    private lateinit var btnUpdate: Button
    private lateinit var btnDeactivate: Button
    private lateinit var pbProfile: ProgressBar

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

        loadLocalProfile()
        fetchRemoteProfile()
    }

    private fun initializeViews() {
        tvNic = findViewById(R.id.tv_profile_nic)
        tvStatus = findViewById(R.id.tv_profile_status)
        etName = findViewById(R.id.et_profile_name)
        etEmail = findViewById(R.id.et_profile_email)
        etPhone = findViewById(R.id.et_profile_phone)
        btnUpdate = findViewById(R.id.btn_update_profile)
        btnDeactivate = findViewById(R.id.btn_deactivate_profile)
        pbProfile = findViewById(R.id.pb_profile)
    }

    private fun setupListeners() {
        btnUpdate.setOnClickListener {
            handleProfileUpdate()
        }

        btnDeactivate.setOnClickListener {
            confirmAccountDeactivation()
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
     * Populates UI fields with user information.
     */
    private fun displayUserData(user: User) {
        tvNic.text = "NIC: ${user.nic} (Read-only)"
        tvStatus.text = "Status: ${user.status ?: "Active"}"
        etName.setText(user.fullName)
        etEmail.setText(user.email)
        etPhone.setText(user.phone)
    }

    /**
     * Asynchronously queries the backend server for fresh profile attributes.
     */
    private fun fetchRemoteProfile() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = ApiClient.userApi.getProfile()
                if (response.isSuccessful && response.body() != null) {
                    val remoteUser = response.body()!!
                    currentUser = remoteUser

                    withContext(Dispatchers.Main) {
                        displayUserData(remoteUser)
                        sessionManager.saveSession(sessionManager.getAuthToken(), remoteUser)
                        userDao.saveUserSession(remoteUser, sessionManager.getAuthToken())
                    }
                }
            } catch (_: Exception) {
                // Network unavailable; local cached profile remains active
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
                        Toast.makeText(
                            this@ProfileActivity,
                            "Profile updated successfully!",
                            Toast.LENGTH_SHORT
                        ).show()
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

                    Toast.makeText(
                        this@ProfileActivity,
                        "Offline Mode: Profile changes saved locally.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    /**
     * Prompts the prosumer with a warning before executing deactivation.
     */
    private fun confirmAccountDeactivation() {
        AlertDialog.Builder(this)
            .setTitle("Deactivate Account")
            .setMessage("Are you sure you want to deactivate your prosumer account? You will be logged out and cannot make bookings until Backoffice reactivates it.")
            .setPositiveButton("Yes, Deactivate") { _, _ ->
                executeDeactivation()
            }
            .setNegativeButton("Cancel", null)
            .show()
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

                Toast.makeText(
                    this@ProfileActivity,
                    "Account has been deactivated.",
                    Toast.LENGTH_LONG
                ).show()

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
        AlertDialog.Builder(this)
            .setTitle("Update Failed")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun navigateToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
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
