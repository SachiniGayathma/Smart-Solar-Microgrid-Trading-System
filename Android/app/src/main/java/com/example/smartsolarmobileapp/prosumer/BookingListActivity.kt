/**
 * Manages prosumer reservations with real-time status filtering and search.
 */
package com.example.smartsolarmobileapp.prosumer

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.ImageButton
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.smartsolarmobileapp.R
import com.example.smartsolarmobileapp.prosumer.adapter.BookingAdapter
import com.example.smartsolarmobileapp.api.ApiClient
import com.example.smartsolarmobileapp.database.DatabaseHelper
import com.example.smartsolarmobileapp.database.ReservationDao
import com.example.smartsolarmobileapp.database.StationDao
import com.example.smartsolarmobileapp.models.Reservation
import com.example.smartsolarmobileapp.utils.SessionManager
import com.example.smartsolarmobileapp.utils.UiAlertUtils
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BookingListActivity : AppCompatActivity() {

    private lateinit var etSearch: TextInputEditText
    private lateinit var chipGroupFilter: ChipGroup
    private lateinit var pbBookings: ProgressBar
    private lateinit var layoutEmpty: View
    private lateinit var rvBookings: RecyclerView
    private lateinit var btnHeaderLogout: ImageButton
    private lateinit var bottomNav: BottomNavigationView

    private lateinit var reservationDao: ReservationDao
    private lateinit var stationDao: StationDao
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

        val dbHelper = DatabaseHelper(this)
        reservationDao = ReservationDao(dbHelper)
        stationDao = StationDao(dbHelper)
        sessionManager = SessionManager(this)

        initializeViews()
        setupRecyclerView()
        setupFilterListeners()
        setupSearchListener()
        setupBottomNavigation()
    }

    override fun onResume() {
        super.onResume()
        bottomNav.selectedItemId = R.id.nav_bookings
        loadLocalBookings()
        syncRemoteBookings()
    }

    private fun initializeViews() {
        etSearch = findViewById(R.id.et_search_bookings)
        chipGroupFilter = findViewById(R.id.chip_group_filter)
        pbBookings = findViewById(R.id.pb_bookings)
        layoutEmpty = findViewById(R.id.layout_empty_bookings)
        rvBookings = findViewById(R.id.rv_bookings)
        btnHeaderLogout = findViewById(R.id.btn_header_logout_bookings)
        bottomNav = findViewById(R.id.bottom_nav_bookings)

        findViewById<ImageButton>(R.id.btn_back_booking_list)?.setOnClickListener {
            finish()
        }

        btnHeaderLogout.setOnClickListener {
            confirmLogout()
        }
    }

    private fun setupBottomNavigation() {
        bottomNav.selectedItemId = R.id.nav_bookings
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
                    // Already on Bookings tab
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
        val intent = Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("EXTRA_NOTICE", "Logged out successfully")
        }
        startActivity(intent)
        finish()
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
        etSearch.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                currentSearchQuery = s?.toString()?.trim() ?: ""
                applyFiltersAndSearch()
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
    }

    /**
     * Loads locally persisted reservations immediately into the list.
     */
    private fun loadLocalBookings() {
        val userNic = sessionManager.getUserNic() ?: ""
        if (userNic.isNotBlank()) {
            val localList = reservationDao.getReservationsByNic(userNic)
            allBookings = decorateWithStationNames(localList)
            applyFiltersAndSearch()
        }
    }

    /**
     * Synchronizes live booking data with the central Web API in the background.
     */
    private fun syncRemoteBookings() {
        pbBookings.visibility = if (allBookings.isEmpty()) View.VISIBLE else View.GONE

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Ensure station cache is up to date if online
                val stationResponse = ApiClient.stationApi.getStations()
                if (stationResponse.isSuccessful && stationResponse.body() != null) {
                    stationDao.insertOrUpdateStations(stationResponse.body()!!)
                }
            } catch (_: Exception) {}

            try {
                val response = ApiClient.reservationApi.searchReservations()
                if (response.isSuccessful && response.body() != null) {
                    val remoteBookings = response.body()!!
                    val decoratedBookings = decorateWithStationNames(remoteBookings)

                    // Update SQLite local persistence
                    reservationDao.insertOrUpdateReservations(decoratedBookings)

                    withContext(Dispatchers.Main) {
                        pbBookings.visibility = View.GONE
                        val userNic = sessionManager.getUserNic() ?: ""
                        allBookings = if (userNic.isNotBlank()) {
                            decorateWithStationNames(reservationDao.getReservationsByNic(userNic))
                        } else {
                            decoratedBookings
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

    private fun decorateWithStationNames(reservations: List<Reservation>): List<Reservation> {
        val stationMap = stationDao.getAllStations().associate { it.id to it.name }
        return reservations.map { res ->
            val isKnownStationName = !res.stationName.isNullOrBlank() && res.stationName != res.stationId
            if (isKnownStationName) {
                res
            } else {
                val resolved = stationMap[res.stationId] ?: res.stationId
                res.copy(stationName = resolved)
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
        layoutEmpty.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun openBookingSummary(reservation: Reservation) {
        val resolvedName = reservation.stationName?.takeIf { it.isNotBlank() && it != reservation.stationId }
            ?: stationDao.getStationById(reservation.stationId)?.name
            ?: reservation.stationId

        val intent = Intent(this, BookingSummaryActivity::class.java).apply {
            putExtra("EXTRA_RESERVATION_ID", reservation.id)
            putExtra("EXTRA_STATION_NAME", resolvedName)
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
