/*
 * File: VerifyQrRequest.cs
 * Description: Body for a Grid Operator scanning a prosumer QR code.
 * Author: LIYAUDEEN D.H
 * Created: 19/09/2026
 */

namespace SmartSolarMicrogrid.API.DTOs
{
    public class VerifyQrRequest
    {
        public string QrToken { get; set; } = string.Empty;
    }
}
