/*
 * File: ReservationService.cs
 * Description: Reservation business rules: 7-day window, 12-hour notice, QR, and completion.
 * Author: Dhiyanah Liyaudeen
 * Created: 19/09/2026
 */

using SmartSolarMicrogrid.API.Constants;
using SmartSolarMicrogrid.API.DTOs;
using SmartSolarMicrogrid.API.Models;
using SmartSolarMicrogrid.API.Repositories;

namespace SmartSolarMicrogrid.API.Services
{
    public class ReservationService
    {
        private readonly ReservationRepository _reservations;
        private readonly SlotRepository _slots;
        private readonly StationRepository _stations;
        private readonly UserRepository _users;

        // Wires reservations to slots, stations, and user checks.
        public ReservationService(
            ReservationRepository reservations,
            SlotRepository slots,
            StationRepository stations,
            UserRepository users)
        {
            _reservations = reservations;
            _slots = slots;
            _stations = stations;
            _users = users;
        }

        // Lists bookings. Prosumers only see their own.
        public async Task<List<ReservationResponse>> SearchAsync(
            string role,
            string currentNic,
            string? status,
            string? search)
        {
            var nicFilter = role == UserRoles.Prosumer ? currentNic : null;
            var items = await _reservations.SearchAsync(status, nicFilter, search);
            return items.Select(r => ToResponse(r)).ToList();
        }

        // Returns dashboard counts for the current role.
        public async Task<ReservationDashboardResponse> GetDashboardAsync(string role, string currentNic)
        {
            var nicFilter = role == UserRoles.Prosumer ? currentNic : null;
            var items = await _reservations.SearchAsync(null, nicFilter, null);
            var now = DateTime.UtcNow;

            return new ReservationDashboardResponse
            {
                PendingCount = items.Count(r => r.Status == ReservationStatuses.Pending),
                ApprovedFutureCount = items.Count(r =>
                    r.Status == ReservationStatuses.Approved && r.ScheduledAt > now),
                CurrentCount = items.Count(r =>
                    r.Status == ReservationStatuses.Approved || r.Status == ReservationStatuses.Pending),
                HistoryCount = items.Count(r =>
                    r.Status == ReservationStatuses.Completed || r.Status == ReservationStatuses.Cancelled)
            };
        }

        // Returns one reservation if the caller is allowed to see it.
        public async Task<(int StatusCode, object Body)> GetByIdAsync(string id, string role, string currentNic)
        {
            var reservation = await _reservations.GetByIdAsync(id);
            if (reservation is null)
            {
                return (404, new { message = "Reservation not found." });
            }

            if (role == UserRoles.Prosumer && reservation.ProsumerNic != currentNic)
            {
                return (403, new { message = "You can only view your own reservations." });
            }

            return (200, ToResponse(reservation));
        }

        // Creates a pending booking if the 7-day rule and slot capacity allow it.
        public async Task<(int StatusCode, object Body)> CreateAsync(
            ReservationRequest request,
            string role,
            string currentNic)
        {
            var nicResult = await ResolveProsumerNicAsync(request.ProsumerNic, role, currentNic);
            if (nicResult.Error is not null)
            {
                return nicResult.Error.Value;
            }

            var slotResult = await LoadBookableSlotAsync(request.SlotId);
            if (slotResult.Error is not null)
            {
                return slotResult.Error.Value;
            }

            var slot = slotResult.Slot!;
            var windowError = ValidateSevenDayWindow(slot.StartTime);
            if (windowError is not null)
            {
                return windowError.Value;
            }

            var reservation = new EnergyReservation
            {
                ProsumerNic = nicResult.Nic!,
                StationId = slot.StationId,
                SlotId = slot.Id!,
                Status = ReservationStatuses.Pending,
                ScheduledAt = slot.StartTime,
                CreatedAt = DateTime.UtcNow
            };

            await _reservations.CreateAsync(reservation);
            await _slots.AdjustCapacityAsync(slot.Id!, -1);

            return (201, ToResponse(reservation, "Reservation created and is pending approval."));
        }

