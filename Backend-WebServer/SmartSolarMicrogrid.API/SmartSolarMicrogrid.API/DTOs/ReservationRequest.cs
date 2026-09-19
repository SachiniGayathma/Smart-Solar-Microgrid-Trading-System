/*
 * File: ReservationRequest.cs
 * Description: Body for creating or updating an energy reservation.
 * Author: Dhiyanah Liyaudeen
 * Created: 19/09/2026
 */

namespace SmartSolarMicrogrid.API.DTOs
{
    public class ReservationRequest
    {
        public string SlotId { get; set; } = string.Empty;

        public string? ProsumerNic { get; set; }
    }
}
