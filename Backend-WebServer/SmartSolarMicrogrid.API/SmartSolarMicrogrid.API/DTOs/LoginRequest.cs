/*
 * File: LoginRequest.cs
 * Description: Body for login using email or NIC plus password.
 * Author: LIYAUDEEN D.H
 * Created: 17/09/2026
 */

namespace SmartSolarMicrogrid.API.DTOs
{
    public class LoginRequest
    {
        public string Identifier { get; set; } = string.Empty;

        public string Password { get; set; } = string.Empty;
    }
}
