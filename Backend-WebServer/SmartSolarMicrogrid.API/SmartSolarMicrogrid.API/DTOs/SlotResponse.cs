/*
 * File: SlotResponse.cs
 * Description: Public energy-slot payload for web and mobile clients.
 * Author: Dhiyanah Liyaudeen
 * Created: 19/09/2026
 */

namespace SmartSolarMicrogrid.API.DTOs
{
    public class SlotResponse
    {
        public string Id { get; set; } = string.Empty;

        public string StationId { get; set; } = string.Empty;

        public DateTime StartTime { get; set; }

        public DateTime EndTime { get; set; }

        public double AvailableCapacity { get; set; }

        public string Status { get; set; } = string.Empty;
    }
}
