/**
 * Binds nearby stations for the operator map list.
 */
package com.example.smartsolarmobileapp.operator.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.smartsolarmobileapp.R
import com.example.smartsolarmobileapp.models.Station

class OperatorStationAdapter(
    private var stations: List<Station>,
    private var distancesKm: Map<String, Double> = emptyMap(),
    private val onStationClicked: (Station) -> Unit
) : RecyclerView.Adapter<OperatorStationAdapter.Holder>() {

    fun update(stations: List<Station>, distancesKm: Map<String, Double>) {
        this.stations = stations
        this.distancesKm = distancesKm
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_operator_station, parent, false)
        return Holder(view)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(stations[position])
    }

    override fun getItemCount(): Int = stations.size

    inner class Holder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val name: TextView = itemView.findViewById(R.id.tv_operator_station_name)
        private val meta: TextView = itemView.findViewById(R.id.tv_operator_station_meta)
        private val distance: TextView = itemView.findViewById(R.id.tv_operator_station_distance)

        fun bind(station: Station) {
            name.text = station.name
            meta.text = "${station.capacityKwh} kWh · ${station.batteryStorageSlots} battery slots · ${station.schedule}"
            val km = distancesKm[station.id]
            distance.text = if (km == null) {
                "Lat ${station.latitude}, Lng ${station.longitude}"
            } else {
                String.format("%.1f km away", km)
            }
            itemView.setOnClickListener { onStationClicked(station) }
        }
    }
}
