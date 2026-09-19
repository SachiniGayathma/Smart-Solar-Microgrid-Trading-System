/*
 * File: StationRepository.cs
 * Description: MongoDB access for the SolarStationInfo collection.
 * Author: Dhiyanah Liyaudeen
 * Created: 19/09/2026
 */

using MongoDB.Bson;
using MongoDB.Driver;
using SmartSolarMicrogrid.API.Data;
using SmartSolarMicrogrid.API.Models;

namespace SmartSolarMicrogrid.API.Repositories
{
    public class StationRepository
    {
        private readonly IMongoCollection<SolarStationInfo> _stations;

        // Stores the stations collection from the shared MongoDB context.
        public StationRepository(MongoDbContext context)
        {
            _stations = context.SolarStations;
        }

        // Builds a filter on MongoDB _id. Returns null when the string is not a valid ObjectId.
        private static FilterDefinition<SolarStationInfo>? IdFilter(string? id)
        {
            if (!ObjectId.TryParse(id, out var objectId))
            {
                return null;
            }

            return Builders<SolarStationInfo>.Filter.Eq("_id", objectId);
        }

        // Returns every station document.
        public async Task<List<SolarStationInfo>> GetAllAsync()
        {
            return await _stations.Find(_ => true).ToListAsync();
        }

        // Returns stations with a given status (for maps, only Active hubs).
        public async Task<List<SolarStationInfo>> GetByStatusAsync(string status)
        {
            return await _stations.Find(s => s.Status == status).ToListAsync();
        }

        // Finds one station by MongoDB ObjectId.
        public async Task<SolarStationInfo?> GetByIdAsync(string id)
        {
            var filter = IdFilter(id);
            if (filter is null)
            {
                return null;
            }

            return await _stations.Find(filter).FirstOrDefaultAsync();
        }

        // Inserts a new station document.
        public async Task CreateAsync(SolarStationInfo station)
        {
            await _stations.InsertOneAsync(station);
        }

        // Replaces an existing station document, matching by _id ObjectId.
        public async Task UpdateAsync(SolarStationInfo station)
        {
            var filter = IdFilter(station.Id);
            if (filter is null)
            {
                return;
            }

            await _stations.ReplaceOneAsync(filter, station);
        }

        // Changes only the status field so _id is never rewritten.
        public async Task UpdateStatusAsync(string id, string status)
        {
            var filter = IdFilter(id);
            if (filter is null)
            {
                return;
            }

            var update = Builders<SolarStationInfo>.Update.Set(s => s.Status, status);
            await _stations.UpdateOneAsync(filter, update);
        }
    }
}
