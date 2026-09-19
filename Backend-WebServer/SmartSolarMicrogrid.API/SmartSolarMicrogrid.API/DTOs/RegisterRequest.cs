/*
 * File: RegisterRequest.cs
 * Description: Body for prosumer self-registration from the mobile app.
 * Author: Dhiyanah Liyaudeen
 * Created: 17/09/2026
 */

namespace SmartSolarMicrogrid.API.DTOs
{
    public class RegisterRequest
    {
        public string Nic { get; set; } = string.Empty;

        public string FullName { get; set; } = string.Empty;

        public string Email { get; set; } = string.Empty;

        public string Phone { get; set; } = string.Empty;

        public string Password { get; set; } = string.Empty;
    }
}
