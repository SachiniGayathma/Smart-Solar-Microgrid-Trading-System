/**
 * RecyclerView adapter binding active charging stations to card list items.
 */
package com.example.smartsolarmobileapp.prosumer.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import com.example.smartsolarmobileapp.R
import com.example.smartsolarmobileapp.models.Station

class StationAdapter(
    private var stations: List<Station>,
    private val onStationClicked: (Station) -> Unit
) : RecyclerView.Adapter<StationAdapter.StationViewHolder>() {

    class StationViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val cardItem: CardView = itemView.findViewById(R.id.card_station_item)
        val tvName: TextView = itemView.findViewById(R.id.tv_item_station_name)
        val tvDetails: TextView = itemView.findViewById(R.id.tv_item_station_details)
        val tvSchedule: TextView = itemView.findViewById(R.id.tv_item_station_schedule)
        val tvStatus: TextView = itemView.findViewById(R.id.tv_item_station_status)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StationViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_station, parent, false)
        return StationViewHolder(view)
    }

    override fun onBindViewHolder(holder: StationViewHolder, position: Int) {
        val station = stations[position]
        holder.tvName.text = station.name
        holder.tvDetails.text = "Capacity: ${station.capacityKwh} kWh • ${station.batteryStorageSlots} Storage Slots"
        holder.tvSchedule.text = "Operating Hours: ${station.schedule}"
        holder.tvStatus.text = "Status: ${station.status}"

        holder.cardItem.setOnClickListener {
            onStationClicked(station)
        }
    }

    override fun getItemCount(): Int = stations.size

    /**
     * Updates adapter dataset and refreshes item list.
     */
    fun updateData(newStations: List<Station>) {
        this.stations = newStations
        notifyDataSetChanged()
    }
}
