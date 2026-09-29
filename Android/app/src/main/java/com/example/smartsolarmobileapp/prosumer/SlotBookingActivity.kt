/**
 * Handles 30-minute energy slot selection with strict 7-day booking window enforcement.
 */
package com.example.smartsolarmobileapp.prosumer

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.smartsolarmobileapp.R
import com.example.smartsolarmobileapp.api.ApiClient
import com.example.smartsolarmobileapp.database.DatabaseHelper
import com.example.smartsolarmobileapp.database.ReservationDao
import com.example.smartsolarmobileapp.models.Reservation
import com.example.smartsolarmobileapp.models.ReservationRequest
import com.example.smartsolarmobileapp.models.Slot
import com.example.smartsolarmobileapp.prosumer.adapter.SlotAdapter
import com.example.smartsolarmobileapp.utils.DateTimeUtils
import com.example.smartsolarmobileapp.utils.SessionManager
import com.example.smartsolarmobileapp.utils.UiAlertUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Date
import java.util.UUID

class SlotBookingActivity : AppCompatActivity() {

    private lateinit var tvStationName: TextView
    private lateinit var tvStationDetails: TextView
    private lateinit var tvSelectedDate: TextView
    private lateinit var btnChangeDate: Button
    private lateinit var pbSlots: ProgressBar
    private lateinit var tvEmptySlots: TextView
    private lateinit var rvSlots: RecyclerView
    private lateinit var btnConfirmBooking: Button

    private lateinit var sessionManager: SessionManager
    private lateinit var reservationDao: ReservationDao
    private lateinit var slotAdapter: SlotAdapter

    private var stationId: String = ""
    private var stationName: String = ""
    private var stationCapacity: Double = 0.0
    private var stationSchedule: String = ""

    /** Modification mode: when non-null, the activity updates an existing reservation instead of creating a new one */
    private var modifyReservationId: String? = null
    private var isModifyMode: Boolean = false

