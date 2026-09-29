/**
 * Shows active stations on a Mapbox map and lists them by distance from the operator.
 */
package com.example.smartsolarmobileapp.operator

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.RectF
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.smartsolarmobileapp.R
import com.example.smartsolarmobileapp.database.DatabaseHelper
import com.example.smartsolarmobileapp.database.ReservationDao
import com.example.smartsolarmobileapp.database.StationDao
import com.example.smartsolarmobileapp.models.Station
import com.example.smartsolarmobileapp.operator.adapter.OperatorStationAdapter
import com.example.smartsolarmobileapp.prosumer.BookingSummaryActivity
import com.example.smartsolarmobileapp.prosumer.SlotBookingActivity
import com.example.smartsolarmobileapp.utils.GeoUtils
import com.example.smartsolarmobileapp.utils.SessionManager
import com.example.smartsolarmobileapp.utils.UiAlertUtils
import com.google.android.gms.location.LocationServices
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import kotlinx.coroutines.launch

class MapActivity : AppCompatActivity() {

    private lateinit var repository: OperatorRepository
    private lateinit var adapter: OperatorStationAdapter
    private lateinit var mapView: MapView
    private var mapLibreMap: MapLibreMap? = null
    private var stations: List<Station> = emptyList()
    private var userLatLng: LatLng? = null

