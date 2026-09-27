/**
 * Lists bookings for the operator, with search, status filters, and approval.
 */
package com.example.smartsolarmobileapp.operator

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.smartsolarmobileapp.R
import com.example.smartsolarmobileapp.models.Reservation
import com.example.smartsolarmobileapp.prosumer.adapter.BookingAdapter
import kotlinx.coroutines.launch

class ReservationListActivity : AppCompatActivity() {

    private lateinit var repository: OperatorRepository
    private lateinit var adapter: BookingAdapter
    private var statusFilter: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_operator_reservations)
        repository = OperatorRepository(this)

        val recycler = findViewById<RecyclerView>(R.id.rv_operator_reservations)
        adapter = BookingAdapter(
            emptyList(),
            R.layout.item_operator_booking,
            webStatusColors = true
        ) { reservation ->
            onReservationClicked(reservation)
        }
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        findViewById<View>(R.id.btn_filter_all).setOnClickListener { statusFilter = null; load() }
        findViewById<View>(R.id.btn_filter_pending).setOnClickListener { statusFilter = "Pending"; load() }
        findViewById<View>(R.id.btn_filter_approved).setOnClickListener { statusFilter = "Approved"; load() }
        findViewById<View>(R.id.btn_filter_completed).setOnClickListener { statusFilter = "Completed"; load() }

        findViewById<EditText>(R.id.et_reservation_search).setOnEditorActionListener { _, _, _ ->
            load()
            true
        }
    }

    override fun onResume() {
        super.onResume()
        load()
    }

    private fun load() {
        val search = findViewById<EditText>(R.id.et_reservation_search).text.toString()
        lifecycleScope.launch {
            when (val result = repository.loadReservations(statusFilter, search)) {
                is OperatorLoad.Fresh -> show(result.data, false)
                is OperatorLoad.Cached -> show(result.data, true)
                is OperatorLoad.Failed -> {
                    show(emptyList(), false)
                    Toast.makeText(this@ReservationListActivity, result.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun show(reservations: List<Reservation>, fromCache: Boolean) {
        adapter.updateData(reservations)
        findViewById<TextView>(R.id.tv_reservation_empty).visibility =
            if (reservations.isEmpty()) View.VISIBLE else View.GONE
        if (fromCache) {
            Toast.makeText(this, "Showing saved reservations", Toast.LENGTH_SHORT).show()
        }
    }

    private fun onReservationClicked(reservation: Reservation) {
        val id = reservation.id
        if (id.isNullOrBlank()) return
        if (reservation.status.equals("Pending", ignoreCase = true)) {
            AlertDialog.Builder(this)
                .setTitle("Approve booking")
                .setMessage("Approve this reservation and issue a QR token for ${reservation.prosumerNic}?")
                .setPositiveButton("Approve") { _, _ -> approve(id) }
                .setNegativeButton("Cancel", null)
                .show()
        } else {
            AlertDialog.Builder(this)
                .setTitle(reservation.status)
                .setMessage(
                    "NIC: ${reservation.prosumerNic}\n" +
                        "Station: ${reservation.stationName ?: reservation.stationId}\n" +
                        "When: ${reservation.scheduledAt}\n" +
                        (reservation.summary ?: "")
                )
                .setPositiveButton("OK", null)
                .show()
        }
    }

    private fun approve(id: String) {
        lifecycleScope.launch {
            when (val result = repository.approveReservation(id)) {
                is OperatorLoad.Fresh -> {
                    Toast.makeText(
                        this@ReservationListActivity,
                        result.data.summary ?: "Reservation approved.",
                        Toast.LENGTH_LONG
                    ).show()
                    load()
                }
                is OperatorLoad.Failed -> {
                    Toast.makeText(this@ReservationListActivity, result.message, Toast.LENGTH_LONG).show()
                }
                is OperatorLoad.Cached -> Unit
            }
        }
    }
}
