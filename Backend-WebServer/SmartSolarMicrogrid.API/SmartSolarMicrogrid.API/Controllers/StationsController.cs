/*
 * File: StationsController.cs
 * Description: HTTP endpoints for microgrid node management.
 * Author: Dhiyanah Liyaudeen
 * Created: 19/09/2026
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
    public class StationsController : ControllerBase
    {
        private readonly StationService _stationService;

        // Injects station business logic.
        public StationsController(StationService stationService)
        {
            _stationService = stationService;
        }

        // Lists hubs. Backoffice sees all; others see active hubs only.
        [HttpGet]
        public async Task<IActionResult> GetAll()
        {
            var role = User.FindFirstValue(ClaimTypes.Role) ?? string.Empty;
            return Ok(await _stationService.GetAllAsync(role));
        }

        // Returns one hub by id.
        [HttpGet("{id:length(24)}")]
        public async Task<IActionResult> GetById(string id)
        {
            var station = await _stationService.GetByIdAsync(id);
            return station is null ? NotFound(new { message = "Station not found." }) : Ok(station);
        }

        // Creates a new microgrid hub.
        [Authorize(Roles = UserRoles.Backoffice)]
        [HttpPost]
        public async Task<IActionResult> Create([FromBody] StationRequest request)
        {
            var (statusCode, body) = await _stationService.CreateAsync(request);
            return StatusCode(statusCode, body);
        }

        // Updates a hub's details and schedule.
        [Authorize(Roles = UserRoles.Backoffice)]
        [HttpPut("{id:length(24)}")]
        public async Task<IActionResult> Update(string id, [FromBody] StationRequest request)
        {
            var (statusCode, body) = await _stationService.UpdateAsync(id, request);
            return StatusCode(statusCode, body);
        }

        // Updates available battery slots (Grid Operator operational tool).
        [Authorize(Roles = $"{UserRoles.Backoffice},{UserRoles.GridOperator}")]
        [HttpPatch("{id:length(24)}/availability")]
        public async Task<IActionResult> UpdateAvailability(string id, [FromBody] UpdateStationAvailabilityRequest request)
        {
            var (statusCode, body) = await _stationService.UpdateAvailabilityAsync(id, request);
            return StatusCode(statusCode, body);
        }

        // Deactivates a hub when no active reservations exist.
        [Authorize(Roles = UserRoles.Backoffice)]
        [HttpPost("{id:length(24)}/deactivate")]
        public async Task<IActionResult> Deactivate(string id)
        {
            var (statusCode, body) = await _stationService.DeactivateAsync(id);
            return StatusCode(statusCode, body);
        }

        // Reactivates a deactivated hub.
        [Authorize(Roles = UserRoles.Backoffice)]
        [HttpPost("{id:length(24)}/activate")]
        public async Task<IActionResult> Activate(string id)
        {
            var (statusCode, body) = await _stationService.ActivateAsync(id);
            return StatusCode(statusCode, body);
        }
    }
}
