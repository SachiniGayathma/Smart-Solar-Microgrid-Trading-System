/*
 * File: JwtTokenService.cs
 * Description: Creates signed JWT tokens that web and mobile send on later requests.
 * Author: Dhiyanah Liyaudeen
 * Created: 17/09/2026
 */

using System.IdentityModel.Tokens.Jwt;
using System.Security.Claims;
using System.Text;
using Microsoft.Extensions.Options;
using Microsoft.IdentityModel.Tokens;
using SmartSolarMicrogrid.API.Configuration;
using SmartSolarMicrogrid.API.Models;

namespace SmartSolarMicrogrid.API.Services
{
    public class JwtTokenService
    {
        private readonly JwtSettings _settings;

        // Loads JWT issuer, audience, key, and expiry from configuration.
        public JwtTokenService(IOptions<JwtSettings> settings)
        {
            _settings = settings.Value;
        }

        // Builds a token that carries user id, NIC, role, and account status.
        public string CreateToken(User user)
        {
            var claims = new List<Claim>
            {
                new(JwtRegisteredClaimNames.Sub, user.Id ?? string.Empty),
                new(ClaimTypes.NameIdentifier, user.Id ?? string.Empty),
                new(ClaimTypes.Name, user.FullName),
                new(ClaimTypes.Email, user.Email),
                new(ClaimTypes.Role, user.Role),
                new("nic", user.Nic),
                new("status", user.Status)
            };

            var key = new SymmetricSecurityKey(Encoding.UTF8.GetBytes(_settings.Key));
            var credentials = new SigningCredentials(key, SecurityAlgorithms.HmacSha256);

            var token = new JwtSecurityToken(
                issuer: _settings.Issuer,
                audience: _settings.Audience,
                claims: claims,
                expires: DateTime.UtcNow.AddMinutes(_settings.ExpiryMinutes),
                signingCredentials: credentials);

            return new JwtSecurityTokenHandler().WriteToken(token);
        }
    }
}
