import { BrowserRouter, Navigate, Route, Routes } from "react-router-dom";
import { AuthProvider, RequireBackoffice, RequireStaff } from "./auth.jsx";
import { BookingForm, Bookings } from "./pages/Bookings.jsx";
import Dashboard from "./pages/Dashboard.jsx";
import Denied from "./pages/Denied.jsx";
import Login from "./pages/Login.jsx";
import { ProsumerCreate, Prosumers } from "./pages/Prosumers.jsx";
import { SlotForm, Slots } from "./pages/Slots.jsx";
import { Staff, StaffCreate } from "./pages/Staff.jsx";
import { StationForm, Stations } from "./pages/Stations.jsx";
import Verify from "./pages/Verify.jsx";
import { Shell } from "./shell.jsx";

export default function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
        <Routes>
          <Route path="/login" element={<Login />} />
          <Route element={<RequireStaff />}>
            <Route element={<Shell />}>
              <Route path="/" element={<Navigate to="/dashboard" replace />} />
              <Route path="/dashboard" element={<Dashboard />} />
              <Route path="/bookings" element={<Bookings />} />
              <Route path="/bookings/new" element={<BookingForm />} />
              <Route path="/bookings/:id/edit" element={<BookingForm />} />
              <Route path="/verify" element={<Verify />} />
              <Route path="/slots" element={<Slots />} />
              <Route path="/slots/new" element={<SlotForm />} />
              <Route path="/slots/:id/edit" element={<SlotForm />} />
              <Route path="/stations" element={<Stations />} />
              <Route path="/denied" element={<Denied />} />
              <Route element={<RequireBackoffice />}>
                <Route path="/stations/new" element={<StationForm />} />
                <Route path="/stations/:id/edit" element={<StationForm />} />
                <Route path="/staff" element={<Staff />} />
                <Route path="/staff/new" element={<StaffCreate />} />
                <Route path="/prosumers" element={<Prosumers />} />
                <Route path="/prosumers/new" element={<ProsumerCreate />} />
              </Route>
            </Route>
          </Route>
          <Route path="*" element={<Navigate to="/dashboard" replace />} />
        </Routes>
      </BrowserRouter>
    </AuthProvider>
  );
}
