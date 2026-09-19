/*
 * File: DatabaseSeeder.cs
 * Description: Creates unique indexes and a default Backoffice account for first login.
 * Author: Dhiyanah Liyaudeen
 * Created: 17/09/2026
 */

using MongoDB.Driver;
using SmartSolarMicrogrid.API.Constants;
using SmartSolarMicrogrid.API.Data;
using SmartSolarMicrogrid.API.Models;

namespace SmartSolarMicrogrid.API.Data
{
    public class DatabaseSeeder
    {
        public const string DefaultBackofficeEmail = "backoffice@smartsolar.local";
        public const string DefaultBackofficePassword = "Admin@123";

        private readonly MongoDbContext _context;

        // Uses the shared database context to seed startup data.
        public DatabaseSeeder(MongoDbContext context)
        {
            _context = context;
        }

        // Ensures NIC/email uniqueness and inserts a default Backoffice user if missing.
        public async Task SeedAsync()
        {
            await _context.Users.Indexes.CreateManyAsync(
            [
                new CreateIndexModel<User>(
                    Builders<User>.IndexKeys.Ascending(u => u.Nic),
                    new CreateIndexOptions { Unique = true, Name = "ux_users_nic" }),
                new CreateIndexModel<User>(
                    Builders<User>.IndexKeys.Ascending(u => u.Email),
                    new CreateIndexOptions { Unique = true, Name = "ux_users_email" })
            ]);

            var existing = await _context.Users
                .Find(u => u.Email == DefaultBackofficeEmail)
                .FirstOrDefaultAsync();

            if (existing is not null)
            {
                return;
            }

            var admin = new User
            {
                Nic = "ADMIN000000V",
                FullName = "Default Backoffice",
                Email = DefaultBackofficeEmail,
                Phone = "0000000000",
                PasswordHash = BCrypt.Net.BCrypt.HashPassword(DefaultBackofficePassword),
                Role = UserRoles.Backoffice,
                Status = UserStatuses.Active,
                CreatedAt = DateTime.UtcNow
            };

            await _context.Users.InsertOneAsync(admin);
        }
    }
}
