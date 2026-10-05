# Smart Solar Microgrid — Web API

C# ASP.NET Core Web API for the SE4040 Smart Solar Microgrid Trading System.

This service is the **FAT service**: all business rules live here. The web app and native Android app are UI only and must call these REST endpoints. They must not talk to MongoDB directly.

Open the project: `Backend-WebServer/SmartSolarMicrogrid.API/SmartSolarMicrogrid.API.slnx`

---

## What is implemented (vs the assignment)

Covered in this API:

- Roles: **Backoffice**, **GridOperator**, **Prosumer**
- Prosumer register with **NIC** as unique key, status **Pending** until Backoffice activates
- Only **Backoffice** can reactivate a deactivated account
- Microgrid nodes: GPS, capacity (kWh), battery slots, schedule; **cannot deactivate** if Pending/Approved reservations exist
- Energy booking slots (time windows on a station)
- Reservations: **within 7 days**, **≥ 12 hours’ notice** to update or cancel
- Approve booking → secure **QR token**; operator **verify-qr** marks the job **Completed**
- Dashboard counts + list/search/history via query params
- MongoDB collections: `Users`, `SolarStationInfo`, `EnergyBookingSlots`, `EnergyReservations`
- JWT auth so both clients can send `Authorization: Bearer <token>`

Not this API (other group members / later):

- Web UI (Bootstrap / Tailwind / React)
- Native Android + SQLite + Google Maps + camera QR scan
- **IIS hosting** for the demo (API currently runs in Visual Studio / Kestrel)

Maps use station `latitude` / `longitude` from `GET /api/stations`. The Android app draws the QR from `qrToken`; this API only **issues and verifies** the token.

---

## Architecture

```
Android / Web  →  REST + JWT  →  Controllers  →  Services (rules)  →  Repositories  →  MongoDB
```

| Layer | Folder | Job |
|---|---|---|
| HTTP | `Controllers/` | Routes, status codes, who is logged in |
| Rules | `Services/` | 7-day window, 12-hour notice, roles, QR |
| Data | `Repositories/` + `Data/` | MongoDB reads/writes |

---

## Roles and account status

| Role | Typical client | Can |
|---|---|---|
| **Backoffice** | Web | Users, stations, slots, approve/cancel bookings, reactivate accounts |
| **GridOperator** | Web + mobile | Slot/station availability, list bookings, approve, scan QR |
| **Prosumer** | Mobile | Register, edit own profile, book/update/cancel own slots, show QR |

| User status | Meaning |
|---|---|
| `Pending` | Prosumer registered; waiting for Backoffice |
| `Active` | Can use the system (deactivated users cannot log in) |
| `Deactivated` | Blocked until Backoffice activates again |

Staff created by Backoffice start as `Active`. NIC and email are unique.

---

## Business flow

### 1. Accounts

1. Prosumer `POST /api/auth/register` → `Pending`.
2. Backoffice `GET /api/users/pending` then `POST /api/users/{id}/activate`.
3. Anyone with an account `POST /api/auth/login` (email **or** NIC + password) → JWT + `role` + `status`.
4. Clients send that JWT on later calls.
5. Deactivated users get **403** on login. Only Backoffice can activate again.

Default seed (first API start):

- Email: `backoffice@smartsolar.local`
- Password: `Admin@123`
- NIC: `ADMIN000000V`

### 2. Hubs and slots

1. Backoffice creates a **station** (hub) with GPS, kWh capacity, battery slots, schedule.
2. Backoffice or Grid Operator creates **slots** (time windows) on an **Active** station.
3. Operators can patch remaining capacity (`availability`).
4. Station/slot deactivate or slot delete is **409** if a reservation is still `Pending` or `Approved`.

### 3. Booking → energy transfer

1. Prosumer (or staff on their behalf) creates a reservation on a slot.
   - Slot start must be **in the future** and **within 7 days**.
   - Slot must be `Available`, station `Active`, capacity ≥ 1.
   - Prosumer must be `Active`.
   - New booking is `Pending`. Capacity on the slot decreases by 1.
2. Staff **approve** → status `Approved` and a `qrToken` is generated.
3. Prosumer `GET .../qr` for the mobile QR.
4. Operator `POST /api/reservations/verify-qr` with that token → `Completed`.
5. Update/cancel only if **at least 12 hours** remain before `scheduledAt`. Cancel restores slot capacity. After cancel/complete, the booking cannot be changed.

Staff creating a booking must send `prosumerNic`. A logged-in prosumer **omits** `prosumerNic`; the NIC comes from the token.

---

## Auth in Postman

Public (no token): `POST /api/auth/login`, `POST /api/auth/register`, `GET /api/health`.

Everything else: **Authorization → Bearer Token** (paste the token only, no `Bearer ` prefix).

---

## Endpoints

### Health

| Method | Path | Auth | Notes |
|---|---|---|---|
| GET | `/api/health` | None | `{ "mongodb": "connected" }` |

### Auth

| Method | Path | Auth | Body |
|---|---|---|---|
| POST | `/api/auth/register` | None | `nic`, `fullName`, `email`, `phone`, `password` → Prosumer `Pending` |
| POST | `/api/auth/login` | None | `identifier` (email or NIC), `password` → `token`, `role`, `status` |

