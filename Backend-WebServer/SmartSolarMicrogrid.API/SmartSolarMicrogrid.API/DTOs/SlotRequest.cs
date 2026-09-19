/*
 * File: SlotRequest.cs
 * Description: Body for creating or updating an energy booking slot.
 * Author: Dhiyanah Liyaudeen
 * Created: 19/09/2026
 */

namespace SmartSolarMicrogrid.API.DTOs
{
    public class SlotRequest
    {
        public string StationId { get; set; } = string.Empty;

        public DateTime StartTime { get; set; }

        public DateTime EndTime { get; set; }

        public double AvailableCapacity { get; set; }
    }
}
