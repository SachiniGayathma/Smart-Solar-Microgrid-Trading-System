import { Link } from "react-router-dom";
import { usePage } from "../shell.jsx";

export default function Denied() {
  usePage("Access limited", "Backoffice administers the system. Grid Operators use the operational tools.");
  return (
    <section className="panel narrow">
      <h2>This area is for Backoffice</h2>
      <p className="lede">Grid Operators work with bookings, energy slots, hub availability, and QR verification. Staff accounts, prosumer records, and hub administration stay with Backoffice.</p>
      <Link className="btn btn-primary" to="/dashboard">Back to overview</Link>
    </section>
  );
}
