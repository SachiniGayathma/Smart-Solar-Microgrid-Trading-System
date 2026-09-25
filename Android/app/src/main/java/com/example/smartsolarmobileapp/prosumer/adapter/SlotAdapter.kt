/**
 * RecyclerView adapter binding 30-minute bookable energy slots with selection handling.
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
    private val onSlotSelected: (Slot) -> Unit
) : RecyclerView.Adapter<SlotAdapter.SlotViewHolder>() {

    private var selectedPosition: Int = -1

    fun updateData(newSlots: List<Slot>) {
        slots = newSlots
        selectedPosition = -1
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
        private val rbSelect: RadioButton = itemView.findViewById(R.id.rb_item_slot_select)

        fun bind(slot: Slot, isSelected: Boolean) {
            val startParsed = DateTimeUtils.parseIsoString(slot.startTime)
            val endParsed = DateTimeUtils.parseIsoString(slot.endTime)

            val timeDisplay = if (startParsed != null && endParsed != null) {
                "${DateTimeUtils.formatDisplayTime(startParsed)} - ${DateTimeUtils.formatDisplayTime(endParsed)}"
            } else if (slot.startTime.isNotBlank()) {
                "${slot.startTime} - ${slot.endTime}"
            } else {
                "30-Minute Charging Slot"
            }

            tvTime.text = timeDisplay

            val isBookable = slot.availableCapacity >= 1 && slot.status.equals("Available", ignoreCase = true)

            if (isBookable) {
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
                cardRoot.setCardBackgroundColor(Color.parseColor("#E8F5E9"))
            } else {
                cardRoot.setCardBackgroundColor(Color.WHITE)
            }

            if (isBookable) {
                cardRoot.setOnClickListener {
                    val prev = selectedPosition
                    val currentPos = bindingAdapterPosition
                    if (currentPos != RecyclerView.NO_POSITION) {
                        selectedPosition = currentPos
                        if (prev != -1) notifyItemChanged(prev)
                        notifyItemChanged(selectedPosition)
                        onSlotSelected(slot)
                    }
                }
            } else {
                cardRoot.setOnClickListener(null)
            }
        }
    }
}
