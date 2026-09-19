/*
 * File: UserRepository.cs
 * Description: MongoDB access for the Users collection only.
 * Author: Dhiyanah Liyaudeen
 * Created: 17/09/2026
 */

using MongoDB.Driver;
using SmartSolarMicrogrid.API.Data;
using SmartSolarMicrogrid.API.Models;

namespace SmartSolarMicrogrid.API.Repositories
{
    public class UserRepository
    {
        private readonly IMongoCollection<User> _users;

        // Stores the Users collection from the shared MongoDB context.
        public UserRepository(MongoDbContext context)
        {
            _users = context.Users;
        }

        // Returns every user document.
        public async Task<List<User>> GetAllAsync()
        {
            return await _users.Find(_ => true).ToListAsync();
        }

        // Returns users waiting for Backoffice activation.
        public async Task<List<User>> GetByStatusAsync(string status)
        {
            return await _users.Find(u => u.Status == status).ToListAsync();
        }

        // Finds one user by MongoDB id.
        public async Task<User?> GetByIdAsync(string id)
        {
            return await _users.Find(u => u.Id == id).FirstOrDefaultAsync();
        }

        // Finds one user by NIC (assignment primary key for prosumers).
        public async Task<User?> GetByNicAsync(string nic)
        {
            return await _users.Find(u => u.Nic == nic).FirstOrDefaultAsync();
        }

        // Finds one user by email (case-insensitive).
        public async Task<User?> GetByEmailAsync(string email)
        {
            var normalized = email.Trim().ToLowerInvariant();
            return await _users.Find(u => u.Email == normalized).FirstOrDefaultAsync();
        }

        // Finds a user by email or NIC so web and mobile can use either login field.
        public async Task<User?> GetByIdentifierAsync(string identifier)
        {
            var value = identifier.Trim();
            var email = value.ToLowerInvariant();

            return await _users.Find(u => u.Email == email || u.Nic == value).FirstOrDefaultAsync();
        }

        // Inserts a new user document.
        public async Task CreateAsync(User user)
        {
            await _users.InsertOneAsync(user);
        }

        // Replaces an existing user document.
        public async Task UpdateAsync(User user)
        {
            await _users.ReplaceOneAsync(u => u.Id == user.Id, user);
        }
    }
}
