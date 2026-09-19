/*
 * File: UserService.cs
 * Description: User management rules for Backoffice and self-service profile edits.
 * Author: Dhiyanah Liyaudeen
 * Created: 17/09/2026
 */

using SmartSolarMicrogrid.API.Constants;
using SmartSolarMicrogrid.API.DTOs;
using SmartSolarMicrogrid.API.Models;
using SmartSolarMicrogrid.API.Repositories;

namespace SmartSolarMicrogrid.API.Services
{
    public class UserService
    {
        private readonly UserRepository _users;

        // Stores the user repository used by all account operations.
        public UserService(UserRepository users)
        {
            _users = users;
        }

        // Returns all users without password hashes.
        public async Task<List<UserResponse>> GetAllAsync()
        {
            var users = await _users.GetAllAsync();
            return users.Select(ToResponse).ToList();
        }

        // Returns prosumers still waiting for activation.
        public async Task<List<UserResponse>> GetPendingAsync()
        {
            var users = await _users.GetByStatusAsync(UserStatuses.Pending);
            return users.Select(ToResponse).ToList();
        }

        // Returns one user or null if the id does not exist.
        public async Task<UserResponse?> GetByIdAsync(string id)
        {
            var user = await _users.GetByIdAsync(id);
            return user is null ? null : ToResponse(user);
        }

        // Creates an active Backoffice or Grid Operator account.
        public async Task<(int StatusCode, object Body)> CreateStaffAsync(CreateStaffRequest request)
        {
            if (request.Role is not (UserRoles.Backoffice or UserRoles.GridOperator))
            {
                return (400, new { message = "Role must be Backoffice or GridOperator." });
            }

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
                Role = request.Role,
                Status = UserStatuses.Active,
                CreatedAt = DateTime.UtcNow
            };

            await _users.CreateAsync(user);
            return (201, ToResponse(user));
        }

        // Lets the signed-in user edit name, email, and phone.
        public async Task<(int StatusCode, object Body)> UpdateOwnProfileAsync(string userId, UpdateProfileRequest request)
        {
            var user = await _users.GetByIdAsync(userId);
            if (user is null)
            {
                return (404, new { message = "User not found." });
            }

            if (string.IsNullOrWhiteSpace(request.FullName) || string.IsNullOrWhiteSpace(request.Email))
            {
                return (400, new { message = "Full name and email are required." });
            }

            var email = request.Email.Trim().ToLowerInvariant();
            var existing = await _users.GetByEmailAsync(email);
            if (existing is not null && existing.Id != user.Id)
            {
                return (409, new { message = "A user with this email already exists." });
            }

            user.FullName = request.FullName.Trim();
            user.Email = email;
            user.Phone = request.Phone.Trim();
            await _users.UpdateAsync(user);

            return (200, ToResponse(user));
        }

        // Activates a pending account or reactivates a deactivated one (Backoffice only).
        public async Task<(int StatusCode, object Body)> ActivateAsync(string id)
        {
            var user = await _users.GetByIdAsync(id);
            if (user is null)
            {
                return (404, new { message = "User not found." });
            }

            if (user.Status == UserStatuses.Active)
            {
                return (400, new { message = "This account is already active." });
            }

            user.Status = UserStatuses.Active;
            await _users.UpdateAsync(user);
            return (200, ToResponse(user));
        }

        // Deactivates an account. Reactivation is a separate Backoffice-only action.
        public async Task<(int StatusCode, object Body)> DeactivateAsync(string id)
        {
            var user = await _users.GetByIdAsync(id);
            if (user is null)
            {
                return (404, new { message = "User not found." });
            }

            if (user.Status == UserStatuses.Deactivated)
            {
                return (400, new { message = "This account is already deactivated." });
            }

            user.Status = UserStatuses.Deactivated;
            await _users.UpdateAsync(user);
            return (200, ToResponse(user));
        }

        // Maps a MongoDB user to the public DTO.
        private static UserResponse ToResponse(User user)
        {
            return new UserResponse
            {
                Id = user.Id ?? string.Empty,
                Nic = user.Nic,
                FullName = user.FullName,
                Email = user.Email,
                Phone = user.Phone,
                Role = user.Role,
                Status = user.Status,
                CreatedAt = user.CreatedAt
            };
        }
    }
}
