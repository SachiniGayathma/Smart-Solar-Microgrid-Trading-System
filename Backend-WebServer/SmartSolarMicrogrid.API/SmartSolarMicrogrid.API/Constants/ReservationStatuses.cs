/*
 * File: ReservationStatuses.cs
 * Description: Booking states used to block station deactivation when work is still active.
 * Author: Dhiyanah Liyaudeen
 * Created: 19/09/2026
 */

namespace SmartSolarMicrogrid.API.Constants
{
    public static class ReservationStatuses
    {
        public const string Pending = "Pending";
        public const string Approved = "Approved";
        public const string Cancelled = "Cancelled";
        public const string Completed = "Completed";

        public static readonly string[] Active =
        [
            Pending,
            Approved
        ];
    }
}
