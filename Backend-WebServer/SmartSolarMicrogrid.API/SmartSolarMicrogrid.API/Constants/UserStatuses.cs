/*
 * File: UserStatuses.cs
 * Description: Account lifecycle states used by registration and Backoffice review.
 * Author: Dhiyanah Liyaudeen
 * Created: 17/09/2026
 */

namespace SmartSolarMicrogrid.API.Constants
{
    public static class UserStatuses
    {
        public const string Pending = "Pending";
        public const string Active = "Active";
        public const string Deactivated = "Deactivated";
    }
}
