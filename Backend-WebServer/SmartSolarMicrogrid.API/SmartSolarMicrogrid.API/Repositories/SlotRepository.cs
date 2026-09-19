/*
 * File: SlotRepository.cs
 * Description: MongoDB access for the EnergyBookingSlots collection.
 * Author: Dhiyanah Liyaudeen
 * Created: 19/09/2026
 */

using MongoDB.Bson;
using MongoDB.Driver;
using SmartSolarMicrogrid.API.Data;
using SmartSolarMicrogrid.API.Models;

namespace SmartSolarMicrogrid.API.Repositories
{
    public class SlotRepository
    {
        private readonly IMongoCollection<EnergyBookingSlot> _slots;

        // Stores the slots collection from the shared MongoDB context.
        public SlotRepository(MongoDbContext context)
        {
            _slots = context.EnergyBookingSlots;
        }

        // Builds a filter on MongoDB _id. Returns null when the string is not a valid ObjectId.
        private static FilterDefinition<EnergyBookingSlot>? IdFilter(string? id)
        {
            if (!ObjectId.TryParse(id, out var objectId))
            {
                return null;
            }

            return Builders<EnergyBookingSlot>.Filter.Eq("_id", objectId);
        }

        // Returns every slot, or only slots for one station.
        public async Task<List<EnergyBookingSlot>> GetAllAsync(string? stationId)
        {
            if (string.IsNullOrWhiteSpace(stationId))
            {
                return await _slots.Find(_ => true).ToListAsync();
            }

            if (!ObjectId.TryParse(stationId, out var stationObjectId))
            {
                return [];
            }

            var filter = Builders<EnergyBookingSlot>.Filter.Eq("stationId", stationObjectId);
            return await _slots.Find(filter).ToListAsync();
        }

        // Finds one slot by MongoDB ObjectId.
        public async Task<EnergyBookingSlot?> GetByIdAsync(string id)
        {
            var filter = IdFilter(id);
            if (filter is null)
            {
                return null;
            }

            return await _slots.Find(filter).FirstOrDefaultAsync();
        }

        // Inserts a new slot document.
        public async Task CreateAsync(EnergyBookingSlot slot)
        {
            await _slots.InsertOneAsync(slot);
        }

        // Replaces an existing slot document.
        public async Task UpdateAsync(EnergyBookingSlot slot)
        {
            var filter = IdFilter(slot.Id);
            if (filter is null)
            {
                return;
            }

            await _slots.ReplaceOneAsync(filter, slot);
        }

        // Changes only the status field so _id is never rewritten.
        public async Task UpdateStatusAsync(string id, string status)
        {
            var filter = IdFilter(id);
            if (filter is null)
            {
                return;
            }

            var update = Builders<EnergyBookingSlot>.Update.Set(s => s.Status, status);
            await _slots.UpdateOneAsync(filter, update);
        }

        // Deletes a slot document.
        public async Task DeleteAsync(string id)
        {
            var filter = IdFilter(id);
            if (filter is null)
            {
                return;
            }

            await _slots.DeleteOneAsync(filter);
        }

        // Adds or subtracts remaining capacity when a booking is created or cancelled.
        public async Task AdjustCapacityAsync(string id, double delta)
        {
            var filter = IdFilter(id);
            if (filter is null)
            {
                return;
            }

            var update = Builders<EnergyBookingSlot>.Update.Inc(s => s.AvailableCapacity, delta);
            await _slots.UpdateOneAsync(filter, update);
        }
    }
}
