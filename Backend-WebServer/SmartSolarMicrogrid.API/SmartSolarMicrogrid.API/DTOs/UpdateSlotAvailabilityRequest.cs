/*
 * File: UpdateSlotAvailabilityRequest.cs
 * Description: Body for operators changing remaining slot capacity.
 * Author: Dhiyanah Liyaudeen
 * Created: 19/09/2026
 */

namespace SmartSolarMicrogrid.API.DTOs
{
    public class UpdateSlotAvailabilityRequest
    {
        public double AvailableCapacity { get; set; }
    }
}
