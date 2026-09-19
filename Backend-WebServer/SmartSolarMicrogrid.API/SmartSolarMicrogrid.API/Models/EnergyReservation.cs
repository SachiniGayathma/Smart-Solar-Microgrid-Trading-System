/*
 * File: EnergyReservation.cs
 * Description: MongoDB document for a prosumer energy reservation.
 * Author: Dhiyanah Liyaudeen
 * Created: 17/09/2026
 */

using MongoDB.Bson;
using MongoDB.Bson.Serialization.Attributes;

namespace SmartSolarMicrogrid.API.Models
{
    public class EnergyReservation
    {
        [BsonId]
        [BsonRepresentation(BsonType.ObjectId)]
        public string? Id { get; set; }

        [BsonElement("prosumerNic")]
        public string ProsumerNic { get; set; } = string.Empty;

        [BsonElement("stationId")]
        [BsonRepresentation(BsonType.ObjectId)]
        public string StationId { get; set; } = string.Empty;

        [BsonElement("slotId")]
        [BsonRepresentation(BsonType.ObjectId)]
        public string SlotId { get; set; } = string.Empty;

        [BsonElement("status")]
        public string Status { get; set; } = "Pending";

        [BsonElement("scheduledAt")]
        public DateTime ScheduledAt { get; set; }

        [BsonElement("qrToken")]
        public string? QrToken { get; set; }

        [BsonElement("createdAt")]
        public DateTime CreatedAt { get; set; } = DateTime.UtcNow;
    }
}
