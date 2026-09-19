/*
 * File: EnergyBookingSlot.cs
 * Description: MongoDB document for an energy booking slot at a station.
 * Author: Dhiyanah Liyaudeen
 * Created: 17/09/2026
 */

using MongoDB.Bson;
using MongoDB.Bson.Serialization.Attributes;

namespace SmartSolarMicrogrid.API.Models
{
    public class EnergyBookingSlot
    {
        [BsonId]
        [BsonRepresentation(BsonType.ObjectId)]
        public string? Id { get; set; }

        [BsonElement("stationId")]
        [BsonRepresentation(BsonType.ObjectId)]
        public string StationId { get; set; } = string.Empty;

        [BsonElement("startTime")]
        public DateTime StartTime { get; set; }

        [BsonElement("endTime")]
        public DateTime EndTime { get; set; }

        [BsonElement("availableCapacity")]
        public double AvailableCapacity { get; set; }

        [BsonElement("status")]
        public string Status { get; set; } = "Available";
    }
}
