/*
 * File: UsersController.cs
 * Description: HTTP endpoints for Backoffice user admin and self-service profile actions.
 * Author: Dhiyanah Liyaudeen
 * Created: 17/09/2026
 */

using System.Security.Claims;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using SmartSolarMicrogrid.API.Constants;
using SmartSolarMicrogrid.API.DTOs;
using SmartSolarMicrogrid.API.Services;

namespace SmartSolarMicrogrid.API.Controllers
{
    [ApiController]
    [Authorize]
    [Route("api/[controller]")]
    public class UsersController : ControllerBase
    {
        private readonly UserService _userService;

        // Injects user-management business logic.
        public UsersController(UserService userService)
        {
            _userService = userService;
        }

        // Lists every account for Backoffice.
        [Authorize(Roles = UserRoles.Backoffice)]
        [HttpGet]
        public async Task<IActionResult> GetAll()
        {
            return Ok(await _userService.GetAllAsync());
        }

        // Lists prosumers waiting for Backoffice activation.
        [Authorize(Roles = UserRoles.Backoffice)]
        [HttpGet("pending")]
        public async Task<IActionResult> GetPending()
        {
            return Ok(await _userService.GetPendingAsync());
        }

        // Returns the signed-in user's profile.
        [HttpGet("me")]
        public async Task<IActionResult> GetMe()
        {
            var userId = GetCurrentUserId();
            if (userId is null)
            {
                return Unauthorized();
            }

            var user = await _userService.GetByIdAsync(userId);
            return user is null ? NotFound(new { message = "User not found." }) : Ok(user);
        }

        // Returns one user by id for Backoffice.
        [Authorize(Roles = UserRoles.Backoffice)]
        [HttpGet("{id}")]
        public async Task<IActionResult> GetById(string id)
        {
            var user = await _userService.GetByIdAsync(id);
            return user is null ? NotFound(new { message = "User not found." }) : Ok(user);
        }

        // Creates an active Backoffice or Grid Operator user.
        [Authorize(Roles = UserRoles.Backoffice)]
        [HttpPost]
        public async Task<IActionResult> CreateStaff([FromBody] CreateStaffRequest request)
        {
            var (statusCode, body) = await _userService.CreateStaffAsync(request);
            return StatusCode(statusCode, body);
        }

        // Updates the signed-in user's own profile.
        [HttpPut("me")]
        public async Task<IActionResult> UpdateMe([FromBody] UpdateProfileRequest request)
        {
            var userId = GetCurrentUserId();
            if (userId is null)
            {
                return Unauthorized();
            }

            var (statusCode, body) = await _userService.UpdateOwnProfileAsync(userId, request);
            return StatusCode(statusCode, body);
        }

        // Lets a user request deactivation of their own account.
        [HttpPost("me/deactivate")]
        public async Task<IActionResult> DeactivateMe()
        {
            var userId = GetCurrentUserId();
            if (userId is null)
            {
                return Unauthorized();
            }

            var (statusCode, body) = await _userService.DeactivateAsync(userId);
            return StatusCode(statusCode, body);
        }

        // Activates a pending account or reactivates a deactivated account.
        [Authorize(Roles = UserRoles.Backoffice)]
        [HttpPost("{id}/activate")]
        public async Task<IActionResult> Activate(string id)
        {
            var (statusCode, body) = await _userService.ActivateAsync(id);
            return StatusCode(statusCode, body);
        }

        // Deactivates a user. Only Backoffice can later reactivate.
        [Authorize(Roles = UserRoles.Backoffice)]
        [HttpPost("{id}/deactivate")]
        public async Task<IActionResult> Deactivate(string id)
        {
            var (statusCode, body) = await _userService.DeactivateAsync(id);
            return StatusCode(statusCode, body);
        }

        // Reads the user id placed in the JWT.
        private string? GetCurrentUserId()
        {
            return User.FindFirstValue(ClaimTypes.NameIdentifier);
        }
    }
}
