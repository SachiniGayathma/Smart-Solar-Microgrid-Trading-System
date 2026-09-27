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
import com.example.smartsolarmobileapp.models.Station
import com.example.smartsolarmobileapp.operator.adapter.OperatorStationAdapter
import com.example.smartsolarmobileapp.utils.GeoUtils
import com.google.android.gms.location.LocationServices
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
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
            map.setStyle(mapboxStyle()) {
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

    private fun mapboxStyle(): String {
        val token = getString(R.string.mapbox_access_token)
        return "https://api.mapbox.com/styles/v1/mapbox/streets-v12?access_token=$token"
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
        val features = stations.map { station ->
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

        val focus = userLatLng ?: stations.firstOrNull()?.let { LatLng(it.latitude, it.longitude) }
        if (focus != null) {
            map.cameraPosition = CameraPosition.Builder()
                .target(focus)
                .zoom(13.0)
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
        startActivity(
            Intent(this, StationDetailsActivity::class.java)
                .putExtra(StationDetailsActivity.EXTRA_STATION_ID, id)
        )
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
    }

    companion object {
        private const val SOURCE_ID = "stations"
        private const val LAYER_ID = "station-circles"
    }
}
