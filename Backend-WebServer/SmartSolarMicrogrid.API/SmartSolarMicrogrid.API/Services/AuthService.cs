/*
 * File: AuthService.cs
 * Description: Registration and login business rules (FAT service layer).
 * Author: Dhiyanah Liyaudeen
 * Created: 17/09/2026
 */

using SmartSolarMicrogrid.API.Constants;
using SmartSolarMicrogrid.API.DTOs;
using SmartSolarMicrogrid.API.Models;
using SmartSolarMicrogrid.API.Repositories;

namespace SmartSolarMicrogrid.API.Services
{
    public class AuthService
    {
        private readonly UserRepository _users;
        private readonly JwtTokenService _jwt;

        // Wires user storage and token creation.
        public AuthService(UserRepository users, JwtTokenService jwt)
        {
            _users = users;
            _jwt = jwt;
        }

        // Registers a prosumer with NIC as the unique key and Pending status.
        public async Task<(int StatusCode, object Body)> RegisterAsync(RegisterRequest request)
        {
            if (string.IsNullOrWhiteSpace(request.Nic) ||
                string.IsNullOrWhiteSpace(request.Email) ||
                string.IsNullOrWhiteSpace(request.Password) ||
                string.IsNullOrWhiteSpace(request.FullName))
            {
                return (400, new { message = "NIC, full name, email, and password are required." });
            }

            var nic = request.Nic.Trim().ToUpperInvariant();
            var email = request.Email.Trim().ToLowerInvariant();

            if (await _users.GetByNicAsync(nic) is not null)
            {
                return (409, new { message = "A user with this NIC already exists." });
            }

            if (await _users.GetByEmailAsync(email) is not null)
            {
                return (409, new { message = "A user with this email already exists." });
            }

            var user = new User
            {
                Nic = nic,
                FullName = request.FullName.Trim(),
                Email = email,
                Phone = request.Phone.Trim(),
                PasswordHash = BCrypt.Net.BCrypt.HashPassword(request.Password),
                Role = UserRoles.Prosumer,
                Status = UserStatuses.Pending,
                CreatedAt = DateTime.UtcNow
            };

            await _users.CreateAsync(user);

            return (201, ToAuthResponse(user, _jwt.CreateToken(user)));
        }

        // Verifies password and issues a JWT. Deactivated accounts cannot sign in.
        public async Task<(int StatusCode, object Body)> LoginAsync(LoginRequest request)
        {
            if (string.IsNullOrWhiteSpace(request.Identifier) || string.IsNullOrWhiteSpace(request.Password))
            {
                return (400, new { message = "Identifier and password are required." });
            }

            var user = await _users.GetByIdentifierAsync(request.Identifier);
            if (user is null || !BCrypt.Net.BCrypt.Verify(request.Password, user.PasswordHash))
            {
                return (401, new { message = "Invalid credentials." });
            }

            if (user.Status == UserStatuses.Deactivated)
            {
                return (403, new { message = "This account is deactivated. A Backoffice officer must reactivate it." });
            }

            return (200, ToAuthResponse(user, _jwt.CreateToken(user)));
        }

        // Maps a user entity to the public auth payload.
        private static AuthResponse ToAuthResponse(User user, string token)
        {
            return new AuthResponse
            {
                Token = token,
                Id = user.Id ?? string.Empty,
                Nic = user.Nic,
                FullName = user.FullName,
                Email = user.Email,
                Role = user.Role,
                Status = user.Status
            };
        }
    }
}
