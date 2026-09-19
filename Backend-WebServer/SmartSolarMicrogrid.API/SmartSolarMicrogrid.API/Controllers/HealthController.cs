/*
 * File: HealthController.cs
 * Description: Checks whether the API can ping MongoDB.
 * Author: Dhiyanah Liyaudeen
 * Created: 17/09/2026
 */

using Microsoft.AspNetCore.Mvc;
using MongoDB.Bson;
using MongoDB.Driver;
using SmartSolarMicrogrid.API.Data;

namespace SmartSolarMicrogrid.API.Controllers
{
    [ApiController]
    [Route("api/[controller]")]
    public class HealthController : ControllerBase
    {
        private readonly MongoDbContext _context;

        // Injects the shared MongoDB context.
        public HealthController(MongoDbContext context)
        {
            _context = context;
        }

        // Pings MongoDB and reports connected or disconnected.
        [HttpGet]
        public async Task<IActionResult> Get()
        {
            try
            {
                await _context.Database.RunCommandAsync<BsonDocument>(new BsonDocument("ping", 1));

                return Ok(new { mongodb = "connected" });
            }
            catch (Exception ex)
            {
                return StatusCode(500, new
                {
                    mongodb = "disconnected",
                    error = ex.Message
                });
            }
        }
    }
}
