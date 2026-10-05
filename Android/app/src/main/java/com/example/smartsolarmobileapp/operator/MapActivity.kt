/**
 * Shows active stations on a street map and lists them.
 * Distances appear only when the user opts in to live device location.
 */
package com.example.smartsolarmobileapp.operator

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.RectF
import android.location.LocationManager
import android.os.Bundle
import android.provider.Settings
import android.widget.TextView
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
import com.example.smartsolarmobileapp.utils.ScreenInsets
import com.example.smartsolarmobileapp.utils.SessionManager
import com.example.smartsolarmobileapp.utils.UiAlertUtils
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.material.materialswitch.MaterialSwitch
import kotlinx.coroutines.launch
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

class MapActivity : AppCompatActivity() {

    private lateinit var repository: OperatorRepository
    private lateinit var adapter: OperatorStationAdapter
    private lateinit var mapView: MapView
    private lateinit var locationSwitch: MaterialSwitch
    private var mapLibreMap: MapLibreMap? = null
    private var stations: List<Station> = emptyList()
    private var userLatLng: LatLng? = null
    private var locationPromptShown = false
    private var locationFetchInFlight = false
    private var didFrameCamera = false
    private var framedWithLocation = false

    private val locationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            locationSwitch.isChecked = true
            fetchLiveLocation()
        } else {
            clearLiveLocation("Location permission is off. Station pins are shown without distances.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        MapLibre.getInstance(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_map)
        ScreenInsets.apply(findViewById(android.R.id.content), extraHorizontalDp = 16, extraVerticalDp = 16)
        repository = OperatorRepository(this)

        locationSwitch = findViewById(R.id.switch_use_location)
        locationSwitch.setOnCheckedChangeListener { _, checked ->
            if (checked) {
                enableLiveLocationFromUser()
            } else {
                clearLiveLocation("Location off. Showing station pins only.")
            }
        }

        val recycler = findViewById<RecyclerView>(R.id.rv_operator_stations)
        adapter = OperatorStationAdapter(emptyList()) { station -> openStation(station.id) }
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        findViewById<android.view.View>(R.id.btn_map_zoom_in).setOnClickListener { zoomBy(1.0) }
        findViewById<android.view.View>(R.id.btn_map_zoom_out).setOnClickListener { zoomBy(-1.0) }
        findViewById<android.view.View>(R.id.btn_map_recenter).setOnClickListener { recenterOnMe() }

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

        promptForLocation()
    }

    override fun onStart() {
        super.onStart()
        mapView.onStart()
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
        loadStations(announceFallback = stations.isEmpty())
        if (locationSwitch.isChecked && hasLocationPermission() && isDeviceLocationEnabled()) {
            fetchLiveLocation()
        }
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

    private fun promptForLocation() {
        if (locationPromptShown || isFinishing) return
        locationPromptShown = true
        UiAlertUtils.showModernDialog(
            context = this,
            title = "Enable location?",
            message = "Turn on live location to see how far each solar hub is from you. You can skip and only see station pins on the map.",
            type = UiAlertUtils.AlertType.INFO,
            positiveButtonText = "Enable",
            onPositiveClick = {
                locationSwitch.isChecked = true
            },
            negativeButtonText = "Not now",
            onNegativeClick = {
                locationSwitch.isChecked = false
                clearLiveLocation("Location off. Showing station pins only.")
            }
        )
    }

    private fun enableLiveLocationFromUser() {
        if (!hasLocationPermission()) {
            locationPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            return
        }
        if (!isDeviceLocationEnabled()) {
            UiAlertUtils.showModernDialog(
                context = this,
                title = "Location is turned off",
                message = "Open phone settings and turn on location, then come back to this map.",
                type = UiAlertUtils.AlertType.WARNING,
                positiveButtonText = "Open settings",
                onPositiveClick = {
                    startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                },
                negativeButtonText = "Cancel",
                onNegativeClick = {
                    locationSwitch.isChecked = false
                }
            )
            return
        }
        fetchLiveLocation()
    }

    private fun fetchLiveLocation() {
        if (!hasLocationPermission() || locationFetchInFlight) return
        locationFetchInFlight = true
        val client = LocationServices.getFusedLocationProviderClient(this)
        val token = CancellationTokenSource()
        client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, token.token)
            .addOnSuccessListener { location ->
                locationFetchInFlight = false
                if (location == null) {
                    clearLiveLocation("Could not read your live location yet. Station pins are shown without distances.")
                    return@addOnSuccessListener
                }
                userLatLng = LatLng(location.latitude, location.longitude)
                locationSwitch.isChecked = true
                render(stations)
            }
            .addOnFailureListener {
                locationFetchInFlight = false
                clearLiveLocation("Could not read your live location. Station pins are shown without distances.")
            }
    }

    private fun clearLiveLocation(note: String? = null) {
        userLatLng = null
        if (::locationSwitch.isInitialized && locationSwitch.isChecked) {
            locationSwitch.setOnCheckedChangeListener(null)
            locationSwitch.isChecked = false
            locationSwitch.setOnCheckedChangeListener { _, checked ->
                if (checked) enableLiveLocationFromUser()
                else clearLiveLocation("Location off. Showing station pins only.")
            }
        }
        render(stations)
        if (!note.isNullOrBlank() && !isFinishing) {
            findViewById<TextView>(R.id.tv_map_note).text = note
        }
    }

    private fun loadStations(announceFallback: Boolean) {
        lifecycleScope.launch {
            try {
                when (val result = repository.loadStations()) {
                    is OperatorLoad.Fresh -> render(result.data)
                    is OperatorLoad.Cached -> {
                        render(result.data)
                        if (announceFallback && !isFinishing) {
                            UiAlertUtils.showModernDialog(
                                this@MapActivity,
                                "Saved stations",
                                "The server could not be reached, so these are the stations saved on this phone.",
                                UiAlertUtils.AlertType.WARNING
                            )
                        }
                    }
                    is OperatorLoad.Failed -> {
                        if (!isFinishing) {
                            UiAlertUtils.showModernDialog(
                                this@MapActivity,
                                "Could not load stations",
                                result.message,
                                UiAlertUtils.AlertType.ERROR
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                if (!isFinishing) {
                    UiAlertUtils.showModernDialog(
                        this@MapActivity,
                        "Could not load stations",
                        e.message ?: "Please try again.",
                        UiAlertUtils.AlertType.ERROR
                    )
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
        val active = loaded.filter { it.status.equals("Active", ignoreCase = true) }
        val nearbyOnly = !SessionManager(this).isOperator() && origin != null
        stations = active
            .filter { station ->
                if (!nearbyOnly) true
                else (distances[station.id] ?: Double.MAX_VALUE) <= NEARBY_RADIUS_KM
            }
            .let { visible ->
                if (distances.isEmpty()) visible
                else visible.sortedBy { distances[it.id] ?: Double.MAX_VALUE }
            }
        adapter.update(stations, distances)
        findViewById<TextView>(R.id.tv_map_note).text = mapNote(origin != null, nearbyOnly)
        plot(stations)
    }

    private fun mapNote(hasLocation: Boolean, nearbyOnly: Boolean): String {
        return when {
            nearbyOnly && stations.isEmpty() ->
                "No hubs within ${NEARBY_RADIUS_KM.toInt()} km of you"
            nearbyOnly ->
                "${stations.size} hubs within ${NEARBY_RADIUS_KM.toInt()} km · nearest first"
            hasLocation ->
                "${stations.size} active stations · nearest first from your live location"
            !SessionManager(this).isOperator() ->
                "Turn on location to see hubs within ${NEARBY_RADIUS_KM.toInt()} km"
            else ->
                "${stations.size} active stations · turn on location for distances"
        }
    }

    private fun zoomBy(delta: Double) {
        mapLibreMap?.animateCamera(CameraUpdateFactory.zoomBy(delta))
    }

    private fun recenterOnMe() {
        val origin = userLatLng
        if (origin == null) {
            UiAlertUtils.showToast(this, "Turn on live location to show where you are.", UiAlertUtils.AlertType.INFO)
            if (::locationSwitch.isInitialized && !locationSwitch.isChecked) {
                locationSwitch.isChecked = true
            }
            return
        }
        mapLibreMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(origin, 14.0))
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
                    circleColor("#0D7A46"),
                    circleStrokeWidth(2f),
                    circleStrokeColor("#FFFFFF")
                )
            )
        } else {
            existing.setGeoJson(collection)
        }
        plotUser(style)
        frameStations(stations)
    }

    private fun plotUser(style: Style) {
        val origin = userLatLng
        val features = if (origin == null) {
            emptyList()
        } else {
            listOf(Feature.fromGeometry(Point.fromLngLat(origin.longitude, origin.latitude)))
        }
        val collection = FeatureCollection.fromFeatures(features)
        val existing = style.getSourceAs<GeoJsonSource>(USER_SOURCE_ID)
        if (existing == null) {
            style.addSource(GeoJsonSource(USER_SOURCE_ID, collection))
            style.addLayer(
                CircleLayer(USER_LAYER_ID, USER_SOURCE_ID).withProperties(
                    circleRadius(7f),
                    circleColor("#0F172A"),
                    circleStrokeWidth(3f),
                    circleStrokeColor("#FBBF24")
                )
            )
        } else {
            existing.setGeoJson(collection)
        }
    }

    private fun frameStations(stations: List<Station>) {
        val map = mapLibreMap ?: return
        val hasOrigin = userLatLng != null
        if (didFrameCamera && (!hasOrigin || framedWithLocation)) return
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
        }
        if (points.isEmpty()) return
        if (mapView.width == 0 || mapView.height == 0) {
            mapView.post { frameStations(stations) }
            return
        }
        didFrameCamera = true
        framedWithLocation = hasOrigin
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

    private fun isDeviceLocationEnabled(): Boolean {
        val manager = getSystemService(LOCATION_SERVICE) as? LocationManager ?: return false
        return manager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
            manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    companion object {
        private const val SOURCE_ID = "stations"
        private const val LAYER_ID = "station-circles"
        private const val USER_SOURCE_ID = "user-location"
        private const val USER_LAYER_ID = "user-location-circle"
        private const val NEARBY_RADIUS_KM = 10.0
        private const val STREET_STYLE = "https://tiles.openfreemap.org/styles/liberty"
    }
}
