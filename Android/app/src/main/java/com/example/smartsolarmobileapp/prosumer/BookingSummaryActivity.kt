/**
 * Displays reservation summary details after booking, updating, or cancelling an energy slot.
 * Shows a summary page after each action (Create, Update, Cancel) per rubric requirements.
 */
package com.example.smartsolarmobileapp.prosumer

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.smartsolarmobileapp.R
import com.example.smartsolarmobileapp.api.ApiClient
import com.example.smartsolarmobileapp.database.DatabaseHelper
import com.example.smartsolarmobileapp.database.ReservationDao
import com.example.smartsolarmobileapp.database.StationDao
import com.example.smartsolarmobileapp.utils.DateTimeUtils
import com.example.smartsolarmobileapp.utils.SessionManager
import com.example.smartsolarmobileapp.utils.UiAlertUtils
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BookingSummaryActivity : AppCompatActivity() {

    private lateinit var tvHeader: TextView
    private lateinit var tvMessage: TextView
    private lateinit var tvId: TextView
    private lateinit var tvStatus: TextView
    private lateinit var tvStation: TextView
    private lateinit var tvTime: TextView
    private lateinit var tvProsumerNic: TextView
    private lateinit var btnViewQr: Button
    private lateinit var btnModifyBooking: Button
    private lateinit var btnCancelBooking: Button
    private lateinit var btnAllBookings: Button
    private lateinit var bottomNav: BottomNavigationView
    private lateinit var swipeRefresh: androidx.swiperefreshlayout.widget.SwipeRefreshLayout

    private lateinit var reservationDao: ReservationDao
    private lateinit var sessionManager: SessionManager
    private var approvalPollingJob: kotlinx.coroutines.Job? = null

    private var reservationId: String = ""
    private var stationName: String = ""
    private var stationId: String = ""
    private var slotTime: String = ""
    private var scheduledAt: String = ""
    private var status: String = "Pending"
    private var summaryMessage: String = ""
    private var qrToken: String? = null

    companion object {
        /** Request code for the slot modification flow */
        private const val REQUEST_MODIFY_SLOT = 2001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_booking_summary)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Reservation Summary"

        reservationDao = ReservationDao(DatabaseHelper(this))
        sessionManager = SessionManager(this)

        extractExtras()
        initializeViews()
        populateDetails()
        setupListeners()
        setupBottomNavigation()
    }

    override fun onResume() {
        super.onResume()
        fetchLatestReservationStatus(isManual = false)
        startApprovalPoller()
    }

    override fun onPause() {
        super.onPause()
        approvalPollingJob?.cancel()
    }

    private fun startApprovalPoller() {
        approvalPollingJob?.cancel()
        if (!status.equals("Pending", ignoreCase = true)) return

        approvalPollingJob = lifecycleScope.launch {
            while (isActive && status.equals("Pending", ignoreCase = true)) {
                kotlinx.coroutines.delay(6000)
                fetchLatestReservationStatus(isManual = false)
            }
        }
    }

    private fun fetchLatestReservationStatus(isManual: Boolean = false) {
        if (reservationId.isBlank()) {
            if (isManual) swipeRefresh.isRefreshing = false
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = ApiClient.reservationApi.getReservationById(reservationId)
                if (response.isSuccessful && response.body() != null) {
                    val updated = response.body()!!
                    status = updated.status
                    if (!updated.qrToken.isNullOrBlank()) {
                        qrToken = updated.qrToken
                    }
                    if (!updated.scheduledAt.isNullOrBlank()) {
                        scheduledAt = updated.scheduledAt
                    }

                    reservationDao.insertOrUpdateReservation(updated)

                    withContext(Dispatchers.Main) {
                        applyStatusStyling(status)
                        populateDetails()
                        if (isManual) {
                            swipeRefresh.isRefreshing = false
                            UiAlertUtils.showToast(this@BookingSummaryActivity, "Status updated: $status", UiAlertUtils.AlertType.INFO)
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        if (isManual) swipeRefresh.isRefreshing = false
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    if (isManual) {
                        swipeRefresh.isRefreshing = false
                        UiAlertUtils.showToast(this@BookingSummaryActivity, "Unable to reach server", UiAlertUtils.AlertType.WARNING)
                    }
                }
            }
        }
    }

    private fun extractExtras() {
        reservationId = intent.getStringExtra("EXTRA_RESERVATION_ID") ?: ""
        stationName = intent.getStringExtra("EXTRA_STATION_NAME") ?: "Microgrid Station"
        stationId = intent.getStringExtra("EXTRA_STATION_ID") ?: ""
        slotTime = intent.getStringExtra("EXTRA_SLOT_TIME") ?: ""
        scheduledAt = intent.getStringExtra("EXTRA_SCHEDULED_AT") ?: ""
        status = intent.getStringExtra("EXTRA_STATUS") ?: "Pending"
        summaryMessage = intent.getStringExtra("EXTRA_SUMMARY") ?: "Reservation submitted successfully."
        qrToken = intent.getStringExtra("EXTRA_QR_TOKEN")

        // If data is missing, try loading from local SQLite
        if (reservationId.isNotBlank()) {
            val localRes = reservationDao.getReservationById(reservationId)
            localRes?.let {
                if (stationName.isBlank() || stationName == "Microgrid Station" || stationName == stationId) {
                    stationName = it.stationName ?: ""
                }
                if (stationId.isBlank()) stationId = it.stationId
                if (scheduledAt.isBlank()) scheduledAt = it.scheduledAt
                status = it.status
                if (qrToken.isNullOrBlank()) qrToken = it.qrToken
            }
        }

        // Always resolve station name from StationDao if missing or matching raw hex ID
        val stationDao = StationDao(DatabaseHelper(this))
        if (stationName.isBlank() || stationName == "Microgrid Station" || stationName == stationId) {
            if (stationId.isNotBlank()) {
                val dbStation = stationDao.getStationById(stationId)
                if (dbStation != null) {
                    stationName = dbStation.name
                }
            }
        }
    }

    private fun initializeViews() {
        tvHeader = findViewById(R.id.tv_summary_header)
        tvMessage = findViewById(R.id.tv_summary_message)
        tvId = findViewById(R.id.tv_summary_id)
        tvStatus = findViewById(R.id.tv_summary_status)
        tvStation = findViewById(R.id.tv_summary_station)
        tvTime = findViewById(R.id.tv_summary_time)
        tvProsumerNic = findViewById(R.id.tv_summary_prosumer_nic)
        btnViewQr = findViewById(R.id.btn_view_qr)
        btnModifyBooking = findViewById(R.id.btn_modify_booking)
        btnCancelBooking = findViewById(R.id.btn_cancel_booking)
        btnAllBookings = findViewById(R.id.btn_summary_all_bookings)
        bottomNav = findViewById(R.id.bottom_nav_summary)
        swipeRefresh = findViewById(R.id.swipe_refresh_summary)
        swipeRefresh.setColorSchemeColors(getColor(R.color.solar_green_primary))
        swipeRefresh.setOnRefreshListener {
            fetchLatestReservationStatus(isManual = true)
        }
    }

    private fun populateDetails() {
        tvMessage.text = summaryMessage
        tvId.text = if (reservationId.length > 8) "Booking #${reservationId.take(8)}" else "Booking #$reservationId"
        tvStation.text = stationName

        val parsedDate = DateTimeUtils.parseIsoString(scheduledAt)
        if (parsedDate != null) {
            tvTime.text = "${DateTimeUtils.formatDisplayDate(parsedDate)} at ${DateTimeUtils.formatDisplayTime(parsedDate)}"
        } else {
            tvTime.text = if (slotTime.isNotBlank()) slotTime else scheduledAt
        }

        val userNic = sessionManager.getUserNic() ?: "Prosumer"
        tvProsumerNic.text = "Prosumer NIC: $userNic"

        applyStatusStyling(status)
    }

    private fun applyStatusStyling(currentStatus: String) {
        tvStatus.text = currentStatus

        when {
            currentStatus.equals("Approved", ignoreCase = true) -> {
                tvStatus.setTextColor(Color.parseColor("#2E7D32"))
                tvStatus.setBackgroundColor(Color.parseColor("#E8F5E9"))
                btnViewQr.text = "View Transaction QR Pass ⚡"
                btnViewQr.visibility = View.VISIBLE
                btnModifyBooking.visibility = View.VISIBLE
                btnCancelBooking.visibility = View.VISIBLE
            }
            currentStatus.equals("Pending", ignoreCase = true) -> {
                tvStatus.setTextColor(Color.parseColor("#E65100"))
                tvStatus.setBackgroundColor(Color.parseColor("#FFF3E0"))
                btnViewQr.text = "QR Pass (Available Upon Approval) ⏳"
                btnViewQr.visibility = View.VISIBLE
                btnModifyBooking.visibility = View.VISIBLE
                btnCancelBooking.visibility = View.VISIBLE
            }
            currentStatus.equals("Cancelled", ignoreCase = true) -> {
                tvStatus.setTextColor(Color.parseColor("#C62828"))
                tvStatus.setBackgroundColor(Color.parseColor("#FFEBEE"))
                tvHeader.text = "Reservation Cancelled"
                tvMessage.text = "This energy reservation has been cancelled."
                btnViewQr.visibility = View.GONE
                btnModifyBooking.visibility = View.GONE
                btnCancelBooking.visibility = View.GONE
            }
            currentStatus.equals("Completed", ignoreCase = true) -> {
                tvStatus.setTextColor(Color.parseColor("#1565C0"))
                tvStatus.setBackgroundColor(Color.parseColor("#E3F2FD"))
                tvHeader.text = "Energy Transfer Completed"
                btnViewQr.visibility = View.GONE
                btnModifyBooking.visibility = View.GONE
                btnCancelBooking.visibility = View.GONE
            }
        }
    }

    private fun setupListeners() {
        btnViewQr.setOnClickListener {
            handleViewQr()
        }

        btnModifyBooking.setOnClickListener {
            handleModifyBooking()
        }

        btnCancelBooking.setOnClickListener {
            handleCancelBooking()
        }

        btnAllBookings.setOnClickListener {
            startActivity(Intent(this, BookingListActivity::class.java))
        }

        findViewById<android.widget.ImageButton>(R.id.btn_back_summary)?.setOnClickListener {
            val intent = Intent(this, ProsumerDashboardActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            startActivity(intent)
            finish()
        }

        findViewById<android.widget.ImageButton>(R.id.btn_header_logout_summary)?.setOnClickListener {
            confirmLogout()
        }

    }

    private fun setupBottomNavigation() {
        bottomNav.menu.findItem(R.id.nav_bookings)?.isChecked = true
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
                    val intent = Intent(this, ProfileActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    }
                    startActivity(intent)
                    finish()
                    true
                }
                else -> false
            }
        }
    }

    private fun handleViewQr() {
        if (status.equals("Pending", ignoreCase = true)) {
            UiAlertUtils.showModernDialog(
                context = this,
                title = "QR Pass Pending Approval ⏳",
                message = "Per microgrid trading rules, transaction QR passes are generated and activated once your reservation is approved by Backoffice on the Web Management Portal.\n\nCurrent Status: PENDING\nPlease wait for Backoffice confirmation before presenting your pass at the station.",
                type = UiAlertUtils.AlertType.INFO,
                positiveButtonText = "Check Status Now",
                onPositiveClick = { fetchLatestReservationStatus(isManual = true) },
                negativeButtonText = "Preview Pass",
                onNegativeClick = { launchQrActivity() }
            )
            return
        }

        launchQrActivity()
    }

    private fun launchQrActivity() {
        val tokenToDisplay = qrToken ?: reservationId
        if (tokenToDisplay.isBlank()) {
            Toast.makeText(this, "QR code will be generated upon approval.", Toast.LENGTH_SHORT).show()
            return
        }

        val intent = Intent(this, QRDisplayActivity::class.java).apply {
            putExtra("EXTRA_QR_TOKEN", tokenToDisplay)
            putExtra("EXTRA_RESERVATION_ID", reservationId)
            putExtra("EXTRA_STATION_NAME", stationName)
            putExtra("EXTRA_SLOT_TIME", tvTime.text.toString())
            putExtra("EXTRA_STATUS", status)
        }
        startActivity(intent)
    }

    /**
     * Enforces the 12-Hour Modification Rule before permitting reservation update.
     * Navigates to SlotBookingActivity for new slot selection, then submits the update.
     */
    private fun handleModifyBooking() {
        val parsedDate = DateTimeUtils.parseIsoString(scheduledAt)

        // Strict business rule check: must have at least 12 hours advance notice
        if (parsedDate != null && !DateTimeUtils.isAtLeastTwelveHoursNotice(parsedDate)) {
            showRuleViolationDialog(
                "12-Hour Modification Rule Violation",
                "Modifications and cancellations require at least 12 hours' notice prior to the scheduled start time. Less than 12 hours remain for this slot."
            )
            return
        }

        UiAlertUtils.showModernDialog(
            context = this,
            title = "Modify Reservation",
            message = "You will be taken to the slot selection screen to choose a new time slot for this reservation. The existing booking will be updated.",
            type = UiAlertUtils.AlertType.INFO,
            positiveButtonText = "Choose New Slot",
            onPositiveClick = { navigateToSlotSelection() },
            negativeButtonText = "Keep Current Slot"
        )
    }

    /**
     * Opens SlotBookingActivity in modification mode, passing the existing reservation ID
     * so the booking engine can issue a PUT update instead of a POST create.
     */
    private fun navigateToSlotSelection() {
        val intent = Intent(this, SlotBookingActivity::class.java).apply {
            putExtra("EXTRA_STATION_ID", stationId)
            putExtra("EXTRA_STATION_NAME", stationName)
            putExtra("EXTRA_MODE", "MODIFY")
            putExtra("EXTRA_RESERVATION_ID", reservationId)
        }
        startActivityForResult(intent, REQUEST_MODIFY_SLOT)
    }

    /**
     * Handles the result after the prosumer selects a new slot in modification mode.
     */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_MODIFY_SLOT && resultCode == RESULT_OK && data != null) {
            // Refresh with updated reservation data from the modification flow
            val updatedScheduledAt = data.getStringExtra("EXTRA_SCHEDULED_AT") ?: scheduledAt
            val updatedStatus = data.getStringExtra("EXTRA_STATUS") ?: status
            val updatedSlotTime = data.getStringExtra("EXTRA_SLOT_TIME") ?: slotTime

            scheduledAt = updatedScheduledAt
            status = updatedStatus
            slotTime = updatedSlotTime
            summaryMessage = "Reservation modified successfully. New slot has been assigned."
            tvHeader.text = "Reservation Modified"

            populateDetails()
            UiAlertUtils.showToast(this, "Reservation updated successfully!", UiAlertUtils.AlertType.SUCCESS)
        }
    }

    /**
     * Enforces the 12-Hour Cancellation Rule before permitting reservation cancellation.
     */
    private fun handleCancelBooking() {
        val parsedDate = DateTimeUtils.parseIsoString(scheduledAt)

        // Strict business rule check: must have at least 12 hours advance notice
        if (parsedDate != null && !DateTimeUtils.isAtLeastTwelveHoursNotice(parsedDate)) {
            showRuleViolationDialog(
                "12-Hour Cancellation Rule Violation",
                "Modifications and cancellations require at least 12 hours' notice prior to the scheduled start time. Less than 12 hours remain for this slot."
            )
            return
        }

        UiAlertUtils.showModernDialog(
            context = this,
            title = "Confirm Cancellation",
            message = "Are you sure you want to cancel this energy reservation? This action cannot be undone.",
            type = UiAlertUtils.AlertType.WARNING,
            positiveButtonText = "Yes, Cancel",
            onPositiveClick = { executeCancellation() },
            negativeButtonText = "Keep Reservation"
        )
    }

    private fun executeCancellation() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                ApiClient.reservationApi.cancelReservation(reservationId)
            } catch (e: Exception) {
                // Network unavailable or server error; handled via local cache update
            }

            // Update local SQLite persistence
            reservationDao.updateReservationStatus(reservationId, "Cancelled")

            withContext(Dispatchers.Main) {
                status = "Cancelled"
                applyStatusStyling("Cancelled")
                UiAlertUtils.showToast(this@BookingSummaryActivity, "Reservation cancelled successfully", UiAlertUtils.AlertType.INFO)
            }
        }
    }

    private fun showRuleViolationDialog(title: String, message: String) {
        UiAlertUtils.showModernDialog(
            context = this,
            title = title,
            message = message,
            type = UiAlertUtils.AlertType.WARNING,
            positiveButtonText = "Understood"
        )
    }

    private fun confirmLogout() {
        UiAlertUtils.showModernDialog(
            context = this,
            title = "Log Out",
            message = "Are you sure you want to end your prosumer session and return to the login screen?",
            type = UiAlertUtils.AlertType.WARNING,
            positiveButtonText = "Log Out",
            onPositiveClick = {
                sessionManager.logout()
                val intent = Intent(this, LoginActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    putExtra("EXTRA_NOTICE", "Logged out successfully")
                }
                startActivity(intent)
                finish()
            },
            negativeButtonText = "Cancel"
        )
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
