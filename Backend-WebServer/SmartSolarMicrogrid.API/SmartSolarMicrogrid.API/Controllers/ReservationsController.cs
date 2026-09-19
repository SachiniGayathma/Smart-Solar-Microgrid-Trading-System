/*
 * File: ReservationsController.cs
 * Description: HTTP endpoints for energy reservations, dashboard, QR, and operator scan.
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
    public class ReservationsController : ControllerBase
    {
        private readonly ReservationService _reservationService;

        // Injects reservation business logic.
        public ReservationsController(ReservationService reservationService)
        {
            _reservationService = reservationService;
        }

        // Returns pending / approved-future counts.
        [HttpGet("dashboard")]
        public async Task<IActionResult> GetDashboard()
        {
            return Ok(await _reservationService.GetDashboardAsync(GetRole(), GetNic()));
        }

        // Lists bookings. Optional status and search query parameters.
        [HttpGet]
        public async Task<IActionResult> Search([FromQuery] string? status, [FromQuery] string? search)
        {
            return Ok(await _reservationService.SearchAsync(GetRole(), GetNic(), status, search));
        }

        // Returns one booking.
        [HttpGet("{id:length(24)}")]
        public async Task<IActionResult> GetById(string id)
        {
            var (statusCode, body) = await _reservationService.GetByIdAsync(id, GetRole(), GetNic());
            return StatusCode(statusCode, body);
        }

        // Creates a pending reservation.
        [HttpPost]
        public async Task<IActionResult> Create([FromBody] ReservationRequest request)
        {
            var (statusCode, body) = await _reservationService.CreateAsync(request, GetRole(), GetNic());
            return StatusCode(statusCode, body);
        }

        // Updates a reservation when 12-hour notice is met.
        [HttpPut("{id:length(24)}")]
        public async Task<IActionResult> Update(string id, [FromBody] ReservationRequest request)
        {
            var (statusCode, body) = await _reservationService.UpdateAsync(id, request, GetRole(), GetNic());
            return StatusCode(statusCode, body);
        }

        // Cancels a reservation when 12-hour notice is met.
        [HttpPost("{id:length(24)}/cancel")]
        public async Task<IActionResult> Cancel(string id)
        {
            var (statusCode, body) = await _reservationService.CancelAsync(id, GetRole(), GetNic());
            return StatusCode(statusCode, body);
        }

        // Approves a pending reservation and issues a QR token.
        [Authorize(Roles = $"{UserRoles.Backoffice},{UserRoles.GridOperator}")]
        [HttpPost("{id:length(24)}/approve")]
        public async Task<IActionResult> Approve(string id)
        {
            var (statusCode, body) = await _reservationService.ApproveAsync(id);
            return StatusCode(statusCode, body);
        }

        // Returns the QR token after approval.
        [HttpGet("{id:length(24)}/qr")]
        public async Task<IActionResult> GetQr(string id)
        {
            var (statusCode, body) = await _reservationService.GetQrAsync(id, GetRole(), GetNic());
            return StatusCode(statusCode, body);
        }

        // Operator scan: verify QR against the server and mark the job done.
        [Authorize(Roles = $"{UserRoles.Backoffice},{UserRoles.GridOperator}")]
        [HttpPost("verify-qr")]
        public async Task<IActionResult> VerifyQr([FromBody] VerifyQrRequest request)
        {
            var (statusCode, body) = await _reservationService.VerifyQrAsync(request.QrToken);
            return StatusCode(statusCode, body);
        }

        // Reads the role claim from the JWT.
        private string GetRole()
        {
            return User.FindFirstValue(ClaimTypes.Role) ?? string.Empty;
        }

        // Reads the NIC claim from the JWT.
        private string GetNic()
        {
            return User.FindFirstValue("nic") ?? string.Empty;
        }
    }
}
