/*
 * File: StationRequest.cs
 * Description: Body for creating or updating a microgrid station.
 * Author: Dhiyanah Liyaudeen
 * Created: 19/09/2026
 */

namespace SmartSolarMicrogrid.API.DTOs
{
    public class StationRequest
    {
        public string Name { get; set; } = string.Empty;

        public double Latitude { get; set; }

        public double Longitude { get; set; }

        public double CapacityKwh { get; set; }

        public int BatteryStorageSlots { get; set; }

        public string Schedule { get; set; } = string.Empty;
    }
}
