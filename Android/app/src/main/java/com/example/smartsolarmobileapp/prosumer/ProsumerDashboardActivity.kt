/**
 * Primary dashboard for authenticated solar prosumers displaying live metrics and navigation.
 */
package com.example.smartsolarmobileapp.prosumer

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import com.example.smartsolarmobileapp.R
import com.example.smartsolarmobileapp.database.DatabaseHelper
import com.example.smartsolarmobileapp.database.ReservationDao
import com.example.smartsolarmobileapp.database.UserDao
import com.example.smartsolarmobileapp.utils.SessionManager
import com.example.smartsolarmobileapp.utils.UiAlertUtils

class ProsumerDashboardActivity : AppCompatActivity() {

    private lateinit var tvWelcome: TextView
    private lateinit var tvNic: TextView
    private lateinit var tvCountPending: TextView
    private lateinit var tvCountApproved: TextView
    private lateinit var btnProfile: Button
    private lateinit var btnLogout: Button
    private lateinit var cardBookSlot: CardView
    private lateinit var btnBookSlot: Button
    private lateinit var cardMyBookings: CardView
    private lateinit var btnViewBookings: Button

    private lateinit var sessionManager: SessionManager
    private lateinit var dbHelper: DatabaseHelper
    private lateinit var reservationDao: ReservationDao
    private lateinit var userDao: UserDao

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sessionManager = SessionManager(this)
        if (!sessionManager.isLoggedIn()) {
            navigateToLogin()
            return
        }

        setContentView(R.layout.activity_prosumer_dashboard)

        dbHelper = DatabaseHelper(this)
        reservationDao = ReservationDao(dbHelper)
        userDao = UserDao(dbHelper)

        initializeViews()
        setupListeners()
    }

    override fun onResume() {
        super.onResume()
        refreshDashboard()
    }

    private fun initializeViews() {
        tvWelcome = findViewById(R.id.tv_dashboard_welcome)
        tvNic = findViewById(R.id.tv_dashboard_nic)
        tvCountPending = findViewById(R.id.tv_count_pending)
        tvCountApproved = findViewById(R.id.tv_count_approved)
        btnProfile = findViewById(R.id.btn_dashboard_profile)
        btnLogout = findViewById(R.id.btn_dashboard_logout)
        cardBookSlot = findViewById(R.id.card_book_slot)
        btnBookSlot = findViewById(R.id.btn_book_slot)
        cardMyBookings = findViewById(R.id.card_my_bookings)
        btnViewBookings = findViewById(R.id.btn_view_bookings)
    }

    private fun setupListeners() {
        btnProfile.setOnClickListener {
            val intent = Intent(this, ProfileActivity::class.java)
            startActivity(intent)
        }

        btnBookSlot.setOnClickListener {
            navigateToStationSelect()
        }

        cardBookSlot.setOnClickListener {
            navigateToStationSelect()
        }

        btnViewBookings.setOnClickListener {
            navigateToBookingList()
        }

        cardMyBookings.setOnClickListener {
            navigateToBookingList()
        }

        btnLogout.setOnClickListener {
            confirmLogout()
        }
    }

    /**
     * Refreshes prosumer greeting and live booking statistics from SQLite storage.
     */
    private fun refreshDashboard() {
        val user = sessionManager.getUser() ?: userDao.getActiveUser()
        if (user == null) {
            navigateToLogin()
            return
        }

        tvWelcome.text = "Welcome, ${user.fullName}"
        tvNic.text = "NIC: ${user.nic} • Prosumer Portal"

        // Load live metric counts from local database
        val pendingCount = reservationDao.getPendingCount(user.nic)
        val approvedCount = reservationDao.getApprovedFutureCount(user.nic)

        tvCountPending.text = pendingCount.toString()
        tvCountApproved.text = approvedCount.toString()
    }

    private fun navigateToStationSelect() {
        val intent = Intent(this, StationSelectActivity::class.java)
        startActivity(intent)
    }

    private fun navigateToBookingList() {
        val intent = Intent(this, BookingListActivity::class.java)
        startActivity(intent)
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

    private fun navigateToLogin() {
        val intent = Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("EXTRA_NOTICE", "Logged out successfully")
        }
        startActivity(intent)
        finish()
    }
}
