/*
 * File: MongoDbSettings.cs
 * Description: Holds MongoDB connection values loaded from appsettings.json.
 * Author: Dhiyanah Liyaudeen
 * Created: 17/09/2026
 */

namespace SmartSolarMicrogrid.API.Configuration
{
    public class MongoDbSettings
    {
        public string ConnectionString { get; set; } = string.Empty;

        public string DatabaseName { get; set; } = string.Empty;
    }
}
