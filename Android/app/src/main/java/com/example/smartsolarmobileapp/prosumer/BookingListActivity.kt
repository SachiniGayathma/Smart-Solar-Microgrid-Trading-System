/**
 * Displays prosumer energy reservation history with status filters and real-time search.
 */
package com.example.smartsolarmobileapp.prosumer

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.MenuItem
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.smartsolarmobileapp.R
import com.example.smartsolarmobileapp.api.ApiClient
import com.example.smartsolarmobileapp.database.DatabaseHelper
import com.example.smartsolarmobileapp.database.ReservationDao
import com.example.smartsolarmobileapp.models.Reservation
import com.example.smartsolarmobileapp.prosumer.adapter.BookingAdapter
import com.example.smartsolarmobileapp.utils.SessionManager
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BookingListActivity : AppCompatActivity() {

    private lateinit var etSearch: TextInputEditText
    private lateinit var chipGroupFilter: ChipGroup
    private lateinit var pbBookings: ProgressBar
    private lateinit var tvEmpty: TextView
    private lateinit var rvBookings: RecyclerView

    private lateinit var reservationDao: ReservationDao
    private lateinit var sessionManager: SessionManager
    private lateinit var bookingAdapter: BookingAdapter

    private var allBookings: List<Reservation> = emptyList()
    private var currentFilter: String = "ALL"
    private var currentSearchQuery: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_booking_list)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "My Reservations"

        reservationDao = ReservationDao(DatabaseHelper(this))
        sessionManager = SessionManager(this)

        initializeViews()
        setupRecyclerView()
        setupFilterListeners()
        setupSearchListener()
    }

    override fun onResume() {
        super.onResume()
        loadLocalBookings()
        syncRemoteBookings()
    }

    private fun initializeViews() {
        etSearch = findViewById(R.id.et_search_bookings)
        chipGroupFilter = findViewById(R.id.chip_group_filter)
        pbBookings = findViewById(R.id.pb_bookings)
        tvEmpty = findViewById(R.id.tv_empty_bookings)
        rvBookings = findViewById(R.id.rv_bookings)
    }

    private fun setupRecyclerView() {
        bookingAdapter = BookingAdapter(emptyList()) { reservation ->
            openBookingSummary(reservation)
        }
        rvBookings.layoutManager = LinearLayoutManager(this)
        rvBookings.adapter = bookingAdapter
    }

    private fun setupFilterListeners() {
        chipGroupFilter.setOnCheckedStateChangeListener { _, checkedIds ->
            val checkedId = checkedIds.firstOrNull() ?: R.id.chip_all
            currentFilter = when (checkedId) {
                R.id.chip_active -> "ACTIVE"
                R.id.chip_history -> "HISTORY"
                else -> "ALL"
            }
            applyFiltersAndSearch()
        }
    }

    private fun setupSearchListener() {
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                currentSearchQuery = s?.toString()?.trim() ?: ""
                applyFiltersAndSearch()
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    /**
     * Loads local bookings immediately from SQLite database for fast display.
     */
    private fun loadLocalBookings() {
        val userNic = sessionManager.getUserNic() ?: ""
        val localRecords = if (userNic.isNotBlank()) {
            reservationDao.getReservationsByNic(userNic)
        } else {
            reservationDao.getAllReservations()
        }

        allBookings = localRecords
        applyFiltersAndSearch()
    }

    /**
     * Synchronizes fresh reservation records from the C# Web API.
     */
    private fun syncRemoteBookings() {
        pbBookings.visibility = if (allBookings.isEmpty()) View.VISIBLE else View.GONE

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = ApiClient.reservationApi.searchReservations()
                if (response.isSuccessful && response.body() != null) {
                    val remoteBookings = response.body()!!

                    // Update SQLite local persistence
                    reservationDao.insertOrUpdateReservations(remoteBookings)

                    withContext(Dispatchers.Main) {
                        pbBookings.visibility = View.GONE
                        val userNic = sessionManager.getUserNic() ?: ""
                        allBookings = if (userNic.isNotBlank()) {
                            reservationDao.getReservationsByNic(userNic)
                        } else {
                            remoteBookings
                        }
                        applyFiltersAndSearch()
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        pbBookings.visibility = View.GONE
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    pbBookings.visibility = View.GONE
                }
            }
        }
    }

    /**
     * Filters reservations by status category (All, Active/Pending, History) and search term.
     */
    private fun applyFiltersAndSearch() {
        var filtered = when (currentFilter) {
            "ACTIVE" -> allBookings.filter {
                it.status.equals("Pending", ignoreCase = true) || it.status.equals("Approved", ignoreCase = true)
            }
            "HISTORY" -> allBookings.filter {
                it.status.equals("Completed", ignoreCase = true) || it.status.equals("Cancelled", ignoreCase = true)
            }
            else -> allBookings
        }

        if (currentSearchQuery.isNotBlank()) {
            filtered = filtered.filter { res ->
                val nameMatch = res.stationName?.contains(currentSearchQuery, ignoreCase = true) == true
                val idMatch = res.id?.contains(currentSearchQuery, ignoreCase = true) == true
                nameMatch || idMatch
            }
        }

        bookingAdapter.updateData(filtered)
        tvEmpty.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun openBookingSummary(reservation: Reservation) {
        val intent = Intent(this, BookingSummaryActivity::class.java).apply {
            putExtra("EXTRA_RESERVATION_ID", reservation.id)
            putExtra("EXTRA_STATION_NAME", reservation.stationName)
            putExtra("EXTRA_STATION_ID", reservation.stationId)
            putExtra("EXTRA_SCHEDULED_AT", reservation.scheduledAt)
            putExtra("EXTRA_STATUS", reservation.status)
            putExtra("EXTRA_SUMMARY", reservation.summary)
            putExtra("EXTRA_QR_TOKEN", reservation.qrToken)
        }
        startActivity(intent)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
