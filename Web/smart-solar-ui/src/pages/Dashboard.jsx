import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { api } from "../api.js";
import { useAuth } from "../auth.jsx";
import { decorateBookings, greeting } from "../format.js";
import { Banner, Empty, Status, usePage } from "../shell.jsx";

const EMPTY_COUNTS = { pendingCount: 0, approvedFutureCount: 0, currentCount: 0, historyCount: 0 };

export default function Dashboard() {
  const { session, isBackoffice } = useAuth();
  usePage("Overview", `${greeting()}, ${session?.fullName || "there"}. Live position of bookings and the microgrid.`);
  const [counts, setCounts] = useState(EMPTY_COUNTS);
  const [queue, setQueue] = useState([]);
  const [stations, setStations] = useState(0);
  const [openSlots, setOpenSlots] = useState(0);
  const [prosumers, setProsumers] = useState(0);
  const [pendingPeople, setPendingPeople] = useState(0);
  const [error, setError] = useState("");

  useEffect(() => {
    let cancel = false;
    async function load() {
      try {
        const [dash, stationRows, slotRows, pending] = await Promise.all([
          api("/api/reservations/dashboard"),
          api("/api/stations"),
          api("/api/slots"),
          api("/api/reservations?status=Pending"),
        ]);
        let activeProsumers = 0;
        let waiting = 0;
        if (isBackoffice) {
          const users = await api("/api/users");
          activeProsumers = (users || []).filter((user) => user.role === "Prosumer" && user.status === "Active").length;
          waiting = (users || []).filter((user) => user.role === "Prosumer" && user.status === "Pending").length;
        }
        if (cancel) return;
        setCounts(dash || EMPTY_COUNTS);
        setStations((stationRows || []).length);
        setOpenSlots((slotRows || []).filter((slot) => slot.status === "Available").length);
        setQueue(decorateBookings(pending, stationRows, slotRows).slice(0, 6));
        setProsumers(activeProsumers);
        setPendingPeople(waiting);
        setError("");
      } catch (err) {
        if (!cancel) setError(err.message);
      }
    }
    load();
    return () => {
      cancel = true;
    };
  }, [isBackoffice]);

  const shown = (value) => (error ? "—" : value);

  return (
    <>
      <Banner>{error}</Banner>
      <section className="kpi-grid" aria-label="Booking counts">
        <Link className="kpi" to="/bookings?scope=pending">
          <span>Pending approval</span>
          <strong>{shown(counts.pendingCount)}</strong>
          <small>Waiting for a decision</small>
        </Link>
        <Link className="kpi" to="/bookings?scope=approved">
          <span>Approved upcoming</span>
          <strong>{shown(counts.approvedFutureCount)}</strong>
          <small>Approved, still in the future</small>
        </Link>
        <Link className="kpi" to="/bookings?scope=current">
          <span>Current bookings</span>
          <strong>{shown(counts.currentCount)}</strong>
          <small>Pending and approved</small>
        </Link>
        <Link className="kpi" to="/bookings?scope=history">
          <span>History</span>
          <strong>{shown(counts.historyCount)}</strong>
          <small>Completed or cancelled</small>
        </Link>
      </section>
      <section className="kpi-grid secondary" aria-label="Network counts">
        {isBackoffice && (
          <>
            <Link className="kpi quiet" to="/prosumers?status=Active">
              <span>Active prosumers</span>
              <strong>{shown(prosumers)}</strong>
            </Link>
            <Link className="kpi quiet" to="/prosumers?status=Pending">
              <span>Awaiting activation</span>
              <strong>{shown(pendingPeople)}</strong>
            </Link>
          </>
        )}
        <Link className="kpi quiet" to="/stations">
          <span>Hubs in view</span>
          <strong>{shown(stations)}</strong>
        </Link>
        <Link className="kpi quiet" to="/slots?status=Available">
          <span>Open slots</span>
          <strong>{shown(openSlots)}</strong>
        </Link>
      </section>
      <div className="split">
        <section className="panel">
          <header className="panel-head">
            <h2>Approval queue</h2>
            <Link to="/bookings?scope=pending">Open pending</Link>
          </header>
          {queue.length === 0 ? (
            <Empty>No bookings are waiting for approval.</Empty>
          ) : (
            <div className="table-panel">
              <table className="table">
                <thead>
                  <tr>
                    <th>NIC</th>
                    <th>Hub</th>
                    <th>Window</th>
                    <th>Status</th>
                  </tr>
                </thead>
                <tbody>
                  {queue.map((row) => (
                    <tr key={row.reservation.id}>
                      <td className="mono">{row.reservation.prosumerNic}</td>
                      <td>{row.stationName}</td>
                      <td>{row.window}</td>
                      <td><Status value={row.reservation.status} /></td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </section>
        <aside className="panel rules">
          <h2>Operating rules</h2>
          <ul>
            <li>Reservations must be scheduled within 7 days.</li>
            <li>Updates and cancellations need at least 12 hours&apos; notice.</li>
            <li>A hub or slot cannot be closed while a pending or approved booking still uses it.</li>
            <li>Only a Backoffice officer can reactivate a deactivated account.</li>
          </ul>
          <div className="form-actions">
            <Link className="btn btn-primary" to="/bookings/new">New booking</Link>
            {isBackoffice && <Link className="btn btn-ghost" to="/stations/new">New hub</Link>}
          </div>
        </aside>
      </div>
    </>
  );
}
