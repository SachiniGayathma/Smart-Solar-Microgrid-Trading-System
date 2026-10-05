/**
 * RecyclerView adapter binding energy slots with selection handling,
 * modification state awareness, and current booking highlighting.
 */
package com.example.smartsolarmobileapp.prosumer.adapter

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import com.example.smartsolarmobileapp.R
import com.example.smartsolarmobileapp.models.Slot
import com.example.smartsolarmobileapp.utils.DateTimeUtils

class SlotAdapter(
    private var slots: List<Slot>,
    private val onSlotSelected: (Slot, Boolean) -> Unit
) : RecyclerView.Adapter<SlotAdapter.SlotViewHolder>() {

    private var selectedPosition: Int = -1
    private var currentBookedSlotIso: String? = null

    fun setCurrentBookedSlot(slotIso: String?) {
        currentBookedSlotIso = slotIso
    }

    fun updateData(newSlots: List<Slot>) {
        slots = newSlots
        selectedPosition = -1

        // Pre-select current booked slot if present on this date
        if (!currentBookedSlotIso.isNullOrBlank()) {
            val currentTargetDate = DateTimeUtils.parseIsoString(currentBookedSlotIso)
            if (currentTargetDate != null) {
                val matchedIndex = slots.indexOfFirst { s ->
                    val sDate = DateTimeUtils.parseIsoString(s.startTime)
                    sDate != null && sDate.time == currentTargetDate.time
                }
                if (matchedIndex != -1) {
                    selectedPosition = matchedIndex
                }
            }
        }

        notifyDataSetChanged()
    }

    fun getSelectedSlot(): Slot? {
        return if (selectedPosition in slots.indices) slots[selectedPosition] else null
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SlotViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_slot, parent, false)
        return SlotViewHolder(view)
    }

    override fun onBindViewHolder(holder: SlotViewHolder, position: Int) {
        val slot = slots[position]
        holder.bind(slot, position == selectedPosition)
    }

    override fun getItemCount(): Int = slots.size

    inner class SlotViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val cardRoot: CardView = itemView.findViewById(R.id.card_slot_root)
        private val tvTime: TextView = itemView.findViewById(R.id.tv_item_slot_time)
        private val tvAvailability: TextView = itemView.findViewById(R.id.tv_item_slot_availability)
        private val tvCurrentBadge: TextView = itemView.findViewById(R.id.tv_item_slot_current_badge)
        private val rbSelect: RadioButton = itemView.findViewById(R.id.rb_item_slot_select)

        fun bind(slot: Slot, isSelected: Boolean) {
            val startParsed = DateTimeUtils.parseIsoString(slot.startTime)
            val endParsed = DateTimeUtils.parseIsoString(slot.endTime)

            val timeDisplay = if (startParsed != null && endParsed != null) {
                "${DateTimeUtils.formatDisplayTime(startParsed)} - ${DateTimeUtils.formatDisplayTime(endParsed)}"
            } else if (slot.startTime.isNotBlank()) {
                "${slot.startTime} - ${slot.endTime}"
            } else {
                "Energy Charging Slot"
            }

            tvTime.text = timeDisplay

            // Check if this slot corresponds to the user's current booked slot
            val isCurrentSlot = !currentBookedSlotIso.isNullOrBlank() && run {
                val currentTargetDate = DateTimeUtils.parseIsoString(currentBookedSlotIso)
                startParsed != null && currentTargetDate != null && startParsed.time == currentTargetDate.time
            }

            if (isCurrentSlot) {
                tvCurrentBadge.visibility = View.VISIBLE
            } else {
                tvCurrentBadge.visibility = View.GONE
            }

            val now = java.util.Date()
            val isPastTime = startParsed != null && startParsed.before(now)
            val isBookable = (slot.availableCapacity >= 1 || isCurrentSlot) && 
                             slot.status.equals("Available", ignoreCase = true) && 
                             !isPastTime

            if (isPastTime) {
                tvAvailability.text = "Time slot has passed (Expired)"
                tvAvailability.setTextColor(Color.parseColor("#9E9E9E"))
                cardRoot.alpha = 0.45f
                rbSelect.isEnabled = false
            } else if (isCurrentSlot) {
                tvAvailability.text = "Currently Booked by You"
                tvAvailability.setTextColor(Color.parseColor("#D97706"))
                cardRoot.alpha = 1.0f
                rbSelect.isEnabled = true
            } else if (isBookable) {
                tvAvailability.text = "Available Capacity: ${slot.availableCapacity} slot(s)"
                tvAvailability.setTextColor(Color.parseColor("#2E7D32"))
                cardRoot.alpha = 1.0f
                rbSelect.isEnabled = true
            } else {
                tvAvailability.text = "Fully Booked (Unavailable)"
                tvAvailability.setTextColor(Color.parseColor("#C62828"))
                cardRoot.alpha = 0.5f
                rbSelect.isEnabled = false
            }

            rbSelect.isChecked = isSelected

            if (isSelected) {
                if (isCurrentSlot) {
                    cardRoot.setCardBackgroundColor(Color.parseColor("#FEF3C7"))
                } else {
                    cardRoot.setCardBackgroundColor(Color.parseColor("#E8F5E9"))
                }
            } else {
                cardRoot.setCardBackgroundColor(Color.WHITE)
            }

            cardRoot.setOnClickListener {
                if (isPastTime) {
                    com.example.smartsolarmobileapp.utils.UiAlertUtils.showSnackbar(
                        itemView,
                        "This time slot has already passed. Please select a future time slot.",
                        com.example.smartsolarmobileapp.utils.UiAlertUtils.AlertType.WARNING
                    )
                    return@setOnClickListener
                }
                if (isBookable || isCurrentSlot) {
                    val prev = selectedPosition
                    val currentPos = bindingAdapterPosition
                    if (currentPos != RecyclerView.NO_POSITION) {
                        selectedPosition = currentPos
                        if (prev != -1) notifyItemChanged(prev)
                        notifyItemChanged(selectedPosition)
                        onSlotSelected(slot, isCurrentSlot)
                    }
                } else {
                    com.example.smartsolarmobileapp.utils.UiAlertUtils.showSnackbar(
                        itemView,
                        "This slot is fully booked. Please choose another time.",
                        com.example.smartsolarmobileapp.utils.UiAlertUtils.AlertType.WARNING
                    )
                }
            }
        }
    }
}
