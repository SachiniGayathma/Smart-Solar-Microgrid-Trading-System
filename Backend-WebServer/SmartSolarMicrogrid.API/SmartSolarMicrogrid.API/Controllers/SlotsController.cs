/*
 * File: SlotsController.cs
 * Description: HTTP endpoints for energy booking slot management.
 * Author: Dhiyanah Liyaudeen
 * Created: 19/09/2026
 */

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
    public class SlotsController : ControllerBase
    {
        private readonly SlotService _slotService;

        // Injects slot business logic.
        public SlotsController(SlotService slotService)
        {
            _slotService = slotService;
        }

        // Lists slots. Pass stationId to see windows for one hub.
        [HttpGet]
        public async Task<IActionResult> GetAll([FromQuery] string? stationId)
        {
            var (statusCode, body) = await _slotService.GetAllAsync(stationId);
            return StatusCode(statusCode, body);
        }

        // Returns one slot by id.
        [HttpGet("{id:length(24)}")]
        public async Task<IActionResult> GetById(string id)
        {
            var slot = await _slotService.GetByIdAsync(id);
            return slot is null ? NotFound(new { message = "Slot not found." }) : Ok(slot);
        }

        // Creates a booking window on a station.
        [Authorize(Roles = $"{UserRoles.Backoffice},{UserRoles.GridOperator}")]
        [HttpPost]
        public async Task<IActionResult> Create([FromBody] SlotRequest request)
        {
            var (statusCode, body) = await _slotService.CreateAsync(request);
            return StatusCode(statusCode, body);
        }

        // Updates a slot's times and capacity.
        [Authorize(Roles = $"{UserRoles.Backoffice},{UserRoles.GridOperator}")]
        [HttpPut("{id:length(24)}")]
        public async Task<IActionResult> Update(string id, [FromBody] SlotRequest request)
        {
            var (statusCode, body) = await _slotService.UpdateAsync(id, request);
            return StatusCode(statusCode, body);
        }

        // Updates remaining capacity only.
        [Authorize(Roles = $"{UserRoles.Backoffice},{UserRoles.GridOperator}")]
        [HttpPatch("{id:length(24)}/availability")]
        public async Task<IActionResult> UpdateAvailability(string id, [FromBody] UpdateSlotAvailabilityRequest request)
        {
            var (statusCode, body) = await _slotService.UpdateAvailabilityAsync(id, request);
            return StatusCode(statusCode, body);
        }

        // Deactivates a slot when no active reservations exist.
        [Authorize(Roles = $"{UserRoles.Backoffice},{UserRoles.GridOperator}")]
        [HttpPost("{id:length(24)}/deactivate")]
        public async Task<IActionResult> Deactivate(string id)
        {
            var (statusCode, body) = await _slotService.DeactivateAsync(id);
            return StatusCode(statusCode, body);
        }

        // Deletes a slot when no active reservations exist.
        [Authorize(Roles = UserRoles.Backoffice)]
        [HttpDelete("{id:length(24)}")]
        public async Task<IActionResult> Delete(string id)
        {
            var (statusCode, body) = await _slotService.DeleteAsync(id);
            return StatusCode(statusCode, body);
        }
    }
}
