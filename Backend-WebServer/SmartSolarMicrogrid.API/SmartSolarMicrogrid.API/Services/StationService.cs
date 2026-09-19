/*
 * File: StationService.cs
 * Description: Microgrid node rules including the blocked-deactivation check.
 * Author: Dhiyanah Liyaudeen
 * Created: 19/09/2026
 */

using SmartSolarMicrogrid.API.Constants;
using SmartSolarMicrogrid.API.DTOs;
using SmartSolarMicrogrid.API.Models;
using SmartSolarMicrogrid.API.Repositories;

namespace SmartSolarMicrogrid.API.Services
{
    public class StationService
    {
        private readonly StationRepository _stations;
        private readonly ReservationRepository _reservations;

        // Wires station storage and reservation checks.
        public StationService(StationRepository stations, ReservationRepository reservations)
        {
            _stations = stations;
            _reservations = reservations;
        }

        // Backoffice sees every hub; other roles only see active ones (for maps).
        public async Task<List<StationResponse>> GetAllAsync(string role)
        {
            var stations = role == UserRoles.Backoffice
                ? await _stations.GetAllAsync()
                : await _stations.GetByStatusAsync(StationStatuses.Active);

            return stations.Select(ToResponse).ToList();
        }

        // Returns one station or null.
        public async Task<StationResponse?> GetByIdAsync(string id)
        {
            var station = await _stations.GetByIdAsync(id);
            return station is null ? null : ToResponse(station);
        }

        // Creates a new active microgrid hub.
        public async Task<(int StatusCode, object Body)> CreateAsync(StationRequest request)
        {
            var error = Validate(request);
            if (error is not null)
            {
                return (400, error);
            }

            var station = new SolarStationInfo
            {
                Name = request.Name.Trim(),
                Latitude = request.Latitude,
                Longitude = request.Longitude,
                CapacityKwh = request.CapacityKwh,
                BatteryStorageSlots = request.BatteryStorageSlots,
                Schedule = request.Schedule.Trim(),
                Status = StationStatuses.Active,
                CreatedAt = DateTime.UtcNow
            };

            await _stations.CreateAsync(station);
            return (201, ToResponse(station));
        }

        // Updates GPS, capacity, schedule, and slot count. Status is not changed here.
        public async Task<(int StatusCode, object Body)> UpdateAsync(string id, StationRequest request)
        {
            var station = await _stations.GetByIdAsync(id);
            if (station is null)
            {
                return (404, new { message = "Station not found." });
            }

            var error = Validate(request);
            if (error is not null)
            {
                return (400, error);
            }

            station.Name = request.Name.Trim();
            station.Latitude = request.Latitude;
            station.Longitude = request.Longitude;
            station.CapacityKwh = request.CapacityKwh;
            station.BatteryStorageSlots = request.BatteryStorageSlots;
            station.Schedule = request.Schedule.Trim();
            await _stations.UpdateAsync(station);

            return (200, ToResponse(station));
        }

        // Lets operators change how many battery slots are currently available.
        public async Task<(int StatusCode, object Body)> UpdateAvailabilityAsync(string id, UpdateStationAvailabilityRequest request)
        {
            var station = await _stations.GetByIdAsync(id);
            if (station is null)
            {
                return (404, new { message = "Station not found." });
            }

            if (request.BatteryStorageSlots < 0)
            {
                return (400, new { message = "Battery storage slots cannot be negative." });
            }

            station.BatteryStorageSlots = request.BatteryStorageSlots;
            await _stations.UpdateAsync(station);
            return (200, ToResponse(station));
        }

        // Deactivates a hub unless it still has pending or approved reservations.
        public async Task<(int StatusCode, object Body)> DeactivateAsync(string id)
        {
            var station = await _stations.GetByIdAsync(id);
            if (station is null)
            {
                return (404, new { message = "Station not found." });
            }

            if (station.Status == StationStatuses.Deactivated)
            {
                return (400, new { message = "This station is already deactivated." });
            }

            if (await _reservations.HasActiveReservationsAsync(id))
            {
                return (409, new { message = "Cannot deactivate this station while active energy reservations exist." });
            }

            await _stations.UpdateStatusAsync(id, StationStatuses.Deactivated);
            station.Status = StationStatuses.Deactivated;
            return (200, ToResponse(station));
        }

        // Reactivates a deactivated hub.
        public async Task<(int StatusCode, object Body)> ActivateAsync(string id)
        {
            var station = await _stations.GetByIdAsync(id);
            if (station is null)
            {
                return (404, new { message = "Station not found." });
            }

            if (station.Status == StationStatuses.Active)
            {
                return (400, new { message = "This station is already active." });
            }

            await _stations.UpdateStatusAsync(id, StationStatuses.Active);
            station.Status = StationStatuses.Active;
            return (200, ToResponse(station));
        }

        // Checks required station fields.
        private static object? Validate(StationRequest request)
        {
            if (string.IsNullOrWhiteSpace(request.Name))
            {
                return new { message = "Station name is required." };
            }

            if (request.Latitude is < -90 or > 90 || request.Longitude is < -180 or > 180)
            {
                return new { message = "Latitude and longitude are invalid." };
            }

            if (request.CapacityKwh <= 0)
            {
                return new { message = "Capacity (kW/h) must be greater than zero." };
            }

            if (request.BatteryStorageSlots < 0)
            {
                return new { message = "Battery storage slots cannot be negative." };
            }

            return null;
        }

        // Maps a MongoDB station to the public DTO.
        private static StationResponse ToResponse(SolarStationInfo station)
        {
            return new StationResponse
            {
                Id = station.Id ?? string.Empty,
                Name = station.Name,
                Latitude = station.Latitude,
                Longitude = station.Longitude,
                CapacityKwh = station.CapacityKwh,
                BatteryStorageSlots = station.BatteryStorageSlots,
                Schedule = station.Schedule,
                Status = station.Status,
                CreatedAt = station.CreatedAt
            };
        }
    }
}