    private var selectedCalendar: Calendar = Calendar.getInstance()
    private var selectedSlot: Slot? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_slot_booking)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Book 30-Min Slot"

        sessionManager = SessionManager(this)
        reservationDao = ReservationDao(DatabaseHelper(this))

        extractIntentExtras()
        initializeViews()
        setupRecyclerView()
        updateDateDisplay()
        loadSlotsForSelectedDate()
    }

    private fun extractIntentExtras() {
        stationId = intent.getStringExtra("EXTRA_STATION_ID") ?: ""
        stationName = intent.getStringExtra("EXTRA_STATION_NAME") ?: "Microgrid Hub"
        stationCapacity = intent.getDoubleExtra("EXTRA_STATION_CAPACITY", 100.0)
        stationSchedule = intent.getStringExtra("EXTRA_STATION_SCHEDULE") ?: "08:00 - 18:00"

        // Check if launched in modification mode from BookingSummaryActivity
        val mode = intent.getStringExtra("EXTRA_MODE") ?: ""
        if (mode.equals("MODIFY", ignoreCase = true)) {
            isModifyMode = true
            modifyReservationId = intent.getStringExtra("EXTRA_RESERVATION_ID")
        }
    }

    private fun initializeViews() {
        tvStationName = findViewById(R.id.tv_booking_station_name)
        tvStationDetails = findViewById(R.id.tv_booking_station_details)
        tvSelectedDate = findViewById(R.id.tv_selected_date)
        btnChangeDate = findViewById(R.id.btn_change_date)
        pbSlots = findViewById(R.id.pb_slots)
        tvEmptySlots = findViewById(R.id.tv_empty_slots)
        rvSlots = findViewById(R.id.rv_slots)
        btnConfirmBooking = findViewById(R.id.btn_confirm_booking)

        tvStationName.text = stationName
        tvStationDetails.text = "Operating Hours: $stationSchedule | Capacity: ${stationCapacity.toInt()} kWh"

        // Adjust confirm button text and header depending on mode
        if (isModifyMode) {
            btnConfirmBooking.text = "Confirm Slot Change"
            findViewById<TextView>(R.id.tv_slot_booking_header_title)?.text = "Modify Booking Slot"
            supportActionBar?.title = "Modify Booking Slot"
        }

        findViewById<android.widget.ImageButton>(R.id.btn_back_slots)?.setOnClickListener {
            finish()
        }

        findViewById<android.widget.ImageButton>(R.id.btn_header_logout_slots)?.setOnClickListener {
            confirmLogout()
        }

        btnChangeDate.setOnClickListener {
            UiAlertUtils.showToast(this, "Select a date within the allowed 7-day booking window", UiAlertUtils.AlertType.INFO)
            showDatePicker()
        }

        btnConfirmBooking.setOnClickListener {
            proceedToBookingConfirmation()
        }
    }

    private fun setupRecyclerView() {
        slotAdapter = SlotAdapter(emptyList()) { slot ->
            selectedSlot = slot
            btnConfirmBooking.isEnabled = true
        }
        rvSlots.layoutManager = LinearLayoutManager(this)
        rvSlots.adapter = slotAdapter
    }

    /**
     * Displays a date picker restricted strictly between today and today + 7 days.
     */
    private fun showDatePicker() {
        val now = Calendar.getInstance()
        val datePicker = DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                val newDate = Calendar.getInstance().apply {
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month)
                    set(Calendar.DAY_OF_MONTH, dayOfMonth)
                    set(Calendar.HOUR_OF_DAY, 12)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                }

                if (!DateTimeUtils.isWithinSevenDays(newDate.time)) {
                    showRuleViolationDialog(
                        "7-Day Booking Rule Violation",
                        "Energy reservations must be scheduled within 7 days from today. Please select a valid upcoming date."
                    )
                    return@DatePickerDialog
                }

                selectedCalendar = newDate
                selectedSlot = null
                btnConfirmBooking.isEnabled = false
                updateDateDisplay()
                loadSlotsForSelectedDate()
            },
            selectedCalendar.get(Calendar.YEAR),
            selectedCalendar.get(Calendar.MONTH),
            selectedCalendar.get(Calendar.DAY_OF_MONTH)
        )

        // Strict UI enforcement: physically block dates outside the 7-day range
        datePicker.datePicker.minDate = now.timeInMillis - 1000
        datePicker.datePicker.maxDate = DateTimeUtils.getMaxBookingDate().time

        datePicker.show()
    }

    private fun updateDateDisplay() {
        tvSelectedDate.text = DateTimeUtils.formatDisplayDate(selectedCalendar.time)
    }

    /**
     * Loads available 30-minute slots for the selected date from API or offline generator.
     */
    private fun loadSlotsForSelectedDate() {
        pbSlots.visibility = View.VISIBLE
        tvEmptySlots.visibility = View.GONE
        btnConfirmBooking.isEnabled = false

        lifecycleScope.launch(Dispatchers.IO) {
            var slotsToDisplay: List<Slot> = emptyList()
            var isOffline = false

            try {
                val response = ApiClient.slotApi.getSlots(stationId)
                if (response.isSuccessful && response.body() != null) {
                    val targetDateStr = DateTimeUtils.formatShortDate(selectedCalendar.time)
                    val now = java.util.Date()
                    slotsToDisplay = response.body()!!.filter { slot ->
                        val slotDate = DateTimeUtils.parseIsoString(slot.startTime)
                        val isSameDate = slotDate != null && DateTimeUtils.formatShortDate(slotDate) == targetDateStr
                        val isStatusAvailable = slot.status.equals("Available", ignoreCase = true)
                        val hasCapacity = slot.availableCapacity >= 1
                        val isFuture = slotDate != null && slotDate.after(now)

                        isSameDate && isStatusAvailable && hasCapacity && isFuture
                    }
                } else {
                    isOffline = true
                }
            } catch (e: Exception) {
                isOffline = true
            }

            withContext(Dispatchers.Main) {
                pbSlots.visibility = View.GONE
                slotAdapter.updateData(slotsToDisplay)
                if (isOffline) {
                    tvEmptySlots.text = "Server is currently offline.\n\nEnergy slot scheduling requires an active connection to the microgrid trading system. Please check your network connection and try again."
                    tvEmptySlots.visibility = View.VISIBLE
                } else if (slotsToDisplay.isEmpty()) {
                    tvEmptySlots.text = "No energy slots available for this station on ${DateTimeUtils.formatDisplayDate(selectedCalendar.time)}.\n\nPlease select another date."
                    tvEmptySlots.visibility = View.VISIBLE
                } else {
                    tvEmptySlots.visibility = View.GONE
                }
            }
        }
    }

    /**
     * Validates business rules and submits the slot reservation request.
     */
    private fun proceedToBookingConfirmation() {
        val slot = selectedSlot
        if (slot == null) {
            UiAlertUtils.showToast(this, "Please select an available 30-minute slot", UiAlertUtils.AlertType.WARNING)
            return
        }

        val slotDate = DateTimeUtils.parseIsoString(slot.startTime) ?: selectedCalendar.time
        val now = java.util.Date()

        // Enforce past time prevention for today's slots
        if (slotDate.before(now)) {
            showRuleViolationDialog(
                "Expired Slot Selected",
                "The selected time slot has already passed. Please select an upcoming slot or a future date."
            )
            return
        }

        // Enforce 7-day rule verification before submission
        if (!DateTimeUtils.isWithinSevenDays(slotDate)) {
            showRuleViolationDialog(
                "7-Day Rule Violation",
                "This slot is beyond the permitted 7-day scheduling window. Please select a slot within the next 7 days."
            )
            return
        }

        val prosumerNic = sessionManager.getUserNic() ?: "PROSUMER"
        val request = ReservationRequest(slotId = slot.id, prosumerNic = prosumerNic)

        pbSlots.visibility = View.VISIBLE
        btnConfirmBooking.isEnabled = false

        lifecycleScope.launch(Dispatchers.IO) {
            var reservationResult: Reservation? = null
            var serverErrorMessage: String? = null
            var isOffline = false

            try {
                if (isModifyMode && !modifyReservationId.isNullOrBlank()) {
                    // MODIFY MODE: PUT update to change the reservation's slot
                    val response = ApiClient.reservationApi.updateReservation(
                        modifyReservationId!!, request
                    )
                    if (response.isSuccessful && response.body() != null) {
                        reservationResult = response.body()
                    } else {
                        serverErrorMessage = com.example.smartsolarmobileapp.api.ApiMessages.from(response, "Modification rejected by server.")
                    }
                } else {
                    // CREATE MODE: POST new reservation
                    val response = ApiClient.reservationApi.createReservation(request)
                    if (response.isSuccessful && response.body() != null) {
                        reservationResult = response.body()
                    } else {
                        serverErrorMessage = com.example.smartsolarmobileapp.api.ApiMessages.from(response, "Booking rejected by server.")
                    }
                }
            } catch (e: Exception) {
                isOffline = true
            }

            // If the server rejected the request with an error, display the server error instead of faking offline success
            if (serverErrorMessage != null) {
                withContext(Dispatchers.Main) {
                    pbSlots.visibility = View.GONE
                    btnConfirmBooking.isEnabled = true
                    showRuleViolationDialog("Booking Not Saved", serverErrorMessage)
                }
                return@launch
            }

            if (reservationResult == null) {
                withContext(Dispatchers.Main) {
                    pbSlots.visibility = View.GONE
                    btnConfirmBooking.isEnabled = true
                    val errorTitle = if (isOffline) "Server Connection Required" else "Booking Failed"
                    val errorDesc = if (isOffline) {
                        "Cannot reserve energy slot while offline. An active connection to the central microgrid service is required to verify real-time capacity and register your booking."
                    } else {
                        "Unable to complete booking. Please check your connection and try again."
                    }
                    showRuleViolationDialog(errorTitle, errorDesc)
                }
                return@launch
            }

            // Save to local SQLite database for offline persistence & cache
            val finalRes = reservationResult.copy(stationName = stationName)
            reservationDao.insertOrUpdateReservation(finalRes)

            withContext(Dispatchers.Main) {
                pbSlots.visibility = View.GONE

                if (isOffline) {
                    UiAlertUtils.showToast(this@SlotBookingActivity, "Server offline: Reservation saved to local offline cache.", UiAlertUtils.AlertType.INFO)
                }

                if (isModifyMode) {
                    // Return result to BookingSummaryActivity for summary page refresh
                    val resultIntent = Intent().apply {
                        putExtra("EXTRA_SCHEDULED_AT", finalRes.scheduledAt)
                        putExtra("EXTRA_STATUS", finalRes.status)
                        putExtra("EXTRA_SLOT_TIME", "${slot.startTime} - ${slot.endTime}")
                    }
                    setResult(RESULT_OK, resultIntent)
                    finish()
                } else {
                    navigateToSummary(finalRes, slot)
                }
            }
        }
    }

    /**
     * Navigates to the booking summary confirmation screen.
     */
    private fun navigateToSummary(reservation: Reservation, slot: Slot) {
        val intent = Intent(this, BookingSummaryActivity::class.java).apply {
            putExtra("EXTRA_RESERVATION_ID", reservation.id)
            putExtra("EXTRA_STATION_NAME", stationName)
            putExtra("EXTRA_STATION_ID", stationId)
            putExtra("EXTRA_SLOT_TIME", "${slot.startTime} - ${slot.endTime}")
            putExtra("EXTRA_SCHEDULED_AT", reservation.scheduledAt)
            putExtra("EXTRA_STATUS", reservation.status)
            putExtra("EXTRA_SUMMARY", reservation.summary ?: "Reservation submitted successfully.")
            putExtra("EXTRA_QR_TOKEN", reservation.qrToken)
        }
        startActivity(intent)
        finish()
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
                com.example.smartsolarmobileapp.utils.SessionManager(this).logout()
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