        // Moves a booking to another slot when at least 12 hours remain.
        public async Task<(int StatusCode, object Body)> UpdateAsync(
            string id,
            ReservationRequest request,
            string role,
            string currentNic)
        {
            var reservation = await _reservations.GetByIdAsync(id);
            if (reservation is null)
            {
                return (404, new { message = "Reservation not found." });
            }

            var accessError = EnsureCanModify(reservation, role, currentNic);
            if (accessError is not null)
            {
                return accessError.Value;
            }

            var noticeError = ValidateTwelveHourNotice(reservation.ScheduledAt);
            if (noticeError is not null)
            {
                return noticeError.Value;
            }

            var slotResult = await LoadBookableSlotAsync(request.SlotId, reservation.SlotId);
            if (slotResult.Error is not null)
            {
                return slotResult.Error.Value;
            }

            var newSlot = slotResult.Slot!;
            var windowError = ValidateSevenDayWindow(newSlot.StartTime);
            if (windowError is not null)
            {
                return windowError.Value;
            }

            if (newSlot.Id != reservation.SlotId)
            {
                await _slots.AdjustCapacityAsync(reservation.SlotId, 1);
                await _slots.AdjustCapacityAsync(newSlot.Id!, -1);
            }

            reservation.SlotId = newSlot.Id!;
            reservation.StationId = newSlot.StationId;
            reservation.ScheduledAt = newSlot.StartTime;
            reservation.Status = ReservationStatuses.Pending;
            reservation.QrToken = null;
            await _reservations.UpdateAsync(reservation);

            return (200, ToResponse(reservation, "Reservation updated. It is pending approval again."));
        }

        // Cancels a booking when at least 12 hours remain.
        public async Task<(int StatusCode, object Body)> CancelAsync(string id, string role, string currentNic)
        {
            var reservation = await _reservations.GetByIdAsync(id);
            if (reservation is null)
            {
                return (404, new { message = "Reservation not found." });
            }

            var accessError = EnsureCanModify(reservation, role, currentNic);
            if (accessError is not null)
            {
                return accessError.Value;
            }

            var noticeError = ValidateTwelveHourNotice(reservation.ScheduledAt);
            if (noticeError is not null)
            {
                return noticeError.Value;
            }

            reservation.Status = ReservationStatuses.Cancelled;
            reservation.QrToken = null;
            await _reservations.UpdateAsync(reservation);
            await _slots.AdjustCapacityAsync(reservation.SlotId, 1);

            return (200, ToResponse(reservation, "Reservation cancelled."));
        }

        // Approves a pending booking and issues a QR token.
        public async Task<(int StatusCode, object Body)> ApproveAsync(string id)
        {
            var reservation = await _reservations.GetByIdAsync(id);
            if (reservation is null)
            {
                return (404, new { message = "Reservation not found." });
            }

            if (reservation.Status != ReservationStatuses.Pending)
            {
                return (400, new { message = "Only pending reservations can be approved." });
            }

            reservation.Status = ReservationStatuses.Approved;
            reservation.QrToken = Guid.NewGuid().ToString("N");
            await _reservations.UpdateAsync(reservation);

            return (200, ToResponse(reservation, "Reservation approved. QR token issued."));
        }

        // Returns the existing QR token for an approved booking.
        public async Task<(int StatusCode, object Body)> GetQrAsync(string id, string role, string currentNic)
        {
            var reservation = await _reservations.GetByIdAsync(id);
            if (reservation is null)
            {
                return (404, new { message = "Reservation not found." });
            }

            if (role == UserRoles.Prosumer && reservation.ProsumerNic != currentNic)
            {
                return (403, new { message = "You can only view your own QR code." });
            }

            if (reservation.Status != ReservationStatuses.Approved || string.IsNullOrWhiteSpace(reservation.QrToken))
            {
                return (400, new { message = "A QR code is available only after approval." });
            }

            return (200, ToResponse(reservation, "QR token ready for the mobile app."));
        }

        // Verifies a scanned QR token against the server and marks the job completed.
        public async Task<(int StatusCode, object Body)> VerifyQrAsync(string qrToken)
        {
            if (string.IsNullOrWhiteSpace(qrToken))
            {
                return (400, new { message = "QR token is required." });
            }

            var reservation = await _reservations.GetByQrTokenAsync(qrToken.Trim());
            if (reservation is null)
            {
                return (404, new { message = "QR token does not match any approved reservation." });
            }

            if (reservation.Status != ReservationStatuses.Approved)
            {
                return (400, new { message = "This reservation cannot be completed." });
            }

            reservation.Status = ReservationStatuses.Completed;
            await _reservations.UpdateAsync(reservation);

            return (200, ToResponse(reservation, "QR verified. Energy transfer marked as completed."));
        }

