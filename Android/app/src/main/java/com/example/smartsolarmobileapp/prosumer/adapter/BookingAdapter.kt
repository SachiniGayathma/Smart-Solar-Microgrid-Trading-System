/**
 * RecyclerView adapter binding prosumer energy slot reservations.
 */
package com.example.smartsolarmobileapp.prosumer.adapter

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.smartsolarmobileapp.R
import com.example.smartsolarmobileapp.models.Reservation
import com.example.smartsolarmobileapp.utils.DateTimeUtils

class BookingAdapter(
    private var bookings: List<Reservation>,
    private val layoutRes: Int = R.layout.item_booking,
    private val webStatusColors: Boolean = false,
    private val operatorGreenStatus: Boolean = false,
    private val onBookingClick: (Reservation) -> Unit
) : RecyclerView.Adapter<BookingAdapter.BookingViewHolder>() {

    fun updateData(newBookings: List<Reservation>) {
        bookings = newBookings
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BookingViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(layoutRes, parent, false)
        return BookingViewHolder(view)
    }

    override fun onBindViewHolder(holder: BookingViewHolder, position: Int) {
        val booking = bookings[position]
        holder.bind(booking)
    }

    override fun getItemCount(): Int = bookings.size

    inner class BookingViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val cardRoot: View = itemView.findViewById(R.id.card_booking_root) ?: itemView
        private val tvId: TextView = itemView.findViewById(R.id.tv_item_booking_id)
        private val tvStatus: TextView = itemView.findViewById(R.id.tv_item_booking_status)
        private val tvStation: TextView = itemView.findViewById(R.id.tv_item_booking_station)
        private val tvTime: TextView = itemView.findViewById(R.id.tv_item_booking_time)

        fun bind(booking: Reservation) {
            val displayId = booking.id?.let {
                if (it.length > 8) it.take(8).uppercase() else it
            } ?: "N/A"
            tvId.text = "Booking #$displayId"

            tvStation.text = "Station: ${booking.stationName ?: booking.stationId}"

            val parsedDate = DateTimeUtils.parseIsoString(booking.scheduledAt)
            if (parsedDate != null) {
                tvTime.text = "Scheduled: ${DateTimeUtils.formatDisplayDate(parsedDate)} at ${DateTimeUtils.formatDisplayTime(parsedDate)}"
            } else {
                tvTime.text = "Scheduled: ${booking.scheduledAt}"
            }

            tvStatus.text = booking.status

            when {
                operatorGreenStatus -> applyOperatorGreenStatus(booking.status)
                booking.status.equals("Approved", ignoreCase = true) -> {
                    tvStatus.setTextColor(Color.parseColor(if (webStatusColors) "#0F7A4A" else "#2E7D32"))
                    tvStatus.setBackgroundColor(Color.parseColor(if (webStatusColors) "#E5F6EE" else "#E8F5E9"))
                }
                booking.status.equals("Pending", ignoreCase = true) -> {
                    tvStatus.setTextColor(Color.parseColor(if (webStatusColors) "#8A5A00" else "#E65100"))
                    tvStatus.setBackgroundColor(Color.parseColor(if (webStatusColors) "#FFF4D6" else "#FFF3E0"))
                }
                booking.status.equals("Cancelled", ignoreCase = true) -> {
                    tvStatus.setTextColor(Color.parseColor(if (webStatusColors) "#8D3D36" else "#C62828"))
                    tvStatus.setBackgroundColor(Color.parseColor(if (webStatusColors) "#F8E9E6" else "#FFEBEE"))
                }
                booking.status.equals("Completed", ignoreCase = true) -> {
                    tvStatus.setTextColor(Color.parseColor(if (webStatusColors) "#0E5F8A" else "#1565C0"))
                    tvStatus.setBackgroundColor(Color.parseColor(if (webStatusColors) "#E7F3FA" else "#E3F2FD"))
                }
                else -> {
                    tvStatus.setTextColor(Color.parseColor("#555555"))
                    tvStatus.setBackgroundColor(Color.parseColor("#EEEEEE"))
                }
            }

            val clickListener = View.OnClickListener {
                onBookingClick(booking)
            }
            cardRoot.setOnClickListener(clickListener)
            itemView.setOnClickListener(clickListener)
        }

        private fun applyOperatorGreenStatus(status: String) {
            val green = Color.parseColor("#0D7A46")
            when {
                status.equals("Completed", ignoreCase = true) -> {
                    tvStatus.setTextColor(green)
                    tvStatus.setBackgroundColor(Color.parseColor("#DCFCE7"))
                }
                status.equals("Pending", ignoreCase = true) ||
                    status.equals("Approved", ignoreCase = true) -> {
                    tvStatus.setTextColor(green)
                    tvStatus.setBackgroundColor(Color.WHITE)
                }
                status.equals("Cancelled", ignoreCase = true) -> {
                    tvStatus.setTextColor(Color.parseColor("#DC2626"))
                    tvStatus.setBackgroundColor(Color.parseColor("#FEE2E2"))
                }
                else -> {
                    tvStatus.setTextColor(green)
                    tvStatus.setBackgroundColor(Color.WHITE)
                }
            }
        }
    }
}
