/*
 * File: AuthResponse.cs
 * Description: Login/register payload returned to web and mobile clients.
 * Author: Dhiyanah Liyaudeen
 * Created: 17/09/2026
 */

namespace SmartSolarMicrogrid.API.DTOs
{
    public class AuthResponse
    {
        public string Token { get; set; } = string.Empty;

        public string Id { get; set; } = string.Empty;

        public string Nic { get; set; } = string.Empty;

        public string FullName { get; set; } = string.Empty;

        public string Email { get; set; } = string.Empty;

        public string Role { get; set; } = string.Empty;

        public string Status { get; set; } = string.Empty;
    }
}
