/*
 * File: MongoDbContext.cs
 * Description: Shared MongoDB client and the four assignment collections.
 * Author: Dhiyanah Liyaudeen
 * Created: 17/09/2026
 */

using Microsoft.Extensions.Options;
using MongoDB.Driver;
using SmartSolarMicrogrid.API.Configuration;
using SmartSolarMicrogrid.API.Models;

namespace SmartSolarMicrogrid.API.Data
{
    public class MongoDbContext
    {
        private readonly IMongoDatabase _database;

        // Creates a reusable MongoDB client from appsettings and opens the configured database.
        public MongoDbContext(IOptions<MongoDbSettings> settings)
        {
            var client = new MongoClient(settings.Value.ConnectionString);
            _database = client.GetDatabase(settings.Value.DatabaseName);
        }

        public IMongoDatabase Database => _database;

        public IMongoCollection<User> Users =>
            _database.GetCollection<User>("Users");

        public IMongoCollection<SolarStationInfo> SolarStations =>
            _database.GetCollection<SolarStationInfo>("SolarStationInfo");

        public IMongoCollection<EnergyBookingSlot> EnergyBookingSlots =>
            _database.GetCollection<EnergyBookingSlot>("EnergyBookingSlots");

        public IMongoCollection<EnergyReservation> EnergyReservations =>
            _database.GetCollection<EnergyReservation>("EnergyReservations");
    }
}
