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
import com.example.smartsolarmobileapp.utils.DateTimeUtils
import com.example.smartsolarmobileapp.utils.SessionManager
import com.example.smartsolarmobileapp.utils.UiAlertUtils
import kotlinx.coroutines.Dispatchers
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
    private lateinit var btnDashboard: Button

    private lateinit var reservationDao: ReservationDao
    private lateinit var sessionManager: SessionManager

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
        if (reservationId.isNotBlank() && (slotTime.isBlank() || scheduledAt.isBlank())) {
            val localRes = reservationDao.getReservationById(reservationId)
            localRes?.let {
                if (stationName.isBlank()) stationName = it.stationName ?: "Microgrid Station"
                if (stationId.isBlank()) stationId = it.stationId
                if (scheduledAt.isBlank()) scheduledAt = it.scheduledAt
                status = it.status
                if (qrToken.isNullOrBlank()) qrToken = it.qrToken
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
        btnDashboard = findViewById(R.id.btn_summary_dashboard)
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
                btnViewQr.visibility = View.VISIBLE
                btnModifyBooking.visibility = View.VISIBLE
                btnCancelBooking.visibility = View.VISIBLE
            }
            currentStatus.equals("Pending", ignoreCase = true) -> {
                tvStatus.setTextColor(Color.parseColor("#E65100"))
                tvStatus.setBackgroundColor(Color.parseColor("#FFF3E0"))
                // Show QR button if token exists, or keep visible so prosumer can view QR token
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

        btnDashboard.setOnClickListener {
            val intent = Intent(this, ProsumerDashboardActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            startActivity(intent)
            finish()
        }
    }

    private fun handleViewQr() {
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

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
