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
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
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
    private lateinit var layoutEmptySlots: View
    private lateinit var ivEmptySlotsIcon: ImageView
    private lateinit var tvEmptySlotsTitle: TextView
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
    private var currentScheduledAt: String? = null
    private var currentSlotDisplay: String? = null
    private var currentStatus: String? = null

    private var selectedCalendar: Calendar = Calendar.getInstance()
    private var selectedSlot: Slot? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_slot_booking)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Reserve Energy Slot"

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
            currentScheduledAt = intent.getStringExtra("EXTRA_CURRENT_SCHEDULED_AT")
            currentSlotDisplay = intent.getStringExtra("EXTRA_CURRENT_SLOT_TIME")
            currentStatus = intent.getStringExtra("EXTRA_CURRENT_STATUS")

            if (!currentScheduledAt.isNullOrBlank()) {
                val parsedDate = DateTimeUtils.parseIsoString(currentScheduledAt)
                if (parsedDate != null) {
                    selectedCalendar.time = parsedDate
                }
            }
        }
    }

    private fun initializeViews() {
        tvStationName = findViewById(R.id.tv_booking_station_name)
        tvStationDetails = findViewById(R.id.tv_booking_station_details)
        tvSelectedDate = findViewById(R.id.tv_selected_date)
        btnChangeDate = findViewById(R.id.btn_change_date)
        pbSlots = findViewById(R.id.pb_slots)
        layoutEmptySlots = findViewById(R.id.layout_empty_slots)
        ivEmptySlotsIcon = findViewById(R.id.iv_empty_slots_icon)
        tvEmptySlotsTitle = findViewById(R.id.tv_empty_slots_title)
        tvEmptySlots = findViewById(R.id.tv_empty_slots)
        rvSlots = findViewById(R.id.rv_slots)
        btnConfirmBooking = findViewById(R.id.btn_confirm_booking)

        tvStationName.text = stationName
        tvStationDetails.text = "Operating Hours: $stationSchedule | Capacity: ${stationCapacity.toInt()} kWh"

        // Adjust confirm button text and header depending on mode
        if (isModifyMode) {
            btnConfirmBooking.text = "Select a Different Slot"
            btnConfirmBooking.isEnabled = false
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
            showDatePicker()
        }

        btnConfirmBooking.setOnClickListener {
            proceedToBookingConfirmation()
        }
    }

    private fun setupRecyclerView() {
        slotAdapter = SlotAdapter(emptyList()) { slot, isCurrent ->
            if (isModifyMode && isCurrent) {
                selectedSlot = null
                btnConfirmBooking.isEnabled = false
                btnConfirmBooking.text = "Current Slot (No Change)"
                UiAlertUtils.showSnackbar(
                    rvSlots,
                    "This is already your booked slot. Select a different time slot to modify.",
                    UiAlertUtils.AlertType.WARNING
                )
            } else {
                selectedSlot = slot
                btnConfirmBooking.isEnabled = true
                btnConfirmBooking.text = if (isModifyMode) "Confirm Slot Change" else "Proceed to Confirmation"
            }
        }
        if (isModifyMode) {
            slotAdapter.setCurrentBookedSlot(currentScheduledAt)
        }
        rvSlots.layoutManager = LinearLayoutManager(this)
        rvSlots.adapter = slotAdapter
    }

    /**
     * Displays a date picker restricted strictly between today and today + 7 days.
     */
    private fun showDatePicker() {
        val startOfToday = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

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
                    set(Calendar.MILLISECOND, 0)
                }

                if (!DateTimeUtils.isDateWithinSevenDays(newDate.time)) {
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
        datePicker.datePicker.minDate = startOfToday.timeInMillis
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
        layoutEmptySlots.visibility = View.GONE
        btnConfirmBooking.isEnabled = false

        if (isModifyMode) {
            btnConfirmBooking.text = "Select a Different Slot"
            slotAdapter.setCurrentBookedSlot(currentScheduledAt)
        } else {
            btnConfirmBooking.text = "Proceed to Confirmation"
        }

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
                        val isCurrentSlot = isModifyMode && !currentScheduledAt.isNullOrBlank() && run {
                            val cur = DateTimeUtils.parseIsoString(currentScheduledAt)
                            slotDate != null && cur != null && slotDate.time == cur.time
                        }
                        val isStatusAvailable = slot.status.equals("Available", ignoreCase = true)
                        val hasCapacity = slot.availableCapacity >= 1 || isCurrentSlot
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
                    ivEmptySlotsIcon.setImageResource(R.drawable.ic_alert_triangle)
                    ivEmptySlotsIcon.imageTintList = ContextCompat.getColorStateList(this@SlotBookingActivity, R.color.solar_amber_primary)
                    tvEmptySlotsTitle.text = "Connection Offline"
                    tvEmptySlots.text = "Energy slot scheduling requires an active connection to the central microgrid trading system.\n\nPlease check your network connection and try again."
                    layoutEmptySlots.visibility = View.VISIBLE
                } else if (slotsToDisplay.isEmpty()) {
                    ivEmptySlotsIcon.setImageResource(R.drawable.ic_clock)
                    ivEmptySlotsIcon.imageTintList = ContextCompat.getColorStateList(this@SlotBookingActivity, R.color.solar_slate_subtle)
                    tvEmptySlotsTitle.text = "No Available Slots"
                    tvEmptySlots.text = "No energy slots are available for ${DateTimeUtils.formatDisplayDate(selectedCalendar.time)}.\n\nPlease select another date above."
                    layoutEmptySlots.visibility = View.VISIBLE
                } else {
                    layoutEmptySlots.visibility = View.GONE
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
            UiAlertUtils.showToast(this, "Please select an available energy slot", UiAlertUtils.AlertType.WARNING)
            return
        }

        val currentUser = sessionManager.getUser()
        if (currentUser?.status.equals("Pending", ignoreCase = true)) {
            UiAlertUtils.showModernDialog(
                this,
                "Account Pending Activation",
                "Your account is pending activation by Backoffice on the Web Management Portal. Slot booking will be enabled once your account is verified.",
                UiAlertUtils.AlertType.WARNING
            )
            return
        }

        if (isModifyMode) {
            showModernSlotModificationDialog(slot)
            return
        }

        executeBooking(slot)
    }

    private fun showModernSlotModificationDialog(slot: Slot) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_confirm_modify_slot, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val oldTime = currentSlotDisplay?.takeIf { it.isNotBlank() } ?: "Current Slot"
        val startParsed = DateTimeUtils.parseIsoString(slot.startTime)
        val endParsed = DateTimeUtils.parseIsoString(slot.endTime)
        val newTime = if (startParsed != null && endParsed != null) {
            "${DateTimeUtils.formatDisplayTime(startParsed)} - ${DateTimeUtils.formatDisplayTime(endParsed)}"
        } else {
            "${slot.startTime} - ${slot.endTime}"
        }

        dialogView.findViewById<TextView>(R.id.tv_dialog_current_slot).text = oldTime
        dialogView.findViewById<TextView>(R.id.tv_dialog_new_slot).text = newTime

        dialogView.findViewById<View>(R.id.btn_dialog_confirm_change).setOnClickListener {
            dialog.dismiss()
            executeBooking(slot)
        }

        dialogView.findViewById<View>(R.id.btn_dialog_cancel_change).setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun executeBooking(slot: Slot) {
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
