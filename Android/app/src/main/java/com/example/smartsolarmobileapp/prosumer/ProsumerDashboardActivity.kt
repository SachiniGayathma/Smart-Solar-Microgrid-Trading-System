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
import androidx.lifecycle.lifecycleScope
import com.example.smartsolarmobileapp.R
import com.example.smartsolarmobileapp.api.ApiClient
import com.example.smartsolarmobileapp.database.DatabaseHelper
import com.example.smartsolarmobileapp.database.ReservationDao
import com.example.smartsolarmobileapp.database.StationDao
import com.example.smartsolarmobileapp.database.UserDao
import android.view.View
import android.widget.ProgressBar
import com.example.smartsolarmobileapp.utils.RoleRouter
import com.example.smartsolarmobileapp.utils.SessionManager
import com.example.smartsolarmobileapp.utils.UiAlertUtils
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    private lateinit var swipeRefresh: androidx.swiperefreshlayout.widget.SwipeRefreshLayout

    // Option B: Amber Pending Banner and Status Indicator components
    private var cardPendingBanner: View? = null
    private var btnCheckStatus: View? = null
    private var pbCheckingStatus: ProgressBar? = null
    private var tvStatusPill: TextView? = null
    private var isAccountPending: Boolean = false
    private var statusPollJob: Job? = null

    private lateinit var sessionManager: SessionManager
    private lateinit var dbHelper: DatabaseHelper
    private lateinit var reservationDao: ReservationDao
    private lateinit var stationDao: StationDao
    private lateinit var userDao: UserDao

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sessionManager = SessionManager(this)
        if (!sessionManager.isLoggedIn()) {
            navigateToLogin()
            return
        }
        if (sessionManager.isOperator()) {
            startActivity(Intent(this, RoleRouter.homeActivity(sessionManager.getUserRole())))
            finish()
            return
        }

        setContentView(R.layout.activity_prosumer_dashboard)

        dbHelper = DatabaseHelper(this)
        reservationDao = ReservationDao(dbHelper)
        stationDao = StationDao(dbHelper)
        userDao = UserDao(dbHelper)

        initializeViews()
        setupListeners()
        setupBottomNavigation()
    }

    override fun onResume() {
        super.onResume()
        refreshDashboard()
        syncDataFromApi()
        checkAccountStatus(silent = true)
        startStatusPollerIfPending()
        // Ensure bottom nav has Home selected when on dashboard
        bottomNav.selectedItemId = R.id.nav_home
    }

    override fun onPause() {
        super.onPause()
        statusPollJob?.cancel()
        statusPollJob = null
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

        cardPendingBanner = findViewById(R.id.card_pending_banner)
        btnCheckStatus = findViewById(R.id.btn_check_activation_status)
        pbCheckingStatus = findViewById(R.id.pb_checking_status)
        tvStatusPill = findViewById(R.id.tv_dashboard_status_pill)
        swipeRefresh = findViewById(R.id.swipe_refresh_dashboard)
        swipeRefresh.setColorSchemeColors(getColor(R.color.solar_green_primary))
        swipeRefresh.setOnRefreshListener {
            refreshDashboard()
            syncDataFromApi(isManual = true)
            checkAccountStatus(silent = false)
        }
    }

    private fun setupListeners() {
        btnHeaderLogout.setOnClickListener {
            confirmLogout()
        }

        btnCheckStatus?.setOnClickListener {
            checkAccountStatus(silent = false)
        }

        btnBookSlot.setOnClickListener {
            if (isAccountPending) {
                showPendingActivationDialog()
            } else {
                navigateToStationSelect()
            }
        }

        cardBookSlot.setOnClickListener {
            if (isAccountPending) {
                showPendingActivationDialog()
            } else {
                navigateToStationSelect()
            }
        }

        btnViewBookings.setOnClickListener {
            navigateToBookingList()
        }

        cardMyBookings.setOnClickListener {
            navigateToBookingList()
        }
    }

    private fun showPendingActivationDialog() {
        UiAlertUtils.showModernDialog(
            context = this,
            title = "Account Pending Activation",
            message = "Slot booking is locked until your account is approved by Backoffice on the Web Management Portal. You can explore charging stations on the map or review your profile.",
            type = UiAlertUtils.AlertType.WARNING,
            positiveButtonText = "Check Status",
            onPositiveClick = { checkAccountStatus(silent = false) },
            negativeButtonText = "Close"
        )
    }

    /**
     * Starts a coroutine poller checking every 8 seconds while dashboard is in foreground and status is Pending.
     */
    private fun startStatusPollerIfPending() {
        statusPollJob?.cancel()
        if (!isAccountPending) return

        statusPollJob = lifecycleScope.launch {
            while (isActive && isAccountPending) {
                delay(8000)
                checkAccountStatus(silent = true)
            }
        }
    }

    /**
     * Queries the backend for fresh user profile status and updates UI reactively.
     */
    private fun checkAccountStatus(silent: Boolean = false) {
        if (!silent) {
            pbCheckingStatus?.visibility = View.VISIBLE
            btnCheckStatus?.isEnabled = false
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = ApiClient.userApi.getProfile()
                withContext(Dispatchers.Main) {
                    if (!silent) {
                        pbCheckingStatus?.visibility = View.GONE
                        btnCheckStatus?.isEnabled = true
                    }

                    if (response.isSuccessful && response.body() != null) {
                        val remoteUser = response.body()!!
                        val wasPending = isAccountPending
                        val nowStatus = remoteUser.status ?: "Active"
                        val isNowActive = nowStatus.equals("Active", ignoreCase = true)

                        sessionManager.saveUser(remoteUser)
                        userDao.insertOrUpdateUser(remoteUser)
                        refreshDashboard()

                        if (wasPending && isNowActive) {
                            statusPollJob?.cancel()
                            statusPollJob = null
                            UiAlertUtils.showToast(
                                this@ProsumerDashboardActivity,
                                "🎉 Account Approved! You can now book solar charging slots.",
                                UiAlertUtils.AlertType.SUCCESS
                            )
                        } else if (!silent) {
                            if (isNowActive) {
                                UiAlertUtils.showToast(
                                    this@ProsumerDashboardActivity,
                                    "Your account is Active!",
                                    UiAlertUtils.AlertType.SUCCESS
                                )
                            } else {
                                UiAlertUtils.showToast(
                                    this@ProsumerDashboardActivity,
                                    "Account is still pending Backoffice review.",
                                    UiAlertUtils.AlertType.INFO
                                )
                            }
                        }
                    } else if (!silent) {
                        UiAlertUtils.showToast(
                            this@ProsumerDashboardActivity,
                            "Unable to reach server to check status.",
                            UiAlertUtils.AlertType.WARNING
                        )
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    if (!silent) {
                        pbCheckingStatus?.visibility = View.GONE
                        btnCheckStatus?.isEnabled = true
                        UiAlertUtils.showToast(
                            this@ProsumerDashboardActivity,
                            "Network connection unavailable.",
                            UiAlertUtils.AlertType.WARNING
                        )
                    }
                }
            }
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

        val status = user.status ?: "Active"
        isAccountPending = status.equals("Pending", ignoreCase = true)

        if (isAccountPending) {
            cardPendingBanner?.visibility = View.VISIBLE
            tvStatusPill?.text = "Pending"
            tvStatusPill?.setBackgroundResource(R.drawable.bg_pill_badge_amber)
            tvStatusPill?.setTextColor(android.graphics.Color.parseColor("#B45309"))

            btnBookSlot.text = "Awaiting Activation 🔒"
            btnBookSlot.setBackgroundColor(android.graphics.Color.parseColor("#CBD5E1"))
            btnBookSlot.setTextColor(android.graphics.Color.parseColor("#475569"))
        } else {
            cardPendingBanner?.visibility = View.GONE
            tvStatusPill?.text = "Active"
            tvStatusPill?.setBackgroundResource(R.drawable.bg_pill_badge)
            tvStatusPill?.setTextColor(getColor(R.color.solar_green_primary))

            btnBookSlot.text = "Reserve Energy Slot"
            btnBookSlot.setBackgroundColor(getColor(R.color.solar_green_primary))
            btnBookSlot.setTextColor(getColor(R.color.white))
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

    /**
     * Synchronizes stations and reservations from the central Web API into SQLite cache.
     */
    private fun syncDataFromApi(isManual: Boolean = false) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val stResponse = ApiClient.stationApi.getStations()
                if (stResponse.isSuccessful && stResponse.body() != null) {
                    stationDao.insertOrUpdateStations(stResponse.body()!!)
                }
            } catch (_: Exception) {}

            try {
                val userNic = sessionManager.getUserNic() ?: ""
                val resResponse = ApiClient.reservationApi.searchReservations()
                if (resResponse.isSuccessful && resResponse.body() != null) {
                    val remote = resResponse.body()!!
                    reservationDao.insertOrUpdateReservations(remote)
                    withContext(Dispatchers.Main) {
                        refreshDashboard()
                    }
                }
            } catch (_: Exception) {}

            withContext(Dispatchers.Main) {
                swipeRefresh.isRefreshing = false
            }
        }
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
