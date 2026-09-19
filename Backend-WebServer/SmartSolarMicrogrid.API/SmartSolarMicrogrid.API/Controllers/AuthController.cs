/*
 * File: AuthController.cs
 * Description: HTTP endpoints for prosumer registration and login.
 * Author: Dhiyanah Liyaudeen
 * Created: 17/09/2026
 */

using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using SmartSolarMicrogrid.API.DTOs;
using SmartSolarMicrogrid.API.Services;

namespace SmartSolarMicrogrid.API.Controllers
{
    [ApiController]
    [Route("api/[controller]")]
    public class AuthController : ControllerBase
    {
        private readonly AuthService _authService;

        // Injects authentication business logic.
        public AuthController(AuthService authService)
        {
            _authService = authService;
        }

        // Creates a Pending prosumer account from the mobile app.
        [AllowAnonymous]
        [HttpPost("register")]
        public async Task<IActionResult> Register([FromBody] RegisterRequest request)
        {
            var (statusCode, body) = await _authService.RegisterAsync(request);
            return StatusCode(statusCode, body);
        }

        // Signs in with email or NIC and returns a JWT plus role.
        [AllowAnonymous]
        [HttpPost("login")]
        public async Task<IActionResult> Login([FromBody] LoginRequest request)
        {
            var (statusCode, body) = await _authService.LoginAsync(request);
            return StatusCode(statusCode, body);
        }
    }
}
