/*
 * File: UpdateStationAvailabilityRequest.cs
 * Description: Body for Grid Operators updating battery slot availability.
 * Author: Dhiyanah Liyaudeen
 * Created: 19/09/2026
 */

namespace SmartSolarMicrogrid.API.DTOs
{
    public class UpdateStationAvailabilityRequest
    {
        public int BatteryStorageSlots { get; set; }
    }
}
