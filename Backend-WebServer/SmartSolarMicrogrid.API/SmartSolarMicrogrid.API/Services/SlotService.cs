/*
 * File: SlotService.cs
 * Description: Energy booking slot rules, including blocked delete when reservations exist.
 * Author: Dhiyanah Liyaudeen
 * Created: 19/09/2026
 */

using MongoDB.Bson;
using SmartSolarMicrogrid.API.Constants;
using SmartSolarMicrogrid.API.DTOs;
using SmartSolarMicrogrid.API.Models;
using SmartSolarMicrogrid.API.Repositories;

namespace SmartSolarMicrogrid.API.Services
{
    public class SlotService
    {
        private readonly SlotRepository _slots;
        private readonly StationRepository _stations;
        private readonly ReservationRepository _reservations;

        // Wires slot storage, parent stations, and reservation checks.
        public SlotService(
            SlotRepository slots,
            StationRepository stations,
            ReservationRepository reservations)
        {
            _slots = slots;
            _stations = stations;
            _reservations = reservations;
        }

        // Lists slots, optionally filtered by station.
        public async Task<(int StatusCode, object Body)> GetAllAsync(string? stationId)
        {
            if (!string.IsNullOrWhiteSpace(stationId) && !ObjectId.TryParse(stationId, out _))
            {
                return (400, new { message = "stationId must be a valid id." });
            }

            var slots = await _slots.GetAllAsync(stationId);
            return (200, slots.Select(ToResponse).ToList());
        }

        // Returns one slot or null.
        public async Task<SlotResponse?> GetByIdAsync(string id)
        {
            var slot = await _slots.GetByIdAsync(id);
            return slot is null ? null : ToResponse(slot);
        }

        // Creates a time window on an active station.
        public async Task<(int StatusCode, object Body)> CreateAsync(SlotRequest request)
        {
            var error = await ValidateAsync(request);
            if (error is not null)
            {
                return error.Value;
            }

            var slot = new EnergyBookingSlot
            {
                StationId = request.StationId.Trim(),
                StartTime = request.StartTime.ToUniversalTime(),
                EndTime = request.EndTime.ToUniversalTime(),
                AvailableCapacity = request.AvailableCapacity,
                Status = SlotStatuses.Available
            };

            await _slots.CreateAsync(slot);
            return (201, ToResponse(slot));
        }

        // Updates a slot's time window and capacity.
        public async Task<(int StatusCode, object Body)> UpdateAsync(string id, SlotRequest request)
        {
            var slot = await _slots.GetByIdAsync(id);
            if (slot is null)
            {
                return (404, new { message = "Slot not found." });
            }

            var error = await ValidateAsync(request, allowDeactivatedStation: true);
            if (error is not null)
            {
                return error.Value;
            }

            slot.StationId = request.StationId.Trim();
            slot.StartTime = request.StartTime.ToUniversalTime();
            slot.EndTime = request.EndTime.ToUniversalTime();
            slot.AvailableCapacity = request.AvailableCapacity;
            await _slots.UpdateAsync(slot);

            return (200, ToResponse(slot));
        }

        // Lets operators change remaining capacity on a slot.
        public async Task<(int StatusCode, object Body)> UpdateAvailabilityAsync(string id, UpdateSlotAvailabilityRequest request)
        {
            var slot = await _slots.GetByIdAsync(id);
            if (slot is null)
            {
                return (404, new { message = "Slot not found." });
            }

            if (request.AvailableCapacity < 0)
            {
                return (400, new { message = "Available capacity cannot be negative." });
            }

            slot.AvailableCapacity = request.AvailableCapacity;
            await _slots.UpdateAsync(slot);
            return (200, ToResponse(slot));
        }

        // Deactivates a slot unless it still has pending or approved reservations.
        public async Task<(int StatusCode, object Body)> DeactivateAsync(string id)
        {
            var slot = await _slots.GetByIdAsync(id);
            if (slot is null)
            {
                return (404, new { message = "Slot not found." });
            }

            if (slot.Status == SlotStatuses.Deactivated)
            {
                return (400, new { message = "This slot is already deactivated." });
            }

            if (await _reservations.HasActiveReservationsForSlotAsync(id))
            {
                return (409, new { message = "Cannot deactivate this slot while active energy reservations exist." });
            }

            await _slots.UpdateStatusAsync(id, SlotStatuses.Deactivated);
            slot.Status = SlotStatuses.Deactivated;
            return (200, ToResponse(slot));
        }

        // Deletes a slot unless it still has pending or approved reservations.
        public async Task<(int StatusCode, object Body)> DeleteAsync(string id)
        {
            var slot = await _slots.GetByIdAsync(id);
            if (slot is null)
            {
                return (404, new { message = "Slot not found." });
            }

            if (await _reservations.HasActiveReservationsForSlotAsync(id))
            {
                return (409, new { message = "Cannot delete this slot while active energy reservations exist." });
            }

            await _slots.DeleteAsync(id);
            return (200, new { message = "Slot deleted." });
        }

        // Checks station, times, and capacity before create/update.
        private async Task<(int StatusCode, object Body)?> ValidateAsync(SlotRequest request, bool allowDeactivatedStation = false)
        {
            if (!ObjectId.TryParse(request.StationId, out _))
            {
                return (400, new { message = "Station id is invalid." });
            }

            var station = await _stations.GetByIdAsync(request.StationId);
            if (station is null)
            {
                return (404, new { message = "Station not found." });
            }

            if (!allowDeactivatedStation && station.Status != StationStatuses.Active)
            {
                return (400, new { message = "Slots can only be created on an active station." });
            }

            if (request.EndTime <= request.StartTime)
            {
                return (400, new { message = "End time must be after start time." });
            }

            if (request.AvailableCapacity < 0)
            {
                return (400, new { message = "Available capacity cannot be negative." });
            }

            return null;
        }

        // Maps a MongoDB slot to the public DTO.
        private static SlotResponse ToResponse(EnergyBookingSlot slot)
        {
            return new SlotResponse
            {
                Id = slot.Id ?? string.Empty,
                StationId = slot.StationId,
                StartTime = slot.StartTime,
                EndTime = slot.EndTime,
                AvailableCapacity = slot.AvailableCapacity,
                Status = slot.Status
            };
        }
    }
}
