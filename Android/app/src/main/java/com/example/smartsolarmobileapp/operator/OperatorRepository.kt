/**
 * Operator data layer. Talks to the Web API first and falls back to SQLite
 * for stations and reservation lists when the network call fails.
 */
package com.example.smartsolarmobileapp.operator

import android.content.Context
import com.example.smartsolarmobileapp.api.ApiClient
import com.example.smartsolarmobileapp.api.ApiMessages
import com.example.smartsolarmobileapp.database.DatabaseHelper
import com.example.smartsolarmobileapp.database.ReservationDao
import com.example.smartsolarmobileapp.database.StationDao
import com.example.smartsolarmobileapp.models.QRVerificationRequest
import com.example.smartsolarmobileapp.models.QRVerificationResponse
import com.example.smartsolarmobileapp.models.Reservation
import com.example.smartsolarmobileapp.models.ReservationDashboard
import com.example.smartsolarmobileapp.models.Station
import com.example.smartsolarmobileapp.models.UpdateStationAvailabilityRequest
import com.example.smartsolarmobileapp.utils.DateTimeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed class OperatorLoad<out T> {
    data class Fresh<T>(val data: T) : OperatorLoad<T>()
    data class Cached<T>(val data: T) : OperatorLoad<T>()
    data class Failed(val message: String) : OperatorLoad<Nothing>()
}

class OperatorRepository(context: Context) {

    private val stationDao = StationDao(DatabaseHelper(context.applicationContext))
    private val reservationDao = ReservationDao(DatabaseHelper(context.applicationContext))

    suspend fun loadStations(): OperatorLoad<List<Station>> = withContext(Dispatchers.IO) {
        try {
            val response = ApiClient.stationApi.getStations()
            if (response.isSuccessful) {
                val stations = response.body().orEmpty()
                stationDao.insertOrUpdateStations(stations)
                OperatorLoad.Fresh(stations)
            } else {
                cachedStations(ApiMessages.from(response, "Could not load stations."))
            }
        } catch (e: Exception) {
            cachedStations(e.message ?: "Could not reach the server.")
        }
    }

    suspend fun loadStation(id: String): OperatorLoad<Station> = withContext(Dispatchers.IO) {
        try {
            val response = ApiClient.stationApi.getStationById(id)
            if (response.isSuccessful && response.body() != null) {
                val station = response.body()!!
                stationDao.insertOrUpdateStations(listOf(station))
                OperatorLoad.Fresh(station)
            } else {
                val cached = stationDao.getStationById(id)
                if (cached != null) {
                    OperatorLoad.Cached(cached)
                } else {
                    OperatorLoad.Failed(ApiMessages.from(response, "Station not found."))
                }
            }
        } catch (e: Exception) {
            val cached = stationDao.getStationById(id)
            if (cached != null) OperatorLoad.Cached(cached)
            else OperatorLoad.Failed(e.message ?: "Could not reach the server.")
        }
    }

    suspend fun updateBatterySlots(id: String, slots: Int): OperatorLoad<Station> = withContext(Dispatchers.IO) {
        try {
            val response = ApiClient.stationApi.updateAvailability(
                id,
                UpdateStationAvailabilityRequest(slots)
            )
            if (response.isSuccessful && response.body() != null) {
                val station = response.body()!!
                stationDao.insertOrUpdateStations(listOf(station))
                OperatorLoad.Fresh(station)
            } else {
                OperatorLoad.Failed(ApiMessages.from(response, "Could not update station availability."))
            }
        } catch (e: Exception) {
            OperatorLoad.Failed(e.message ?: "Could not reach the server.")
        }
    }

    suspend fun loadDashboard(): OperatorLoad<ReservationDashboard> = withContext(Dispatchers.IO) {
        try {
            val response = ApiClient.reservationApi.getDashboard()
            if (response.isSuccessful && response.body() != null) {
                OperatorLoad.Fresh(response.body()!!)
            } else {
                OperatorLoad.Cached(dashboardFromCache())
            }
        } catch (e: Exception) {
            OperatorLoad.Cached(dashboardFromCache())
        }
    }

    suspend fun loadReservations(status: String?, search: String?): OperatorLoad<List<Reservation>> =
        withContext(Dispatchers.IO) {
            try {
                val response = ApiClient.reservationApi.searchReservations(status, search)
                if (response.isSuccessful) {
                    val reservations = decorate(response.body().orEmpty())
                    reservationDao.insertOrUpdateReservations(reservations)
                    OperatorLoad.Fresh(reservations)
                } else {
                    cachedReservations(status, search)
                }
            } catch (e: Exception) {
                cachedReservations(status, search)
            }
        }

