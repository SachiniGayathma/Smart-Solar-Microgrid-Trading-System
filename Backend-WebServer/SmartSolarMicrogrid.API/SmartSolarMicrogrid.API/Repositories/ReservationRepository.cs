/*
 * File: ReservationRepository.cs
 * Description: MongoDB access for energy reservations and booking queries.
 * Author: Dhiyanah Liyaudeen
 * Created: 19/09/2026
 */

using MongoDB.Bson;
using MongoDB.Driver;
using SmartSolarMicrogrid.API.Constants;
using SmartSolarMicrogrid.API.Data;
using SmartSolarMicrogrid.API.Models;

namespace SmartSolarMicrogrid.API.Repositories
{
    public class ReservationRepository
    {
        private readonly IMongoCollection<EnergyReservation> _reservations;

        // Stores the reservations collection from the shared MongoDB context.
        public ReservationRepository(MongoDbContext context)
        {
            _reservations = context.EnergyReservations;
        }

        // Builds a filter on MongoDB _id. Returns null when the string is not a valid ObjectId.
        private static FilterDefinition<EnergyReservation>? IdFilter(string? id)
        {
            if (!ObjectId.TryParse(id, out var objectId))
            {
                return null;
            }

            return Builders<EnergyReservation>.Filter.Eq("_id", objectId);
        }

        // True when the station still has pending or approved reservations.
        public async Task<bool> HasActiveReservationsAsync(string stationId)
        {
            return await _reservations
                .Find(r =>
                    r.StationId == stationId &&
                    (r.Status == ReservationStatuses.Pending || r.Status == ReservationStatuses.Approved))
                .AnyAsync();
        }

        // True when a specific slot still has pending or approved reservations.
        public async Task<bool> HasActiveReservationsForSlotAsync(string slotId)
        {
            return await _reservations
                .Find(r =>
                    r.SlotId == slotId &&
                    (r.Status == ReservationStatuses.Pending || r.Status == ReservationStatuses.Approved))
                .AnyAsync();
        }

        // Finds one reservation by MongoDB ObjectId.
        public async Task<EnergyReservation?> GetByIdAsync(string id)
        {
            var filter = IdFilter(id);
            if (filter is null)
            {
                return null;
            }

            return await _reservations.Find(filter).FirstOrDefaultAsync();
        }

        // Finds an approved reservation by its QR token.
        public async Task<EnergyReservation?> GetByQrTokenAsync(string qrToken)
        {
            return await _reservations.Find(r => r.QrToken == qrToken).FirstOrDefaultAsync();
        }

        // Lists reservations with optional status, NIC, and search filters.
        public async Task<List<EnergyReservation>> SearchAsync(string? status, string? prosumerNic, string? search)
        {
            var filters = new List<FilterDefinition<EnergyReservation>>();
            var builder = Builders<EnergyReservation>.Filter;

            if (!string.IsNullOrWhiteSpace(status))
            {
                filters.Add(builder.Eq(r => r.Status, status));
            }

            if (!string.IsNullOrWhiteSpace(prosumerNic))
            {
                filters.Add(builder.Eq(r => r.ProsumerNic, prosumerNic.Trim().ToUpperInvariant()));
            }

            if (!string.IsNullOrWhiteSpace(search))
            {
                var term = search.Trim();
                filters.Add(builder.Or(
                    builder.Regex(r => r.ProsumerNic, new BsonRegularExpression(term, "i")),
                    builder.Eq(r => r.Status, term),
                    builder.Eq(r => r.StationId, term),
                    builder.Eq(r => r.SlotId, term)));
            }

            var filter = filters.Count == 0 ? builder.Empty : builder.And(filters);
            return await _reservations.Find(filter).SortByDescending(r => r.CreatedAt).ToListAsync();
        }

        // Inserts a new reservation document.
        public async Task CreateAsync(EnergyReservation reservation)
        {
            await _reservations.InsertOneAsync(reservation);
        }

        // Replaces an existing reservation document.
        public async Task UpdateAsync(EnergyReservation reservation)
        {
            var filter = IdFilter(reservation.Id);
            if (filter is null)
            {
                return;
            }

            await _reservations.ReplaceOneAsync(filter, reservation);
        }
    }
}