### Users

| Method | Path | Auth | Notes |
|---|---|---|---|
| GET | `/api/users` | Backoffice | All accounts |
| GET | `/api/users/pending` | Backoffice | Waiting activation |
| GET | `/api/users/me` | Any user | Own profile |
| GET | `/api/users/{id}` | Backoffice | One user |
| POST | `/api/users` | Backoffice | Create Backoffice or GridOperator (`role` must be exactly that) |
| PUT | `/api/users/me` | Any user | `fullName`, `email`, `phone` |
| POST | `/api/users/me/deactivate` | Any user | Request own deactivation |
| POST | `/api/users/{id}/activate` | Backoffice | Pending or deactivated → Active |
| POST | `/api/users/{id}/deactivate` | Backoffice | No body |

Create staff example:

```json
{
  "nic": "199812345V",
  "fullName": "Grid Operator One",
  "email": "operator@smartsolar.local",
  "phone": "0770000001",
  "password": "Operator@123",
  "role": "GridOperator"
}
```

### Stations

| Method | Path | Auth | Notes |
|---|---|---|---|
| GET | `/api/stations` | Any user | Backoffice sees all; others only `Active` |
| GET | `/api/stations/{id}` | Any user | One hub (24-char id) |
| POST | `/api/stations` | Backoffice | Create hub |
| PUT | `/api/stations/{id}` | Backoffice | Update GPS, capacity, schedule, slots |
| PATCH | `/api/stations/{id}/availability` | Backoffice, GridOperator | `{ "batteryStorageSlots": 5 }` |
| POST | `/api/stations/{id}/deactivate` | Backoffice | No body; **409** if active bookings |
| POST | `/api/stations/{id}/activate` | Backoffice | No body |

Create station example:

```json
{
  "name": "Nugegoda Solar Hub",
  "latitude": 6.8649,
  "longitude": 79.8997,
  "capacityKwh": 50,
  "batteryStorageSlots": 8,
  "schedule": "06:00-18:00 daily"
}
```

### Slots

| Method | Path | Auth | Notes |
|---|---|---|---|
| GET | `/api/slots` | Any user | Optional `?stationId=` |
| GET | `/api/slots/{id}` | Any user | |
| POST | `/api/slots` | Backoffice, GridOperator | Window on an Active station |
| PUT | `/api/slots/{id}` | Backoffice, GridOperator | |
| PATCH | `/api/slots/{id}/availability` | Backoffice, GridOperator | `{ "availableCapacity": 6 }` |
| POST | `/api/slots/{id}/deactivate` | Backoffice, GridOperator | **409** if active bookings |
| DELETE | `/api/slots/{id}` | Backoffice | **409** if active bookings |

Create slot example (`endTime` after `startTime`; use a time in the next 7 days if you will book it):

```json
{
  "stationId": "PASTE_STATION_ID",
  "startTime": "2026-09-22T09:00:00Z",
  "endTime": "2026-09-22T11:00:00Z",
  "availableCapacity": 10
}
```

### Reservations

| Method | Path | Auth | Notes |
|---|---|---|---|
| GET | `/api/reservations/dashboard` | Any user | Pending, approved-future, current, history counts. Prosumer = own data |
| GET | `/api/reservations` | Any user | `?status=Pending` and/or `?search=` |
| GET | `/api/reservations/{id}` | Any user | Prosumer: own only |
| POST | `/api/reservations` | Any user | Create `Pending` |
| PUT | `/api/reservations/{id}` | Any user | Change slot; 12-hour + 7-day rules; goes back to Pending |
| POST | `/api/reservations/{id}/cancel` | Any user | 12-hour rule; no body |
| POST | `/api/reservations/{id}/approve` | Backoffice, GridOperator | Issues `qrToken` |
| GET | `/api/reservations/{id}/qr` | Any user | Prosumer: own QR only |
| POST | `/api/reservations/verify-qr` | Backoffice, GridOperator | `{ "qrToken": "..." }` → Completed |

Create as **staff**:

```json
{
  "slotId": "PASTE_SLOT_ID",
  "prosumerNic": "199912345V"
}
```

Create as **that prosumer** (their token; do not send `prosumerNic`):

```json
{
  "slotId": "PASTE_SLOT_ID"
}
```

Reservation statuses: `Pending` → `Approved` → `Completed`, or `Cancelled`.

Each create/update/cancel/approve/verify response includes a `summary` string for the client summary screen.

---

## MongoDB

Database name (see `appsettings.json`): `SmartSolarMicrogridDB`

| Collection | Purpose |
|---|---|
| `Users` | Accounts |
| `SolarStationInfo` | Hubs |
| `EnergyBookingSlots` | Bookable windows |
| `EnergyReservations` | Bookings + QR |

---

## Typical HTTP codes

| Code | Meaning |
|---|---|
| 200 / 201 | OK / created |
| 400 | Rule failed (7-day, 12-hour, validation) |
| 401 | Missing or invalid token |
| 403 | Wrong role, or deactivated login |
| 404 | Id not found |
| 409 | Duplicate NIC/email, or deactivate blocked, or no slot capacity |
| 500 | MongoDB down (`/api/health`) |

---