    suspend fun approveReservation(id: String): OperatorLoad<Reservation> = withContext(Dispatchers.IO) {
        try {
            val response = ApiClient.reservationApi.approveReservation(id)
            if (response.isSuccessful && response.body() != null) {
                val reservation = response.body()!!
                reservationDao.insertOrUpdateReservation(reservation)
                OperatorLoad.Fresh(reservation)
            } else {
                OperatorLoad.Failed(ApiMessages.from(response, "Could not approve this reservation."))
            }
        } catch (e: Exception) {
            OperatorLoad.Failed(e.message ?: "Could not reach the server.")
        }
    }

    /**
     * Accepts the prosumer QR payload (qrToken, or reservation id when offline)
     * then verifies it and marks the transfer completed.
     */
    suspend fun verifyQr(payload: String): QRVerificationResponse = withContext(Dispatchers.IO) {
        val raw = payload.trim()
        if (raw.isEmpty()) {
            return@withContext QRVerificationResponse(false, "QR token is required.")
        }
        try {
            val token = resolveQrToken(raw)
            val response = ApiClient.reservationApi.verifyQr(QRVerificationRequest(token))
            if (!response.isSuccessful || response.body() == null) {
                return@withContext QRVerificationResponse(
                    verified = false,
                    message = ApiMessages.from(response, "QR token could not be verified.")
                )
            }

            var reservation = decorate(listOf(response.body()!!)).first()
            if (!reservation.status.equals("Completed", ignoreCase = true)) {
                val id = reservation.id
                if (id.isNullOrBlank()) {
                    return@withContext QRVerificationResponse(
                        verified = false,
                        message = "Verified, but the reservation id is missing so it cannot be completed."
                    )
                }
                val completed = ApiClient.reservationApi.completeReservation(id)
                if (!completed.isSuccessful || completed.body() == null) {
                    return@withContext QRVerificationResponse(
                        verified = false,
                        message = ApiMessages.from(completed, "QR was verified, but the transfer could not be completed."),
                        reservation = reservation
                    )
                }
                reservation = decorate(listOf(completed.body()!!)).first()
            }

            reservationDao.insertOrUpdateReservation(reservation)
            QRVerificationResponse(
                verified = true,
                message = reservation.summary ?: "Energy transfer completed.",
                reservation = reservation
            )
        } catch (e: Exception) {
            QRVerificationResponse(
                verified = false,
                message = e.message ?: "Could not reach the server."
            )
        }
    }

    private suspend fun resolveQrToken(payload: String): String {
        if (!payload.matches(Regex("^[a-fA-F0-9]{24}$"))) {
            return payload
        }
        val remote = try {
            val response = ApiClient.reservationApi.getReservationById(payload)
            if (response.isSuccessful) response.body() else null
        } catch (e: Exception) {
            null
        }
        val reservation = remote ?: reservationDao.getReservationById(payload)
        return reservation?.qrToken?.takeIf { it.isNotBlank() } ?: payload
    }

    private fun cachedStations(message: String): OperatorLoad<List<Station>> {
        val cached = stationDao.getAllStations().filter {
            it.status.equals("Active", ignoreCase = true)
        }
        return if (cached.isNotEmpty()) OperatorLoad.Cached(cached) else OperatorLoad.Failed(message)
    }

    private fun cachedReservations(status: String?, search: String?): OperatorLoad<List<Reservation>> {
        val filtered = decorate(reservationDao.getAllReservations()).filter { reservation ->
            val statusMatches = status.isNullOrBlank() || reservation.status.equals(status, ignoreCase = true)
            val query = search?.trim().orEmpty()
            val searchMatches = query.isEmpty() ||
                reservation.prosumerNic.contains(query, ignoreCase = true) ||
                reservation.id.orEmpty().contains(query, ignoreCase = true) ||
                reservation.stationId.contains(query, ignoreCase = true) ||
                reservation.stationName.orEmpty().contains(query, ignoreCase = true)
            statusMatches && searchMatches
        }
        return OperatorLoad.Cached(filtered)
    }

    private fun decorate(reservations: List<Reservation>): List<Reservation> {
        val names = stationDao.getActiveStations().associate { it.id to it.name }
        return reservations.map { reservation ->
            if (!reservation.stationName.isNullOrBlank()) {
                reservation
            } else {
                reservation.copy(stationName = names[reservation.stationId])
            }
        }
    }

    private fun dashboardFromCache(): ReservationDashboard {
        val items = reservationDao.getAllReservations()
        val now = System.currentTimeMillis()
        return ReservationDashboard(
            pendingCount = items.count { it.status.equals("Pending", ignoreCase = true) },
            approvedFutureCount = items.count { reservation ->
                reservation.status.equals("Approved", ignoreCase = true) &&
                    (DateTimeUtils.parseIsoString(reservation.scheduledAt)?.time ?: 0L) > now
            },
            currentCount = items.count {
                it.status.equals("Approved", ignoreCase = true) || it.status.equals("Pending", ignoreCase = true)
            },
            historyCount = items.count {
                it.status.equals("Completed", ignoreCase = true) || it.status.equals("Cancelled", ignoreCase = true)
            }
        )
    }
}
