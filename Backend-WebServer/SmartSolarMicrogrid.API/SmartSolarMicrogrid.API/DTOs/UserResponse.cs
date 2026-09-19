/*
 * File: UserResponse.cs
 * Description: Safe user view returned by APIs (never includes password hash).
 * Author: Dhiyanah Liyaudeen
 * Created: 17/09/2026
 */

namespace SmartSolarMicrogrid.API.DTOs
{
    public class UserResponse
    {
        public string Id { get; set; } = string.Empty;

        public string Nic { get; set; } = string.Empty;

        public string FullName { get; set; } = string.Empty;

        public string Email { get; set; } = string.Empty;

        public string Phone { get; set; } = string.Empty;

        public string Role { get; set; } = string.Empty;

        public string Status { get; set; } = string.Empty;

        public DateTime CreatedAt { get; set; }
    }
}
