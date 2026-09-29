/**
 * Displays active microgrid battery charging stations and handles station selection for slot booking.
 */
package com.example.smartsolarmobileapp.prosumer

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.smartsolarmobileapp.R
import com.example.smartsolarmobileapp.api.ApiClient
import com.example.smartsolarmobileapp.database.DatabaseHelper
import com.example.smartsolarmobileapp.database.StationDao
import com.example.smartsolarmobileapp.models.Station
import com.example.smartsolarmobileapp.prosumer.adapter.StationAdapter
import com.example.smartsolarmobileapp.utils.UiAlertUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class StationSelectActivity : AppCompatActivity() {

    private lateinit var rvStations: RecyclerView
    private lateinit var layoutEmpty: View
    private lateinit var pbStations: ProgressBar

    private lateinit var dbHelper: DatabaseHelper
    private lateinit var stationDao: StationDao
    private lateinit var stationAdapter: StationAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_station_select)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Select Station"

        dbHelper = DatabaseHelper(this)
        stationDao = StationDao(dbHelper)

        initializeViews()
        setupRecyclerView()

        loadLocalStations()
        fetchRemoteStations()
    }

    private fun initializeViews() {
        rvStations = findViewById(R.id.rv_stations)
        layoutEmpty = findViewById(R.id.layout_empty_stations)
        pbStations = findViewById(R.id.pb_stations)

        findViewById<android.widget.ImageButton>(R.id.btn_back_stations)?.setOnClickListener {
            finish()
        }

        findViewById<android.widget.ImageButton>(R.id.btn_view_map_stations)?.setOnClickListener {
            openStationMap()
        }

        findViewById<View>(R.id.card_view_stations_map)?.setOnClickListener {
            openStationMap()
        }

        findViewById<android.widget.ImageButton>(R.id.btn_header_logout_stations)?.setOnClickListener {
            confirmLogout()
        }
    }

    private fun openStationMap() {
        startActivity(Intent(this, com.example.smartsolarmobileapp.operator.MapActivity::class.java))
    }

    private fun confirmLogout() {
        com.example.smartsolarmobileapp.utils.UiAlertUtils.showModernDialog(
            context = this,
            title = "Log Out",
            message = "Are you sure you want to end your prosumer session and return to the login screen?",
            type = com.example.smartsolarmobileapp.utils.UiAlertUtils.AlertType.WARNING,
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

    private fun setupRecyclerView() {
        stationAdapter = StationAdapter(emptyList()) { selectedStation ->
            onStationSelected(selectedStation)
        }
        rvStations.layoutManager = LinearLayoutManager(this)
        rvStations.adapter = stationAdapter
    }

    /**
     * Loads locally cached active stations immediately to prevent blank UI.
     */
    private fun loadLocalStations() {
        val cached = stationDao.getActiveStations()
        if (cached.isNotEmpty()) {
            stationAdapter.updateData(cached)
            layoutEmpty.visibility = View.GONE
        }
    }

    /**
     * Fetches fresh active stations from the central Web API and updates SQLite cache.
     */
    private fun fetchRemoteStations() {
        pbStations.visibility = View.VISIBLE

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = ApiClient.stationApi.getStations()

                withContext(Dispatchers.Main) {
                    pbStations.visibility = View.GONE
                    if (response.isSuccessful && response.body() != null) {
                        val activeStations = response.body()!!.filter {
                            it.status.equals("Active", ignoreCase = true)
                        }

                        // Update local SQLite cache
                        stationDao.insertOrUpdateStations(activeStations)

                        // Update list display
                        stationAdapter.updateData(activeStations)
                        layoutEmpty.visibility = if (activeStations.isEmpty()) View.VISIBLE else View.GONE
                    } else {
                        handleFetchFailure()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    pbStations.visibility = View.GONE
                    handleFetchFailure()
                }
            }
        }
    }

    private fun handleFetchFailure() {
        val localStations = stationDao.getActiveStations()
        if (localStations.isNotEmpty()) {
            stationAdapter.updateData(localStations)
            layoutEmpty.visibility = View.GONE
            UiAlertUtils.showToast(this, "Showing cached station directory (Offline)", UiAlertUtils.AlertType.INFO)
        } else {
            layoutEmpty.visibility = View.VISIBLE
        }
    }

    /**
     * Navigates to the slot booking calendar passing selected station metadata.
     */
    private fun onStationSelected(station: Station) {
        val intent = Intent(this, SlotBookingActivity::class.java).apply {
            putExtra("EXTRA_STATION_ID", station.id)
            putExtra("EXTRA_STATION_NAME", station.name)
            putExtra("EXTRA_STATION_CAPACITY", station.capacityKwh)
            putExtra("EXTRA_STATION_SCHEDULE", station.schedule)
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