        // Picks the NIC: prosumers use their own; staff must pass an active prosumer NIC.
        private async Task<(string? Nic, (int StatusCode, object Body)? Error)> ResolveProsumerNicAsync(
            string? requestedNic,
            string role,
            string currentNic)
        {
            if (role == UserRoles.Prosumer)
            {
                if (string.IsNullOrWhiteSpace(currentNic))
                {
                    return (null, (400, new { message = "Your account has no NIC." }));
                }

                return (currentNic.ToUpperInvariant(), null);
            }

            if (string.IsNullOrWhiteSpace(requestedNic))
            {
                return (null, (400, new { message = "prosumerNic is required when staff create a booking." }));
            }

            var nic = requestedNic.Trim().ToUpperInvariant();
            var user = await _users.GetByNicAsync(nic);
            if (user is null || user.Role != UserRoles.Prosumer)
            {
                return (null, (404, new { message = "Prosumer not found." }));
            }

            if (user.Status != UserStatuses.Active)
            {
                return (null, (400, new { message = "Prosumer account must be active before booking." }));
            }

            return (nic, null);
        }

        // Loads a slot that is available and belongs to an active station.
        private async Task<(EnergyBookingSlot? Slot, (int StatusCode, object Body)? Error)> LoadBookableSlotAsync(
            string slotId,
            string? currentSlotId = null)
        {
            if (string.IsNullOrWhiteSpace(slotId))
            {
                return (null, (400, new { message = "slotId is required." }));
            }

            var slot = await _slots.GetByIdAsync(slotId);
            if (slot is null)
            {
                return (null, (404, new { message = "Slot not found." }));
            }

            if (slot.Status != SlotStatuses.Available)
            {
                return (null, (400, new { message = "This slot is not available." }));
            }

            var station = await _stations.GetByIdAsync(slot.StationId);
            if (station is null || station.Status != StationStatuses.Active)
            {
                return (null, (400, new { message = "The station for this slot is not active." }));
            }

            var needsCapacity = currentSlotId is null || currentSlotId != slot.Id;
            if (needsCapacity && slot.AvailableCapacity < 1)
            {
                return (null, (409, new { message = "This slot has no remaining capacity." }));
            }

            return (slot, null);
        }

        // Enforces the assignment 7-day booking window.
        private static (int StatusCode, object Body)? ValidateSevenDayWindow(DateTime scheduledAt)
        {
            var now = DateTime.UtcNow;
            if (scheduledAt <= now)
            {
                return (400, new { message = "Reservations must be for a future slot." });
            }

            if (scheduledAt > now.AddDays(7))
            {
                return (400, new { message = "Reservations must be scheduled within 7 days." });
            }

            return null;
        }

        // Enforces the assignment 12-hour notice rule for updates and cancellations.
        private static (int StatusCode, object Body)? ValidateTwelveHourNotice(DateTime scheduledAt)
        {
            if (scheduledAt < DateTime.UtcNow.AddHours(12))
            {
                return (400, new { message = "Updates and cancellations require at least 12 hours' notice." });
            }

            return null;
        }

        // Stops cancelled/completed bookings from being changed; prosumers may only change their own.
        private static (int StatusCode, object Body)? EnsureCanModify(
            EnergyReservation reservation,
            string role,
            string currentNic)
        {
            if (reservation.Status is ReservationStatuses.Cancelled or ReservationStatuses.Completed)
            {
                return (400, new { message = "This reservation can no longer be changed." });
            }

            if (role == UserRoles.Prosumer && reservation.ProsumerNic != currentNic)
            {
                return (403, new { message = "You can only change your own reservations." });
            }

            return null;
        }

        // Maps a reservation to the public DTO, including a short summary for clients.
        private static ReservationResponse ToResponse(EnergyReservation reservation, string? summary = null)
        {
            return new ReservationResponse
            {
                Id = reservation.Id ?? string.Empty,
                ProsumerNic = reservation.ProsumerNic,
                StationId = reservation.StationId,
                SlotId = reservation.SlotId,
                Status = reservation.Status,
                ScheduledAt = reservation.ScheduledAt,
                QrToken = reservation.QrToken,
                CreatedAt = reservation.CreatedAt,
                Summary = summary ?? $"Reservation is {reservation.Status}."
            };
        }
    }
}
