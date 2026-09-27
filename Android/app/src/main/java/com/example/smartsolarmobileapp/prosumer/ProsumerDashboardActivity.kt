/**
 * Primary dashboard for authenticated solar prosumers displaying live metrics and navigation.
 */
package com.example.smartsolarmobileapp.prosumer

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import com.example.smartsolarmobileapp.R
import com.example.smartsolarmobileapp.database.DatabaseHelper
import com.example.smartsolarmobileapp.database.ReservationDao
import com.example.smartsolarmobileapp.database.UserDao
import com.example.smartsolarmobileapp.utils.SessionManager
import com.example.smartsolarmobileapp.utils.UiAlertUtils
import com.google.android.material.bottomnavigation.BottomNavigationView

class ProsumerDashboardActivity : AppCompatActivity() {

    private lateinit var tvAvatar: TextView
    private lateinit var tvUserName: TextView
    private lateinit var tvNic: TextView
    private lateinit var tvCountPending: TextView
    private lateinit var tvCountApproved: TextView
    private lateinit var cardBookSlot: CardView
    private lateinit var btnBookSlot: Button
    private lateinit var cardMyBookings: CardView
    private lateinit var btnViewBookings: Button
    private lateinit var btnHeaderLogout: ImageButton
    private lateinit var bottomNav: BottomNavigationView

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
        setupBottomNavigation()
    }

    override fun onResume() {
        super.onResume()
        refreshDashboard()
        // Ensure bottom nav has Home selected when on dashboard
        bottomNav.selectedItemId = R.id.nav_home
    }

    private fun initializeViews() {
        tvAvatar = findViewById(R.id.tv_dashboard_avatar)
        tvUserName = findViewById(R.id.tv_dashboard_user_name)
        tvNic = findViewById(R.id.tv_dashboard_nic)
        tvCountPending = findViewById(R.id.tv_count_pending)
        tvCountApproved = findViewById(R.id.tv_count_approved)
        cardBookSlot = findViewById(R.id.card_book_slot)
        btnBookSlot = findViewById(R.id.btn_book_slot)
        cardMyBookings = findViewById(R.id.card_my_bookings)
        btnViewBookings = findViewById(R.id.btn_view_bookings)
        btnHeaderLogout = findViewById(R.id.btn_header_logout)
        bottomNav = findViewById(R.id.bottom_nav)
    }

    private fun setupListeners() {
        btnHeaderLogout.setOnClickListener {
            confirmLogout()
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
    }

    /**
     * Configures bottom navigation bar tab selection handling.
     */
    private fun setupBottomNavigation() {
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    // Already on home — no-op
                    true
                }
                R.id.nav_bookings -> {
                    navigateToBookingList()
                    true
                }
                R.id.nav_profile -> {
                    val intent = Intent(this, ProfileActivity::class.java)
                    startActivity(intent)
                    true
                }
                else -> false
            }
        }
    }

    /**
     * Confirms prosumer logout intent with a modern styled alert dialog.
     */
    private fun confirmLogout() {
        UiAlertUtils.showModernDialog(
            context = this,
            title = "Log Out",
            message = "Are you sure you want to end your prosumer session and return to the login screen?",
            type = UiAlertUtils.AlertType.WARNING,
            positiveButtonText = "Log Out",
            onPositiveClick = { executeLogout() },
            negativeButtonText = "Cancel"
        )
    }

    private fun executeLogout() {
        sessionManager.logout()
        navigateToLogin()
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

        // Generate initials from full name
        val initials = user.fullName
            .split(" ")
            .filter { it.isNotBlank() }
            .take(2)
            .joinToString("") { it.first().uppercase() }

        tvAvatar.text = if (initials.isNotBlank()) initials else "SP"
        tvUserName.text = user.fullName
        tvNic.text = "NIC: ${user.nic}"

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

    private fun navigateToLogin() {
        val intent = Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("EXTRA_NOTICE", "Logged out successfully")
        }
        startActivity(intent)
        finish()
    }
}
