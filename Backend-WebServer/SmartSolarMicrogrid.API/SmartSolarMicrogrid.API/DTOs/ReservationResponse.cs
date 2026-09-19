/*
 * File: ReservationResponse.cs
 * Description: Public reservation payload, including QR after approval.
 * Author: Dhiyanah Liyaudeen
 * Created: 19/09/2026
 */

namespace SmartSolarMicrogrid.API.DTOs
{
    public class ReservationResponse
    {
        public string Id { get; set; } = string.Empty;

        public string ProsumerNic { get; set; } = string.Empty;

        public string StationId { get; set; } = string.Empty;

        public string SlotId { get; set; } = string.Empty;

        public string Status { get; set; } = string.Empty;

        public DateTime ScheduledAt { get; set; }

        public string? QrToken { get; set; }

        public DateTime CreatedAt { get; set; }

        public string Summary { get; set; } = string.Empty;
    }
}
