/*
 * File: UpdateProfileRequest.cs
 * Description: Body for a logged-in user editing their own profile.
 * Author: LIYAUDEEN D.H
 * Created: 17/09/2026
 */

namespace SmartSolarMicrogrid.API.DTOs
{
    public class UpdateProfileRequest
    {
        public string FullName { get; set; } = string.Empty;

        public string Email { get; set; } = string.Empty;

        public string Phone { get; set; } = string.Empty;
    }
}
