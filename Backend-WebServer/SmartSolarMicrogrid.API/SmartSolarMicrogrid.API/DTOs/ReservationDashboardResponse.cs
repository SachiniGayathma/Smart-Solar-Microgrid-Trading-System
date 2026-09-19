/*
 * File: ReservationDashboardResponse.cs
 * Description: Counts for pending and approved future bookings.
 * Author: Dhiyanah Liyaudeen
 * Created: 19/09/2026
 */

namespace SmartSolarMicrogrid.API.DTOs
{
    public class ReservationDashboardResponse
    {
        public int PendingCount { get; set; }

        public int ApprovedFutureCount { get; set; }

        public int CurrentCount { get; set; }

        public int HistoryCount { get; set; }
    }
}
