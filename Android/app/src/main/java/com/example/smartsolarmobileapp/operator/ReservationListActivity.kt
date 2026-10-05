/**
 * Lists bookings for the operator, with search, status filters, and approval.
 */
package com.example.smartsolarmobileapp.operator

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.smartsolarmobileapp.R
import com.example.smartsolarmobileapp.models.Reservation
import com.example.smartsolarmobileapp.prosumer.adapter.BookingAdapter
import com.example.smartsolarmobileapp.utils.ScreenInsets
import com.example.smartsolarmobileapp.utils.UiAlertUtils
import kotlinx.coroutines.launch

class ReservationListActivity : AppCompatActivity() {

    private lateinit var repository: OperatorRepository
    private lateinit var adapter: BookingAdapter
    private var statusFilter: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_operator_reservations)
        ScreenInsets.apply(findViewById(android.R.id.content), extraHorizontalDp = 24, extraVerticalDp = 22)
        repository = OperatorRepository(this)

        val recycler = findViewById<RecyclerView>(R.id.rv_operator_reservations)
        adapter = BookingAdapter(
            emptyList(),
            R.layout.item_operator_booking,
            webStatusColors = false,
            operatorGreenStatus = true
        ) { reservation ->
            onReservationClicked(reservation)
        }
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        findViewById<View>(R.id.btn_filter_all).setOnClickListener { selectFilter(null) }
        findViewById<View>(R.id.btn_filter_pending).setOnClickListener { selectFilter("Pending") }
        findViewById<View>(R.id.btn_filter_approved).setOnClickListener { selectFilter("Approved") }
        findViewById<View>(R.id.btn_filter_completed).setOnClickListener { selectFilter("Completed") }
        highlightFilter()

        findViewById<EditText>(R.id.et_reservation_search).setOnEditorActionListener { _, _, _ ->
            load()
            true
        }
    }

    override fun onResume() {
        super.onResume()
        load()
    }

    private fun selectFilter(status: String?) {
        statusFilter = status
        highlightFilter()
        load()
    }

    private fun highlightFilter() {
        val selectedId = when (statusFilter) {
            "Pending" -> R.id.btn_filter_pending
            "Approved" -> R.id.btn_filter_approved
            "Completed" -> R.id.btn_filter_completed
            else -> R.id.btn_filter_all
        }
        listOf(
            R.id.btn_filter_all,
            R.id.btn_filter_pending,
            R.id.btn_filter_approved,
            R.id.btn_filter_completed
        ).forEach { id ->
            val button = findViewById<Button>(id)
            val selected = id == selectedId
            button.isSelected = selected
            val background = if (selected) R.color.solar_green_primary else R.color.solar_surface
            val label = if (selected) R.color.white else R.color.solar_slate_dark
            ViewCompat.setBackgroundTintList(
                button,
                ColorStateList.valueOf(ContextCompat.getColor(this, background))
            )
            button.setTextColor(ContextCompat.getColor(this, label))
        }
    }

    private fun load() {
        val search = findViewById<EditText>(R.id.et_reservation_search).text.toString()
        lifecycleScope.launch {
            try {
                when (val result = repository.loadReservations(statusFilter, search)) {
                    is OperatorLoad.Fresh -> show(result.data, false)
                    is OperatorLoad.Cached -> show(result.data, true)
                    is OperatorLoad.Failed -> {
                        show(emptyList(), false)
                        showNotice("Could not load bookings", result.message)
                    }
                }
            } catch (e: Exception) {
                show(emptyList(), false)
                showNotice("Could not load bookings", e.message)
            }
        }
    }

    private fun show(reservations: List<Reservation>, fromCache: Boolean) {
        adapter.updateData(reservations)
        val empty = reservations.isEmpty()
        findViewById<TextView>(R.id.tv_reservation_empty).visibility = if (empty) View.VISIBLE else View.GONE
        findViewById<RecyclerView>(R.id.rv_operator_reservations).visibility = if (empty) View.GONE else View.VISIBLE
        if (fromCache && !isFinishing) {
            showNotice("Saved bookings", "The server could not be reached, so these are the bookings saved on this phone.")
        }
    }

    private fun showNotice(
        title: String,
        message: String?,
        type: UiAlertUtils.AlertType = UiAlertUtils.AlertType.WARNING
    ) {
        if (isFinishing) return
        UiAlertUtils.showModernDialog(this, title, message ?: "Please try again.", type)
    }

    private fun onReservationClicked(reservation: Reservation) {
        val id = reservation.id
        if (id.isNullOrBlank()) return
        if (reservation.status.equals("Pending", ignoreCase = true)) {
            UiAlertUtils.showModernDialog(
                this,
                "Approve booking",
                "Approve this reservation and issue a QR token for ${reservation.prosumerNic}?",
                UiAlertUtils.AlertType.INFO,
                positiveButtonText = "Approve",
                onPositiveClick = { approve(id) },
                negativeButtonText = "Cancel"
            )
        } else {
            UiAlertUtils.showModernDialog(
                this,
                reservation.status,
                "NIC: ${reservation.prosumerNic}\nStation: ${reservation.stationName ?: reservation.stationId}",
                UiAlertUtils.AlertType.INFO
            )
        }
    }

    private fun approve(id: String) {
        lifecycleScope.launch {
            try {
                when (val result = repository.approveReservation(id)) {
                    is OperatorLoad.Fresh -> {
                        showNotice(
                            "Booking approved",
                            result.data.summary ?: "A QR token was issued.",
                            UiAlertUtils.AlertType.SUCCESS
                        )
                        load()
                    }
                    is OperatorLoad.Failed -> showNotice("Could not approve", result.message)
                    is OperatorLoad.Cached -> Unit
                }
            } catch (e: Exception) {
                showNotice("Could not approve", e.message)
            }
        }
    }
}