    private val locationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) readLocation() else render(stations)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        MapLibre.getInstance(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_map)
        repository = OperatorRepository(this)

        val recycler = findViewById<RecyclerView>(R.id.rv_operator_stations)
        adapter = OperatorStationAdapter(emptyList()) { station -> openStation(station.id) }
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        mapView = findViewById(R.id.map_stations)
        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync { map ->
            mapLibreMap = map
            map.uiSettings.isAttributionEnabled = true
            map.uiSettings.isLogoEnabled = true
            map.setStyle(STREET_STYLE) {
                plot(stations)
            }
            map.addOnMapClickListener { latLng ->
                openNearestPin(latLng)
                true
            }
        }

        if (hasLocationPermission()) {
            readLocation()
        } else {
            locationPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        loadStations()
    }

    override fun onStart() {
        super.onStart()
        mapView.onStart()
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        mapView.onPause()
        super.onPause()
    }

    override fun onStop() {
        mapView.onStop()
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView.onSaveInstanceState(outState)
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView.onLowMemory()
    }

    override fun onDestroy() {
        mapView.onDestroy()
        super.onDestroy()
    }

    private fun readLocation() {
        if (!hasLocationPermission()) return
        LocationServices.getFusedLocationProviderClient(this).lastLocation
            .addOnSuccessListener { location ->
                if (location == null) return@addOnSuccessListener
                userLatLng = LatLng(location.latitude, location.longitude)
                render(stations)
            }
    }

    private fun loadStations() {
        lifecycleScope.launch {
            when (val result = repository.loadStations()) {
                is OperatorLoad.Fresh -> render(result.data)
                is OperatorLoad.Cached -> {
                    Toast.makeText(this@MapActivity, "Showing saved stations", Toast.LENGTH_SHORT).show()
                    render(result.data)
                }
                is OperatorLoad.Failed -> {
                    Toast.makeText(this@MapActivity, result.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun render(loaded: List<Station>) {
        val origin = userLatLng
        val distances = if (origin != null) {
            loaded.associate { station ->
                station.id to GeoUtils.distanceKm(
                    origin.latitude,
                    origin.longitude,
                    station.latitude,
                    station.longitude
                )
            }
        } else {
            emptyMap()
        }
        stations = loaded
            .filter { it.status.equals("Active", ignoreCase = true) }
            .let { active ->
                if (distances.isEmpty()) active
                else active.sortedBy { distances[it.id] ?: Double.MAX_VALUE }
            }
        adapter.update(stations, distances)
        findViewById<TextView>(R.id.tv_map_note).text = if (origin == null) {
            "${stations.size} active stations"
        } else {
            "${stations.size} active stations, nearest first"
        }
        plot(stations)
    }

    private fun plot(stations: List<Station>) {
        val map = mapLibreMap ?: return
        val style = map.style ?: return
        val features = stations.filter { it.hasMapPosition() }.map { station ->
            Feature.fromGeometry(
                Point.fromLngLat(station.longitude, station.latitude)
            ).apply {
                addStringProperty("id", station.id)
                addStringProperty("name", station.name)
            }
        }
        val collection = FeatureCollection.fromFeatures(features)
        val existing = style.getSourceAs<GeoJsonSource>(SOURCE_ID)
        if (existing == null) {
            style.addSource(GeoJsonSource(SOURCE_ID, collection))
            style.addLayer(
                CircleLayer(LAYER_ID, SOURCE_ID).withProperties(
                    circleRadius(8f),
                    circleColor("#1B5E20"),
                    circleStrokeWidth(2f),
                    circleStrokeColor("#FFFFFF")
                )
            )
        } else {
            existing.setGeoJson(collection)
        }

        frameStations(stations)
    }

    private fun frameStations(stations: List<Station>) {
        val map = mapLibreMap ?: return
        val stationPoints = stations.filter { it.hasMapPosition() }.map { LatLng(it.latitude, it.longitude) }
        val points = buildList {
            addAll(stationPoints)
            val origin = userLatLng
            if (origin != null && stationPoints.any { point ->
                    GeoUtils.distanceKm(origin.latitude, origin.longitude, point.latitude, point.longitude) < 150.0
                }
            ) {
                add(origin)
            }
            if (isEmpty() && origin != null) add(origin)
        }
        if (points.isEmpty()) return
        if (mapView.width == 0 || mapView.height == 0) {
            mapView.post { frameStations(stations) }
            return
        }
        if (points.size == 1) {
            map.cameraPosition = CameraPosition.Builder()
                .target(points.first())
                .zoom(12.0)
                .build()
            return
        }
        try {
            val bounds = LatLngBounds.Builder().apply {
                points.forEach { include(it) }
            }.build()
            val padding = (48 * resources.displayMetrics.density).toInt()
            map.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, padding))
        } catch (e: Exception) {
            map.cameraPosition = CameraPosition.Builder()
                .target(points.first())
                .zoom(8.0)
                .build()
        }
    }

    private fun openNearestPin(tap: LatLng) {
        val map = mapLibreMap ?: return
        val screen = map.projection.toScreenLocation(tap)
        val area = RectF(screen.x - 32f, screen.y - 32f, screen.x + 32f, screen.y + 32f)
        val hit = map.queryRenderedFeatures(area, LAYER_ID).firstOrNull()
        val id = hit?.getStringProperty("id")
        if (!id.isNullOrBlank()) {
            openStation(id)
        }
    }

    private fun openStation(id: String) {
        val sessionManager = SessionManager(this)
        if (!sessionManager.isOperator()) {
            val dbHelper = DatabaseHelper(this)
            val station = stations.firstOrNull { it.id == id }
                ?: StationDao(dbHelper).getStationById(id)
            val userNic = sessionManager.getUserNic() ?: ""
            val activeBooking = if (userNic.isNotBlank()) {
                val resDao = ReservationDao(dbHelper)
                resDao.getReservationsByNic(userNic).firstOrNull { res ->
                    res.stationId == id && (res.status.equals("Approved", ignoreCase = true) || res.status.equals("Pending", ignoreCase = true))
                }
            } else null

            if (activeBooking != null) {
                UiAlertUtils.showModernDialog(
                    context = this,
                    title = station?.name ?: "Solar Hub",
                    message = "You have an active reservation at this station (${activeBooking.status}). Would you like to view your booking or schedule a new energy slot?",
                    type = UiAlertUtils.AlertType.INFO,
                    positiveButtonText = "View My Booking",
                    onPositiveClick = {
                        val intent = Intent(this, BookingSummaryActivity::class.java).apply {
                            putExtra("EXTRA_RESERVATION_ID", activeBooking.id)
                            putExtra("EXTRA_STATION_NAME", if (!activeBooking.stationName.isNullOrBlank()) activeBooking.stationName else station?.name)
                            putExtra("EXTRA_STATION_ID", activeBooking.stationId)
                            putExtra("EXTRA_SCHEDULED_AT", activeBooking.scheduledAt)
                            putExtra("EXTRA_STATUS", activeBooking.status)
                            putExtra("EXTRA_SUMMARY", activeBooking.summary)
                            putExtra("EXTRA_QR_TOKEN", activeBooking.qrToken)
                        }
                        startActivity(intent)
                    },
                    negativeButtonText = "Book New Slot",
                    onNegativeClick = {
                        navigateToSlotBooking(id, station)
                    }
                )
            } else {
                navigateToSlotBooking(id, station)
            }
            return
        }

        startActivity(
            Intent(this, StationDetailsActivity::class.java)
                .putExtra(StationDetailsActivity.EXTRA_STATION_ID, id)
        )
    }

    private fun navigateToSlotBooking(id: String, station: Station?) {
        val intent = Intent(this, SlotBookingActivity::class.java).apply {
            putExtra("EXTRA_STATION_ID", id)
            putExtra("EXTRA_STATION_NAME", station?.name ?: "Microgrid Hub")
            putExtra("EXTRA_STATION_CAPACITY", station?.capacityKwh ?: 100.0)
            putExtra("EXTRA_STATION_SCHEDULE", station?.schedule ?: "06:00 - 18:00")
        }
        startActivity(intent)
    }

    private fun Station.hasMapPosition(): Boolean {
        return latitude != 0.0 || longitude != 0.0
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
    }

    companion object {
        private const val SOURCE_ID = "stations"
        private const val LAYER_ID = "station-circles"
        private const val STREET_STYLE = "https://tiles.openfreemap.org/styles/liberty"
    }
}
