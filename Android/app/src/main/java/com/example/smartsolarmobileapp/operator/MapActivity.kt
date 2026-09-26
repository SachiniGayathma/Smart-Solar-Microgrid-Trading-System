/**
 * TODO: Member 4 (Grid Operator) — Google Maps Activity
 *
 * This activity must be implemented by Member 4 to display nearby solar microgrid
 * stations on a Google Maps view. This is worth 5 marks in the rubric under
 * "Grid Operator Verification and Map Features".
 *
 * Implementation checklist for Member 4:
 * ──────────────────────────────────────
 * 1. Extend AppCompatActivity and implement OnMapReadyCallback.
 *
 * 2. Fetch station data from the API:
 *       GET /api/Stations
 *    Each Station model (models/Station.kt) already has latitude and longitude fields.
 *
 * 3. For each active station, add a Google Maps Marker at (latitude, longitude) with:
 *       - Title: station.name
 *       - Snippet: "Capacity: ${station.capacityKwh} kWh | ${station.schedule}"
 *
 * 4. On marker click, navigate to StationDetailsActivity showing full station info.
 *
 * 5. Use FusedLocationProviderClient to center the map on the operator's current location.
 *
 * 6. Offline fallback: Load cached stations from StationDao (database/StationDao.kt)
 *    when the API is unreachable.
 *
 * Files to reference:
 *   - Station.kt (models/Station.kt) — data model with lat/lng coordinates
 *   - StationApi.kt (api/StationApi.kt) — GET /api/Stations endpoint
 *   - StationDao.kt (database/StationDao.kt) — local SQLite caching layer
 *   - ApiClient.kt (api/ApiClient.kt) — provides stationApi singleton
 *
 * Dependencies to add in build.gradle.kts:
 *   - com.google.android.gms:play-services-maps
 *   - com.google.android.gms:play-services-location
 *
 * Configuration:
 *   - Add Google Maps API key in AndroidManifest.xml:
 *     <meta-data android:name="com.google.android.geo.API_KEY" android:value="YOUR_KEY" />
 *   - Add <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
 *
 * Layout file: Create activity_map.xml with a SupportMapFragment.
 */
package com.example.smartsolarmobileapp.operator

import androidx.appcompat.app.AppCompatActivity

class MapActivity : AppCompatActivity() {
    // TODO: Member 4 — Implement Google Maps with station markers (see docblock above)
}
