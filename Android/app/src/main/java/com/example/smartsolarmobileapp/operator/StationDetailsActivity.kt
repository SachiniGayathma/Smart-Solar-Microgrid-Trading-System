/**
 * Station details and battery-slot availability update for a grid operator.
 */
package com.example.smartsolarmobileapp.operator

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.smartsolarmobileapp.R
import com.example.smartsolarmobileapp.models.Station
import com.example.smartsolarmobileapp.utils.UiAlertUtils
import kotlinx.coroutines.launch

class StationDetailsActivity : AppCompatActivity() {

    private lateinit var repository: OperatorRepository
    private var stationId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_station_details)
        repository = OperatorRepository(this)
        stationId = intent.getStringExtra(EXTRA_STATION_ID).orEmpty()
        if (stationId.isBlank()) {
            finish()
            return
        }
        findViewById<View>(R.id.btn_save_availability).setOnClickListener { save() }
        load()
    }

    private fun load() {
        findViewById<ProgressBar>(R.id.pb_station).visibility = View.VISIBLE
        lifecycleScope.launch {
            findViewById<ProgressBar>(R.id.pb_station).visibility = View.GONE
            when (val result = repository.loadStation(stationId)) {
                is OperatorLoad.Fresh -> bind(result.data)
                is OperatorLoad.Cached -> {
                    bind(result.data)
                    notify("Saved station", "Showing the station saved on this phone.", UiAlertUtils.AlertType.WARNING)
                }
                is OperatorLoad.Failed -> {
                    notify("Could not load station", result.message, UiAlertUtils.AlertType.ERROR)
                }
            }
        }
    }

    private fun bind(station: Station) {
        findViewById<TextView>(R.id.tv_station_name).text = station.name
        findViewById<TextView>(R.id.tv_station_status).text = station.status
        findViewById<TextView>(R.id.tv_station_location).text =
            "Location: ${station.latitude}, ${station.longitude}"
        findViewById<TextView>(R.id.tv_station_capacity).text =
            "Capacity: ${station.capacityKwh} kWh · ${station.batteryStorageSlots} battery slots"
        findViewById<TextView>(R.id.tv_station_schedule).text = "Schedule: ${station.schedule}"
        findViewById<EditText>(R.id.et_battery_slots).setText(station.batteryStorageSlots.toString())
    }

    private fun save() {
        val slots = findViewById<EditText>(R.id.et_battery_slots).text.toString().toIntOrNull()
        if (slots == null || slots < 0) {
            notify("Check the slot count", "Enter a whole number of battery slots, 0 or more.", UiAlertUtils.AlertType.WARNING)
            return
        }
        findViewById<ProgressBar>(R.id.pb_station).visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                findViewById<ProgressBar>(R.id.pb_station).visibility = View.GONE
                when (val result = repository.updateBatterySlots(stationId, slots)) {
                    is OperatorLoad.Fresh -> {
                        bind(result.data)
                        notify(
                            "Availability updated",
                            "${result.data.name} now has ${result.data.batteryStorageSlots} battery slots.",
                            UiAlertUtils.AlertType.SUCCESS
                        )
                    }
                    is OperatorLoad.Failed -> {
                        notify("Could not update availability", result.message, UiAlertUtils.AlertType.ERROR)
                    }
                    is OperatorLoad.Cached -> Unit
                }
            } catch (e: Exception) {
                findViewById<ProgressBar>(R.id.pb_station).visibility = View.GONE
                notify("Could not update availability", e.message, UiAlertUtils.AlertType.ERROR)
            }
        }
    }

    private fun notify(title: String, message: String?, type: UiAlertUtils.AlertType) {
        if (isFinishing) return
        UiAlertUtils.showModernDialog(this, title, message ?: "Please try again.", type)
    }

    companion object {
        const val EXTRA_STATION_ID = "station_id"
    }
}
